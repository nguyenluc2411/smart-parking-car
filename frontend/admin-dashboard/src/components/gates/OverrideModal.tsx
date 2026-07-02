"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import type { Gate, GateCommand } from "@/types";
import { useGateOverride } from "@/lib/hooks/useGates";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

export function OverrideModal({ gate }: { gate: Gate }) {
  const [open, setOpen] = useState(false);
  const [command, setCommand] = useState<GateCommand>("OPEN");
  const [reason, setReason] = useState("");
  const override = useGateOverride();
  const { toast } = useToast();

  const onConfirm = () => {
    if (!reason.trim()) {
      toast("Vui lòng nhập lý do", { variant: "destructive" });
      return;
    }
    override.mutate(
      { id: gate.id, body: { command, reason } },
      {
        onSuccess: () => {
          toast(`Đã gửi lệnh ${command} tới ${gate.gateCode}`, {
            variant: "success",
          });
          setOpen(false);
          setReason("");
        },
        onError: () =>
          toast("Override thất bại", { variant: "destructive" }),
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          Override
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Điều khiển thủ công · {gate.gateCode}</DialogTitle>
          <DialogDescription>
            Gửi lệnh trực tiếp tới barie. Thao tác này được ghi vào audit log.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label>Lệnh</Label>
            <Select
              value={command}
              onValueChange={(v) => setCommand(v as GateCommand)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="OPEN">Mở barie (OPEN)</SelectItem>
                <SelectItem value="CLOSE">Đóng barie (CLOSE)</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="reason">Lý do</Label>
            <Input
              id="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="VD: Xe bị kẹt, cần mở thủ công"
            />
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={override.isPending}
          >
            Hủy
          </Button>
          <Button onClick={onConfirm} disabled={override.isPending}>
            {override.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Gửi lệnh
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
