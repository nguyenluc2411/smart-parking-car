"use client";

import {
  Bar,
  BarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatCurrency } from "@/lib/utils";

export function RevenueChart({
  data,
}: {
  data: { hour: number; revenue: number; sessions: number }[];
}) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={data}>
        <XAxis
          dataKey="hour"
          tickFormatter={(h) => `${h}h`}
          fontSize={12}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          fontSize={12}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => `${v / 1000}k`}
          width={40}
        />
        <Tooltip
          formatter={(v) => [formatCurrency(Number(v)), "Doanh thu"]}
          labelFormatter={(h) => `Giờ ${h}:00`}
        />
        <Bar dataKey="revenue" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
