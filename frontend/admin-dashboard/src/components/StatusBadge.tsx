import { Badge } from "@/components/ui/badge";
import type {
  SessionStatus,
  GateStatus,
  InvoiceStatus,
  VehicleType,
  SlotStatus,
} from "@/types";
import { cn } from "@/lib/utils";

type Variant = React.ComponentProps<typeof Badge>["variant"];

const dotColors: Record<Variant & string, string> = {
  success: "bg-success",
  warning: "bg-warning",
  destructive: "bg-destructive",
  secondary: "bg-muted-foreground",
  default: "bg-primary",
  info: "bg-info",
  outline: "bg-foreground",
};

function StatusBadgeBase({ variant = "secondary", label }: { variant?: Variant; label: string }) {
  const dotColor = dotColors[variant as string] || "bg-muted-foreground";
  return (
    <Badge variant={variant} className="gap-1.5 px-2.5">
      <span className={cn("h-1.5 w-1.5 rounded-full", dotColor)} />
      {label}
    </Badge>
  );
}

const sessionMap: Record<SessionStatus, { label: string; variant: Variant }> = {
  PENDING: { label: "Chờ xử lý", variant: "warning" },
  ACTIVE: { label: "Đang đỗ", variant: "success" },
  CLOSED: { label: "Đã ra", variant: "secondary" },
  CANCELLED: { label: "Đã hủy", variant: "destructive" },
  REQUIRES_ATTENTION: { label: "Cần đối soát", variant: "warning" },
};

export function SessionStatusBadge({ status }: { status: SessionStatus }) {
  const c = sessionMap[status] ?? { label: status, variant: "secondary" };
  return <StatusBadgeBase variant={c.variant} label={c.label} />;
}

const gateMap: Record<GateStatus, { label: string; variant: Variant }> = {
  OPEN: { label: "Đang mở", variant: "success" },
  CLOSED: { label: "Đang đóng", variant: "secondary" },
  ERROR: { label: "Lỗi", variant: "destructive" },
};

export function GateStatusBadge({ status }: { status: GateStatus }) {
  const c = gateMap[status] ?? { label: status, variant: "secondary" };
  return <StatusBadgeBase variant={c.variant} label={c.label} />;
}

const invoiceMap: Record<InvoiceStatus, { label: string; variant: Variant }> = {
  PENDING: { label: "Chưa thanh toán", variant: "warning" },
  PAID: { label: "Đã thanh toán", variant: "success" },
  WAIVED: { label: "Miễn phí", variant: "secondary" },
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  const c = invoiceMap[status] ?? { label: status, variant: "secondary" };
  return <StatusBadgeBase variant={c.variant} label={c.label} />;
}

const slotMap: Record<SlotStatus, { label: string; variant: Variant }> = {
  EMPTY: { label: "Trống", variant: "success" },
  OCCUPIED: { label: "Có xe", variant: "secondary" },
  MAINTENANCE: { label: "Bảo trì", variant: "warning" },
};

export function SlotStatusBadge({ status }: { status: SlotStatus }) {
  const c = slotMap[status] ?? { label: status, variant: "secondary" };
  return <StatusBadgeBase variant={c.variant} label={c.label} />;
}

const vehicleMap: Record<VehicleType, { label: string; variant: Variant }> = {
  REGULAR: { label: "Thường", variant: "secondary" },
  WHITELIST: { label: "Whitelist", variant: "success" },
  BLACKLIST: { label: "Blacklist", variant: "destructive" },
};

export function VehicleTypeBadge({ type }: { type: VehicleType }) {
  const c = vehicleMap[type] ?? { label: type, variant: "secondary" };
  return <StatusBadgeBase variant={c.variant} label={c.label} />;
}
