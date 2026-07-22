"use client";

import { DollarSign, Car, Clock, TrendingUp, Banknote, CreditCard } from "lucide-react";
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
import type { MethodRevenue } from "@/types";
import dynamic from "next/dynamic";

const RevenueChart = dynamic(
  () => import("@/components/dashboard/RevenueChart"),
  { ssr: false, loading: () => <div className="h-64 animate-pulse bg-muted/20 rounded-xl" /> }
);

const MonthlyChart = dynamic(
  () => import("@/components/dashboard/MonthlyChart"),
  { ssr: false, loading: () => <div className="h-64 animate-pulse bg-muted/20 rounded-xl" /> }
);

const PaymentMethodChart = dynamic(
  () => import("@/components/dashboard/PaymentMethodChart"),
  { ssr: false, loading: () => <div className="h-56 animate-pulse bg-muted/20 rounded-xl" /> }
);

/** BR-005-8: CASH riêng; QR_CODE + ONLINE gộp thành "Hệ thống" (tiền không qua tay operator). */
function cashVsSystem(revenueByMethod: MethodRevenue[]) {
  const cash = revenueByMethod.find((m) => m.method === "CASH")?.revenue ?? 0;
  const system = revenueByMethod
    .filter((m) => m.method !== "CASH")
    .reduce((sum, m) => sum + m.revenue, 0);
  return { cash, system };
}

export default function ReportsPage() {
  const localToday = new Date().toLocaleDateString("sv");
  const daily = useDailyReport(localToday, {
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
  });
  const monthly = useMonthlyReport();
  const dailySplit = daily.data ? cashVsSystem(daily.data.revenueByMethod) : null;

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
        {dailySplit && (
          <>
            <StatCard
              title="Tiền mặt (hôm nay)"
              value={formatCurrency(dailySplit.cash)}
              icon={Banknote}
            />
            <StatCard
              title="Hệ thống (hôm nay)"
              value={formatCurrency(dailySplit.system)}
              icon={CreditCard}
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

        <Card>
          <CardHeader>
            <CardTitle>Tiền mặt vs Hệ thống (hôm nay)</CardTitle>
          </CardHeader>
          <CardContent>
            {daily.isLoading || !daily.data ? (
              <Spinner />
            ) : (
              <PaymentMethodChart data={daily.data.revenueByMethod} />
            )}
          </CardContent>
        </Card>
      </div>
    </RoleGuard>
  );
}
