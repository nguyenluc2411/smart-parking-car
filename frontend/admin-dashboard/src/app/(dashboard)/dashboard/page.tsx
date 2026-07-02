"use client";

import { Car, DollarSign, Activity, TrendingUp } from "lucide-react";
import { useSlotAvailability } from "@/lib/hooks/useSlots";
import { useSessions } from "@/lib/hooks/useSessions";
import { useDailyReport } from "@/lib/hooks/useReports";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/dashboard/StatCard";
import { SlotGauge } from "@/components/dashboard/SlotGauge";
import { RevenueChart } from "@/components/dashboard/RevenueChart";
import { AlertsPanel } from "@/components/dashboard/AlertsPanel";
import { Spinner } from "@/components/ui/spinner";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { formatCurrency } from "@/lib/utils";

export default function DashboardPage() {
  const slots = useSlotAvailability();
  const active = useSessions({ status: "ACTIVE" });
  const report = useDailyReport();

  return (
    <div>
      <PageHeader
        title="Tổng quan"
        description="Tình trạng bãi xe và doanh thu hôm nay (cập nhật mỗi 10 giây)"
      />

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {slots.data && (
          <StatCard
            title="Chỗ trống"
            value={slots.data.emptySlots}
            icon={Car}
            hint={`Trên tổng ${slots.data.totalSlots} chỗ`}
          />
        )}
        <StatCard
          title="Xe đang đỗ"
          value={active.data?.totalElements ?? "—"}
          icon={Activity}
          hint="Phiên đang hoạt động"
        />
        {report.data && (
          <StatCard
            title="Doanh thu hôm nay"
            value={formatCurrency(report.data.totalRevenue)}
            icon={DollarSign}
            hint={`${report.data.totalSessions} lượt gửi`}
          />
        )}
        {report.data && (
          <StatCard
            title="Giờ cao điểm"
            value={report.data.peakSessions}
            icon={TrendingUp}
            hint={`TB ${report.data.avgDurationMinutes} phút/lượt`}
          />
        )}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-1">
          {slots.isLoading || !slots.data ? (
            <Card>
              <CardContent>
                <Spinner />
              </CardContent>
            </Card>
          ) : (
            <SlotGauge data={slots.data} />
          )}
        </div>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Doanh thu theo giờ</CardTitle>
          </CardHeader>
          <CardContent>
            {report.isLoading || !report.data ? (
              <Spinner />
            ) : (
              <RevenueChart data={report.data.revenueByHour} />
            )}
          </CardContent>
        </Card>
      </div>

      <div className="mt-4">
        <AlertsPanel />
      </div>
    </div>
  );
}
