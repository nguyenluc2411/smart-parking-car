import React from "react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { LucideIcon } from "lucide-react";

interface StatCardProps {
  title: string;
  value: React.ReactNode;
  icon: LucideIcon;
  hint?: string;
  trend?: { value: number; label: string };
  accentColor?: "indigo" | "emerald" | "amber" | "sky";
  className?: string;
}

const accentStyles: Record<NonNullable<StatCardProps["accentColor"]>, {
  iconBg: string;
  iconColor: string;
  ring: string;
}> = {
  indigo: {
    iconBg: "bg-indigo-500/10 dark:bg-indigo-500/15",
    iconColor: "text-indigo-500",
    ring: "shadow-indigo-500/10",
  },
  emerald: {
    iconBg: "bg-emerald-500/10 dark:bg-emerald-500/15",
    iconColor: "text-emerald-500",
    ring: "shadow-emerald-500/10",
  },
  amber: {
    iconBg: "bg-amber-500/10 dark:bg-amber-500/15",
    iconColor: "text-amber-500",
    ring: "shadow-amber-500/10",
  },
  sky: {
    iconBg: "bg-sky-500/10 dark:bg-sky-500/15",
    iconColor: "text-sky-500",
    ring: "shadow-sky-500/10",
  },
};

export const StatCard = React.memo(function StatCard({
  title,
  value,
  icon: Icon,
  hint,
  trend,
  accentColor = "indigo",
  className,
}: StatCardProps) {
  const accent = accentStyles[accentColor];
  const trendPositive = (trend?.value ?? 0) >= 0;

  return (
    <Card className={cn(
      "relative overflow-hidden border border-border/50 bg-card shadow-sm",
      "transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md",
      className
    )}>
      {/* Subtle top gradient stripe */}
      <div
        className="absolute inset-x-0 top-0 h-[2.5px] opacity-75 bg-brand-gradient"
        aria-hidden
      />

      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <p className="text-[12px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
              {title}
            </p>
            <p className="mt-3 truncate text-[2rem] font-bold tracking-tight tabular-nums text-foreground leading-none">
              {value}
            </p>
            {hint && (
              <p className="mt-2 text-[12px] text-muted-foreground font-medium">
                {hint}
              </p>
            )}
            {trend && (
              <div className={cn(
                "mt-2 flex items-center gap-1 text-[12px] font-semibold",
                trendPositive ? "text-emerald-500" : "text-red-500"
              )}>
                <span>{trendPositive ? "↑" : "↓"} {Math.abs(trend.value)}%</span>
                <span className="font-normal text-muted-foreground">{trend.label}</span>
              </div>
            )}
          </div>

          <div className={cn(
            "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl",
            accent.iconBg
          )}>
            <Icon className={cn("h-5 w-5", accent.iconColor)} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
});
