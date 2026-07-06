"use client";

import {
  Area,
  AreaChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatCurrency } from "@/lib/utils";

interface CustomTooltipProps {
  active?: boolean;
  payload?: { value: number }[];
  label?: string;
}

function CustomTooltip({ active, payload, label }: CustomTooltipProps) {
  if (active && payload && payload.length) {
    let formattedDate = label ?? "";
    try {
      if (label) {
        formattedDate = new Date(label).toLocaleDateString("vi-VN", {
          day: "numeric",
          month: "numeric",
        });
      }
    } catch {
      // fallback
    }
    return (
      <div className="rounded-xl border border-border/50 bg-background/95 p-3 shadow-xl backdrop-blur-md">
        <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider">{`Ngày ${formattedDate}`}</p>
        <p className="text-sm font-extrabold text-foreground mt-1">
          {formatCurrency(payload[0].value)}
        </p>
      </div>
    );
  }
  return null;
}

export default function MonthlyChart({
  data,
}: {
  data: { date: string; revenue: number }[];
}) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <AreaChart data={data} margin={{ top: 10, right: 10, left: -5, bottom: 0 }}>
        <defs>
          <linearGradient id="monthlyRevenueGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.4} />
            <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0} />
          </linearGradient>
        </defs>
        <XAxis
          dataKey="date"
          tickFormatter={(d: string) => d.slice(8)}
          fontSize={11}
          tickLine={false}
          axisLine={false}
          stroke="hsl(var(--muted-foreground))"
        />
        <YAxis
          fontSize={11}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => `${(v / 1_000_000).toFixed(1)}M`}
          width={40}
          stroke="hsl(var(--muted-foreground))"
        />
        <Tooltip content={<CustomTooltip />} />
        <Area
          type="monotone"
          dataKey="revenue"
          stroke="hsl(var(--primary))"
          strokeWidth={2.5}
          fillOpacity={1}
          fill="url(#monthlyRevenueGrad)"
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
