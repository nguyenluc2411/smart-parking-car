"use client";

import {
  PointerEvent as ReactPointerEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowDownToLine,
  ArrowLeft,
  ArrowRight,
  ArrowUpFromLine,
  Building2,
  CarFront,
  Check,
  ChevronRight,
  CircleParking,
  DoorOpen,
  Eye,
  Layers3,
  Map,
  Move,
  Navigation,
  PanelTop,
  Pencil,
  Plus,
  Radio,
  RotateCw,
  Save,
  Send,
  Sparkles,
  Trash2,
  Warehouse,
  Wrench,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { useSlots } from "@/lib/hooks/useSlots";
import { useGates } from "@/lib/hooks/useGates";
import {
  useAddParkingFloor,
  useAddParkingZone,
  useCreateParkingLot,
  useParkingLayout,
  useParkingLots,
  useParkingStructure,
  usePublishParkingLayout,
  useSaveParkingLayout,
  useParkingStructureCrud,
} from "@/lib/hooks/useParkingLayout";
import { parkingApi } from "@/lib/api/parking";
import { cn } from "@/lib/utils";
import type {
  Gate,
  LayoutElementType,
  ParkingFloor,
  ParkingLayoutElement,
  Slot,
  SlotStatus,
} from "@/types";

const CANVAS_WIDTH = 1280;
const CANVAS_HEIGHT = 780;

const STATUS_LABEL: Record<SlotStatus, string> = {
  EMPTY: "Còn trống",
  RESERVED: "Đã được đặt trước",
  OCCUPIED: "Đang có xe",
  MAINTENANCE: "Tạm ngưng",
};

const STATUS_DOT: Record<SlotStatus, string> = {
  EMPTY: "bg-emerald-500",
  RESERVED: "bg-amber-500",
  OCCUPIED: "bg-rose-500",
  MAINTENANCE: "bg-slate-500",
};

const STATUS_SLOT: Record<SlotStatus, string> = {
  EMPTY: "border-emerald-300 bg-slate-700 text-white",
  RESERVED: "border-amber-300 bg-amber-500 text-slate-950",
  OCCUPIED: "border-rose-300 bg-rose-600 text-white",
  MAINTENANCE: "border-slate-300 bg-slate-500 text-white",
};

type LotWizard = {
  lotCode: string;
  name: string;
  address: string;
  groundZoneCount: number;
  groundSlotsPerZone: number;
  hasFloors: boolean;
  floorCount: number;
  floorZoneCount: number;
  floorSlotsPerZone: number;
};

type CrudTarget = {
  type: "LOT" | "FLOOR" | "ZONE" | "SLOT";
  id: string;
  name: string;
  code: string;
  address?: string;
};

const INITIAL_WIZARD: LotWizard = {
  lotCode: "",
  name: "",
  address: "",
  groundZoneCount: 2,
  groundSlotsPerZone: 10,
  hasFloors: false,
  floorCount: 1,
  floorZoneCount: 2,
  floorSlotsPerZone: 10,
};

function vietnameseError(error: unknown, action = "thực hiện thao tác") {
  const responseError = error as {
    code?: string;
    message?: string;
    response?: {
      status?: number;
      data?: { error?: { code?: string; message?: string }; message?: string };
    };
  };
  const status = responseError.response?.status;
  const serverMessage =
    responseError.response?.data?.error?.message ??
    responseError.response?.data?.message ??
    responseError.message;

  if (!responseError.response) {
    return `Không thể ${action} vì không kết nối được máy chủ. Hãy kiểm tra Docker và thử lại.`;
  }
  if (status === 400) {
    return `Thông tin chưa hợp lệ${serverMessage ? `: ${serverMessage}` : ". Hãy kiểm tra các ô đang nhập."}`;
  }
  if (status === 401 || status === 403) {
    return "Phiên đăng nhập đã hết hạn hoặc tài khoản không có quyền. Hãy đăng nhập lại bằng tài khoản quản trị.";
  }
  if (status === 404) {
    return `Không tìm thấy dữ liệu cần ${action}. Dữ liệu có thể đã bị thay đổi, hãy tải lại trang.`;
  }
  if (status === 409) {
    return `Không thể ${action} vì mã đã tồn tại hoặc dữ liệu vừa được người khác cập nhật${serverMessage ? `: ${serverMessage}` : "."}`;
  }
  if (status && status >= 500) {
    return `Máy chủ gặp lỗi khi ${action}. Dữ liệu chưa được thay đổi, hãy thử lại sau.`;
  }
  return serverMessage
    ? `Không thể ${action}: ${serverMessage}`
    : `Không thể ${action}. Hãy kiểm tra lại thông tin và thử lại.`;
}

function layoutElement(
  type: LayoutElementType,
  label: string,
  x: number,
  y: number,
  width: number,
  height: number,
  properties: Record<string, string> = {}
): ParkingLayoutElement {
  return {
    id: crypto.randomUUID(),
    type,
    referenceId: null,
    label,
    x,
    y,
    width,
    height,
    rotation: 0,
    properties,
  };
}

function buildVisualTemplate(
  floor: ParkingFloor,
  slots: Slot[],
  gates: Gate[]
): ParkingLayoutElement[] {
  const elements: ParkingLayoutElement[] = [
    layoutElement("ROAD", "Luồng xe chính", 80, 320, 1120, 140, {
      kind: "MAIN_ROAD",
    }),
    layoutElement(
      "LABEL",
      floor.sortOrder === 0 ? "NHÀ ĐIỀU HÀNH" : "SẢNH THANG MÁY",
      535,
      345,
      210,
      90,
      { kind: "BOOTH" }
    ),
    layoutElement("LABEL", "HƯỚNG DI CHUYỂN  →", 490, 280, 300, 38, {
      kind: "DIRECTION",
    }),
  ];

  if (floor.sortOrder > 0) {
    elements.push(
      layoutElement("ROAD", "DỐC LÊN ↑", 95, 332, 235, 115, {
        kind: "RAMP_UP",
      }),
      layoutElement("ROAD", "DỐC XUỐNG ↓", 950, 332, 235, 115, {
        kind: "RAMP_DOWN",
      })
    );
  }

  const upperCount = Math.ceil(floor.zones.length / 2);
  const lowerCount = Math.floor(floor.zones.length / 2);

  floor.zones.forEach((zone, zoneIndex) => {
    const upper = zoneIndex % 2 === 0;
    const position = Math.floor(zoneIndex / 2);
    const rowZoneCount = Math.max(1, upper ? upperCount : lowerCount);
    const zoneWidth = Math.max(240, Math.floor(1040 / rowZoneCount));
    const actualWidth = Math.min(zoneWidth - 20, 1020);
    const zoneX = 120 + position * zoneWidth;
    const zoneY = upper ? 25 : 490;
    const zoneSlots = slots
      .filter((slot) => slot.zoneId === zone.id)
      .sort((a, b) => a.slotCode.localeCompare(b.slotCode));

    elements.push(
      layoutElement("ZONE", zone.name, zoneX, zoneY, actualWidth, 265, {
        zoneCode: zone.zoneCode,
      })
    );

    const columns = Math.max(
      1,
      Math.ceil(Math.sqrt(Math.max(1, zoneSlots.length) * (actualWidth / 215)))
    );
    const rows = Math.max(1, Math.ceil(zoneSlots.length / columns));
    const slotWidth = Math.max(20, Math.min(42, Math.floor((actualWidth - 28) / columns) - 3));
    const slotHeight = Math.max(20, Math.min(62, Math.floor(210 / rows) - 3));
    const stepX = slotWidth + 3;
    const stepY = slotHeight + 3;

    zoneSlots.forEach((slot, slotIndex) => {
      elements.push({
        ...layoutElement(
          "SLOT",
          slot.slotCode,
          zoneX + 14 + (slotIndex % columns) * stepX,
          zoneY + 42 + Math.floor(slotIndex / columns) * stepY,
          slotWidth,
          slotHeight,
          { zoneCode: zone.zoneCode }
        ),
        referenceId: slot.id,
      });
    });
  });

  gates
    .sort((a, b) => a.gateCode.localeCompare(b.gateCode))
    .forEach((gate) => {
      const x = gate.direction === "IN" ? 15 : 1155;
      const y = gate.hasBarrier ? 338 : 405;
      elements.push({
        ...layoutElement("GATE", gate.gateCode, x, y, 110, 56, {
          hasBarrier: String(gate.hasBarrier),
        }),
        referenceId: gate.id,
      });
      if (gate.hasBarrier) {
        elements.push(
          layoutElement(
            "BARRIER",
            "BARIE",
            gate.direction === "IN" ? 75 : 1110,
            y + 50,
            115,
            24,
            { direction: gate.direction }
          )
        );
      }
    });

  return elements;
}

function LayoutItem({
  element,
  slot,
  gate,
  selected,
  editing,
  onSelect,
  onDragStart,
}: {
  element: ParkingLayoutElement;
  slot?: Slot;
  gate?: Gate;
  selected: boolean;
  editing: boolean;
  onSelect: () => void;
  onDragStart: (event: ReactPointerEvent<HTMLDivElement>) => void;
}) {
  const kind = element.properties.kind;
  const status = slot?.status ?? "EMPTY";
  const isAuxiliary = gate && !gate.hasBarrier;

  return (
    <div
      className={cn(
        "absolute select-none overflow-hidden transition-all",
        editing && "cursor-move",
        selected && "ring-4 ring-sky-400 ring-offset-2 ring-offset-slate-900",
        element.type === "ZONE" &&
          "rounded-2xl border border-sky-300/50 bg-sky-950/25 text-sky-100",
        element.type === "SLOT" &&
          cn(
            "flex items-center justify-center rounded border-2 text-center font-bold shadow-sm",
            STATUS_SLOT[status]
          ),
        element.type === "GATE" &&
          cn(
            "flex items-center justify-center rounded-xl border-2 bg-white text-xs font-bold shadow-lg",
            isAuxiliary ? "border-orange-400 text-orange-700" : "border-sky-400 text-sky-800"
          ),
        element.type === "BARRIER" &&
          "flex items-center justify-center rounded-full border border-white bg-white text-[9px] font-black text-slate-800 shadow",
        element.type === "ROAD" &&
          "flex items-center justify-center overflow-hidden rounded-xl text-center font-black",
        element.type === "LABEL" &&
          "flex items-center justify-center rounded-xl text-center font-bold",
        kind === "BOOTH" &&
          "border-4 border-white bg-gradient-to-br from-sky-100 to-blue-300 text-slate-800 shadow-xl",
        kind === "DIRECTION" &&
          "border border-white/20 bg-slate-900/90 text-sm tracking-widest text-white shadow",
        (kind === "RAMP_UP" || kind === "RAMP_DOWN") &&
          "border-4 border-white/70 bg-gradient-to-b from-slate-500 to-slate-800 text-white shadow-xl"
      )}
      style={{
        left: element.x,
        top: element.y,
        width: element.width,
        height: element.height,
        transform: `rotate(${element.rotation}deg)`,
        zIndex:
          element.type === "ZONE"
            ? 1
            : element.type === "ROAD"
              ? 2
              : element.type === "LABEL"
                ? 4
                : 6,
        ...(element.type === "BARRIER"
          ? {
              backgroundImage:
                "repeating-linear-gradient(135deg,#ffffff 0,#ffffff 13px,#ef4444 13px,#ef4444 26px)",
            }
          : {}),
      }}
      onPointerDown={editing ? onDragStart : undefined}
      onClick={(event) => {
        event.stopPropagation();
        onSelect();
      }}
    >
      {element.type === "ZONE" && (
        <div className="absolute left-3 top-2 flex items-center gap-1.5 text-xs font-black uppercase tracking-widest">
          <CircleParking className="h-4 w-4" />
          {element.label}
        </div>
      )}

      {element.type === "SLOT" && (
        <>
          <span className="absolute right-0.5 top-0.5">
            <Radio className="h-2.5 w-2.5 opacity-80" />
          </span>
          <div className="leading-none">
            {status === "EMPTY" ? (
              <span className="text-[10px] font-black">P</span>
            ) : (
              <CarFront className="mx-auto h-4 w-4" />
            )}
            <span className="mt-0.5 block text-[8px]">{slot?.slotCode ?? element.label}</span>
          </div>
        </>
      )}

      {element.type === "GATE" && (
        <div className="flex items-center gap-1.5 px-2">
          <DoorOpen className="h-4 w-4 shrink-0" />
          <span>
            {isAuxiliary
              ? gate?.direction === "IN"
                ? "CỔNG PHỤ VÀO"
                : "CỔNG PHỤ RA"
              : gate?.direction === "IN"
                ? "LỐI VÀO"
                : "LỐI RA"}
          </span>
        </div>
      )}

      {element.type === "ROAD" && kind === "MAIN_ROAD" && (
        <>
          <div className="absolute inset-0 bg-slate-700" />
          <div className="absolute left-0 right-0 top-1/2 border-t-4 border-dashed border-amber-300/90" />
          <div className="relative z-10 flex w-full items-center justify-around text-4xl text-white/90">
            <ArrowRight />
            <ArrowRight />
            <ArrowRight />
            <ArrowRight />
          </div>
        </>
      )}

      {element.type === "ROAD" && kind === "RAMP_UP" && (
        <div className="flex items-center gap-3">
          <ArrowUpFromLine className="h-8 w-8 text-emerald-300" />
          <div><div className="text-lg">DỐC LÊN</div><div className="text-[10px] font-medium">Đi lên tầng trên</div></div>
        </div>
      )}

      {element.type === "ROAD" && kind === "RAMP_DOWN" && (
        <div className="flex items-center gap-3">
          <ArrowDownToLine className="h-8 w-8 text-orange-300" />
          <div><div className="text-lg">DỐC XUỐNG</div><div className="text-[10px] font-medium">Đi xuống tầng dưới</div></div>
        </div>
      )}

      {element.type === "ROAD" && !kind && (
        <div className="flex h-full w-full items-center justify-center border-y-4 border-dashed border-white/60 bg-slate-700 text-white">
          <Navigation className="mr-2 h-5 w-5" /> {element.label}
        </div>
      )}

      {element.type === "LABEL" && kind === "BOOTH" && (
        <div>
          <Warehouse className="mx-auto mb-1 h-7 w-7 text-blue-700" />
          <span className="text-xs">{element.label}</span>
        </div>
      )}
      {element.type === "LABEL" && kind !== "BOOTH" && element.label}
      {element.type === "BARRIER" && <span className="rounded bg-white/85 px-1">{element.label}</span>}
    </div>
  );
}

function WizardSteps({ step }: { step: number }) {
  const items = ["Thông tin bãi", "Quy mô", "Xác nhận"];
  return (
    <div className="grid grid-cols-3 gap-2">
      {items.map((item, index) => {
        const number = index + 1;
        const active = number === step;
        const done = number < step;
        return (
          <div key={item} className="flex items-center gap-2">
            <span
              className={cn(
                "flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs font-bold",
                active && "border-blue-600 bg-blue-600 text-white",
                done && "border-emerald-600 bg-emerald-600 text-white",
                !active && !done && "text-muted-foreground"
              )}
            >
              {done ? <Check className="h-4 w-4" /> : number}
            </span>
            <span className={cn("hidden text-xs font-medium sm:block", active && "text-blue-700")}>{item}</span>
            {index < 2 && <ChevronRight className="ml-auto hidden h-4 w-4 text-muted-foreground sm:block" />}
          </div>
        );
      })}
    </div>
  );
}

export default function ParkingMapPage() {
  const queryClient = useQueryClient();
  const lotsQuery = useParkingLots();
  const slotsQuery = useSlots();
  const gatesQuery = useGates();
  const [lotId, setLotId] = useState("");
  const structureQuery = useParkingStructure(lotId);
  const [floorId, setFloorId] = useState("");
  const layoutQuery = useParkingLayout(floorId);
  const createLotMutation = useCreateParkingLot();
  const addFloorMutation = useAddParkingFloor(lotId);
  const addZoneMutation = useAddParkingZone(lotId, floorId);
  const structureCrud = useParkingStructureCrud(lotId, floorId);
  const saveMutation = useSaveParkingLayout(floorId);
  const publishMutation = usePublishParkingLayout(floorId);

  const [elements, setElements] = useState<ParkingLayoutElement[]>([]);
  const [version, setVersion] = useState(1);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardStep, setWizardStep] = useState(1);
  const [wizard, setWizard] = useState<LotWizard>(INITIAL_WIZARD);
  const [wizardError, setWizardError] = useState("");
  const [creating, setCreating] = useState(false);
  const [floorDialogOpen, setFloorDialogOpen] = useState(false);
  const [zoneDialogOpen, setZoneDialogOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<CrudTarget | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CrudTarget | null>(null);
  const [slotZone, setSlotZone] = useState<{ id: string; name: string; code: string } | null>(null);
  const [editValue, setEditValue] = useState("");
  const [editAddress, setEditAddress] = useState("");
  const [newSlotCode, setNewSlotCode] = useState("");
  const [floorForm, setFloorForm] = useState({
    floorCode: "",
    name: "",
    zoneCount: 2,
    slotsPerZone: 10,
  });
  const [zoneForm, setZoneForm] = useState({
    zoneCode: "",
    name: "",
    initialSlots: 10,
  });
  const canvasViewportRef = useRef<HTMLDivElement>(null);
  const [canvasScale, setCanvasScale] = useState(0.8);

  useEffect(() => {
    if (!lotId && lotsQuery.data?.[0]) {
      const visualSample = lotsQuery.data.find((lot) => lot.lotCode === "MALL01");
      setLotId(visualSample?.id ?? lotsQuery.data[0].id);
    }
  }, [lotId, lotsQuery.data]);

  useEffect(() => {
    const floors = structureQuery.data?.floors ?? [];
    if (!floors.some((floor) => floor.id === floorId)) setFloorId(floors[0]?.id ?? "");
  }, [floorId, structureQuery.data]);

  useEffect(() => {
    if (layoutQuery.data) {
      setElements(layoutQuery.data.elements);
      setVersion(layoutQuery.data.draftVersion);
      setSelectedId(null);
    }
  }, [layoutQuery.data]);

  useEffect(() => {
    const viewport = canvasViewportRef.current;
    if (!viewport) return;
    const observer = new ResizeObserver(([entry]) => {
      const width = entry.contentRect.width - 24;
      const canvasWidth = layoutQuery.data?.canvasWidth ?? CANVAS_WIDTH;
      setCanvasScale(Math.min(1, Math.max(0.35, width / canvasWidth)));
    });
    observer.observe(viewport);
    return () => observer.disconnect();
  }, [layoutQuery.data?.canvasWidth, editing]);

  const currentFloor = useMemo(
    () => structureQuery.data?.floors.find((floor) => floor.id === floorId),
    [floorId, structureQuery.data]
  );
  const zoneIds = useMemo(
    () => new Set((currentFloor?.zones ?? []).map((zone) => zone.id)),
    [currentFloor]
  );
  const slots = useMemo(
    () => (slotsQuery.data ?? []).filter((slot) => slot.zoneId && zoneIds.has(slot.zoneId)),
    [slotsQuery.data, zoneIds]
  );
  const gates = useMemo(
    () => (gatesQuery.data ?? []).filter((gate) => gate.floorId === floorId),
    [floorId, gatesQuery.data]
  );
  const slotMap = useMemo(() => new globalThis.Map(slots.map((slot) => [slot.id, slot])), [slots]);
  const gateMap = useMemo(() => new globalThis.Map(gates.map((gate) => [gate.id, gate])), [gates]);
  const selected = elements.find((element) => element.id === selectedId);
  const layout = layoutQuery.data;
  const dirty = JSON.stringify(elements) !== JSON.stringify(layout?.elements ?? []);
  const statusCount = useMemo(
    () =>
      slots.reduce(
        (count, slot) => ({ ...count, [slot.status]: count[slot.status] + 1 }),
        { EMPTY: 0, RESERVED: 0, OCCUPIED: 0, MAINTENANCE: 0 } as Record<SlotStatus, number>
      ),
    [slots]
  );

  function updateSelected(patch: Partial<ParkingLayoutElement>) {
    if (!selectedId) return;
    setElements((current) =>
      current.map((element) => (element.id === selectedId ? { ...element, ...patch } : element))
    );
  }

  function dragStart(event: ReactPointerEvent<HTMLDivElement>, element: ParkingLayoutElement) {
    event.preventDefault();
    event.stopPropagation();
    setSelectedId(element.id);
    const startX = event.clientX;
    const startY = event.clientY;
    const originX = element.x;
    const originY = element.y;
    const move = (pointer: PointerEvent) => {
      const maxX = (layout?.canvasWidth ?? CANVAS_WIDTH) - element.width;
      const maxY = (layout?.canvasHeight ?? CANVAS_HEIGHT) - element.height;
      const deltaX = (pointer.clientX - startX) / canvasScale;
      const deltaY = (pointer.clientY - startY) / canvasScale;
      const x = Math.max(0, Math.min(maxX, Math.round((originX + deltaX) / 5) * 5));
      const y = Math.max(0, Math.min(maxY, Math.round((originY + deltaY) / 5) * 5));
      setElements((current) =>
        current.map((item) => (item.id === element.id ? { ...item, x, y } : item))
      );
    };
    const up = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  }

  function addSceneElement(kind: "ROAD" | "RAMP_UP" | "RAMP_DOWN" | "BOOTH" | "BARRIER" | "LABEL") {
    const element =
      kind === "ROAD"
        ? layoutElement("ROAD", "Lối xe chạy", 200, 320, 420, 110)
        : kind === "RAMP_UP"
          ? layoutElement("ROAD", "DỐC LÊN ↑", 95, 332, 235, 115, { kind })
          : kind === "RAMP_DOWN"
            ? layoutElement("ROAD", "DỐC XUỐNG ↓", 950, 332, 235, 115, { kind })
            : kind === "BOOTH"
              ? layoutElement("LABEL", "NHÀ ĐIỀU HÀNH", 535, 345, 210, 90, { kind })
              : kind === "BARRIER"
                ? layoutElement("BARRIER", "BARIE", 80, 400, 115, 24)
                : layoutElement("LABEL", "Biển hướng dẫn", 500, 270, 260, 40, {
                    kind: "DIRECTION",
                  });
    setElements((current) => [...current, element]);
    setSelectedId(element.id);
  }

  function applyTemplate() {
    if (!currentFloor) {
      toast.error("Chưa chọn tầng để tạo bố cục mẫu.");
      return;
    }
    setElements(buildVisualTemplate(currentFloor, slots, gates));
    setSelectedId(null);
    toast.success("Đã sắp xếp lại mặt bằng theo mẫu. Hãy kiểm tra và bấm “Lưu bản nháp”.");
  }

  function save() {
    if (!layout) return;
    saveMutation.mutate(
      {
        expectedVersion: version,
        canvasWidth: CANVAS_WIDTH,
        canvasHeight: CANVAS_HEIGHT,
        elements,
      },
      {
        onSuccess: (response) => {
          setVersion(response.data.draftVersion);
          toast.success("Đã lưu bản nháp sơ đồ.");
        },
        onError: (error) => toast.error(vietnameseError(error, "lưu sơ đồ")),
      }
    );
  }

  function publish() {
    if (dirty) {
      toast.error("Sơ đồ còn thay đổi chưa lưu. Hãy lưu bản nháp trước khi xuất bản.");
      return;
    }
    publishMutation.mutate(undefined, {
      onSuccess: () => toast.success("Đã xuất bản sơ đồ để sử dụng khi vận hành."),
      onError: (error) => toast.error(vietnameseError(error, "xuất bản sơ đồ")),
    });
  }

  function validateWizardStep() {
    if (wizardStep === 1) {
      if (!wizard.lotCode.trim()) return "Vui lòng nhập mã bãi, ví dụ: Q1 hoặc TTTM01.";
      if (!/^[A-Za-z0-9_-]+$/.test(wizard.lotCode)) {
        return "Mã bãi chỉ được chứa chữ, số, dấu gạch ngang hoặc gạch dưới.";
      }
      if (!wizard.name.trim()) return "Vui lòng nhập tên bãi xe để dễ nhận biết.";
    }
    if (wizardStep === 2) {
      if (wizard.groundZoneCount < 1 || wizard.groundZoneCount > 8) {
        return "Bãi mặt đất cần từ 1 đến 8 khu.";
      }
      if (wizard.groundSlotsPerZone < 1 || wizard.groundSlotsPerZone > 100) {
        return "Mỗi khu cần từ 1 đến 100 ô ô tô.";
      }
      if (wizard.hasFloors && (wizard.floorCount < 1 || wizard.floorCount > 10)) {
        return "Số tầng bổ sung cần từ 1 đến 10.";
      }
    }
    return "";
  }

  function nextWizardStep() {
    const validation = validateWizardStep();
    if (validation) {
      setWizardError(validation);
      return;
    }
    setWizardError("");
    setWizardStep((step) => Math.min(3, step + 1));
  }

  async function createLot() {
    setWizardError("");
    setCreating(true);
    try {
      const response = await createLotMutation.mutateAsync({
        lotCode: wizard.lotCode.trim().toUpperCase(),
        name: wizard.name.trim(),
        address: wizard.address.trim(),
        createTemplate: true,
        groundZoneCount: wizard.groundZoneCount,
        slotsPerZone: wizard.groundSlotsPerZone,
      });
      let structure = response.data;
      if (wizard.hasFloors) {
        for (let index = 1; index <= wizard.floorCount; index += 1) {
          const floorResponse = await parkingApi.addParkingFloor(structure.lot.id, {
            floorCode: `F${index}`,
            name: `Tầng ${index}`,
            zoneCount: wizard.floorZoneCount,
            slotsPerZone: wizard.floorSlotsPerZone,
          });
          structure = floorResponse.data;
        }
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["parking-lots"] }),
        queryClient.invalidateQueries({ queryKey: ["parking-structure", structure.lot.id] }),
        queryClient.invalidateQueries({ queryKey: ["slots"] }),
        queryClient.invalidateQueries({ queryKey: ["gates"] }),
      ]);
      setLotId(structure.lot.id);
      setFloorId(structure.floors[0]?.id ?? "");
      setWizardOpen(false);
      setWizardStep(1);
      setWizard(INITIAL_WIZARD);
      toast.success(`Đã tạo ${structure.lot.name} với sơ đồ mặt bằng ban đầu.`);
    } catch (error) {
      setWizardError(vietnameseError(error, "tạo bãi xe"));
    } finally {
      setCreating(false);
    }
  }

  function addFloor() {
    if (!floorForm.floorCode.trim() || !floorForm.name.trim()) {
      toast.error("Vui lòng nhập đầy đủ mã tầng và tên tầng.");
      return;
    }
    addFloorMutation.mutate(floorForm, {
      onSuccess: (response) => {
        const floor = response.data.floors.at(-1);
        if (floor) setFloorId(floor.id);
        setFloorDialogOpen(false);
        setFloorForm({ floorCode: "", name: "", zoneCount: 2, slotsPerZone: 10 });
        queryClient.invalidateQueries({ queryKey: ["slots"] });
        toast.success("Đã thêm tầng cùng các khu và dốc lên/xuống mẫu.");
      },
      onError: (error) => toast.error(vietnameseError(error, "thêm tầng")),
    });
  }

  function addZone() {
    if (!zoneForm.zoneCode.trim() || !zoneForm.name.trim()) {
      toast.error("Vui lòng nhập đầy đủ mã khu và tên khu.");
      return;
    }
    addZoneMutation.mutate(zoneForm, {
      onSuccess: () => {
        setZoneDialogOpen(false);
        setZoneForm({ zoneCode: "", name: "", initialSlots: 10 });
        toast.success("Đã thêm khu và các ô ô tô vào tầng đang chọn.");
      },
      onError: (error) => toast.error(vietnameseError(error, "thêm khu")),
    });
  }

  function openEdit(target: CrudTarget) {
    setEditTarget(target);
    setEditValue(target.name);
    setEditAddress(target.address ?? "");
  }

  function submitEdit() {
    if (!editTarget || !editValue.trim()) {
      toast.error(editTarget?.type === "SLOT"
        ? "Vui lòng nhập mã ô xe."
        : "Vui lòng nhập tên mới.");
      return;
    }
    const success = () => {
      setEditTarget(null);
      toast.success("Đã cập nhật thông tin.");
    };
    const failed = (error: unknown) => toast.error(vietnameseError(error, "cập nhật thông tin"));

    if (editTarget.type === "LOT") {
      structureCrud.updateLot.mutate(
        { name: editValue.trim(), address: editAddress.trim() },
        { onSuccess: success, onError: failed }
      );
    } else if (editTarget.type === "FLOOR") {
      structureCrud.updateFloor.mutate(
        { id: editTarget.id, name: editValue.trim() },
        { onSuccess: success, onError: failed }
      );
    } else if (editTarget.type === "ZONE") {
      structureCrud.updateZone.mutate(
        { id: editTarget.id, name: editValue.trim() },
        { onSuccess: success, onError: failed }
      );
    } else {
      structureCrud.updateSlot.mutate(
        { id: editTarget.id, slotCode: editValue.trim().toUpperCase() },
        { onSuccess: success, onError: failed }
      );
    }
  }

  function confirmDelete() {
    if (!deleteTarget) return;
    const target = deleteTarget;
    const success = () => {
      if (target.type === "LOT") {
        setLotId("");
        setFloorId("");
      } else if (target.type === "FLOOR") {
        setFloorId("");
      }
      setDeleteTarget(null);
      toast.success(`Đã xóa ${target.name}.`);
    };
    const failed = (error: unknown) => {
      setDeleteTarget(null);
      toast.error(vietnameseError(error, `xóa ${target.name}`));
    };

    if (target.type === "LOT") {
      structureCrud.deleteLot.mutate(undefined, { onSuccess: success, onError: failed });
    } else if (target.type === "FLOOR") {
      structureCrud.deleteFloor.mutate(target.id, { onSuccess: success, onError: failed });
    } else if (target.type === "ZONE") {
      structureCrud.deleteZone.mutate(target.id, { onSuccess: success, onError: failed });
    } else {
      structureCrud.deleteSlot.mutate(target.id, { onSuccess: success, onError: failed });
    }
  }

  function addSingleSlot() {
    if (!slotZone || !newSlotCode.trim()) {
      toast.error("Vui lòng nhập mã ô xe, ví dụ A09.");
      return;
    }
    structureCrud.addSlot.mutate(
      { zoneId: slotZone.id, slotCode: newSlotCode.trim().toUpperCase() },
      {
        onSuccess: () => {
          setSlotZone(null);
          setNewSlotCode("");
          toast.success("Đã thêm ô xe và đặt vào sơ đồ.");
        },
        onError: (error) => toast.error(vietnameseError(error, "thêm ô xe")),
      }
    );
  }

  function toggleSlotMaintenance(slot: Slot) {
    if (slot.status !== "EMPTY" && slot.status !== "MAINTENANCE") {
      toast.error(`Không thể đổi trạng thái ô ${slot.slotCode} vì đang có xe hoặc đã được đặt trước.`);
      return;
    }
    const target = slot.status === "MAINTENANCE" ? "EMPTY" : "MAINTENANCE";
    structureCrud.setSlotStatus.mutate(
      { id: slot.id, status: target },
      {
        onSuccess: () => toast.success(
          target === "MAINTENANCE"
            ? `Đã tạm ngưng ô ${slot.slotCode}.`
            : `Đã đưa ô ${slot.slotCode} hoạt động trở lại.`
        ),
        onError: (error) => toast.error(vietnameseError(error, "đổi trạng thái ô xe")),
      }
    );
  }

  if (lotsQuery.isLoading) {
    return <div className="p-10 text-center text-muted-foreground">Đang tải danh sách bãi xe...</div>;
  }

  return (
    <RoleGuard allow={["ADMIN"]}>
      <div className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-bold">
              <Map className="h-6 w-6 text-blue-600" /> Quản lý bãi xe
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Xem toàn bộ hành trình ô tô từ cổng vào, khu đỗ, đường lên tầng đến cổng ra.
            </p>
          </div>
          <Button onClick={() => setWizardOpen(true)} className="shadow-sm">
            <Plus className="mr-2 h-4 w-4" /> Thêm bãi xe
          </Button>
        </div>

        {!lotsQuery.data?.length ? (
          <Card className="border-dashed">
            <CardContent className="flex min-h-80 flex-col items-center justify-center text-center">
              <Building2 className="mb-4 h-14 w-14 text-blue-500" />
              <h2 className="text-xl font-semibold">Chưa có bãi xe nào</h2>
              <p className="mt-2 max-w-md text-sm text-muted-foreground">
                Tạo bãi đầu tiên, hệ thống sẽ dựng sẵn mặt đất, khu đỗ, cổng vào/ra và sơ đồ mẫu.
              </p>
              <Button className="mt-5" onClick={() => setWizardOpen(true)}>
                <Plus className="mr-2 h-4 w-4" /> Tạo bãi xe đầu tiên
              </Button>
            </CardContent>
          </Card>
        ) : (
          <>
            <Card>
              <CardContent className="grid gap-4 pt-6 lg:grid-cols-[minmax(240px,1.2fr)_2fr]">
                <div>
                  <Label htmlFor="parking-lot">Bãi xe đang quản lý</Label>
                  <div className="mt-2 flex gap-2">
                    <select
                      id="parking-lot"
                      value={lotId}
                      onChange={(event) => {
                        setLotId(event.target.value);
                        setFloorId("");
                      }}
                      className="h-11 min-w-0 flex-1 rounded-lg border bg-background px-3 text-sm font-semibold"
                    >
                      {lotsQuery.data.map((lot) => (
                        <option key={lot.id} value={lot.id}>
                          {lot.name} · {lot.lotCode}
                        </option>
                      ))}
                    </select>
                    {structureQuery.data?.lot && (
                      <>
                        <Button
                          variant="outline"
                          size="icon"
                          title="Đổi tên hoặc địa chỉ bãi"
                          aria-label="Sửa bãi xe"
                          onClick={() => openEdit({
                            type: "LOT",
                            id: structureQuery.data!.lot.id,
                            name: structureQuery.data!.lot.name,
                            code: structureQuery.data!.lot.lotCode,
                            address: structureQuery.data!.lot.address ?? "",
                          })}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="outline"
                          size="icon"
                          title={structureQuery.data.lot.lotCode === "DEFAULT" ? "Không thể xóa bãi mặc định" : "Xóa bãi xe"}
                          aria-label="Xóa bãi xe"
                          className="text-destructive"
                          disabled={structureQuery.data.lot.lotCode === "DEFAULT"}
                          onClick={() => setDeleteTarget({
                            type: "LOT",
                            id: structureQuery.data!.lot.id,
                            name: structureQuery.data!.lot.name,
                            code: structureQuery.data!.lot.lotCode,
                          })}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </>
                    )}
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">
                    {structureQuery.data?.lot.address || "Bãi xe chưa cập nhật địa chỉ"}
                  </p>
                </div>
                <div>
                  <Label>Chọn mặt bằng cần xem</Label>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {structureQuery.data?.floors.map((floor) => (
                      <button
                        key={floor.id}
                        onClick={() => setFloorId(floor.id)}
                        className={cn(
                          "flex min-w-32 items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm transition",
                          floor.id === floorId
                            ? "border-blue-600 bg-blue-50 text-blue-800 shadow-sm"
                            : "hover:border-blue-300 hover:bg-muted/50"
                        )}
                      >
                        <Layers3 className="h-4 w-4" />
                        <span>
                          <span className="block font-semibold">{floor.name}</span>
                          <span className="block text-[11px] opacity-70">
                            {floor.zones.length} khu ·{" "}
                            {floor.zones.reduce((sum, zone) => sum + zone.slotCount, 0)} ô
                          </span>
                        </span>
                      </button>
                    ))}
                    <button
                      onClick={() => setFloorDialogOpen(true)}
                      className="flex items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-sm text-blue-700 hover:bg-blue-50"
                    >
                      <Plus className="h-4 w-4" /> Thêm tầng
                    </button>
                  </div>
                </div>
              </CardContent>
            </Card>

            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
              <Card><CardContent className="pt-5"><p className="text-xs text-muted-foreground">Tổng số ô tầng này</p><p className="mt-1 text-2xl font-bold">{slots.length}</p></CardContent></Card>
              {(["EMPTY", "RESERVED", "OCCUPIED", "MAINTENANCE"] as SlotStatus[]).map((status) => (
                <Card key={status}>
                  <CardContent className="flex items-center justify-between pt-5">
                    <div><p className="text-xs text-muted-foreground">{STATUS_LABEL[status]}</p><p className="mt-1 text-2xl font-bold">{statusCount[status]}</p></div>
                    <span className={cn("h-3 w-3 rounded-full", STATUS_DOT[status])} />
                  </CardContent>
                </Card>
              ))}
            </div>

            <div className="grid gap-5 xl:grid-cols-[330px_minmax(0,1fr)]">
              <Card className="h-fit">
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Cấu trúc bãi</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    {structureQuery.data?.floors.map((floor) => (
                      <div
                        key={floor.id}
                        className={cn(
                          "rounded-xl border p-3",
                          floor.id === floorId && "border-blue-400 bg-blue-50/70"
                        )}
                      >
                        <div className="flex items-center gap-1">
                          <button
                            className="flex min-w-0 flex-1 items-center gap-2 text-left text-sm font-bold"
                            onClick={() => setFloorId(floor.id)}
                          >
                            {floor.sortOrder === 0 ? <Warehouse className="h-4 w-4 shrink-0" /> : <Layers3 className="h-4 w-4 shrink-0" />}
                            <span className="truncate">{floor.name}</span>
                          </button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7"
                            title={`Đổi tên ${floor.name}`}
                            aria-label={`Sửa ${floor.name}`}
                            onClick={() => openEdit({
                              type: "FLOOR", id: floor.id, name: floor.name, code: floor.floorCode,
                            })}
                          >
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 text-destructive"
                            title={floor.sortOrder === 0 ? "Không thể xóa riêng mặt đất" : `Xóa ${floor.name}`}
                            aria-label={`Xóa ${floor.name}`}
                            disabled={floor.sortOrder === 0}
                            onClick={() => setDeleteTarget({
                              type: "FLOOR", id: floor.id, name: floor.name, code: floor.floorCode,
                            })}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                        <div className="mt-2 space-y-2 pl-6">
                          {floor.zones.map((zone) => (
                            <div key={zone.id} className="rounded-lg border bg-background/80 p-2">
                              <div className="flex items-center gap-1 text-xs">
                                <span className="flex min-w-0 flex-1 items-center gap-1.5 font-medium">
                                  <span className="h-2 w-2 shrink-0 rounded-full bg-sky-500" />
                                  <span className="truncate">{zone.name}</span>
                                  <span className="text-muted-foreground">({zone.slotCount} ô)</span>
                                </span>
                                <button
                                  className="rounded p-1 hover:bg-muted"
                                  title={`Thêm ô vào ${zone.name}`}
                                  aria-label={`Thêm ô vào ${zone.name}`}
                                  onClick={() => {
                                    setSlotZone({ id: zone.id, name: zone.name, code: zone.zoneCode });
                                    setNewSlotCode(`${zone.zoneCode}${String(zone.slotCount + 1).padStart(2, "0")}`);
                                  }}
                                >
                                  <Plus className="h-3.5 w-3.5" />
                                </button>
                                <button
                                  className="rounded p-1 hover:bg-muted"
                                  title={`Đổi tên ${zone.name}`}
                                  aria-label={`Sửa ${zone.name}`}
                                  onClick={() => openEdit({
                                    type: "ZONE", id: zone.id, name: zone.name, code: zone.zoneCode,
                                  })}
                                >
                                  <Pencil className="h-3.5 w-3.5" />
                                </button>
                                <button
                                  className="rounded p-1 text-destructive hover:bg-red-50"
                                  title={`Xóa ${zone.name}`}
                                  aria-label={`Xóa ${zone.name}`}
                                  disabled={floor.zones.length <= 1}
                                  onClick={() => setDeleteTarget({
                                    type: "ZONE", id: zone.id, name: zone.name, code: zone.zoneCode,
                                  })}
                                >
                                  <Trash2 className="h-3.5 w-3.5" />
                                </button>
                              </div>
                              {floor.id === floorId && (
                                <details className="mt-2">
                                  <summary className="cursor-pointer text-[11px] font-medium text-blue-700">
                                    Quản lý từng ô xe
                                  </summary>
                                  <div className="mt-2 max-h-44 space-y-1 overflow-y-auto">
                                    {slots
                                      .filter((slot) => slot.zoneId === zone.id)
                                      .map((slot) => (
                                        <div key={slot.id} className="flex items-center gap-1 rounded border px-2 py-1 text-[11px]">
                                          <span className={cn("h-2 w-2 rounded-full", STATUS_DOT[slot.status])} />
                                          <span className="min-w-0 flex-1 font-semibold">{slot.slotCode}</span>
                                          <span className="text-[10px] text-muted-foreground">{STATUS_LABEL[slot.status]}</span>
                                          <button
                                            className="rounded p-1 hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40"
                                            title={slot.status === "MAINTENANCE" ? `Cho ô ${slot.slotCode} hoạt động lại` : `Tạm ngưng ô ${slot.slotCode}`}
                                            aria-label={slot.status === "MAINTENANCE" ? `Kích hoạt ô ${slot.slotCode}` : `Tạm ngưng ô ${slot.slotCode}`}
                                            disabled={slot.status !== "EMPTY" && slot.status !== "MAINTENANCE"}
                                            onClick={() => toggleSlotMaintenance(slot)}
                                          >
                                            <Wrench className="h-3 w-3" />
                                          </button>
                                          <button
                                            className="rounded p-1 hover:bg-muted"
                                            title={`Đổi mã ô ${slot.slotCode}`}
                                            aria-label={`Sửa ô ${slot.slotCode}`}
                                            onClick={() => openEdit({
                                              type: "SLOT", id: slot.id, name: slot.slotCode, code: slot.slotCode,
                                            })}
                                          >
                                            <Pencil className="h-3 w-3" />
                                          </button>
                                          <button
                                            className="rounded p-1 text-destructive hover:bg-red-50"
                                            title={`Xóa ô ${slot.slotCode}`}
                                            aria-label={`Xóa ô ${slot.slotCode}`}
                                            onClick={() => setDeleteTarget({
                                              type: "SLOT", id: slot.id, name: `ô ${slot.slotCode}`, code: slot.slotCode,
                                            })}
                                          >
                                            <Trash2 className="h-3 w-3" />
                                          </button>
                                        </div>
                                      ))}
                                  </div>
                                </details>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                  <Button variant="outline" className="w-full" onClick={() => setZoneDialogOpen(true)} disabled={!floorId}>
                    <Plus className="mr-2 h-4 w-4" /> Thêm khu vào tầng này
                  </Button>

                  {editing && (
                    <div className="space-y-3 border-t pt-4">
                      <div>
                        <p className="text-xs font-bold uppercase text-muted-foreground">Thêm vào sơ đồ</p>
                        <p className="mt-1 text-[11px] text-muted-foreground">Chọn thành phần rồi kéo đến vị trí mong muốn.</p>
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        <Button variant="outline" size="sm" onClick={() => addSceneElement("ROAD")}><Navigation className="mr-1 h-3.5 w-3.5" />Đường</Button>
                        <Button variant="outline" size="sm" onClick={() => addSceneElement("BARRIER")}><PanelTop className="mr-1 h-3.5 w-3.5" />Barie</Button>
                        <Button variant="outline" size="sm" onClick={() => addSceneElement("RAMP_UP")}><ArrowUpFromLine className="mr-1 h-3.5 w-3.5" />Dốc lên</Button>
                        <Button variant="outline" size="sm" onClick={() => addSceneElement("RAMP_DOWN")}><ArrowDownToLine className="mr-1 h-3.5 w-3.5" />Dốc xuống</Button>
                        <Button variant="outline" size="sm" onClick={() => addSceneElement("BOOTH")}><Warehouse className="mr-1 h-3.5 w-3.5" />Booth</Button>
                        <Button variant="outline" size="sm" onClick={() => addSceneElement("LABEL")}><Navigation className="mr-1 h-3.5 w-3.5" />Biển báo</Button>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>

              <div className="min-w-0 space-y-3">
                <Card>
                  <CardContent className="flex flex-wrap items-center gap-2 pt-4">
                    <Button
                      variant={editing ? "default" : "outline"}
                      size="sm"
                      onClick={() => setEditing((value) => !value)}
                    >
                      {editing ? <Move className="mr-2 h-4 w-4" /> : <Eye className="mr-2 h-4 w-4" />}
                      {editing ? "Đang chỉnh sửa" : "Đang xem vận hành"}
                    </Button>
                    {editing && (
                      <>
                        <Button variant="outline" size="sm" onClick={applyTemplate}>
                          <Sparkles className="mr-2 h-4 w-4" /> Áp dụng bố cục mẫu
                        </Button>
                        <Button variant="outline" size="sm" onClick={save} disabled={!dirty || saveMutation.isPending}>
                          <Save className="mr-2 h-4 w-4" /> Lưu bản nháp
                        </Button>
                        <Button size="sm" onClick={publish} disabled={publishMutation.isPending || dirty}>
                          <Send className="mr-2 h-4 w-4" /> Xuất bản
                        </Button>
                      </>
                    )}
                    <span className="ml-auto text-xs text-muted-foreground">
                      Bản nháp v{version}{dirty ? " · Chưa lưu" : ""} · Đã xuất bản v{layout?.publishedVersion ?? 0}
                    </span>
                  </CardContent>
                </Card>

                {layoutQuery.isLoading ? (
                  <Card><CardContent className="p-12 text-center text-muted-foreground">Đang dựng mặt bằng...</CardContent></Card>
                ) : (
                  <div
                    ref={canvasViewportRef}
                    className="overflow-hidden rounded-2xl border-4 border-slate-300 bg-slate-200 p-3 shadow-inner"
                  >
                    <div
                      style={{
                        width: (layout?.canvasWidth ?? CANVAS_WIDTH) * canvasScale,
                        height: (layout?.canvasHeight ?? CANVAS_HEIGHT) * canvasScale,
                      }}
                    >
                      <div
                        className="relative origin-top-left overflow-hidden rounded-xl border-8 border-slate-500 bg-slate-800 shadow-2xl"
                        style={{
                          width: layout?.canvasWidth ?? CANVAS_WIDTH,
                          height: layout?.canvasHeight ?? CANVAS_HEIGHT,
                          transform: `scale(${canvasScale})`,
                          backgroundImage:
                            "radial-gradient(circle at 20% 15%,rgba(255,255,255,.045) 0 2px,transparent 3px),radial-gradient(circle at 70% 65%,rgba(255,255,255,.035) 0 2px,transparent 3px)",
                          backgroundSize: "38px 38px,52px 52px",
                        }}
                        onClick={() => setSelectedId(null)}
                      >
                        <div className="absolute left-4 top-4 z-20 rounded-lg bg-slate-950/80 px-3 py-2 text-white shadow">
                          <p className="text-[10px] uppercase tracking-wider text-slate-300">Mặt bằng đang xem</p>
                          <p className="text-sm font-bold">{currentFloor?.name}</p>
                        </div>
                        {elements.map((element) => (
                          <LayoutItem
                            key={element.id}
                            element={element}
                            slot={element.referenceId ? slotMap.get(element.referenceId) : undefined}
                            gate={element.referenceId ? gateMap.get(element.referenceId) : undefined}
                            selected={selectedId === element.id}
                            editing={editing}
                            onSelect={() => setSelectedId(element.id)}
                            onDragStart={(event) => dragStart(event, element)}
                          />
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                <div className="flex flex-wrap gap-3 rounded-xl border bg-background p-3 text-xs">
                  <span className="font-semibold">Chú thích:</span>
                  {(["EMPTY", "RESERVED", "OCCUPIED", "MAINTENANCE"] as SlotStatus[]).map((status) => (
                    <span key={status} className="flex items-center gap-1.5">
                      <span className={cn("h-2.5 w-2.5 rounded-full", STATUS_DOT[status])} />
                      {STATUS_LABEL[status]}
                    </span>
                  ))}
                  <span className="flex items-center gap-1.5"><Radio className="h-3.5 w-3.5 text-sky-500" /> Cảm biến ô xe</span>
                </div>

                {editing && selected && (
                  <Card>
                    <CardHeader className="pb-3"><CardTitle className="text-base">Chỉnh thành phần đang chọn</CardTitle></CardHeader>
                    <CardContent className="grid gap-4 md:grid-cols-[1fr_2fr_auto]">
                      <div>
                        <Label>Tên hiển thị</Label>
                        <Input
                          className="mt-1.5"
                          value={selected.label ?? ""}
                          onChange={(event) => updateSelected({ label: event.target.value })}
                          disabled={selected.type === "SLOT" || selected.type === "GATE"}
                        />
                      </div>
                      <div className="grid grid-cols-4 gap-2">
                        <div><Label>X</Label><Input className="mt-1.5" type="number" value={selected.x} onChange={(event) => updateSelected({ x: Number(event.target.value) })} /></div>
                        <div><Label>Y</Label><Input className="mt-1.5" type="number" value={selected.y} onChange={(event) => updateSelected({ y: Number(event.target.value) })} /></div>
                        <div><Label>Rộng</Label><Input className="mt-1.5" type="number" value={selected.width} onChange={(event) => updateSelected({ width: Number(event.target.value) })} /></div>
                        <div><Label>Cao</Label><Input className="mt-1.5" type="number" value={selected.height} onChange={(event) => updateSelected({ height: Number(event.target.value) })} /></div>
                      </div>
                      <div className="flex items-end gap-2">
                        <Button variant="outline" size="icon" title="Xoay 15 độ" onClick={() => updateSelected({ rotation: (selected.rotation + 15) % 360 })}>
                          <RotateCw className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="outline"
                          size="icon"
                          title="Gỡ khỏi sơ đồ"
                          className="text-destructive"
                          onClick={() => {
                            setElements((current) => current.filter((element) => element.id !== selected.id));
                            setSelectedId(null);
                          }}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                )}
              </div>
            </div>
          </>
        )}
      </div>

      <Dialog open={wizardOpen} onOpenChange={setWizardOpen}>
        <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Thêm bãi xe mới</DialogTitle>
            <DialogDescription>
              Thiết lập quy mô trước, hệ thống sẽ tự dựng sơ đồ mẫu để bạn chỉnh lại sau.
            </DialogDescription>
          </DialogHeader>
          <WizardSteps step={wizardStep} />

          <div className="min-h-72 py-3">
            {wizardStep === 1 && (
              <div className="grid gap-5 sm:grid-cols-2">
                <div className="sm:col-span-2 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
                  <p className="font-semibold">Bước đầu chỉ cần thông tin nhận diện bãi.</p>
                  <p className="mt-1 text-xs">Tầng mặt đất, cổng chính, cổng phụ và sơ đồ sẽ được tạo tự động ở bước sau.</p>
                </div>
                <div>
                  <Label htmlFor="new-lot-code">Mã bãi *</Label>
                  <Input
                    id="new-lot-code"
                    className="mt-1.5"
                    placeholder="Ví dụ: Q1, TTTM01"
                    maxLength={10}
                    value={wizard.lotCode}
                    onChange={(event) => setWizard({ ...wizard, lotCode: event.target.value.toUpperCase() })}
                  />
                  <p className="mt-1 text-[11px] text-muted-foreground">Dùng để phân biệt trong hệ thống, không nhập khoảng trắng.</p>
                </div>
                <div>
                  <Label htmlFor="new-lot-name">Tên bãi xe *</Label>
                  <Input
                    id="new-lot-name"
                    className="mt-1.5"
                    placeholder="Ví dụ: Bãi xe Nguyễn Huệ"
                    value={wizard.name}
                    onChange={(event) => setWizard({ ...wizard, name: event.target.value })}
                  />
                </div>
                <div className="sm:col-span-2">
                  <Label htmlFor="new-lot-address">Địa chỉ</Label>
                  <Input
                    id="new-lot-address"
                    className="mt-1.5"
                    placeholder="Số nhà, tên đường, quận/huyện..."
                    value={wizard.address}
                    onChange={(event) => setWizard({ ...wizard, address: event.target.value })}
                  />
                </div>
              </div>
            )}

            {wizardStep === 2 && (
              <div className="space-y-5">
                <div className="rounded-xl border-2 border-blue-300 bg-blue-50/70 p-4">
                  <div className="flex items-start gap-3">
                    <Warehouse className="mt-0.5 h-6 w-6 text-blue-700" />
                    <div className="flex-1">
                      <p className="font-bold text-blue-950">Mặt đất — luôn được tạo trước</p>
                      <p className="mt-1 text-xs text-blue-800">Chia thành Khu A, B, C... khi mặt bằng rộng.</p>
                      <div className="mt-4 grid gap-4 sm:grid-cols-2">
                        <div>
                          <Label>Số khu ở mặt đất</Label>
                          <Input
                            className="mt-1.5"
                            type="number"
                            min={1}
                            max={8}
                            value={wizard.groundZoneCount}
                            onChange={(event) => setWizard({ ...wizard, groundZoneCount: Number(event.target.value) })}
                          />
                        </div>
                        <div>
                          <Label>Số ô ô tô mỗi khu</Label>
                          <Input
                            className="mt-1.5"
                            type="number"
                            min={1}
                            max={100}
                            value={wizard.groundSlotsPerZone}
                            onChange={(event) => setWizard({ ...wizard, groundSlotsPerZone: Number(event.target.value) })}
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <label className="flex cursor-pointer items-start gap-3 rounded-xl border p-4 hover:bg-muted/40">
                  <input
                    type="checkbox"
                    className="mt-1 h-4 w-4"
                    checked={wizard.hasFloors}
                    onChange={(event) => setWizard({ ...wizard, hasFloors: event.target.checked })}
                  />
                  <span>
                    <span className="block font-semibold">Bãi có thêm tầng</span>
                    <span className="mt-1 block text-xs text-muted-foreground">
                      Dành cho trung tâm thương mại hoặc bãi quy mô lớn. Mỗi tầng có khu riêng và dốc lên/xuống.
                    </span>
                  </span>
                </label>

                {wizard.hasFloors && (
                  <div className="grid gap-4 rounded-xl border bg-muted/30 p-4 sm:grid-cols-3">
                    <div><Label>Số tầng thêm</Label><Input className="mt-1.5" type="number" min={1} max={10} value={wizard.floorCount} onChange={(event) => setWizard({ ...wizard, floorCount: Number(event.target.value) })} /></div>
                    <div><Label>Số khu mỗi tầng</Label><Input className="mt-1.5" type="number" min={1} max={8} value={wizard.floorZoneCount} onChange={(event) => setWizard({ ...wizard, floorZoneCount: Number(event.target.value) })} /></div>
                    <div><Label>Số ô mỗi khu</Label><Input className="mt-1.5" type="number" min={1} max={100} value={wizard.floorSlotsPerZone} onChange={(event) => setWizard({ ...wizard, floorSlotsPerZone: Number(event.target.value) })} /></div>
                  </div>
                )}
              </div>
            )}

            {wizardStep === 3 && (
              <div className="grid gap-5 lg:grid-cols-[1fr_1.2fr]">
                <div className="space-y-3 rounded-xl border p-4">
                  <p className="font-semibold">Thông tin sẽ tạo</p>
                  <div className="text-sm"><span className="text-muted-foreground">Bãi xe:</span><p className="font-bold">{wizard.name}</p></div>
                  <div className="text-sm"><span className="text-muted-foreground">Mã bãi:</span><p className="font-bold">{wizard.lotCode.toUpperCase()}</p></div>
                  <div className="text-sm"><span className="text-muted-foreground">Địa chỉ:</span><p>{wizard.address || "Chưa nhập"}</p></div>
                  <div className="border-t pt-3 text-sm">
                    <p><strong>Mặt đất:</strong> {wizard.groundZoneCount} khu × {wizard.groundSlotsPerZone} ô</p>
                    {wizard.hasFloors && <p className="mt-1"><strong>Tầng 1–{wizard.floorCount}:</strong> mỗi tầng {wizard.floorZoneCount} khu × {wizard.floorSlotsPerZone} ô</p>}
                  </div>
                </div>
                <div className="rounded-xl bg-slate-800 p-4 text-white">
                  <div className="mb-3 flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider">Mô phỏng cấu trúc</span>
                    <CarFront className="h-5 w-5 text-sky-300" />
                  </div>
                  <div className="space-y-2">
                    {wizard.hasFloors &&
                      Array.from({ length: wizard.floorCount }, (_, index) => wizard.floorCount - index).map((floor) => (
                        <div key={floor} className="rounded-lg border border-white/20 bg-white/10 px-3 py-2 text-xs">
                          <span className="font-bold">Tầng {floor}</span>
                          <span className="ml-2 text-slate-300">Khu {Array.from({ length: wizard.floorZoneCount }, (_, i) => String.fromCharCode(65 + i)).join(", ")}</span>
                          <span className="ml-2 text-emerald-300">↑ dốc lên · ↓ dốc xuống</span>
                        </div>
                      ))}
                    <div className="rounded-lg border-2 border-sky-400 bg-sky-950 px-3 py-3 text-xs">
                      <p className="font-bold">Mặt đất</p>
                      <p className="mt-1 text-sky-200">Cổng vào → Khu {Array.from({ length: wizard.groundZoneCount }, (_, i) => String.fromCharCode(65 + i)).join(", ")} → Cổng ra</p>
                      <p className="mt-1 text-orange-300">Có cổng phụ dùng khi barie gặp sự cố</p>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {wizardError && (
            <div role="alert" className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">
              <strong>Chưa thể tiếp tục:</strong> {wizardError}
            </div>
          )}

          <DialogFooter className="gap-2">
            {wizardStep > 1 && (
              <Button variant="outline" onClick={() => { setWizardError(""); setWizardStep((step) => step - 1); }} disabled={creating}>
                <ArrowLeft className="mr-2 h-4 w-4" /> Quay lại
              </Button>
            )}
            {wizardStep < 3 ? (
              <Button onClick={nextWizardStep}>
                Tiếp tục <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            ) : (
              <Button onClick={createLot} disabled={creating}>
                {creating ? "Đang dựng bãi xe..." : "Tạo bãi xe và sơ đồ"}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={floorDialogOpen} onOpenChange={setFloorDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Thêm tầng vào bãi</DialogTitle>
            <DialogDescription>Tầng mới sẽ có sẵn các khu, ô ô tô, dốc lên và dốc xuống.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <div><Label>Mã tầng *</Label><Input className="mt-1.5" placeholder="F1" value={floorForm.floorCode} onChange={(event) => setFloorForm({ ...floorForm, floorCode: event.target.value.toUpperCase() })} /></div>
            <div><Label>Tên tầng *</Label><Input className="mt-1.5" placeholder="Tầng 1" value={floorForm.name} onChange={(event) => setFloorForm({ ...floorForm, name: event.target.value })} /></div>
            <div><Label>Số khu</Label><Input className="mt-1.5" type="number" min={1} max={8} value={floorForm.zoneCount} onChange={(event) => setFloorForm({ ...floorForm, zoneCount: Number(event.target.value) })} /></div>
            <div><Label>Số ô mỗi khu</Label><Input className="mt-1.5" type="number" min={0} max={100} value={floorForm.slotsPerZone} onChange={(event) => setFloorForm({ ...floorForm, slotsPerZone: Number(event.target.value) })} /></div>
          </div>
          <DialogFooter><Button onClick={addFloor} disabled={addFloorMutation.isPending}>{addFloorMutation.isPending ? "Đang thêm tầng..." : "Thêm tầng"}</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={zoneDialogOpen} onOpenChange={setZoneDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Thêm khu vào {currentFloor?.name}</DialogTitle>
            <DialogDescription>Dùng khi mặt bằng rộng và cần chia thêm Khu B, C, D...</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <div><Label>Mã khu *</Label><Input className="mt-1.5" placeholder="B" value={zoneForm.zoneCode} onChange={(event) => setZoneForm({ ...zoneForm, zoneCode: event.target.value.toUpperCase() })} /></div>
            <div><Label>Tên khu *</Label><Input className="mt-1.5" placeholder="Khu B" value={zoneForm.name} onChange={(event) => setZoneForm({ ...zoneForm, name: event.target.value })} /></div>
            <div className="sm:col-span-2"><Label>Số ô ô tô ban đầu</Label><Input className="mt-1.5" type="number" min={0} max={100} value={zoneForm.initialSlots} onChange={(event) => setZoneForm({ ...zoneForm, initialSlots: Number(event.target.value) })} /></div>
          </div>
          <DialogFooter><Button onClick={addZone} disabled={addZoneMutation.isPending}>{addZoneMutation.isPending ? "Đang thêm khu..." : "Thêm khu"}</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(editTarget)} onOpenChange={(open) => !open && setEditTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editTarget?.type === "LOT"
                ? "Sửa thông tin bãi xe"
                : editTarget?.type === "SLOT"
                  ? "Đổi mã ô xe"
                  : `Đổi tên ${editTarget?.type === "FLOOR" ? "tầng" : "khu"}`}
            </DialogTitle>
            <DialogDescription>
              Mã định danh <strong>{editTarget?.code}</strong>{" "}
              {editTarget?.type === "SLOT"
                ? "có thể đổi nếu chưa trùng trong cùng khu."
                : "được giữ cố định để không ảnh hưởng dữ liệu vận hành."}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label>{editTarget?.type === "SLOT" ? "Mã ô mới" : "Tên hiển thị mới"}</Label>
              <Input
                className="mt-1.5"
                value={editValue}
                maxLength={editTarget?.type === "SLOT" ? 10 : 120}
                onChange={(event) => setEditValue(
                  editTarget?.type === "SLOT" ? event.target.value.toUpperCase() : event.target.value
                )}
              />
            </div>
            {editTarget?.type === "LOT" && (
              <div>
                <Label>Địa chỉ</Label>
                <Input
                  className="mt-1.5"
                  value={editAddress}
                  maxLength={255}
                  onChange={(event) => setEditAddress(event.target.value)}
                />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditTarget(null)}>Hủy</Button>
            <Button onClick={submitEdit}>Lưu thay đổi</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(slotZone)} onOpenChange={(open) => !open && setSlotZone(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Thêm ô xe vào {slotZone?.name}</DialogTitle>
            <DialogDescription>
              Ô mới sẽ có trạng thái còn trống và được tự động đặt vào vị trí tiếp theo trên sơ đồ.
            </DialogDescription>
          </DialogHeader>
          <div>
            <Label>Mã ô xe *</Label>
            <Input
              className="mt-1.5"
              placeholder={`${slotZone?.code ?? "A"}01`}
              maxLength={10}
              value={newSlotCode}
              onChange={(event) => setNewSlotCode(event.target.value.toUpperCase())}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSlotZone(null)}>Hủy</Button>
            <Button onClick={addSingleSlot} disabled={structureCrud.addSlot.isPending}>
              {structureCrud.addSlot.isPending ? "Đang thêm ô..." : "Thêm ô xe"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(deleteTarget)} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Xác nhận xóa {deleteTarget?.name}</DialogTitle>
            <DialogDescription>
              {deleteTarget?.type === "LOT" && "Toàn bộ tầng, khu, ô xe, cổng và sơ đồ của bãi sẽ bị xóa."}
              {deleteTarget?.type === "FLOOR" && "Toàn bộ khu, ô xe và sơ đồ thuộc tầng này sẽ bị xóa."}
              {deleteTarget?.type === "ZONE" && "Toàn bộ ô xe thuộc khu này cũng sẽ bị xóa."}
              {deleteTarget?.type === "SLOT" && "Ô xe sẽ được gỡ khỏi danh sách và sơ đồ."}
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
            Hệ thống sẽ tự chặn nếu dữ liệu đang có xe, booking hoặc lịch sử vận hành. Khi đó không có dữ liệu nào bị xóa.
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>Không xóa</Button>
            <Button variant="destructive" onClick={confirmDelete}>
              Xác nhận xóa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </RoleGuard>
  );
}
