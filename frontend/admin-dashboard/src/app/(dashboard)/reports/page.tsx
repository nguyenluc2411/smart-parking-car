"use client";

import { DollarSign, Car, Clock, TrendingUp } from "lucide-react";
import { useDailyReport, useMonthlyReport } from "@/lib/hooks/useReports";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { StatCard } from "@/components/dashboard/StatCard";
import { Spinner } from "@/components/ui/spinner";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { formatCurrency } from "@/lib/utils";
import dynamic from "next/dynamic";

const RevenueChart = dynamic(
  () => import("@/components/dashboard/RevenueChart"),
  { ssr: false, loading: () => <div className="h-64 animate-pulse bg-muted/20 rounded-xl" /> }
);

const MonthlyChart = dynamic(
  () => import("@/components/dashboard/MonthlyChart"),
  { ssr: false, loading: () => <div className="h-64 animate-pulse bg-muted/20 rounded-xl" /> }
);

export default function ReportsPage() {
  const localToday = new Date().toLocaleDateString("sv");
  const daily = useDailyReport(localToday, {
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
  });
  const monthly = useMonthlyReport();

  return (
    <RoleGuard allow={["ADMIN"]}>
      <PageHeader
        title="Báo cáo doanh thu"
        description="Thống kê theo ngày và theo tháng"
      />

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {daily.data && (
          <>
            <StatCard
              title="Doanh thu hôm nay"
              value={formatCurrency(daily.data.totalRevenue)}
              icon={DollarSign}
            />
            <StatCard
              title="Lượt gửi xe"
              value={daily.data.totalSessions}
              icon={Car}
            />
            <StatCard
              title="Thời lượng TB"
              value={`${daily.data.avgDurationMinutes}'`}
              icon={Clock}
            />
          </>
        )}
        {monthly.data && (
          <StatCard
            title="Tăng trưởng tháng"
            value={`${(monthly.data.growthRate * 100).toFixed(1)}%`}
            icon={TrendingUp}
            hint={`So với ${formatCurrency(monthly.data.prevMonthRevenue)}`}
          />
        )}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Doanh thu theo giờ (hôm nay)</CardTitle>
          </CardHeader>
          <CardContent>
            {daily.isLoading || !daily.data ? (
              <Spinner />
            ) : (
              <RevenueChart data={daily.data.revenueByHour} />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Doanh thu theo ngày (tháng này)</CardTitle>
          </CardHeader>
          <CardContent>
            {monthly.isLoading || !monthly.data ? (
              <Spinner />
            ) : (
              <MonthlyChart data={monthly.data.revenueByDay} />
            )}
          </CardContent>
        </Card>
      </div>
    </RoleGuard>
  );
}
