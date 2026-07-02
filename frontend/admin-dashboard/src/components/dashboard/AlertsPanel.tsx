"use client";

import Link from "next/link";
import { ShieldAlert, Check, Loader2, ShieldCheck } from "lucide-react";
import { useAlerts, useAckAlert } from "@/lib/hooks/useAlerts";
import {
  ALERT_META,
  SEVERITY_STYLE,
  describeAlert,
  alertHref,
} from "@/components/dashboard/alertMeta";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useToast } from "@/components/ui/toast";
import { timeAgo, cn } from "@/lib/utils";

export function AlertsPanel() {
  const alerts = useAlerts("NEW");
  const ack = useAckAlert();
  const { toast } = useToast();

  const items = alerts.data?.content ?? [];
  const count = alerts.data?.totalElements ?? 0;

  const onAck = (id: string) =>
    ack.mutate(id, {
      onSuccess: () => toast("Đã xử lý cảnh báo", { variant: "success" }),
      onError: () => toast("Thao tác thất bại", { variant: "destructive" }),
    });

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldAlert className="h-5 w-5 text-destructive" />
          Cảnh báo an ninh
        </CardTitle>
        {count > 0 && (
          <span className="rounded-full bg-destructive/10 px-2.5 py-1 text-xs font-semibold text-destructive">
            {count} chưa xử lý
          </span>
        )}
      </CardHeader>
      <CardContent>
        {alerts.isLoading || !alerts.data ? (
          <div className="py-10">
            <Spinner />
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-10 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-600">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <p className="text-sm font-medium">Không có cảnh báo</p>
            <p className="text-xs text-muted-foreground">
              Hệ thống đang hoạt động bình thường
            </p>
          </div>
        ) : (
          <ul className="space-y-2.5">
            {items.map((a) => {
              const meta = ALERT_META[a.alertType];
              const sev = SEVERITY_STYLE[a.severity];
              const Icon = meta.icon;
              const href = alertHref(a);
              return (
                <li
                  key={a.id}
                  className={cn(
                    "flex items-center gap-3 rounded-xl border border-l-[3px] bg-card p-3 transition-colors hover:bg-muted/40",
                    a.severity === "CRITICAL"
                      ? "border-l-destructive"
                      : "border-l-amber-500"
                  )}
                >
                  <Link
                    href={href ?? "#"}
                    className="flex min-w-0 flex-1 items-center gap-3"
                  >
                    {a.imageUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={a.imageUrl}
                        alt="Khung hình"
                        className="h-12 w-16 shrink-0 rounded-lg border object-cover"
                      />
                    ) : (
                      <div
                        className={cn(
                          "flex h-10 w-10 shrink-0 items-center justify-center rounded-full",
                          sev.chip
                        )}
                      >
                        <Icon className="h-5 w-5" />
                      </div>
                    )}

                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-semibold">
                          {meta.label}
                        </span>
                        <span
                          className={cn(
                            "rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide",
                            sev.chip
                          )}
                        >
                          {sev.label}
                        </span>
                        {a.plateNumber && (
                          <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] font-medium">
                            {a.plateNumber}
                          </span>
                        )}
                      </div>
                      <p className="mt-0.5 line-clamp-1 text-xs text-muted-foreground">
                        {describeAlert(a)}
                      </p>
                      <p className="mt-0.5 text-[11px] text-muted-foreground">
                        {timeAgo(a.createdAt)}
                      </p>
                    </div>
                  </Link>

                  <Button
                    variant="outline"
                    size="sm"
                    className="shrink-0"
                    disabled={ack.isPending}
                    onClick={() => onAck(a.id)}
                  >
                    {ack.isPending ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Check className="h-3.5 w-3.5" />
                    )}
                    Xử lý
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
