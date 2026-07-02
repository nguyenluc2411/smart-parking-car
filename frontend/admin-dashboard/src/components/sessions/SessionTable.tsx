"use client";

import Link from "next/link";
import type { SessionListItem } from "@/types";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { SessionStatusBadge } from "@/components/StatusBadge";
import { formatDateTime, formatDuration } from "@/lib/utils";

export function SessionTable({ rows }: { rows: SessionListItem[] }) {
  if (rows.length === 0) {
    return (
      <p className="py-12 text-center text-sm text-muted-foreground">
        Không có phiên nào khớp bộ lọc.
      </p>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Biển số</TableHead>
          <TableHead>Chỗ</TableHead>
          <TableHead>Giờ vào</TableHead>
          <TableHead>Giờ ra</TableHead>
          <TableHead>Thời lượng</TableHead>
          <TableHead>Trạng thái</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((s) => (
          <TableRow key={s.id} className="cursor-pointer">
            <TableCell className="font-medium">
              <Link href={`/sessions/${s.id}`} className="hover:underline">
                {s.plateNumber}
              </Link>
            </TableCell>
            <TableCell>{s.slotCode ?? "—"}</TableCell>
            <TableCell>{formatDateTime(s.entryTime)}</TableCell>
            <TableCell>{formatDateTime(s.exitTime)}</TableCell>
            <TableCell>{formatDuration(s.durationSeconds)}</TableCell>
            <TableCell>
              <SessionStatusBadge status={s.status} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
