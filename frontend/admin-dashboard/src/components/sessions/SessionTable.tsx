"use client";

import React from "react";
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

export const SessionTable = React.memo(function SessionTable({ rows }: { rows: SessionListItem[] }) {
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
          <TableRow key={s.id} className="cursor-pointer group">
            <TableCell className="font-medium">
              <Link href={`/sessions/${s.id}`} className="inline-flex items-center">
                <span className="font-mono text-xs font-semibold bg-secondary/50 border border-border/50 px-2 py-1 rounded transition-colors group-hover:bg-secondary">
                  {s.plateNumber}
                </span>
              </Link>
            </TableCell>
            <TableCell className="font-medium text-muted-foreground">{s.slotCode ?? "—"}</TableCell>
            <TableCell className="tabular-nums text-[13px] text-muted-foreground">{formatDateTime(s.entryTime)}</TableCell>
            <TableCell className="tabular-nums text-[13px] text-muted-foreground">{formatDateTime(s.exitTime)}</TableCell>
            <TableCell className="tabular-nums text-[13px] text-muted-foreground">{formatDuration(s.durationSeconds)}</TableCell>
            <TableCell>
              <SessionStatusBadge status={s.status} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
});
