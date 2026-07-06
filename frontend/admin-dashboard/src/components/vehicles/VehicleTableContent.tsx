"use client";

import { useEffect, useMemo, useState } from "react";
import { Search, Trash2 } from "lucide-react";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { VehicleTypeBadge } from "@/components/StatusBadge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/lib/utils";
import type { UseQueryResult, UseMutationResult } from "@tanstack/react-query";
import type { Vehicle } from "@/types";
import type { Kind } from "./AddVehicleDialog";

export function VehicleTableContent({
  kind,
  query,
  del,
}: {
  kind: Kind;
  query: UseQueryResult<Vehicle[], unknown>;
  del: UseMutationResult<unknown, unknown, string, unknown>;
}) {
  const { toast } = useToast();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const itemsPerPage = 10;

  const onDelete = (plate: string) => {
    del.mutate(plate, {
      onSuccess: () => toast("Đã xóa khỏi danh sách", { variant: "success" }),
      onError: () => toast("Xóa thất bại", { variant: "destructive" }),
    });
  };

  // Reset page when search changes
  useEffect(() => {
    setPage(0);
  }, [search]);

  // useMemo phải đặt trước early return để tuân thủ Rules of Hooks
  const filteredData = useMemo(() => {
    const data = query.data ?? [];
    const term = search.toLowerCase().trim();
    if (!term) return data;
    return data.filter((v: Vehicle) =>
      v.plateNumber.toLowerCase().includes(term) ||
      (v.ownerName ?? "").toLowerCase().includes(term) ||
      (v.note ?? "").toLowerCase().includes(term)
    );
  }, [query.data, search]);

  const totalElements = filteredData.length;
  const totalPages = Math.ceil(totalElements / itemsPerPage);
  const paginatedData = useMemo(() =>
    filteredData.slice(page * itemsPerPage, (page + 1) * itemsPerPage),
    [filteredData, page, itemsPerPage]
  );

  if (query.isError) return <ErrorState onRetry={() => query.refetch()} />;
  if (query.isLoading || !query.data) return <Spinner />;

  return (
    <div className="space-y-4">
      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Tìm biển số, chủ xe..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
        />
      </div>

      {filteredData.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          {search ? "Không tìm thấy kết quả phù hợp" : "Danh sách trống"}
        </p>
      ) : (
        <>
          <div className="rounded-md border border-border/50 shadow-sm overflow-hidden">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Biển số</TableHead>
                  <TableHead>Loại</TableHead>
                  <TableHead>Chủ xe</TableHead>
                  <TableHead>{kind === "blacklist" ? "Lý do" : "Ghi chú"}</TableHead>
                  <TableHead>Ngày tạo</TableHead>
                  <TableHead className="w-12"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {paginatedData.map((v: Vehicle) => (
                  <TableRow key={v.id}>
                    <TableCell className="font-medium">
                      <span className="font-mono text-xs font-semibold bg-secondary/50 border border-border/50 px-2 py-1 rounded">
                        {v.plateNumber}
                      </span>
                    </TableCell>
                    <TableCell>
                      <VehicleTypeBadge type={v.vehicleType} />
                    </TableCell>
                    <TableCell>{v.ownerName ?? "—"}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {v.note ?? "—"}
                    </TableCell>
                    <TableCell className="tabular-nums text-[13px] text-muted-foreground">{formatDateTime(v.createdAt)}</TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive"
                        title="Xóa"
                        aria-label="Xóa phương tiện"
                        onClick={() => onDelete(v.plateNumber)}
                        disabled={del.isPending}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
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
    </div>
  );
}
