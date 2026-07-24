// ─── Common ───────────────────────────────────────────────
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string;
  timestamp: string;
  error?: {
    code: string;
    message: string;
    field: string | null;
  };
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

// ─── Auth ─────────────────────────────────────────────────
export type Role = "ADMIN" | "OPERATOR";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  role: Role;
}

// ─── Slots ────────────────────────────────────────────────
export type SlotStatus = "EMPTY" | "OCCUPIED" | "RESERVED" | "MAINTENANCE";

export interface SlotAvailability {
  totalSlots: number;
  occupiedSlots: number;
  /** Held for a driver's booking (BR-009); unavailable to a walk-in. */
  reservedSlots: number;
  emptySlots: number;
  maintenanceSlots: number;
  /** (occupied + reserved) / total — a held slot cannot take a car, so it counts as used. */
  occupancyRate: number;
}

export interface Slot {
  id: string;
  slotCode: string;
  zone: string;
  zoneId: string | null;
  status: SlotStatus;
  currentSessionId: string | null;
  /** Position on the zone map (BR-003-6); null for slots created before the map existed. */
  gridRow: number | null;
  gridCol: number | null;
}

export interface CreateSlotRequest {
  slotCode: string;
  zone: string;
}

export interface UpdateSlotStatusRequest {
  status: SlotStatus;
}

export interface ProvisionZoneRequest {
  zone: string;
  count: number;
}

export interface ProvisionResult {
  zone: string;
  zoneTotal: number;
  created: number;
  removed: number;
  total: number;
}

// ─── Sessions ─────────────────────────────────────────────
export type SessionStatus =
  | "PENDING"
  | "ACTIVE"
  | "CLOSED"
  | "CANCELLED"
  | "REQUIRES_ATTENTION";

export interface SessionListItem {
  id: string;
  plateNumber: string;
  slotCode: string | null;
  entryTime: string;
  exitTime: string | null;
  durationSeconds: number | null;
  status: SessionStatus;
  /** Non-null only once the exit barrier has physically opened for this session (BR-005-5). */
  exitReleasedAt: string | null;
}

export interface SessionDetail {
  id: string;
  plateNumber: string;
  slot: { id: string; slotCode: string; zone: string } | null;
  entryGate: { id: string; gateCode: string } | null;
  exitGate: { id: string; gateCode: string } | null;
  entryTime: string;
  exitTime: string | null;
  durationSeconds: number | null;
  status: SessionStatus;
  /** Non-null only once the exit barrier has physically opened for this session (BR-005-5). */
  exitReleasedAt: string | null;
  entryImageUrl: string | null;
  exitImageUrl: string | null;
  entryPlateImageUrl: string | null;
  exitPlateImageUrl: string | null;
}

export interface SessionFilterParams {
  status?: SessionStatus;
  date?: string;
  plate?: string;
  page?: number;
  size?: number;
}

export interface InvoiceFilterParams {
  status?: InvoiceStatus;
  date?: string;
  plate?: string;
  page?: number;
  size?: number;
}

// ─── Gates ────────────────────────────────────────────────
export type GateStatus = "OPEN" | "CLOSED" | "ERROR";
export type GateDirection = "IN" | "OUT";
export type GateCommand = "OPEN" | "CLOSE";

export interface Gate {
  id: string;
  gateCode: string;
  direction: GateDirection;
  status: GateStatus;
  /** False for the auxiliary outage gate, which is a physical lane without a barrier. */
  hasBarrier: boolean;
  parkingLotId: string | null;
  floorId: string | null;
  lastCommand: string | null;
  lastCommandAt: string | null;
}

export interface GateOverrideRequest {
  command: GateCommand;
  reason: string;
}

export type OutageEventType = "ENTRY" | "EXIT";

export interface OutageEventRequest {
  clientEventId: string;
  type: OutageEventType;
  plateNumber: string;
  gateId: string;
  /** Actual capture time. The server uses this value instead of synchronization time. */
  occurredAt: string;
  note: string;
}

export interface QueuedOutageEvent extends OutageEventRequest {
  status: "PENDING" | "FAILED";
  queuedAt: string;
  lastError?: string;
}

export interface ParkingLot {
  id: string;
  lotCode: string;
  name: string;
  address: string | null;
  active: boolean;
}

export type LayoutElementType = "SLOT" | "GATE" | "BARRIER" | "ROAD" | "LABEL" | "ZONE";

export interface ParkingLayoutElement {
  id: string;
  type: LayoutElementType;
  referenceId: string | null;
  label: string | null;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  properties: Record<string, string>;
}

export interface ParkingLayout {
  parkingLotId: string;
  floorId: string;
  canvasWidth: number;
  canvasHeight: number;
  draftVersion: number;
  publishedVersion: number;
  publishedAt: string | null;
  elements: ParkingLayoutElement[];
}

export interface ParkingZone {
  id: string;
  zoneCode: string;
  name: string;
  slotCount: number;
}

export interface ParkingFloor {
  id: string;
  floorCode: string;
  name: string;
  sortOrder: number;
  zones: ParkingZone[];
}

export interface ParkingStructure {
  lot: ParkingLot;
  floors: ParkingFloor[];
}

export interface CreateParkingLotRequest {
  lotCode: string;
  name: string;
  address: string;
  createTemplate: boolean;
  groundZoneCount?: number;
  slotsPerZone?: number;
}

export interface UpdateParkingLotRequest {
  name: string;
  address: string;
}

export interface UpdateNameRequest {
  name: string;
}

export interface SlotCodeRequest {
  slotCode: string;
}

export interface CreateFloorRequest {
  floorCode: string;
  name: string;
  zoneCount?: number;
  slotsPerZone?: number;
}

export interface CreateZoneRequest {
  zoneCode: string;
  name: string;
  initialSlots: number;
}

export interface SaveParkingLayoutRequest {
  expectedVersion: number;
  canvasWidth: number;
  canvasHeight: number;
  elements: ParkingLayoutElement[];
}

// ─── Vehicles ─────────────────────────────────────────────
export type VehicleType = "REGULAR" | "WHITELIST" | "BLACKLIST";

export interface Vehicle {
  id: string;
  plateNumber: string;
  vehicleType: VehicleType;
  ownerName: string | null;
  note: string | null;
  createdAt: string;
  // Set only on a POST that moved a plate between lists; null otherwise.
  reclassifiedFrom?: VehicleType | null;
}

export interface CreateWhitelistRequest {
  plateNumber: string;
  ownerName: string;
  note: string;
  // Confirm re-classifying a plate already in the other list (whitelist<->blacklist).
  force?: boolean;
}

// ─── Alerts (BR-007) ──────────────────────────────────────
export type AlertType =
  | "DUPLICATE_ACTIVE_ENTRY"
  | "BLACKLIST_HIT"
  | "UNMATCHED_EXIT"
  | "LOW_CONFIDENCE";
export type AlertSeverity = "CRITICAL" | "WARNING";
export type AlertStatus = "NEW" | "ACKNOWLEDGED";

export interface Alert {
  id: string;
  alertType: AlertType;
  severity: AlertSeverity;
  plateNumber: string | null;
  gateId: string | null;
  sessionId: string | null;
  imageUrl: string | null;
  message: string;
  status: AlertStatus;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  createdAt: string;
}

// ─── Billing ──────────────────────────────────────────────
export type InvoiceStatus = "PENDING" | "PAID" | "WAIVED";
// ONLINE = gate self-pay settled via PayOS/MoMo gateway (BillingServiceImpl.settleOnlinePaid).
// CASH_OFFLINE = cash taken by hand during a power cut, keyed in later (BR-005-7).
export type PaymentMethod = "CASH" | "QR_CODE" | "ONLINE" | "CASH_OFFLINE";

export interface Invoice {
  invoiceId: string;
  sessionId: string;
  plateNumber: string;
  entryTime: string;
  exitTime: string;
  durationMinutes: number;
  ratePerMin: number;
  peakApplied: boolean;
  overnightApplied: boolean;
  amount: number;
  status: InvoiceStatus;
  // Only populated by GET /billing/sessions/{sessionId} (single-invoice detail) — the rate
  // actually applied to this invoice (BR-004-5 traceability), not whatever is effective now.
  peakMultiplier?: number;
  overnightFlat?: number;
  minCharge?: number;
}

export interface PaymentRequest {
  method: PaymentMethod;
  amountPaid: number;
  note: string;
}

export interface PaymentResult {
  invoiceId: string;
  status: InvoiceStatus;
  paidAt: string;
}

export type DayType = "ALL" | "WEEKDAY" | "WEEKEND";

export interface RateSchedule {
  hourStart: number;
  hourEnd: number;
  isPeak: boolean;
  dayType: DayType;
}

export interface Rate {
  id: string;
  ratePerMin: number;
  peakMultiplier: number;
  overnightFlat: number;
  minCharge: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  schedules: RateSchedule[];
}

export interface UpdateRateRequest {
  ratePerMin: number;
  peakMultiplier: number;
  overnightFlat: number;
  minCharge: number;
}

/** One row of `collected.byMethod` — money actually taken, keyed on when it was received. */
export interface MethodRevenue {
  method: PaymentMethod;
  amount: number;
  count: number;
}

/**
 * BR-005-8: what was actually collected in the period, split cash vs gateway.
 *
 * `total` deliberately does NOT equal `totalRevenue`: that one counts invoices for cars that left
 * in the period (including unpaid ones), this one counts payments by `paid_at` — including outage
 * cash taken days earlier and keyed in now. The gap between them is the thing worth looking at.
 */
export interface CollectionSummary {
  /** CASH + CASH_OFFLINE — what an operator's till is counted against. */
  cashTotal: number;
  /** QR_CODE + ONLINE — money that never passed through anyone's hands. */
  gatewayTotal: number;
  total: number;
  byMethod: MethodRevenue[];
}

export interface DailyReport {
  date: string;
  totalSessions: number;
  totalRevenue: number;
  peakSessions: number;
  avgDurationMinutes: number;
  revenueByHour: { hour: number; revenue: number; sessions: number }[];
  collected: CollectionSummary;
}

export interface MonthlyReport {
  month: string;
  totalSessions: number;
  totalRevenue: number;
  prevMonthRevenue: number;
  growthRate: number;
  avgDailyRevenue: number;
  revenueByDay: { date: string; revenue: number }[];
  collected: CollectionSummary;
}

// ─── Users ────────────────────────────────────────────────
export interface User {
  id: string;
  username: string;
  email: string;
  role: Role;
  isActive: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  role: Role;
}

// ─── Audit ────────────────────────────────────────────────
export interface AuditLog {
  id: string;
  userId: string;
  username: string;
  action: string;
  targetEntity: string;
  targetId: string;
  payload: Record<string, unknown>;
  sourceService: string;
  createdAt: string;
}
