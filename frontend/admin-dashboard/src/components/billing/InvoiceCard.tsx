"use client";

import type { Invoice } from "@/types";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { InvoiceStatusBadge } from "@/components/StatusBadge";
import { formatCurrency, formatDateTime } from "@/lib/utils";

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between py-2 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}

export function InvoiceCard({ invoice }: { invoice: Invoice }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{invoice.plateNumber}</CardTitle>
        <InvoiceStatusBadge status={invoice.status} />
      </CardHeader>
      <CardContent>
        <Row label="Mã hóa đơn" value={invoice.invoiceId} />
        <Separator />
        <Row label="Giờ vào" value={formatDateTime(invoice.entryTime)} />
        <Separator />
        <Row label="Giờ ra" value={formatDateTime(invoice.exitTime)} />
        <Separator />
        <Row label="Thời lượng" value={`${invoice.durationMinutes} phút`} />
        <Separator />
        <Row
          label="Đơn giá"
          value={`${formatCurrency(invoice.ratePerMin)}/phút`}
        />
        <Separator />
        <Row
          label="Phụ phí"
          value={
            <span className="flex gap-1">
              {invoice.peakApplied && <Badge variant="warning">Cao điểm</Badge>}
              {invoice.overnightApplied && (
                <Badge variant="secondary">Qua đêm</Badge>
              )}
              {!invoice.peakApplied && !invoice.overnightApplied && "—"}
            </span>
          }
        />
        <Separator />
        <div className="flex items-center justify-between pt-3">
          <span className="text-sm font-semibold">Tổng cộng</span>
          <span className="text-xl font-bold">
            {formatCurrency(invoice.amount)}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}
