"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import type { Invoice, PaymentMethod } from "@/types";
import { usePayInvoice } from "@/lib/hooks/useBilling";
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
import { formatCurrency } from "@/lib/utils";

export function PaymentDialog({ invoice }: { invoice: Invoice }) {
  const [open, setOpen] = useState(false);
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [amountPaid, setAmountPaid] = useState(String(invoice.amount));
  const [note, setNote] = useState("");
  const pay = usePayInvoice();
  const { toast } = useToast();

  const disabled = invoice.status !== "PENDING";

  const onConfirm = () => {
    pay.mutate(
      {
        sessionId: invoice.sessionId,
        body: { method, amountPaid: Number(amountPaid), note },
      },
      {
        onSuccess: () => {
          toast("Đã ghi nhận thanh toán", { variant: "success" });
          setOpen(false);
        },
        onError: () =>
          toast("Thanh toán thất bại", { variant: "destructive" }),
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button className="w-full" disabled={disabled}>
          {disabled ? "Đã thanh toán" : "Xác nhận thanh toán"}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Xác nhận thanh toán</DialogTitle>
          <DialogDescription>
            Hóa đơn {invoice.plateNumber} — {formatCurrency(invoice.amount)}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label>Phương thức</Label>
            <Select
              value={method}
              onValueChange={(v) => setMethod(v as PaymentMethod)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="CASH">Tiền mặt</SelectItem>
                <SelectItem value="QR_CODE">Quét mã QR</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="amount">Số tiền khách trả</Label>
            <Input
              id="amount"
              type="number"
              value={amountPaid}
              onChange={(e) => setAmountPaid(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="note">Ghi chú</Label>
            <Input
              id="note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Tùy chọn"
            />
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={pay.isPending}
          >
            Hủy
          </Button>
          <Button onClick={onConfirm} disabled={pay.isPending}>
            {pay.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Xác nhận
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
