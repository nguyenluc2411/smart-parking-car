import { useQuery } from "@tanstack/react-query";
import { parkingApi } from "@/lib/api/parking";
import type { SessionFilterParams } from "@/types";

export function useSessions(params: SessionFilterParams) {
  return useQuery({
    queryKey: ["sessions", params],
    queryFn: () => parkingApi.listSessions(params).then((r) => r.data),
  });
}

export function useSession(id: string) {
  return useQuery({
    queryKey: ["sessions", id],
    queryFn: () => parkingApi.getSession(id).then((r) => r.data),
    enabled: !!id,
  });
}
