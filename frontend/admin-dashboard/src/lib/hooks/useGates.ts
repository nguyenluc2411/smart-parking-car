import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { parkingApi } from "@/lib/api/parking";
import type { GateOverrideRequest } from "@/types";

export function useGates() {
  return useQuery({
    queryKey: ["gates"],
    queryFn: () => parkingApi.listGates().then((r) => r.data),
    refetchInterval: 10_000,
    // Dừng polling khi tab bị ẩn, tiết kiệm tài nguyên
    refetchIntervalInBackground: false,
    placeholderData: keepPreviousData,
  });
}

export function useGateOverride() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: GateOverrideRequest }) =>
      parkingApi.overrideGate(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["gates"] }),
  });
}
