"use client";

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import axios from "axios";
import { AlertTriangle, Cloud, CloudOff, RefreshCw, Trash2, WifiOff } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { parkingApi } from "@/lib/api/parking";
import {
  cacheGates,
  deleteOutageEvent,
  getCachedGates,
  listOutageEvents,
  putOutageEvent,
} from "@/lib/offline/outageQueue";
import type {
  Gate,
  OutageEventRequest,
  OutageEventType,
  QueuedOutageEvent,
} from "@/types";

const FALLBACK_GATES: Gate[] = [
  {
    id: "offline-aux-entry",
    gateCode: "GATE_AUX_ENTRY",
    direction: "IN",
    status: "OPEN",
    hasBarrier: false,
    parkingLotId: null,
    floorId: null,
    lastCommand: null,
    lastCommandAt: null,
  },
  {
    id: "offline-aux-exit",
    gateCode: "GATE_AUX_EXIT",
    direction: "OUT",
    status: "OPEN",
    hasBarrier: false,
    parkingLotId: null,
    floorId: null,
    lastCommand: null,
    lastCommandAt: null,
  },
];

function localDateTimeNow() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 16);
}

function apiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return (
      error.response?.data?.error?.message ??
      error.response?.data?.message ??
      error.message
    );
  }
  return error instanceof Error ? error.message : "Không xác định được lỗi";
}

export default function OutagePage() {
  const [online, setOnline] = useState(true);
  const [gates, setGates] = useState<Gate[]>(FALLBACK_GATES);
  const [queue, setQueue] = useState<QueuedOutageEvent[]>([]);
  const [type, setType] = useState<OutageEventType>("ENTRY");
  const [plate, setPlate] = useState("");
  const [occurredAt, setOccurredAt] = useState(localDateTimeNow);
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const syncingRef = useRef(false);

  const refreshQueue = useCallback(async () => {
    setQueue(await listOutageEvents());
  }, []);

  const loadGates = useCallback(async () => {
    try {
      const response = await parkingApi.listGates();
      const auxiliary = response.data.filter((gate) => !gate.hasBarrier);
      const available = auxiliary.length > 0 ? auxiliary : response.data;
      setGates(available);
      await cacheGates(available);
    } catch {
      const cached = await getCachedGates();
      setGates(cached.length > 0 ? cached : FALLBACK_GATES);
    }
  }, []);

  const syncQueue = useCallback(async () => {
    if (!navigator.onLine || syncingRef.current) return;
    syncingRef.current = true;
    setSyncing(true);
    const pending = await listOutageEvents();
    let synced = 0;
    for (const event of pending) {
      try {
        const body: OutageEventRequest = {
          clientEventId: event.clientEventId,
          type: event.type,
          plateNumber: event.plateNumber,
          gateId: event.gateId,
          occurredAt: event.occurredAt,
          note: event.note,
        };
        await parkingApi.recordOutageEvent(body);
        await deleteOutageEvent(event.clientEventId);
        synced += 1;
      } catch (error) {
        if (axios.isAxiosError(error) && !error.response) {
          break;
        }
        await putOutageEvent({
          ...event,
          status: "FAILED",
          lastError: apiError(error),
        });
      }
    }
    await refreshQueue();
    syncingRef.current = false;
    setSyncing(false);
    if (synced > 0) toast.success(`Đã đồng bộ ${synced} sự kiện lên cloud`);
  }, [refreshQueue]);

  useEffect(() => {
    setOnline(navigator.onLine);
    void refreshQueue();
    void loadGates();
    if (navigator.onLine) void syncQueue();
    const handleOnline = () => {
      setOnline(true);
      void syncQueue();
      void loadGates();
    };
    const handleOffline = () => setOnline(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, [loadGates, refreshQueue, syncQueue]);

  const matchingGates = useMemo(
    () => gates.filter((gate) => gate.direction === (type === "ENTRY" ? "IN" : "OUT")),
    [gates, type]
  );
  const [selectedGate, setSelectedGate] = useState("");

  useEffect(() => {
    if (!matchingGates.some((gate) => gate.gateCode === selectedGate)) {
      setSelectedGate(matchingGates[0]?.gateCode ?? "");
    }
  }, [matchingGates, selectedGate]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!plate.trim() || !selectedGate) return;
    const body: OutageEventRequest = {
      clientEventId: crypto.randomUUID(),
      type,
      plateNumber: plate.trim().toUpperCase(),
      gateId: selectedGate,
      occurredAt: new Date(occurredAt).toISOString(),
      note: note.trim() || "Ghi nhận tại cổng phụ trong chế độ sự cố",
    };
    setSubmitting(true);
    try {
      if (navigator.onLine) {
        await parkingApi.recordOutageEvent(body);
        toast.success(type === "ENTRY" ? "Đã ghi nhận xe vào" : "Đã ghi nhận xe ra và chốt thời gian");
      } else {
        throw new Error("OFFLINE");
      }
      setPlate("");
      setNote("");
      setOccurredAt(localDateTimeNow());
    } catch (error) {
      const networkError =
        !navigator.onLine ||
        (error instanceof Error && error.message === "OFFLINE") ||
        (axios.isAxiosError(error) && !error.response);
      if (!networkError) {
        toast.error(apiError(error));
        setSubmitting(false);
        return;
      }
      await putOutageEvent({
        ...body,
        status: "PENDING",
        queuedAt: new Date().toISOString(),
      });
      await refreshQueue();
      toast.warning("Mất mạng: dữ liệu đã được lưu an toàn trên thiết bị");
      setPlate("");
      setNote("");
      setOccurredAt(localDateTimeNow());
    } finally {
      setSubmitting(false);
    }
  }

  async function removeQueued(id: string) {
    await deleteOutageEvent(id);
    await refreshQueue();
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Chế độ sự cố · Cổng phụ</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Dùng khi barrier mất điện. Dữ liệu offline sẽ tự đồng bộ khi mạng trở lại.
          </p>
        </div>
        <div
          className={`flex items-center gap-2 rounded-full px-3 py-1.5 text-sm font-semibold ${
            online
              ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300"
              : "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300"
          }`}
        >
          {online ? <Cloud className="h-4 w-4" /> : <CloudOff className="h-4 w-4" />}
          {online ? "Đang kết nối cloud" : "Đang lưu offline"}
        </div>
      </div>

      <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
        <div className="flex gap-3">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
          <p>
            Xe đi qua cổng phụ không có barrier. Nhân viên phải kiểm tra đúng biển số và thời điểm
            thực tế; backend sẽ dùng thời điểm này để tính thời gian gửi và hóa đơn.
          </p>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(360px,0.8fr)]">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <WifiOff className="h-5 w-5" />
              Ghi nhận xe tại cổng phụ
            </CardTitle>
          </CardHeader>
          <CardContent>
            <form className="space-y-5" onSubmit={submit}>
              <div className="grid grid-cols-2 gap-2">
                <Button
                  type="button"
                  variant={type === "ENTRY" ? "default" : "outline"}
                  onClick={() => setType("ENTRY")}
                >
                  Xe vào
                </Button>
                <Button
                  type="button"
                  variant={type === "EXIT" ? "default" : "outline"}
                  onClick={() => setType("EXIT")}
                >
                  Xe ra
                </Button>
              </div>

              <div className="space-y-2">
                <Label htmlFor="plate">Biển số xe</Label>
                <Input
                  id="plate"
                  autoComplete="off"
                  value={plate}
                  onChange={(event) => setPlate(event.target.value)}
                  placeholder="51F-123.45"
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="gate">Cổng phụ</Label>
                <select
                  id="gate"
                  value={selectedGate}
                  onChange={(event) => setSelectedGate(event.target.value)}
                  className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  required
                >
                  {matchingGates.map((gate) => (
                    <option key={gate.gateCode} value={gate.gateCode}>
                      {gate.gateCode}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="occurredAt">Thời điểm thực tế</Label>
                <Input
                  id="occurredAt"
                  type="datetime-local"
                  value={occurredAt}
                  max={localDateTimeNow()}
                  onChange={(event) => setOccurredAt(event.target.value)}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="note">Ghi chú</Label>
                <Input
                  id="note"
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder="Ca trực, lý do sử dụng cổng phụ..."
                />
              </div>

              <Button className="w-full" type="submit" disabled={submitting}>
                {submitting ? "Đang lưu..." : online ? "Ghi nhận lên cloud" : "Lưu trên thiết bị"}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle>Chờ đồng bộ ({queue.length})</CardTitle>
              <p className="mt-1 text-xs text-muted-foreground">
                Đồng bộ theo đúng thứ tự thời gian để xe vào được xử lý trước xe ra.
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => void syncQueue()}
              disabled={!online || syncing || queue.length === 0}
            >
              <RefreshCw className={`mr-2 h-4 w-4 ${syncing ? "animate-spin" : ""}`} />
              Đồng bộ
            </Button>
          </CardHeader>
          <CardContent>
            {queue.length === 0 ? (
              <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
                Không có dữ liệu đang chờ.
              </div>
            ) : (
              <div className="space-y-3">
                {queue.map((event) => (
                  <div key={event.clientEventId} className="rounded-lg border p-3">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="font-semibold">
                          {event.type === "ENTRY" ? "XE VÀO" : "XE RA"} · {event.plateNumber}
                        </div>
                        <div className="mt-1 text-xs text-muted-foreground">
                          {new Date(event.occurredAt).toLocaleString("vi-VN")} · {event.gateId}
                        </div>
                        {event.status === "FAILED" && (
                          <p className="mt-2 text-xs text-destructive">{event.lastError}</p>
                        )}
                      </div>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label="Xóa sự kiện chờ"
                        onClick={() => void removeQueued(event.clientEventId)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
