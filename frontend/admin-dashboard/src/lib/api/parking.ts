import { parkingClient } from "./client";
import type {
  ApiResponse,
  Page,
  SessionListItem,
  SessionDetail,
  SessionFilterParams,
  SlotAvailability,
  Slot,
  Gate,
  GateOverrideRequest,
  Vehicle,
  CreateWhitelistRequest,
  CreateSlotRequest,
  UpdateSlotStatusRequest,
  ProvisionZoneRequest,
  ProvisionResult,
  Alert,
  AlertStatus,
  OutageEventRequest,
  ParkingLot,
  ParkingLayout,
  SaveParkingLayoutRequest,
  ParkingStructure,
  CreateParkingLotRequest,
  CreateFloorRequest,
  CreateZoneRequest,
  UpdateParkingLotRequest,
  UpdateNameRequest,
  SlotCodeRequest,
} from "@/types";

export const parkingApi = {
  // Sessions
  listSessions: (params: SessionFilterParams) =>
    parkingClient
      .get<ApiResponse<Page<SessionListItem>>>("/sessions", { params })
      .then((r) => r.data),
  getSession: (id: string) =>
    parkingClient
      .get<ApiResponse<SessionDetail>>(`/sessions/${id}`)
      .then((r) => r.data),
  recordOutageEvent: (body: OutageEventRequest) =>
    parkingClient
      .post<ApiResponse<SessionDetail>>("/sessions/outage-events", body)
      .then((r) => r.data),
  listParkingLots: () =>
    parkingClient.get<ApiResponse<ParkingLot[]>>("/parking-lots").then((r) => r.data),
  createParkingLot: (body: CreateParkingLotRequest) =>
    parkingClient.post<ApiResponse<ParkingStructure>>("/parking-lots", body).then((r) => r.data),
  getParkingStructure: (lotId: string) =>
    parkingClient
      .get<ApiResponse<ParkingStructure>>(`/parking-lots/${lotId}/structure`)
      .then((r) => r.data),
  addParkingFloor: (lotId: string, body: CreateFloorRequest) =>
    parkingClient
      .post<ApiResponse<ParkingStructure>>(`/parking-lots/${lotId}/floors`, body)
      .then((r) => r.data),
  addParkingZone: (floorId: string, body: CreateZoneRequest) =>
    parkingClient
      .post<ApiResponse<ParkingStructure>>(`/parking-lots/floors/${floorId}/zones`, body)
      .then((r) => r.data),
  updateParkingLot: (lotId: string, body: UpdateParkingLotRequest) =>
    parkingClient
      .patch<ApiResponse<ParkingStructure>>(`/parking-lots/${lotId}`, body)
      .then((r) => r.data),
  deleteParkingLot: (lotId: string) =>
    parkingClient.delete(`/parking-lots/${lotId}`),
  updateParkingFloor: (floorId: string, body: UpdateNameRequest) =>
    parkingClient
      .patch<ApiResponse<ParkingStructure>>(`/parking-lots/floors/${floorId}`, body)
      .then((r) => r.data),
  deleteParkingFloor: (floorId: string) =>
    parkingClient.delete(`/parking-lots/floors/${floorId}`),
  updateParkingZone: (zoneId: string, body: UpdateNameRequest) =>
    parkingClient
      .patch<ApiResponse<ParkingStructure>>(`/parking-lots/zones/${zoneId}`, body)
      .then((r) => r.data),
  deleteParkingZone: (zoneId: string) =>
    parkingClient.delete(`/parking-lots/zones/${zoneId}`),
  addZoneSlot: (zoneId: string, body: SlotCodeRequest) =>
    parkingClient
      .post<ApiResponse<Slot>>(`/parking-lots/zones/${zoneId}/slots`, body)
      .then((r) => r.data),
  updateZoneSlot: (slotId: string, body: SlotCodeRequest) =>
    parkingClient
      .patch<ApiResponse<Slot>>(`/parking-lots/slots/${slotId}`, body)
      .then((r) => r.data),
  deleteZoneSlot: (slotId: string) =>
    parkingClient.delete(`/parking-lots/slots/${slotId}`),
  getParkingLayout: (lotId: string) =>
    parkingClient
      .get<ApiResponse<ParkingLayout>>(`/parking-lots/${lotId}/layout`)
      .then((r) => r.data),
  saveParkingLayout: (lotId: string, body: SaveParkingLayoutRequest) =>
    parkingClient
      .put<ApiResponse<ParkingLayout>>(`/parking-lots/${lotId}/layout`, body)
      .then((r) => r.data),
  publishParkingLayout: (lotId: string) =>
    parkingClient
      .post<ApiResponse<ParkingLayout>>(`/parking-lots/${lotId}/layout/publish`)
      .then((r) => r.data),
  getFloorLayout: (floorId: string) =>
    parkingClient
      .get<ApiResponse<ParkingLayout>>(`/parking-lots/floors/${floorId}/layout`)
      .then((r) => r.data),
  saveFloorLayout: (floorId: string, body: SaveParkingLayoutRequest) =>
    parkingClient
      .put<ApiResponse<ParkingLayout>>(`/parking-lots/floors/${floorId}/layout`, body)
      .then((r) => r.data),
  publishFloorLayout: (floorId: string) =>
    parkingClient
      .post<ApiResponse<ParkingLayout>>(`/parking-lots/floors/${floorId}/layout/publish`)
      .then((r) => r.data),

  // Slots
  getSlotAvailability: () =>
    parkingClient
      .get<ApiResponse<SlotAvailability>>("/slots/availability")
      .then((r) => r.data),
  listSlots: () =>
    parkingClient.get<ApiResponse<Slot[]>>("/slots").then((r) => r.data),
  createSlot: (body: CreateSlotRequest) =>
    parkingClient.post<ApiResponse<Slot>>("/slots", body).then((r) => r.data),
  deleteSlot: (id: string) =>
    parkingClient.delete(`/slots/${id}`).then((r) => r.data),
  updateSlotStatus: (id: string, body: UpdateSlotStatusRequest) =>
    parkingClient
      .patch<ApiResponse<Slot>>(`/slots/${id}/status`, body)
      .then((r) => r.data),
  provisionZone: (body: ProvisionZoneRequest) =>
    parkingClient
      .post<ApiResponse<ProvisionResult>>("/slots/provision", body)
      .then((r) => r.data),

  // Gates
  listGates: () =>
    parkingClient.get<ApiResponse<Gate[]>>("/gates").then((r) => r.data),
  overrideGate: (id: string, body: GateOverrideRequest) =>
    parkingClient
      .post<ApiResponse<unknown>>(`/gates/${id}/override`, body)
      .then((r) => r.data),

  // Vehicles — whitelist (BR-002-2) + blacklist (BR-002-1)
  listWhitelist: () =>
    parkingClient
      .get<ApiResponse<Vehicle[]>>("/vehicles/whitelist")
      .then((r) => r.data),
  addWhitelist: (body: CreateWhitelistRequest) =>
    parkingClient
      .post<ApiResponse<Vehicle>>("/vehicles/whitelist", body)
      .then((r) => r.data),
  deleteWhitelist: (plate: string) =>
    parkingClient.delete(`/vehicles/whitelist/${plate}`).then((r) => r.data),

  listBlacklist: () =>
    parkingClient
      .get<ApiResponse<Vehicle[]>>("/vehicles/blacklist")
      .then((r) => r.data),
  addBlacklist: (body: CreateWhitelistRequest) =>
    parkingClient
      .post<ApiResponse<Vehicle>>("/vehicles/blacklist", body)
      .then((r) => r.data),
  deleteBlacklist: (plate: string) =>
    parkingClient.delete(`/vehicles/blacklist/${plate}`).then((r) => r.data),

  // Alerts (BR-007) — security/operational anomalies
  listAlerts: (status?: AlertStatus) =>
    parkingClient
      .get<ApiResponse<Page<Alert>>>("/alerts", {
        params: status ? { status } : undefined,
      })
      .then((r) => r.data),
  ackAlert: (id: string) =>
    parkingClient
      .post<ApiResponse<Alert>>(`/alerts/${id}/ack`)
      .then((r) => r.data),
};
