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
export type SlotStatus = "EMPTY" | "OCCUPIED" | "MAINTENANCE";

export interface SlotAvailability {
  totalSlots: number;
  occupiedSlots: number;
  emptySlots: number;
  maintenanceSlots: number;
  occupancyRate: number;
}

export interface Slot {
  id: string;
  slotCode: string;
  zone: string;
  status: SlotStatus;
  currentSessionId: string | null;
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
  lastCommand: string | null;
  lastCommandAt: string | null;
}

export interface GateOverrideRequest {
  command: GateCommand;
  reason: string;
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
export type PaymentMethod = "CASH" | "QR_CODE";

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

export interface DailyReport {
  date: string;
  totalSessions: number;
  totalRevenue: number;
  peakSessions: number;
  avgDurationMinutes: number;
  revenueByHour: { hour: number; revenue: number; sessions: number }[];
}

export interface MonthlyReport {
  month: string;
  totalSessions: number;
  totalRevenue: number;
  prevMonthRevenue: number;
  growthRate: number;
  avgDailyRevenue: number;
  revenueByDay: { date: string; revenue: number }[];
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
