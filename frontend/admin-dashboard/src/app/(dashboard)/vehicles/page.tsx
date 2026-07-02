"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Plus, Trash2, Loader2 } from "lucide-react";
import {
  useWhitelist,
  useAddWhitelist,
  useDeleteWhitelist,
  useBlacklist,
  useAddBlacklist,
  useDeleteBlacklist,
} from "@/lib/hooks/useVehicles";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { VehicleTypeBadge } from "@/components/StatusBadge";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { ErrorState } from "@/components/ui/error-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { formatDateTime, cn } from "@/lib/utils";
import type { VehicleType } from "@/types";

type Kind = "whitelist" | "blacklist";

const KIND_LABEL: Record<Kind, string> = {
  whitelist: "Whitelist (xe ưu tiên)",
  blacklist: "Blacklist (xe cấm)",
};

function AddVehicleDialog({ kind }: { kind: Kind }) {
  const [open, setOpen] = useState(false);
  const [plateNumber, setPlateNumber] = useState("");
  const [ownerName, setOwnerName] = useState("");
  const [note, setNote] = useState("");
  // The plate's CURRENT list when a re-classification needs confirmation (409 RECLASSIFY); else null.
  const [reclassifyFrom, setReclassifyFrom] = useState<VehicleType | null>(null);
  const addWhite = useAddWhitelist();
  const addBlack = useAddBlacklist();
  const add = kind === "whitelist" ? addWhite : addBlack;
  const { toast } = useToast();

  const targetLabel = kind === "whitelist" ? "WHITELIST" : "BLACKLIST";

  const resetForm = () => {
    setReclassifyFrom(null);
    setOpen(false);
    setPlateNumber("");
    setOwnerName("");
    setNote("");
  };

  // force=true re-sends after the operator confirms moving the plate between lists.
  const submit = (force: boolean) => {
    add.mutate(
      { plateNumber, ownerName, note, force },
      {
        onSuccess: (res) => {
          const movedFrom = res.data.reclassifiedFrom;
          toast(
            movedFrom
              ? `Đã chuyển ${res.data.plateNumber} từ ${movedFrom} sang ${targetLabel}`
              : `Đã thêm vào ${kind === "whitelist" ? "whitelist" : "blacklist"}`,
            { variant: "success" }
          );
          resetForm();
        },
        onError: (e: unknown) => {
          const err = e as {
            response?: { status?: number; data?: { error?: { message?: string } } };
          };
          const status = err.response?.status;
          const message = err.response?.data?.error?.message ?? "";
          // Backend asks to confirm a whitelist<->blacklist move: "RECLASSIFY:{currentType}:..."
          if (status === 409 && message.startsWith("RECLASSIFY:")) {
            setReclassifyFrom(message.split(":")[1] as VehicleType);
            return;
          }
          toast(
            status === 400
              ? "Biển số không hợp lệ. Ví dụ: 51F-12345"
              : "Thêm thất bại",
            { variant: "destructive" }
          );
        },
      }
    );
  };

  const onSubmit = () => {
    if (!plateNumber.trim()) {
      toast("Vui lòng nhập biển số", { variant: "destructive" });
      return;
    }
    submit(false);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4" />
          Thêm xe
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Thêm xe vào {KIND_LABEL[kind]}</DialogTitle>
        </DialogHeader>

        {/* Confirm moving a plate already in the other list. */}
        <Dialog
          open={reclassifyFrom !== null}
          onOpenChange={(o) => !o && setReclassifyFrom(null)}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Chuyển danh sách?</DialogTitle>
            </DialogHeader>
            <p className="text-sm text-muted-foreground">
              Biển <span className="font-medium text-foreground">{plateNumber}</span>{" "}
              đang ở <span className="font-medium text-foreground">{reclassifyFrom}</span>.
              Chuyển sang <span className="font-medium text-foreground">{targetLabel}</span>?
            </p>
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setReclassifyFrom(null)}
                disabled={add.isPending}
              >
                Hủy
              </Button>
              <Button onClick={() => submit(true)} disabled={add.isPending}>
                {add.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                Chuyển
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="plate">Biển số</Label>
            <Input
              id="plate"
              value={plateNumber}
              onChange={(e) => setPlateNumber(e.target.value)}
              placeholder="51F-123.45"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="owner">Chủ xe</Label>
            <Input
              id="owner"
              value={ownerName}
              onChange={(e) => setOwnerName(e.target.value)}
              placeholder="Nguyễn Văn A"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="note">
              {kind === "blacklist" ? "Lý do cấm" : "Ghi chú"}
            </Label>
            <Input
              id="note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={add.isPending}
          >
            Hủy
          </Button>
          <Button onClick={onSubmit} disabled={add.isPending}>
            {add.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Thêm
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function VehicleTable({ kind }: { kind: Kind }) {
  const whitelist = useWhitelist();
  const blacklist = useBlacklist();
  const query = kind === "whitelist" ? whitelist : blacklist;

  const delWhite = useDeleteWhitelist();
  const delBlack = useDeleteBlacklist();
  const del = kind === "whitelist" ? delWhite : delBlack;
  const { toast } = useToast();

  const onDelete = (plate: string) => {
    del.mutate(plate, {
      onSuccess: () => toast("Đã xóa khỏi danh sách", { variant: "success" }),
      onError: () => toast("Xóa thất bại", { variant: "destructive" }),
    });
  };

  if (query.isError) return <ErrorState onRetry={() => query.refetch()} />;
  if (query.isLoading || !query.data) return <Spinner />;
  if (query.data.length === 0)
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        Danh sách trống
      </p>
    );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Biển số</TableHead>
          <TableHead>Loại</TableHead>
          <TableHead>Chủ xe</TableHead>
          <TableHead>{kind === "blacklist" ? "Lý do" : "Ghi chú"}</TableHead>
          <TableHead>Ngày tạo</TableHead>
          <TableHead className="w-12"></TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {query.data.map((v) => (
          <TableRow key={v.id}>
            <TableCell className="font-medium">{v.plateNumber}</TableCell>
            <TableCell>
              <VehicleTypeBadge type={v.vehicleType} />
            </TableCell>
            <TableCell>{v.ownerName ?? "—"}</TableCell>
            <TableCell className="text-muted-foreground">
              {v.note ?? "—"}
            </TableCell>
            <TableCell>{formatDateTime(v.createdAt)}</TableCell>
            <TableCell>
              <Button
                variant="ghost"
                size="icon"
                className="text-destructive"
                onClick={() => onDelete(v.plateNumber)}
                disabled={del.isPending}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function VehiclesContent() {
  const params = useSearchParams();
  // Deep-link from a BLACKLIST_HIT alert: /vehicles?tab=blacklist
  const tabParam: Kind = params.get("tab") === "blacklist" ? "blacklist" : "whitelist";
  const [kind, setKind] = useState<Kind>(tabParam);

  useEffect(() => {
    setKind(tabParam);
  }, [tabParam]);

  return (
    <RoleGuard allow={["ADMIN"]}>
      <PageHeader
        title="Whitelist / Blacklist"
        description="Quản lý xe ưu tiên (mở ngay, miễn phí) và xe cấm (từ chối vào)"
        action={<AddVehicleDialog kind={kind} />}
      />

      <div className="mb-4 inline-flex rounded-lg border p-1">
        {(["whitelist", "blacklist"] as const).map((k) => (
          <button
            key={k}
            type="button"
            onClick={() => setKind(k)}
            className={cn(
              "rounded-md px-4 py-1.5 text-sm font-medium transition-colors",
              kind === k
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            {KIND_LABEL[k]}
          </button>
        ))}
      </div>

      <Card>
        <CardContent className="pt-6">
          <VehicleTable kind={kind} />
        </CardContent>
      </Card>
    </RoleGuard>
  );
}

export default function VehiclesPage() {
  // useSearchParams requires a Suspense boundary in the App Router.
  return (
    <Suspense fallback={<Spinner />}>
      <VehiclesContent />
    </Suspense>
  );
}
