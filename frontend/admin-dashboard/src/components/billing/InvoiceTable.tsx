"use client";

import type { Invoice } from "@/types";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { InvoiceStatusBadge } from "@/components/StatusBadge";
import { formatCurrency, formatDateTime } from "@/lib/utils";

export function InvoiceTable({
  rows,
  selectedId,
  onSelect,
}: {
  rows: Invoice[];
  selectedId?: string;
  onSelect: (invoice: Invoice) => void;
}) {
  if (rows.length === 0) {
    return (
      <p className="py-12 text-center text-sm text-muted-foreground">
        Không có hóa đơn nào khớp bộ lọc.
      </p>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Biển số</TableHead>
          <TableHead>Giờ ra</TableHead>
          <TableHead>Thời lượng</TableHead>
          <TableHead className="text-right">Số tiền</TableHead>
          <TableHead>Trạng thái</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((inv) => (
          <TableRow
            key={inv.invoiceId}
            onClick={() => onSelect(inv)}
            data-state={inv.sessionId === selectedId ? "selected" : undefined}
            className="cursor-pointer"
          >
            <TableCell className="font-medium">{inv.plateNumber}</TableCell>
            <TableCell>{formatDateTime(inv.exitTime)}</TableCell>
            <TableCell>{inv.durationMinutes} phút</TableCell>
            <TableCell className="text-right font-medium">
              {formatCurrency(inv.amount)}
            </TableCell>
            <TableCell>
              <InvoiceStatusBadge status={inv.status} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
