import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { parkingApi } from "@/lib/api/parking";
import type {
  CreateSlotRequest,
  UpdateSlotStatusRequest,
  ProvisionZoneRequest,
} from "@/types";

export function useSlotAvailability() {
  return useQuery({
    queryKey: ["slots", "availability"],
    queryFn: () => parkingApi.getSlotAvailability().then((r) => r.data),
    refetchInterval: 10_000,
  });
}

export function useSlots() {
  return useQuery({
    queryKey: ["slots", "list"],
    queryFn: () => parkingApi.listSlots().then((r) => r.data),
    refetchInterval: 10_000,
  });
}

function useInvalidateSlots() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ["slots"] });
  };
}

export function useCreateSlot() {
  const invalidate = useInvalidateSlots();
  return useMutation({
    mutationFn: (body: CreateSlotRequest) => parkingApi.createSlot(body),
    onSuccess: invalidate,
  });
}

export function useDeleteSlot() {
  const invalidate = useInvalidateSlots();
  return useMutation({
    mutationFn: (id: string) => parkingApi.deleteSlot(id),
    onSuccess: invalidate,
  });
}

export function useUpdateSlotStatus() {
  const invalidate = useInvalidateSlots();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateSlotStatusRequest }) =>
      parkingApi.updateSlotStatus(id, body),
    onSuccess: invalidate,
  });
}

export function useProvisionZone() {
  const invalidate = useInvalidateSlots();
  return useMutation({
    mutationFn: (body: ProvisionZoneRequest) => parkingApi.provisionZone(body),
    onSuccess: invalidate,
  });
}
