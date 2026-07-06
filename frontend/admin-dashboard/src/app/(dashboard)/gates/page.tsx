"use client";

import { ArrowDownToLine, ArrowUpFromLine } from "lucide-react";
import { useGates } from "@/lib/hooks/useGates";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { GateStatusBadge } from "@/components/StatusBadge";
import { OverrideModal } from "@/components/gates/OverrideModal";
import { BarrierAnimation } from "@/components/gates/BarrierAnimation";
import { Spinner } from "@/components/ui/spinner";
import { ErrorState } from "@/components/ui/error-state";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { formatDateTime } from "@/lib/utils";

export default function GatesPage() {
  const query = useGates();

  return (
    <RoleGuard allow={["ADMIN"]}>
      <PageHeader
        title="Cổng / Barie"
        description="Trạng thái cổng và điều khiển thủ công (cập nhật mỗi 10 giây)"
      />

      {query.isError ? (
        <ErrorState onRetry={() => query.refetch()} />
      ) : query.isLoading || !query.data ? (
        <Spinner />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {query.data.map((gate) => (
            <Card key={gate.id}>
              <CardHeader className="flex flex-row items-center justify-between">
                <CardTitle className="flex items-center gap-2">
                  {gate.direction === "IN" ? (
                    <ArrowDownToLine className="h-5 w-5 text-success" />
                  ) : (
                    <ArrowUpFromLine className="h-5 w-5 text-info" />
                  )}
                  {gate.gateCode}
                </CardTitle>
                <GateStatusBadge status={gate.status} />
              </CardHeader>
              <CardContent className="space-y-3">
                {/* Hoạt ảnh barie đóng/mở (thay cho camera bãi xe thật) */}
                <BarrierAnimation
                  status={gate.status}
                  direction={gate.direction}
                />
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Hướng</span>
                  <span className="font-medium">
                    {gate.direction === "IN" ? "Lối vào" : "Lối ra"}
                  </span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Lệnh gần nhất</span>
                  <span className="font-medium">{gate.lastCommand ?? "—"}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Thời điểm</span>
                  <span className="font-medium">
                    {formatDateTime(gate.lastCommandAt)}
                  </span>
                </div>
                <div className="pt-2">
                  <OverrideModal gate={gate} />
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </RoleGuard>
  );
}
