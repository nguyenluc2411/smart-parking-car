import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { parkingApi } from "@/lib/api/parking";
import type { SaveParkingLayoutRequest } from "@/types";
import type { CreateParkingLotRequest, CreateFloorRequest, CreateZoneRequest } from "@/types";

export function useParkingLots() {
  return useQuery({
    queryKey: ["parking-lots"],
    queryFn: () => parkingApi.listParkingLots().then((response) => response.data),
  });
}

export function useParkingLayout(lotId?: string) {
  return useQuery({
    queryKey: ["parking-layout", lotId],
    queryFn: () => parkingApi.getFloorLayout(lotId!).then((response) => response.data),
    enabled: Boolean(lotId),
  });
}

export function useSaveParkingLayout(lotId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SaveParkingLayoutRequest) => parkingApi.saveFloorLayout(lotId, body),
    onSuccess: (response) => {
      queryClient.setQueryData(["parking-layout", lotId], response.data);
    },
  });
}

export function usePublishParkingLayout(lotId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => parkingApi.publishFloorLayout(lotId),
    onSuccess: (response) => {
      queryClient.setQueryData(["parking-layout", lotId], response.data);
    },
  });
}

export function useParkingStructure(lotId?: string) {
  return useQuery({
    queryKey: ["parking-structure", lotId],
    queryFn: () => parkingApi.getParkingStructure(lotId!).then((response) => response.data),
    enabled: Boolean(lotId),
  });
}

export function useCreateParkingLot() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateParkingLotRequest) => parkingApi.createParkingLot(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["parking-lots"] }),
  });
}

export function useAddParkingFloor(lotId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateFloorRequest) => parkingApi.addParkingFloor(lotId, body),
    onSuccess: (response) => {
      queryClient.setQueryData(["parking-structure", lotId], response.data);
    },
  });
}

export function useAddParkingZone(lotId: string, floorId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateZoneRequest) => parkingApi.addParkingZone(floorId, body),
    onSuccess: (response) => {
      queryClient.setQueryData(["parking-structure", lotId], response.data);
      queryClient.invalidateQueries({ queryKey: ["slots"] });
      queryClient.invalidateQueries({ queryKey: ["parking-layout", floorId] });
    },
  });
}

export function useParkingStructureCrud(lotId: string, floorId: string) {
  const queryClient = useQueryClient();

  const refreshStructure = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["parking-lots"] }),
      queryClient.invalidateQueries({ queryKey: ["parking-structure", lotId] }),
      queryClient.invalidateQueries({ queryKey: ["slots"] }),
      queryClient.invalidateQueries({ queryKey: ["gates"] }),
      queryClient.invalidateQueries({ queryKey: ["parking-layout", floorId] }),
    ]);
  };

  return {
    updateLot: useMutation({
      mutationFn: (body: import("@/types").UpdateParkingLotRequest) =>
        parkingApi.updateParkingLot(lotId, body),
      onSuccess: refreshStructure,
    }),
    deleteLot: useMutation({
      mutationFn: () => parkingApi.deleteParkingLot(lotId),
      onSuccess: refreshStructure,
    }),
    updateFloor: useMutation({
      mutationFn: ({ id, name }: { id: string; name: string }) =>
        parkingApi.updateParkingFloor(id, { name }),
      onSuccess: refreshStructure,
    }),
    deleteFloor: useMutation({
      mutationFn: (id: string) => parkingApi.deleteParkingFloor(id),
      onSuccess: refreshStructure,
    }),
    updateZone: useMutation({
      mutationFn: ({ id, name }: { id: string; name: string }) =>
        parkingApi.updateParkingZone(id, { name }),
      onSuccess: refreshStructure,
    }),
    deleteZone: useMutation({
      mutationFn: (id: string) => parkingApi.deleteParkingZone(id),
      onSuccess: refreshStructure,
    }),
    addSlot: useMutation({
      mutationFn: ({ zoneId, slotCode }: { zoneId: string; slotCode: string }) =>
        parkingApi.addZoneSlot(zoneId, { slotCode }),
      onSuccess: refreshStructure,
    }),
    updateSlot: useMutation({
      mutationFn: ({ id, slotCode }: { id: string; slotCode: string }) =>
        parkingApi.updateZoneSlot(id, { slotCode }),
      onSuccess: refreshStructure,
    }),
    setSlotStatus: useMutation({
      mutationFn: ({ id, status }: { id: string; status: "EMPTY" | "MAINTENANCE" }) =>
        parkingApi.updateSlotStatus(id, { status }),
      onSuccess: refreshStructure,
    }),
    deleteSlot: useMutation({
      mutationFn: (id: string) => parkingApi.deleteZoneSlot(id),
      onSuccess: refreshStructure,
    }),
  };
}
