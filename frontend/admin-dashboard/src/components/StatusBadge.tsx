import { Badge } from "@/components/ui/badge";
import type {
  SessionStatus,
  GateStatus,
  InvoiceStatus,
  VehicleType,
  SlotStatus,
} from "@/types";

type Variant = React.ComponentProps<typeof Badge>["variant"];

const sessionMap: Record<SessionStatus, { label: string; variant: Variant }> = {
  PENDING: { label: "Chờ xử lý", variant: "warning" },
  ACTIVE: { label: "Đang đỗ", variant: "success" },
  CLOSED: { label: "Đã ra", variant: "secondary" },
  CANCELLED: { label: "Đã hủy", variant: "destructive" },
  REQUIRES_ATTENTION: { label: "Cần đối soát", variant: "warning" },
};

const gateMap: Record<GateStatus, { label: string; variant: Variant }> = {
  OPEN: { label: "Đang mở", variant: "success" },
  CLOSED: { label: "Đang đóng", variant: "secondary" },
  ERROR: { label: "Lỗi", variant: "destructive" },
};

const invoiceMap: Record<InvoiceStatus, { label: string; variant: Variant }> = {
  PENDING: { label: "Chưa thanh toán", variant: "warning" },
  PAID: { label: "Đã thanh toán", variant: "success" },
  WAIVED: { label: "Miễn phí", variant: "secondary" },
};

const vehicleMap: Record<VehicleType, { label: string; variant: Variant }> = {
  REGULAR: { label: "Thường", variant: "secondary" },
  WHITELIST: { label: "Whitelist", variant: "success" },
  BLACKLIST: { label: "Blacklist", variant: "destructive" },
};

export function SessionStatusBadge({ status }: { status: SessionStatus }) {
  const c = sessionMap[status] ?? { label: status, variant: "secondary" };
  return <Badge variant={c.variant}>{c.label}</Badge>;
}

export function GateStatusBadge({ status }: { status: GateStatus }) {
  const c = gateMap[status] ?? { label: status, variant: "secondary" };
  return <Badge variant={c.variant}>{c.label}</Badge>;
}

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  const c = invoiceMap[status] ?? { label: status, variant: "secondary" };
  return <Badge variant={c.variant}>{c.label}</Badge>;
}

const slotMap: Record<SlotStatus, { label: string; variant: Variant }> = {
  EMPTY: { label: "Trống", variant: "success" },
  OCCUPIED: { label: "Có xe", variant: "secondary" },
  MAINTENANCE: { label: "Bảo trì", variant: "warning" },
};

export function VehicleTypeBadge({ type }: { type: VehicleType }) {
  const c = vehicleMap[type] ?? { label: type, variant: "secondary" };
  return <Badge variant={c.variant}>{c.label}</Badge>;
}

export function SlotStatusBadge({ status }: { status: SlotStatus }) {
  const c = slotMap[status] ?? { label: status, variant: "secondary" };
  return <Badge variant={c.variant}>{c.label}</Badge>;
}
