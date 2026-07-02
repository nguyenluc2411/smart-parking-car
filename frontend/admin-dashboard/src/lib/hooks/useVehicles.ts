import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { parkingApi } from "@/lib/api/parking";
import type { CreateWhitelistRequest } from "@/types";

// A plate has ONE classification, so adding/removing in either list can change the other
// (e.g. a re-classification moves it whitelist->blacklist). Invalidate BOTH lists every time
// so neither table is left showing stale data (the cause of "appears in both lists").
const VEHICLES_KEY = ["vehicles"];

export function useWhitelist() {
  return useQuery({
    queryKey: ["vehicles", "whitelist"],
    queryFn: () => parkingApi.listWhitelist().then((r) => r.data),
  });
}

export function useAddWhitelist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateWhitelistRequest) => parkingApi.addWhitelist(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: VEHICLES_KEY }),
  });
}

export function useDeleteWhitelist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (plate: string) => parkingApi.deleteWhitelist(plate),
    onSuccess: () => qc.invalidateQueries({ queryKey: VEHICLES_KEY }),
  });
}

export function useBlacklist() {
  return useQuery({
    queryKey: ["vehicles", "blacklist"],
    queryFn: () => parkingApi.listBlacklist().then((r) => r.data),
  });
}

export function useAddBlacklist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateWhitelistRequest) => parkingApi.addBlacklist(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: VEHICLES_KEY }),
  });
}

export function useDeleteBlacklist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (plate: string) => parkingApi.deleteBlacklist(plate),
    onSuccess: () => qc.invalidateQueries({ queryKey: VEHICLES_KEY }),
  });
}
