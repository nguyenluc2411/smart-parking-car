import { useQuery } from "@tanstack/react-query";
import { billingApi } from "@/lib/api/billing";

export function useDailyReport(date?: string, options?: { refetchInterval?: number; refetchIntervalInBackground?: boolean }) {
  return useQuery({
    queryKey: ["report", "daily", date ?? "today"],
    queryFn: () => billingApi.getDailyReport(date).then((r) => r.data),
    ...options,
  });
}

export function useMonthlyReport(month?: string) {
  return useQuery({
    queryKey: ["report", "monthly", month ?? "current"],
    queryFn: () => billingApi.getMonthlyReport(month).then((r) => r.data),
  });
}
