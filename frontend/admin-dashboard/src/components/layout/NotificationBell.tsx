"use client";

import { useState } from "react";
import Link from "next/link";
import { Bell, Check, Loader2, BellOff } from "lucide-react";
import { useAlerts, useAckAlert } from "@/lib/hooks/useAlerts";
import {
  ALERT_META,
  SEVERITY_STYLE,
  describeAlert,
  alertHref,
} from "@/components/dashboard/alertMeta";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/components/ui/toast";
import { timeAgo, cn } from "@/lib/utils";
import type { Alert } from "@/types";

/**
 * Header notification bell: open-alert count badge + a dropdown to review/ack. Clicking a row jumps
 * to the page to resolve it. Live via the dashboard's AlertStreamListener (invalidates on SSE push).
 */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const alerts = useAlerts("NEW");
  const ack = useAckAlert();
  const { toast } = useToast();

  const count = alerts.data?.totalElements ?? 0;
  const items = alerts.data?.content ?? [];
  const hasCritical = items.some((a) => a.severity === "CRITICAL");

  const onAck = (a: Alert) =>
    ack.mutate(a.id, {
      onSuccess: () => toast("Đã xử lý cảnh báo", { variant: "success" }),
      onError: () => toast("Thao tác thất bại", { variant: "destructive" }),
    });

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative rounded-full"
          aria-label={`Cảnh báo${count ? ` (${count})` : ""}`}
        >
          <Bell className="h-5 w-5" />
          {count > 0 && (
            <span
              className={cn(
                "absolute -right-1 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full px-1 text-[10px] font-bold text-white ring-2 ring-background",
                hasCritical ? "bg-destructive animate-pulse" : "bg-amber-500"
              )}
            >
              {count > 9 ? "9+" : count}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-[22rem] overflow-hidden p-0">
        <div className="flex items-center justify-between border-b bg-muted/40 px-4 py-3">
          <span className="text-sm font-semibold">Cảnh báo</span>
          <span className="text-xs text-muted-foreground">
            {count > 0 ? `${count} chưa xử lý` : "Đã xử lý hết"}
          </span>
        </div>

        <div className="max-h-[26rem] overflow-y-auto">
          {items.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
              <div className="flex h-11 w-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <BellOff className="h-5 w-5" />
              </div>
              <p className="text-sm font-medium">Không có cảnh báo</p>
              <p className="text-xs text-muted-foreground">
                Mọi thứ đang hoạt động bình thường
              </p>
            </div>
          ) : (
            items.map((a) => {
              const meta = ALERT_META[a.alertType];
              const Icon = meta.icon;
              const href = alertHref(a);
              return (
                  <div
                    key={a.id}
                    className={cn(
                      "flex items-stretch border-l-2",
                      a.severity === "CRITICAL"
                        ? "border-l-destructive"
                        : "border-l-warning"
                    )}
                  >
                    <Link
                      href={href ?? "#"}
                      onClick={() => setOpen(false)}
                      className="flex min-w-0 flex-1 items-start gap-3 px-3 py-3 transition-colors hover:bg-muted/50"
                    >
                      <div
                        className={cn(
                          "flex h-9 w-9 shrink-0 items-center justify-center rounded-full",
                          SEVERITY_STYLE[a.severity].chip
                        )}
                      >
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-semibold">
                            {meta.label}
                          </p>
                          {a.plateNumber && (
                            <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] font-medium border border-border/50">
                              {a.plateNumber}
                            </span>
                          )}
                        </div>
                        <p className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">
                          {describeAlert(a)}
                        </p>
                        <p className="mt-1 text-[11px] text-muted-foreground">
                          {timeAgo(a.createdAt)}
                        </p>
                      </div>
                    </Link>

                    <button
                      type="button"
                      title="Đánh dấu đã xử lý"
                      aria-label="Đánh dấu đã xử lý cảnh báo này"
                      disabled={ack.isPending}
                      onClick={() => onAck(a)}
                      className="flex w-10 shrink-0 items-center justify-center text-muted-foreground transition-colors hover:bg-muted/50 hover:text-success disabled:opacity-50"
                    >
                      {ack.isPending ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <Check className="h-3.5 w-3.5" />
                      )}
                    </button>
                  </div>
              );
            })
          )}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
