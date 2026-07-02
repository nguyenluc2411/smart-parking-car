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
