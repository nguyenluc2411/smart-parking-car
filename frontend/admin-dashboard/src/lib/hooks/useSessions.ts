import { useQuery, keepPreviousData, type UseQueryOptions } from "@tanstack/react-query";
import { parkingApi } from "@/lib/api/parking";
import type { Page, SessionListItem, SessionFilterParams } from "@/types";

type SessionQueryOptions = Omit<
  UseQueryOptions<Page<SessionListItem>>,
  "queryKey" | "queryFn"
>;

export function useSessions(
  params: SessionFilterParams,
  options?: SessionQueryOptions
) {
  return useQuery({
    queryKey: ["sessions", params],
    queryFn: () => parkingApi.listSessions(params).then((r) => r.data),
    placeholderData: keepPreviousData,
    ...options,
  });
}

export function useSession(id: string) {
  return useQuery({
    queryKey: ["sessions", id],
    queryFn: () => parkingApi.getSession(id).then((r) => r.data),
    enabled: !!id,
  });
}
