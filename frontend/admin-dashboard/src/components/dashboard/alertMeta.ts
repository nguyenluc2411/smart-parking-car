import {
  ShieldBan,
  CopyCheck,
  DoorOpen,
  ScanLine,
  type LucideIcon,
} from "lucide-react";
import type { Alert, AlertType, AlertSeverity } from "@/types";

/** Per-type label + icon, shared by the header bell and the dashboard panel. */
export const ALERT_META: Record<AlertType, { label: string; icon: LucideIcon }> =
  {
    DUPLICATE_ACTIVE_ENTRY: { label: "Nghi biển giả / clone", icon: CopyCheck },
    BLACKLIST_HIT: { label: "Xe trong danh sách cấm", icon: ShieldBan },
    UNMATCHED_EXIT: { label: "Xe ra không khớp phiên", icon: DoorOpen },
    LOW_CONFIDENCE: { label: "Đọc biển không chắc chắn", icon: ScanLine },
  };

/**
 * A concise, human description composed on the client (not the raw backend message) so the wording
 * stays consistent and readable across the bell + panel.
 */
export function describeAlert(a: Alert): string {
  const plate = a.plateNumber ?? "không rõ";
  const at = a.gateId ? ` tại ${a.gateId}` : "";
  switch (a.alertType) {
    case "DUPLICATE_ACTIVE_ENTRY":
      return `Biển ${plate} đã có xe trong bãi nhưng lại được quét vào${at}.`;
    case "BLACKLIST_HIT":
      return `Biển ${plate} thuộc danh sách cấm, bị từ chối vào${at}.`;
    case "UNMATCHED_EXIT":
      return `Biển ${plate} ra bãi${at} nhưng không có phiên đang mở.`;
    case "LOW_CONFIDENCE":
      return `Đọc biển ${plate}${at} dưới ngưỡng tin cậy.`;
  }
}

/**
 * Where clicking an alert should take the operator to resolve it fastest.
 * - BLACKLIST_HIT: the car was DENIED entry (no session) → the blacklist management tab.
 * - has a session (e.g. UNMATCHED_EXIT's REQUIRES_ATTENTION row) → that session's detail.
 * - otherwise (duplicate / low-confidence) → that plate's session history to investigate.
 */
export function alertHref(a: Alert): string | null {
  if (a.alertType === "BLACKLIST_HIT") return "/vehicles?tab=blacklist";
  if (a.sessionId) return `/sessions/${a.sessionId}`;
  if (a.plateNumber) return `/sessions?plate=${encodeURIComponent(a.plateNumber)}`;
  return null;
}

/** Severity → tailwind classes for the icon chip, accent bar and label. */
export const SEVERITY_STYLE: Record<
  AlertSeverity,
  { label: string; chip: string; bar: string; ring: string }
> = {
  CRITICAL: {
    label: "Nghiêm trọng",
    chip: "bg-destructive/10 text-destructive",
    bar: "bg-destructive",
    ring: "ring-destructive/20",
  },
  WARNING: {
    label: "Cảnh báo",
    chip: "bg-warning/10 text-amber-700 dark:text-amber-400",
    bar: "bg-warning",
    ring: "ring-warning/20",
  },
};
