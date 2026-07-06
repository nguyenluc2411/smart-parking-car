"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import type { Invoice, InvoiceStatus } from "@/types";
import { useInvoice, useInvoices } from "@/lib/hooks/useBilling";
import { useDebounce } from "@/lib/hooks/useDebounce";
import { PageHeader } from "@/components/layout/PageHeader";
import { InvoiceCard } from "@/components/billing/InvoiceCard";
import { InvoiceTable } from "@/components/billing/InvoiceTable";
import { PaymentDialog } from "@/components/billing/PaymentDialog";
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

const STATUS_OPTIONS: { value: InvoiceStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "Tất cả trạng thái" },
  { value: "PENDING", label: "Chờ thanh toán" },
  { value: "PAID", label: "Đã thanh toán" },
  { value: "WAIVED", label: "Miễn phí" },
];

function BillingContent() {
  const params = useSearchParams();
  const deepLink = params.get("sessionId") ?? "";

  const [status, setStatus] = useState<InvoiceStatus | "ALL">("ALL");
  const [date, setDate] = useState("");
  const [plate, setPlate] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Invoice | null>(null);

  const debouncedPlate = useDebounce(plate, 400);

  // Đổi bộ lọc → quay về trang đầu.
  useEffect(() => {
    setPage(0);
  }, [status, date, debouncedPlate]);

  const query = useInvoices({
    status: status === "ALL" ? undefined : status,
    date: date || undefined,
    plate: debouncedPlate || undefined,
    page,
    size: 20,
  });

  // Deep-link cũ (?sessionId=) từ trang Phiên → tự chọn hóa đơn đó.
  const deepInvoice = useInvoice(deepLink);
  useEffect(() => {
    if (deepLink && deepInvoice.data) setSelected(deepInvoice.data);
  }, [deepLink, deepInvoice.data]);

  // Sau khi thanh toán, list refetch → đồng bộ trạng thái panel bên phải.
  useEffect(() => {
    if (!selected || !query.data) return;
    const fresh = query.data.content.find((i) => i.invoiceId === selected.invoiceId);
    if (fresh && fresh.status !== selected.status) setSelected(fresh);
  }, [query.data, selected]);

  return (
    <div>
      <PageHeader
        title="Hóa đơn"
        description="Danh sách hóa đơn — lọc theo trạng thái, biển số, ngày; bấm một dòng để xem & thu tiền"
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2 border border-border/50 shadow-sm">
          <CardContent className="space-y-4 pt-6">
            <div className="flex flex-col gap-3 sm:flex-row">
              <Select
                value={status}
                onValueChange={(v) => setStatus(v as InvoiceStatus | "ALL")}
              >
                <SelectTrigger className="sm:w-52 shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
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
                className="sm:w-44 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
              />
              <Input
                placeholder="Tìm biển số…"
                value={plate}
                onChange={(e) => setPlate(e.target.value)}
                className="sm:w-52 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
              />
            </div>

            {query.isLoading ? (
              <Spinner />
            ) : query.isError ? (
              <ErrorState onRetry={() => query.refetch()} />
            ) : query.data ? (
              <>
                <InvoiceTable
                  rows={query.data.content}
                  selectedId={selected?.sessionId}
                  onSelect={setSelected}
                />
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

        <div className="lg:col-span-1">
          {selected ? (
            <div className="space-y-4">
              <InvoiceCard invoice={selected} />
              <Card className="border border-border/50 shadow-sm">
                <CardContent className="pt-6">
                  <PaymentDialog invoice={selected} />
                </CardContent>
              </Card>
            </div>
          ) : (
            <Card className="border border-border/50 shadow-sm">
              <CardContent className="py-12 text-center text-sm text-muted-foreground">
                Chọn một hóa đơn ở danh sách để xem chi tiết và thu tiền.
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

export default function BillingPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <BillingContent />
    </Suspense>
  );
}
