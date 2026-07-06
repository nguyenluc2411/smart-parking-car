import { useEffect } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { parkingApi } from "@/lib/api/parking";
import { subscribeAlerts } from "@/lib/api/alertStream";
import { useToast } from "@/components/ui/toast";
import type { AlertStatus } from "@/types";

export function useAlerts(status: AlertStatus = "NEW") {
  return useQuery({
    queryKey: ["alerts", status],
    queryFn: () => parkingApi.listAlerts(status).then((r) => r.data),
    refetchInterval: 30_000, // safety net if the SSE stream drops
    // Dừng polling khi tab bị ẩn
    refetchIntervalInBackground: false,
    // Giữ dữ liệu cũ khi đang refetch, tránh badge count bị clear
    placeholderData: keepPreviousData,
  });
}

export function useAckAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => parkingApi.ackAlert(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alerts"] }),
  });
}

/**
 * Subscribe to the live SSE alert stream: refetch the alert lists on each push and toast CRITICAL
 * ones. Mount ONCE (in the dashboard layout) so operators are notified on any page.
 */
export function useAlertStream() {
  const qc = useQueryClient();
  const { toast } = useToast();
  useEffect(() => {
    const controller = new AbortController();
    void subscribeAlerts((alert) => {
      qc.invalidateQueries({ queryKey: ["alerts"] });
      if (alert.severity === "CRITICAL") {
        toast(`⚠ ${alert.message}`, {
          description: alert.plateNumber ?? undefined,
          variant: "destructive",
        });
      }
    }, controller.signal);
    return () => controller.abort();
  }, [qc, toast]);
}
