"use client";

import React from "react";
import {
  Bar,
  BarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  CartesianGrid,
  Cell,
} from "recharts";
import { formatCurrency } from "@/lib/utils";

interface CustomTooltipProps {
  active?: boolean;
  payload?: { value: number; payload: { hour: number; sessions: number } }[];
  label?: number;
}

function CustomTooltip({ active, payload, label }: CustomTooltipProps) {
  if (active && payload && payload.length) {
    return (
      <div className="rounded-xl border border-border/50 bg-background/95 p-3.5 shadow-xl backdrop-blur-md min-w-[140px]">
        <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-2">
          {`${label}:00 — ${((label ?? 0) + 1) % 24}:00`}
        </p>
        <p className="text-base font-bold tabular-nums text-foreground">
          {formatCurrency(payload[0].value)}
        </p>
        {payload[0].payload.sessions !== undefined && (
          <p className="mt-0.5 text-[11px] text-muted-foreground">
            {payload[0].payload.sessions} lượt gửi
          </p>
        )}
      </div>
    );
  }
  return null;
}

const RevenueChartComponent = ({
  data,
}: {
  data: { hour: number; revenue: number; sessions: number }[];
}) => {
  // Normalize data to ensure all 24 hours (0 to 23) are represented in the chart
  const normalizedData = React.useMemo(() => {
    const map = new Map(data.map((d) => [d.hour, d]));
    return Array.from({ length: 24 }, (_, i) => {
      return map.get(i) || { hour: i, revenue: 0, sessions: 0 };
    });
  }, [data]);

  // Find max to highlight that bar
  const maxRevenue = Math.max(...normalizedData.map((d) => d.revenue), 0);

  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={normalizedData} margin={{ top: 5, right: 5, left: -5, bottom: 0 }} barCategoryGap="25%">
        <defs>
          <linearGradient id="revenueGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="hsl(var(--brand-from))" stopOpacity={1} />
            <stop offset="100%" stopColor="hsl(var(--brand-to))" stopOpacity={0.75} />
          </linearGradient>
          <linearGradient id="revenueGradientPeak" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="hsl(var(--warning))" stopOpacity={1} />
            <stop offset="100%" stopColor="hsl(var(--warning) / 0.8)" stopOpacity={0.8} />
          </linearGradient>
        </defs>
        <CartesianGrid
          vertical={false}
          stroke="hsl(var(--border))"
          strokeDasharray="3 3"
          strokeOpacity={0.5}
        />
        <XAxis
          dataKey="hour"
          tickFormatter={(h) => `${h}h`}
          fontSize={11}
          tickLine={false}
          axisLine={false}
          stroke="hsl(var(--muted-foreground))"
          dy={8}
          interval={1}
        />
        <YAxis
          fontSize={11}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => (v > 0 ? `${(v / 1000).toFixed(0)}k` : "0")}
          width={46}
          stroke="hsl(var(--muted-foreground))"
          dx={-2}
        />
        <Tooltip
          content={<CustomTooltip />}
          cursor={{ fill: "hsl(var(--muted))", opacity: 0.3, radius: 4 }}
        />
        <Bar dataKey="revenue" radius={[4, 4, 0, 0]} maxBarSize={32}>
          {normalizedData.map((entry, index) => (
            <Cell
              key={`cell-${index}`}
              fill={entry.revenue === maxRevenue && maxRevenue > 0
                ? "url(#revenueGradientPeak)"
                : "url(#revenueGradient)"
              }
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
};

export default React.memo(RevenueChartComponent);
