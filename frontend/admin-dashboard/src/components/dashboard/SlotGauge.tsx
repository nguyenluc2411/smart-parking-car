"use client";

import type { SlotAvailability } from "@/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function SlotGauge({ data }: { data: SlotAvailability }) {
  const pct = Math.round(data.occupancyRate * 100);

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">
          Tỷ lệ lấp đầy
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex items-end justify-between">
          <span className="text-3xl font-bold">{pct}%</span>
          <span className="text-sm text-muted-foreground">
            {data.occupiedSlots}/{data.totalSlots} chỗ
          </span>
        </div>
        <div className="mt-3 h-3 w-full overflow-hidden rounded-full bg-muted">
          <div
            className="brand-gradient h-full rounded-full transition-all duration-500"
            style={{ width: `${pct}%` }}
          />
        </div>
        <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
          <div className="rounded-lg bg-success/10 py-2">
            <p className="text-base font-bold text-success">{data.emptySlots}</p>
            <p className="text-muted-foreground">Trống</p>
          </div>
          <div className="rounded-lg bg-primary/10 py-2">
            <p className="text-base font-bold text-primary">
              {data.occupiedSlots}
            </p>
            <p className="text-muted-foreground">Đang đỗ</p>
          </div>
          <div className="rounded-lg bg-warning/10 py-2">
            <p className="text-base font-bold text-warning">
              {data.maintenanceSlots}
            </p>
            <p className="text-muted-foreground">Bảo trì</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
