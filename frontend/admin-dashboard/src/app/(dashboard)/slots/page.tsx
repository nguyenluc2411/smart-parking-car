"use client";

import { useState } from "react";
import { Plus, Trash2, Loader2, Wrench, RotateCcw } from "lucide-react";
import {
  useSlots,
  useCreateSlot,
  useDeleteSlot,
  useUpdateSlotStatus,
  useProvisionZone,
} from "@/lib/hooks/useSlots";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { SlotStatusBadge } from "@/components/StatusBadge";
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
import { cn } from "@/lib/utils";
import type { Slot } from "@/types";

type Tab = "provision" | "list";

function getErrorMessage(e: unknown): string {
  const err = e as { response?: { data?: { error?: { message?: string } } } };
  return err?.response?.data?.error?.message ?? "Thao tác thất bại";
}

// ─── Tab 1: Cấu hình nhanh theo khu ──────────────────────────────────────────
function ProvisionTab({ slots }: { slots: Slot[] }) {
  const [zone, setZone] = useState("A");
  const [count, setCount] = useState("10");
  const provision = useProvisionZone();
  const { toast } = useToast();

  const zones = Array.from(
    slots.reduce((map, s) => {
      const z = map.get(s.zone) ?? { total: 0, occupied: 0 };
      z.total += 1;
      if (s.status === "OCCUPIED") z.occupied += 1;
      return map.set(s.zone, z);
    }, new Map<string, { total: number; occupied: number }>())
  ).sort((a, b) => a[0].localeCompare(b[0]));

  const onApply = () => {
    const n = Number(count);
    if (!zone.trim()) return toast("Nhập tên khu", { variant: "destructive" });
    if (!Number.isInteger(n) || n < 0)
      return toast("Số lượng không hợp lệ", { variant: "destructive" });
    provision.mutate(
      { zone: zone.trim().toUpperCase(), count: n },
      {
        onSuccess: (res) => {
          const d = res.data;
          toast(
            `Khu ${d.zone}: +${d.created} / -${d.removed} (tổng bãi ${d.total})`,
            { variant: "success" }
          );
        },
        onError: (e) => toast(getErrorMessage(e), { variant: "destructive" }),
      }
    );
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="space-y-4 pt-6">
          <p className="text-sm text-muted-foreground">
            Đặt một khu có đúng số slot mong muốn. Hệ thống tự sinh mã{" "}
            <code>{zone || "A"}01…</code>, tạo phần thiếu và xóa phần dư.
            Không xóa slot đang có xe.
          </p>
          <div className="flex flex-wrap items-end gap-3">
            <div className="space-y-2">
              <Label htmlFor="zone">Khu</Label>
              <Input
                id="zone"
                className="w-24"
                value={zone}
                maxLength={5}
                onChange={(e) => setZone(e.target.value)}
                placeholder="A"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="count">Số slot</Label>
              <Input
                id="count"
                className="w-28"
                type="number"
                min={0}
                value={count}
                onChange={(e) => setCount(e.target.value)}
              />
            </div>
            <Button onClick={onApply} disabled={provision.isPending}>
              {provision.isPending && (
                <Loader2 className="h-4 w-4 animate-spin" />
              )}
              Áp dụng
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <h3 className="mb-3 text-sm font-medium">Các khu hiện có</h3>
          {zones.length === 0 ? (
            <p className="text-sm text-muted-foreground">Chưa có slot nào.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Khu</TableHead>
                  <TableHead>Tổng slot</TableHead>
                  <TableHead>Đang có xe</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {zones.map(([z, info]) => (
                  <TableRow key={z}>
                    <TableCell className="font-medium">{z}</TableCell>
                    <TableCell>{info.total}</TableCell>
                    <TableCell>{info.occupied}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Tab 2: Danh sách slot ───────────────────────────────────────────────────
function AddSlotDialog() {
  const [open, setOpen] = useState(false);
  const [slotCode, setSlotCode] = useState("");
  const [zone, setZone] = useState("A");
  const create = useCreateSlot();
  const { toast } = useToast();

  const onSubmit = () => {
    if (!slotCode.trim() || !zone.trim())
      return toast("Nhập mã slot và khu", { variant: "destructive" });
    create.mutate(
      { slotCode: slotCode.trim().toUpperCase(), zone: zone.trim().toUpperCase() },
      {
        onSuccess: () => {
          toast("Đã thêm slot", { variant: "success" });
          setOpen(false);
          setSlotCode("");
        },
        onError: (e) => toast(getErrorMessage(e), { variant: "destructive" }),
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4" />
          Thêm slot
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Thêm slot</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="code">Mã slot</Label>
            <Input
              id="code"
              value={slotCode}
              onChange={(e) => setSlotCode(e.target.value)}
              placeholder="A11"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="z">Khu</Label>
            <Input
              id="z"
              value={zone}
              onChange={(e) => setZone(e.target.value)}
              placeholder="A"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)} disabled={create.isPending}>
            Hủy
          </Button>
          <Button onClick={onSubmit} disabled={create.isPending}>
            {create.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Thêm
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function SlotList({ slots }: { slots: Slot[] }) {
  const del = useDeleteSlot();
  const update = useUpdateSlotStatus();
  const { toast } = useToast();

  const onToggleMaintenance = (s: Slot) => {
    const next = s.status === "MAINTENANCE" ? "EMPTY" : "MAINTENANCE";
    update.mutate(
      { id: s.id, body: { status: next } },
      {
        onSuccess: () =>
          toast(next === "MAINTENANCE" ? "Đã đặt bảo trì" : "Đã mở lại slot", {
            variant: "success",
          }),
        onError: (e) => toast(getErrorMessage(e), { variant: "destructive" }),
      }
    );
  };

  const onDelete = (s: Slot) => {
    del.mutate(s.id, {
      onSuccess: () => toast("Đã xóa slot", { variant: "success" }),
      onError: (e) => toast(getErrorMessage(e), { variant: "destructive" }),
    });
  };

  return (
    <Card>
      <CardContent className="pt-6">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Mã slot</TableHead>
              <TableHead>Khu</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {slots.map((s) => {
              const occupied = s.status === "OCCUPIED";
              return (
                <TableRow key={s.id}>
                  <TableCell className="font-medium">{s.slotCode}</TableCell>
                  <TableCell>{s.zone}</TableCell>
                  <TableCell>
                    <SlotStatusBadge status={s.status} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="icon"
                      title={
                        s.status === "MAINTENANCE" ? "Mở lại" : "Đặt bảo trì"
                      }
                      onClick={() => onToggleMaintenance(s)}
                      disabled={occupied || update.isPending}
                    >
                      {s.status === "MAINTENANCE" ? (
                        <RotateCcw className="h-4 w-4" />
                      ) : (
                        <Wrench className="h-4 w-4" />
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="text-destructive"
                      title={occupied ? "Đang có xe, không thể xóa" : "Xóa"}
                      onClick={() => onDelete(s)}
                      disabled={occupied || del.isPending}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

export default function SlotsPage() {
  const [tab, setTab] = useState<Tab>("provision");
  const query = useSlots();

  return (
    <RoleGuard allow={["ADMIN"]}>
      <PageHeader
        title="Bãi gửi xe / Slot"
        description="Thiết lập sức chứa bãi: cấu hình theo khu hoặc quản lý từng slot"
        action={tab === "list" ? <AddSlotDialog /> : undefined}
      />

      <div className="mb-4 inline-flex rounded-lg border p-1">
        {(
          [
            ["provision", "Cấu hình theo khu"],
            ["list", "Danh sách slot"],
          ] as const
        ).map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={cn(
              "rounded-md px-4 py-1.5 text-sm font-medium transition-colors",
              tab === k
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {query.isError ? (
        <ErrorState onRetry={() => query.refetch()} />
      ) : query.isLoading || !query.data ? (
        <Spinner />
      ) : tab === "provision" ? (
        <ProvisionTab slots={query.data} />
      ) : (
        <SlotList slots={query.data} />
      )}
    </RoleGuard>
  );
}
