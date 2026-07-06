"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useSessions } from "@/lib/hooks/useSessions";
import { useDebounce } from "@/lib/hooks/useDebounce";
import type { SessionStatus } from "@/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { SessionTable } from "@/components/sessions/SessionTable";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const STATUS_OPTIONS: { value: SessionStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "Tất cả trạng thái" },
  { value: "ACTIVE", label: "Đang đỗ" },
  { value: "CLOSED", label: "Đã ra" },
  { value: "PENDING", label: "Chờ xử lý" },
  { value: "CANCELLED", label: "Đã hủy" },
  { value: "REQUIRES_ATTENTION", label: "Cần đối soát" },
];

function SessionsContent() {
  const params = useSearchParams();
  // Deep-link from an alert: /sessions?plate=...&status=...
  const plateParam = params.get("plate") ?? "";
  const statusParam = (params.get("status") as SessionStatus | null) ?? "ALL";

  const [status, setStatus] = useState<SessionStatus | "ALL">(statusParam);
  const [date, setDate] = useState("");
  const [plate, setPlate] = useState(plateParam);
  const [page, setPage] = useState(0);

  const debouncedPlate = useDebounce(plate, 400);

  // Re-apply when navigating to a new alert while already on this page.
  useEffect(() => {
    setPlate(plateParam);
    setStatus(statusParam);
  }, [plateParam, statusParam]);

  // Đổi bộ lọc → quay về trang đầu.
  useEffect(() => {
    setPage(0);
  }, [status, date, debouncedPlate]);

  const query = useSessions({
    status: status === "ALL" ? undefined : status,
    date: date || undefined,
    plate: debouncedPlate || undefined,
    page,
    size: 20,
  });

  return (
    <div>
      <PageHeader
        title="Phiên gửi xe"
        description="Danh sách các phiên vào/ra bãi"
      />

      <Card className="border border-border/50 shadow-sm">
        <CardContent className="space-y-4 pt-6">
          <div className="flex flex-col gap-3 sm:flex-row">
            <Select
              value={status}
              onValueChange={(v) => setStatus(v as SessionStatus | "ALL")}
            >
              <SelectTrigger className="sm:w-52">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {STATUS_OPTIONS.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="sm:w-44"
            />
            <Input
              placeholder="Tìm biển số…"
              value={plate}
              onChange={(e) => setPlate(e.target.value)}
              className="sm:w-52"
            />
          </div>

          {query.isLoading ? (
            <Spinner />
          ) : query.isError ? (
            <ErrorState onRetry={() => query.refetch()} />
          ) : query.data ? (
            <>
              <SessionTable rows={query.data.content} />
              <Pagination
                page={page}
                totalPages={query.data.totalPages}
                totalElements={query.data.totalElements}
                onChange={setPage}
              />
            </>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

export default function SessionsPage() {
  // useSearchParams requires a Suspense boundary in the App Router.
  return (
    <Suspense fallback={<Spinner />}>
      <SessionsContent />
    </Suspense>
  );
}
