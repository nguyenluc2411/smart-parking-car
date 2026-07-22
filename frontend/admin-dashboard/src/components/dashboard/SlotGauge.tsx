"use client";

import React from "react";
import type { SlotAvailability } from "@/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

const segmentConfig = [
  {
    key: "emptySlots" as keyof SlotAvailability,
    label: "Trống",
    colorClass: "text-emerald-500",
    bgClass: "bg-emerald-500/8 dark:bg-emerald-500/12 border-emerald-500/20",
    dotClass: "bg-emerald-500",
  },
  {
    key: "occupiedSlots" as keyof SlotAvailability,
    label: "Đang đỗ",
    colorClass: "text-indigo-500",
    bgClass: "bg-indigo-500/8 dark:bg-indigo-500/12 border-indigo-500/20",
    dotClass: "bg-indigo-500",
  },
  {
    key: "reservedSlots" as keyof SlotAvailability,
    label: "Đã đặt",
    colorClass: "text-cyan-500",
    bgClass: "bg-cyan-500/8 dark:bg-cyan-500/12 border-cyan-500/20",
    dotClass: "bg-cyan-500",
  },
  {
    key: "maintenanceSlots" as keyof SlotAvailability,
    label: "Bảo trì",
    colorClass: "text-amber-500",
    bgClass: "bg-amber-500/8 dark:bg-amber-500/12 border-amber-500/20",
    dotClass: "bg-amber-500",
  },
] as const;

export const SlotGauge = React.memo(function SlotGauge({ data }: { data: SlotAvailability }) {
  const pct = Math.round(data.occupancyRate * 100);
  // Reserved slots are unavailable to a walk-in (BR-009-4), so they belong in the same count the
  // percentage is built from — otherwise the fraction beside it reads lower than the bar.
  const usedSlots = data.occupiedSlots + (data.reservedSlots ?? 0);

  const barGradient =
    pct >= 90
      ? "linear-gradient(90deg, hsl(var(--destructive)) 0%, hsl(var(--destructive) / 0.85) 100%)"
      : pct >= 70
      ? "linear-gradient(90deg, hsl(var(--warning)) 0%, hsl(var(--warning) / 0.85) 100%)"
      : "linear-gradient(90deg, hsl(var(--brand-from)) 0%, hsl(var(--brand-to)) 100%)";

  return (
    <Card className="border border-border/50 bg-card shadow-sm h-full overflow-hidden">
      <CardHeader className="px-5 pb-2 pt-5">
        <div className="flex items-center justify-between">
          <CardTitle className="text-[12px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
            Tỷ lệ lấp đầy
          </CardTitle>
          <span className={cn(
            "rounded-full px-2.5 py-0.5 text-[11px] font-bold",
            pct >= 90
              ? "bg-red-500/10 text-red-500"
              : pct >= 70
              ? "bg-amber-500/10 text-amber-500"
              : "bg-indigo-500/10 text-indigo-500"
          )}>
            {pct >= 90 ? "Gần đầy" : pct >= 70 ? "Cao" : "Bình thường"}
          </span>
        </div>
      </CardHeader>

      <CardContent className="px-5 pb-5">
        {/* Big percentage */}
        <div className="flex items-end justify-between mt-1 mb-4">
          <div>
            <span className="text-[3.5rem] font-extrabold leading-none tabular-nums text-foreground">
              {pct}
            </span>
            <span className="text-xl font-bold text-muted-foreground ml-0.5">%</span>
          </div>
          <p className="text-sm text-muted-foreground tabular-nums font-medium pb-1">
            {usedSlots}<span className="text-muted-foreground/50 mx-1">/</span>{data.totalSlots} chỗ
          </p>
        </div>

        {/* Progress track */}
        <div className="h-2.5 w-full overflow-hidden rounded-full bg-muted/60">
          <div
            className="h-full rounded-full transition-all duration-700 ease-out"
            style={{ width: `${pct}%`, background: barGradient }}
          />
        </div>

        {/* Segment cards */}
        <div className="mt-5 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          {segmentConfig.map(({ key, label, colorClass, bgClass, dotClass }) => (
            <div
              key={key}
              className={cn(
                "flex flex-col items-center gap-1 rounded-xl border px-2 py-3 transition-all",
                bgClass
              )}
            >
              <div className="flex items-center gap-1.5">
                <span className={cn("h-2 w-2 rounded-full shrink-0", dotClass)} />
                <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">
                  {label}
                </span>
              </div>
              <p className={cn("text-2xl font-bold tabular-nums leading-none", colorClass)}>
                {data[key] as number}
              </p>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
});
