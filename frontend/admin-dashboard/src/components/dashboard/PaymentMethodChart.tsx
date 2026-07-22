"use client";

import React from "react";
import { Pie, PieChart, ResponsiveContainer, Tooltip, Cell } from "recharts";
import type { MethodRevenue } from "@/types";
import { formatCurrency } from "@/lib/utils";

const METHOD_LABEL: Record<MethodRevenue["method"], string> = {
  CASH: "Tiền mặt",
  QR_CODE: "Chuyển khoản (QR thủ công)",
  ONLINE: "Chuyển khoản (PayOS/MoMo)",
};

const METHOD_COLOR: Record<MethodRevenue["method"], string> = {
  CASH: "hsl(var(--success))",
  QR_CODE: "hsl(var(--warning))",
  ONLINE: "hsl(var(--brand-from))",
};

interface CustomTooltipProps {
  active?: boolean;
  payload?: { value: number; payload: MethodRevenue }[];
}

function CustomTooltip({ active, payload }: CustomTooltipProps) {
  if (active && payload && payload.length) {
    const d = payload[0].payload;
    return (
      <div className="rounded-xl border border-border/50 bg-background/95 p-3.5 shadow-xl backdrop-blur-md min-w-[160px]">
        <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-2">
          {METHOD_LABEL[d.method]}
        </p>
        <p className="text-base font-bold tabular-nums text-foreground">{formatCurrency(d.revenue)}</p>
        <p className="mt-0.5 text-[11px] text-muted-foreground">{d.count} giao dịch</p>
      </div>
    );
  }
  return null;
}

const PaymentMethodChartComponent = ({ data }: { data: MethodRevenue[] }) => {
  const withRevenue = data.filter((d) => d.revenue > 0);

  if (withRevenue.length === 0) {
    return (
      <div className="flex h-[220px] items-center justify-center text-sm text-muted-foreground">
        Chưa có giao dịch nào
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-3">
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie
            data={withRevenue}
            dataKey="revenue"
            nameKey="method"
            innerRadius={55}
            outerRadius={80}
            paddingAngle={2}
          >
            {withRevenue.map((entry) => (
              <Cell key={entry.method} fill={METHOD_COLOR[entry.method]} />
            ))}
          </Pie>
          <Tooltip content={<CustomTooltip />} />
        </PieChart>
      </ResponsiveContainer>
      <div className="flex flex-wrap justify-center gap-x-4 gap-y-1">
        {withRevenue.map((d) => (
          <div key={d.method} className="flex items-center gap-1.5 text-xs">
            <span
              className="h-2 w-2 rounded-full"
              style={{ backgroundColor: METHOD_COLOR[d.method] }}
            />
            <span className="text-muted-foreground">{METHOD_LABEL[d.method]}</span>
            <span className="font-medium">{formatCurrency(d.revenue)}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default React.memo(PaymentMethodChartComponent);
