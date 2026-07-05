"use client";

import { useState, useEffect, useMemo } from "react";
import { Search, Wrench, RotateCcw, Trash2 } from "lucide-react";
import { useDeleteSlot, useUpdateSlotStatus } from "@/lib/hooks/useSlots";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Pagination } from "@/components/ui/pagination";
import { SlotStatusBadge } from "@/components/StatusBadge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { getApiErrorMessage } from "@/lib/utils";
import type { Slot } from "@/types";

export function SlotList({ slots }: { slots: Slot[] }) {
  const del = useDeleteSlot();
  const update = useUpdateSlotStatus();
  const { toast } = useToast();

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [zoneFilter, setZoneFilter] = useState<string>("ALL");
  const [page, setPage] = useState(0);
  const itemsPerPage = 15;

  // Get list of unique zones to filter
  const zones = Array.from(new Set(slots.map((s) => s.zone))).sort();

  const onToggleMaintenance = (s: Slot) => {
    const next = s.status === "MAINTENANCE" ? "EMPTY" : "MAINTENANCE";
    update.mutate(
      { id: s.id, body: { status: next } },
      {
        onSuccess: () =>
          toast(next === "MAINTENANCE" ? "Đã đặt bảo trì" : "Đã mở lại slot", {
            variant: "success",
          }),
        onError: (e) => toast(getApiErrorMessage(e), { variant: "destructive" }),
      }
    );
  };

  const onDelete = (s: Slot) => {
    del.mutate(s.id, {
      onSuccess: () => toast("Đã xóa slot", { variant: "success" }),
      onError: (e) => toast(getApiErrorMessage(e), { variant: "destructive" }),
    });
  };

  // Reset page when filters change
  useEffect(() => {
    setPage(0);
  }, [search, statusFilter, zoneFilter]);

  // Filter + paginate với useMemo tránh tính toán lại mỗi render
  const filteredSlots = useMemo(() =>
    slots.filter((s) => {
      const matchesSearch = s.slotCode.toLowerCase().includes(search.toLowerCase().trim());
      const matchesStatus = statusFilter === "ALL" || s.status === statusFilter;
      const matchesZone = zoneFilter === "ALL" || s.zone === zoneFilter;
      return matchesSearch && matchesStatus && matchesZone;
    }),
    [slots, search, statusFilter, zoneFilter]
  );

  const totalElements = filteredSlots.length;
  const totalPages = Math.ceil(totalElements / itemsPerPage);
  const paginatedSlots = useMemo(() =>
    filteredSlots.slice(page * itemsPerPage, (page + 1) * itemsPerPage),
    [filteredSlots, page, itemsPerPage]
  );

  return (
    <div className="space-y-4">
      <Card className="border border-border/50 shadow-sm">
        <CardContent className="pt-6 space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center justify-between">
            <div className="flex flex-1 flex-col gap-3 sm:flex-row">
              <div className="relative w-full sm:w-64">
                <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Tìm mã slot..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="pl-9 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
                />
              </div>

              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="w-full sm:w-44 shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
                  <SelectValue placeholder="Tất cả trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">Tất cả trạng thái</SelectItem>
                  <SelectItem value="EMPTY">Trống</SelectItem>
                  <SelectItem value="OCCUPIED">Đang có xe</SelectItem>
                  <SelectItem value="MAINTENANCE">Bảo trì</SelectItem>
                </SelectContent>
              </Select>

              <Select value={zoneFilter} onValueChange={setZoneFilter}>
                <SelectTrigger className="w-full sm:w-40 shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
                  <SelectValue placeholder="Tất cả khu" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">Tất cả khu</SelectItem>
                  {zones.map((z) => (
                    <SelectItem key={z} value={z}>
                      Khu {z}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="text-xs text-muted-foreground shrink-0">
              Hiển thị {paginatedSlots.length} / {totalElements} slot
            </div>
          </div>

          {filteredSlots.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              Không tìm thấy slot nào phù hợp với bộ lọc
            </p>
          ) : (
            <>
              <div className="rounded-md border border-border/50 shadow-sm overflow-hidden">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Mã slot</TableHead>
                      <TableHead>Khu</TableHead>
                      <TableHead>Trạng thái</TableHead>
                      <TableHead className="text-right">Thao tác</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedSlots.map((s) => {
                      const occupied = s.status === "OCCUPIED";
                      return (
                        <TableRow key={s.id}>
                           <TableCell className="font-medium">{s.slotCode}</TableCell>
                          <TableCell>{s.zone}</TableCell>
                          <TableCell>
                            <SlotStatusBadge status={s.status} />
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              variant="ghost"
                              size="icon"
                              title={
                                s.status === "MAINTENANCE" ? "Mở lại" : "Đặt bảo trì"
                              }
                              aria-label={
                                s.status === "MAINTENANCE" ? "Mở lại" : "Đặt bảo trì"
                              }
                              onClick={() => onToggleMaintenance(s)}
                              disabled={occupied || update.isPending}
                            >
                              {s.status === "MAINTENANCE" ? (
                                <RotateCcw className="h-4 w-4" />
                              ) : (
                                <Wrench className="h-4 w-4" />
                              )}
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="text-destructive"
                              title={occupied ? "Đang có xe, không thể xóa" : "Xóa"}
                              aria-label={occupied ? "Đang có xe, không thể xóa" : "Xóa"}
                              onClick={() => onDelete(s)}
                              disabled={occupied || del.isPending}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              <Pagination
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                onChange={setPage}
              />
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
