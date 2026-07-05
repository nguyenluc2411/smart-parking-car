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
    <Card className="border border-border/50 bg-card shadow-sm overflow-hidden">
      <CardHeader className="flex flex-row items-center justify-between space-y-0 px-5 pt-5 pb-4">
        <CardTitle className="flex items-center gap-2.5 text-base font-semibold">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-destructive/10">
            <ShieldAlert className="h-4 w-4 text-destructive" />
          </div>
          Cảnh báo an ninh
        </CardTitle>
        {count > 0 ? (
          <span className="inline-flex items-center rounded-full bg-destructive/10 px-3 py-1 text-xs font-bold text-destructive ring-1 ring-inset ring-destructive/20">
            {count} chưa xử lý
          </span>
        ) : (
          <span className="inline-flex items-center rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-bold text-emerald-600 ring-1 ring-inset ring-emerald-500/20">
            Tất cả đã xử lý
          </span>
        )}
      </CardHeader>

      <CardContent className="px-5 pb-5">
        {alerts.isLoading || !alerts.data ? (
          <div className="flex items-center justify-center py-12">
            <Spinner />
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-12 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-500/10">
              <ShieldCheck className="h-7 w-7 text-emerald-500" />
            </div>
            <div>
              <p className="text-sm font-semibold text-foreground">Không có cảnh báo nào</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Hệ thống đang hoạt động bình thường
              </p>
            </div>
          </div>
        ) : (
          <ul className="space-y-2">
            {items.map((a) => {
              const meta = ALERT_META[a.alertType];
              const sev = SEVERITY_STYLE[a.severity];
              const Icon = meta.icon;
              const href = alertHref(a);
              return (
                <li
                  key={a.id}
                  className={cn(
                    "group relative flex items-center gap-3 rounded-xl border bg-card p-3.5",
                    "transition-all duration-150 hover:bg-muted/30",
                    a.severity === "CRITICAL"
                      ? "border-destructive/25 shadow-[0_0_0_1px_hsl(var(--destructive)/0.1)]"
                      : "border-amber-500/25 shadow-[0_0_0_1px_rgba(245,158,11,0.1)]"
                  )}
                >
                  {/* Severity accent bar */}
                  <div
                    className={cn(
                      "absolute left-0 top-3 bottom-3 w-[3px] rounded-r-full",
                      a.severity === "CRITICAL" ? "bg-destructive" : "bg-amber-500"
                    )}
                  />

                  <Link
                    href={href ?? "#"}
                    className="ml-2 flex min-w-0 flex-1 items-center gap-3"
                  >
                    {a.imageUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={a.imageUrl}
                        alt="Khung hình"
                        className="h-12 w-16 shrink-0 rounded-lg border border-border/50 object-cover"
                      />
                    ) : (
                      <div
                        className={cn(
                          "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl",
                          sev.chip
                        )}
                      >
                        <Icon className="h-5 w-5" />
                      </div>
                    )}

                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-semibold text-foreground">
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
                          <span className="rounded-md border border-border/60 bg-muted px-1.5 py-0.5 font-mono text-[11px] font-semibold tracking-widest">
                            {a.plateNumber}
                          </span>
                        )}
                      </div>
                      <p className="mt-0.5 line-clamp-1 text-xs text-muted-foreground">
                        {describeAlert(a)}
                      </p>
                      <p className="mt-0.5 text-[11px] text-muted-foreground/70 font-medium">
                        {timeAgo(a.createdAt)}
                      </p>
                    </div>
                  </Link>

                  <Button
                    variant="outline"
                    size="sm"
                    className="shrink-0 h-8 gap-1.5 text-xs font-semibold transition-all hover:bg-emerald-500/10 hover:text-emerald-600 hover:border-emerald-500/30"
                    disabled={ack.isPending}
                    onClick={() => onAck(a.id)}
                  >
                    {ack.isPending ? (
                      <Loader2 className="h-3 w-3 animate-spin" />
                    ) : (
                      <Check className="h-3 w-3" />
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
