"use client";

import { useState } from "react";
import { Plus, Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useCreateSlot } from "@/lib/hooks/useSlots";
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
import { getApiErrorMessage } from "@/lib/utils";

const addSlotSchema = z.object({
  slotCode: z.string().min(1, "Vui lòng nhập mã slot").max(10, "Mã slot quá dài"),
  zone: z.string().min(1, "Vui lòng nhập khu").max(5, "Tên khu quá dài"),
});
type AddSlotForm = z.infer<typeof addSlotSchema>;

export function AddSlotDialog() {
  const [open, setOpen] = useState(false);
  const create = useCreateSlot();
  const { toast } = useToast();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AddSlotForm>({
    resolver: zodResolver(addSlotSchema),
    defaultValues: { slotCode: "", zone: "A" },
  });

  const onSubmit = (data: AddSlotForm) => {
    create.mutate(
      { slotCode: data.slotCode.trim().toUpperCase(), zone: data.zone.trim().toUpperCase() },
      {
        onSuccess: () => {
          toast("Đã thêm slot", { variant: "success" });
          setOpen(false);
          reset();
        },
        onError: (e) => toast(getApiErrorMessage(e), { variant: "destructive" }),
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
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="code">Mã slot</Label>
            <Input
              id="code"
              placeholder="A11"
              {...register("slotCode")}
            />
            {errors.slotCode && (
              <p className="text-xs text-destructive">{errors.slotCode.message}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="z">Khu</Label>
            <Input
              id="z"
              placeholder="A"
              {...register("zone")}
            />
            {errors.zone && (
              <p className="text-xs text-destructive">{errors.zone.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={create.isPending}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Thêm
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
