"use client";

import { Car, DollarSign, Activity, TrendingUp } from "lucide-react";
import { useSlotAvailability } from "@/lib/hooks/useSlots";
import { useSessions } from "@/lib/hooks/useSessions";
import { useDailyReport } from "@/lib/hooks/useReports";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/dashboard/StatCard";
import { SlotGauge } from "@/components/dashboard/SlotGauge";
import { AlertsPanel } from "@/components/dashboard/AlertsPanel";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { formatCurrency } from "@/lib/utils";
import dynamic from "next/dynamic";

const RevenueChart = dynamic(
  () => import("@/components/dashboard/RevenueChart"),
  { ssr: false, loading: () => <div className="h-[280px] animate-pulse bg-muted/20 rounded-xl" /> }
);

function StatCardSkeleton() {
  return (
    <Card className="border border-border/50 shadow-sm overflow-hidden">
      <div className="h-[2px] bg-muted/50" />
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 space-y-3">
            <div className="h-3 w-20 animate-pulse rounded-full bg-muted" />
            <div className="h-10 w-28 animate-pulse rounded-lg bg-muted" />
            <div className="h-3 w-24 animate-pulse rounded-full bg-muted" />
          </div>
          <div className="h-10 w-10 animate-pulse rounded-xl bg-muted" />
        </div>
      </CardContent>
    </Card>
  );
}

function SlotGaugeSkeleton() {
  return (
    <Card className="border border-border/50 shadow-sm h-full">
      <CardHeader className="px-5 pb-2 pt-5">
        <div className="h-3 w-24 animate-pulse rounded-full bg-muted" />
      </CardHeader>
      <CardContent className="px-5 pb-5 space-y-4">
        <div className="h-14 w-28 animate-pulse rounded-lg bg-muted" />
        <div className="h-2.5 w-full animate-pulse rounded-full bg-muted" />
        <div className="grid grid-cols-3 gap-2.5">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-[72px] animate-pulse rounded-xl bg-muted" />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const slots = useSlotAvailability();
  const active = useSessions(
    { status: "ACTIVE" },
    { refetchInterval: 30_000, refetchIntervalInBackground: false }
  );
  const localToday = new Date().toLocaleDateString("sv");
  const report = useDailyReport(localToday, {
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
  });

  return (
    <div className="space-y-8">
      <PageHeader
        title="Tổng quan"
        description="Tình trạng bãi xe và doanh thu hôm nay · Cập nhật tự động"
      />

      {/* KPI Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {slots.isLoading || !slots.data ? (
          <StatCardSkeleton />
        ) : (
          <StatCard
            title="Chỗ trống"
            value={slots.data.emptySlots}
            icon={Car}
            hint={`Trên tổng ${slots.data.totalSlots} chỗ`}
            accentColor="emerald"
          />
        )}

        <StatCard
          title="Xe đang đỗ"
          value={active.data?.totalElements ?? "—"}
          icon={Activity}
          hint="Phiên đang hoạt động"
          accentColor="indigo"
        />

        {report.isLoading || !report.data ? (
          <StatCardSkeleton />
        ) : (
          <StatCard
            title="Doanh thu hôm nay"
            value={formatCurrency(report.data.totalRevenue)}
            icon={DollarSign}
            hint={`${report.data.totalSessions} lượt gửi`}
            accentColor="sky"
          />
        )}

        {report.isLoading || !report.data ? (
          <StatCardSkeleton />
        ) : (
          <StatCard
            title="Giờ cao điểm"
            value={report.data.peakSessions}
            icon={TrendingUp}
            hint={`TB ${report.data.avgDurationMinutes} phút/lượt`}
            accentColor="amber"
          />
        )}
      </div>

      {/* Charts row */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Slot gauge */}
        <div className="lg:col-span-1">
          {slots.isLoading || !slots.data ? (
            <SlotGaugeSkeleton />
          ) : (
            <SlotGauge data={slots.data} />
          )}
        </div>

        {/* Revenue chart */}
        <Card className="lg:col-span-2 border border-border/50 shadow-sm">
          <CardHeader className="px-5 pt-5 pb-2">
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-base font-semibold">
                  Doanh thu theo giờ
                </CardTitle>
                <CardDescription className="mt-0.5 text-xs">
                  Biểu đồ doanh thu trong ngày hôm nay
                </CardDescription>
              </div>
              <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
                <span className="flex items-center gap-1">
                  <span className="h-2 w-2 rounded-full bg-indigo-500 inline-block" />
                  Giờ thường
                </span>
                <span className="flex items-center gap-1">
                  <span className="h-2 w-2 rounded-full bg-amber-500 inline-block" />
                  Cao điểm
                </span>
              </div>
            </div>
          </CardHeader>
          <CardContent className="px-5 pb-5 pt-2">
            {report.isLoading || !report.data ? (
              <div className="h-[280px] animate-pulse rounded-xl bg-muted/20" />
            ) : (
              <RevenueChart data={report.data.revenueByHour} />
            )}
          </CardContent>
        </Card>
      </div>

      {/* Alerts */}
      <AlertsPanel />
    </div>
  );
}
