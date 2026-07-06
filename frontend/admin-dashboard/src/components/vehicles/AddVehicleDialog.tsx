"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Plus, Loader2 } from "lucide-react";
import {
  useAddWhitelist,
  useAddBlacklist,
} from "@/lib/hooks/useVehicles";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import type { VehicleType } from "@/types";

export type Kind = "whitelist" | "blacklist";

export const KIND_LABEL: Record<Kind, string> = {
  whitelist: "Whitelist (xe ưu tiên)",
  blacklist: "Blacklist (xe cấm)",
};

const addVehicleSchema = z.object({
  plateNumber: z
    .string()
    .min(1, "Vui lòng nhập biển số")
    .regex(/^[0-9]{2}[A-Z]-[0-9]{3,4}\.[0-9]{2}$|^[0-9]{2}[A-Z][0-9]-[0-9]{3,4}\.[0-9]{2}$|^[0-9]{2}[A-Z]-[0-9]{4,5}$/, "Biển số không hợp lệ (VD: 51F-123.45)"),
  ownerName: z.string().optional(),
  note: z.string().optional(),
});
export type AddVehicleForm = z.infer<typeof addVehicleSchema>;

export function AddVehicleDialog({ kind }: { kind: Kind }) {
  const [open, setOpen] = useState(false);
  const [reclassifyFrom, setReclassifyFrom] = useState<VehicleType | null>(null);
  const [pendingForm, setPendingForm] = useState<AddVehicleForm | null>(null);

  const addWhite = useAddWhitelist();
  const addBlack = useAddBlacklist();
  const add = kind === "whitelist" ? addWhite : addBlack;
  const { toast } = useToast();

  const targetLabel = kind === "whitelist" ? "WHITELIST" : "BLACKLIST";

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AddVehicleForm>({
    resolver: zodResolver(addVehicleSchema),
    defaultValues: { plateNumber: "", ownerName: "", note: "" },
  });

  const resetForm = () => {
    setReclassifyFrom(null);
    setPendingForm(null);
    setOpen(false);
    reset();
  };

  const submit = (data: AddVehicleForm, force: boolean) => {
    add.mutate(
      { plateNumber: data.plateNumber, ownerName: data.ownerName ?? "", note: data.note ?? "", force },
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
          if (status === 409 && message.startsWith("RECLASSIFY:")) {
            setReclassifyFrom(message.split(":")[1] as VehicleType);
            setPendingForm(data);
            return;
          }
          toast(status === 400 ? "Biển số không hợp lệ." : "Thêm thất bại", {
            variant: "destructive",
          });
        },
      }
    );
  };

  const onSubmit = (data: AddVehicleForm) => submit(data, false);

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

        <Dialog
          open={reclassifyFrom !== null}
          onOpenChange={(o) => !o && setReclassifyFrom(null)}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Chuyển danh sách?</DialogTitle>
            </DialogHeader>
            <p className="text-sm text-muted-foreground">
              Biển <span className="font-medium text-foreground">{pendingForm?.plateNumber}</span>{" "}
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
              <Button onClick={() => pendingForm && submit(pendingForm, true)} disabled={add.isPending}>
                {add.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                Chuyển
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="plate">Biển số</Label>
            <Input
              id="plate"
              placeholder="51F-123.45"
              {...register("plateNumber")}
            />
            {errors.plateNumber && (
              <p className="text-xs text-destructive">{errors.plateNumber.message}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="owner">Chủ xe</Label>
            <Input
              id="owner"
              placeholder="Nguyễn Văn A"
              {...register("ownerName")}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="note">
              {kind === "blacklist" ? "Lý do cấm" : "Ghi chú"}
            </Label>
            <Input
              id="note"
              {...register("note")}
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={add.isPending}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={add.isPending}>
              {add.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Thêm
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
