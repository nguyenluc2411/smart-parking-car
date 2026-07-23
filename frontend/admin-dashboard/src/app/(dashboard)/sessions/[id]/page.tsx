"use client";

import Link from "next/link";
import { ArrowLeft, Receipt } from "lucide-react";
import { useSession } from "@/lib/hooks/useSessions";
import { PageHeader } from "@/components/layout/PageHeader";
import { SessionStatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { ErrorState } from "@/components/ui/error-state";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { formatDateTime, formatDuration } from "@/lib/utils";

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between py-2">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-medium">{value}</span>
    </div>
  );
}

export default function SessionDetailPage({
  params,
}: {
  params: { id: string };
}) {
  const query = useSession(params.id);

  return (
    <div>
      <PageHeader
        title="Chi tiết phiên"
        action={
          <Button variant="outline" asChild>
            <Link href="/sessions">
              <ArrowLeft className="h-4 w-4" />
              Quay lại
            </Link>
          </Button>
        }
      />

      {query.isError ? (
        <ErrorState onRetry={() => query.refetch()} />
      ) : query.isLoading || !query.data ? (
        <Spinner />
      ) : (
        <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle>{query.data.plateNumber}</CardTitle>
              <SessionStatusBadge status={query.data.status} exitReleasedAt={query.data.exitReleasedAt} />
            </CardHeader>
            <CardContent>
              <Row label="Mã phiên" value={query.data.id} />
              <Separator />
              <Row
                label="Chỗ đỗ"
                value={
                  query.data.slot
                    ? `${query.data.slot.slotCode} (khu ${query.data.slot.zone})`
                    : "—"
                }
              />
              <Separator />
              <Row label="Giờ vào" value={formatDateTime(query.data.entryTime)} />
              <Separator />
              <Row label="Giờ ra" value={formatDateTime(query.data.exitTime)} />
              <Separator />
              <Row
                label="Thời lượng"
                value={formatDuration(query.data.durationSeconds)}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Cổng & Hóa đơn</CardTitle>
            </CardHeader>
            <CardContent>
              <Row
                label="Cổng vào"
                value={query.data.entryGate?.gateCode ?? "—"}
              />
              <Separator />
              <Row
                label="Cổng ra"
                value={query.data.exitGate?.gateCode ?? "—"}
              />
              <Separator />
              <div className="pt-4">
                <Button variant="outline" className="w-full" asChild>
                  <Link href={`/billing?sessionId=${query.data.id}`}>
                    <Receipt className="h-4 w-4" />
                    Xem hóa đơn
                  </Link>
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Ảnh xe vào / ra</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4 sm:grid-cols-2">
              <SnapshotBox label="Ảnh vào" url={query.data.entryImageUrl} cropUrl={query.data.entryPlateImageUrl} />
              <SnapshotBox label="Ảnh ra" url={query.data.exitImageUrl} cropUrl={query.data.exitPlateImageUrl} />
            </div>
          </CardContent>
        </Card>
        </div>
      )}
    </div>
  );
}

function SnapshotBox({
  label,
  url,
  cropUrl = null,
}: {
  label: string;
  url: string | null;
  cropUrl?: string | null;
}) {
  return (
    <div>
      <p className="mb-2 text-sm text-muted-foreground">{label}</p>
      {url ? (
        <div className="space-y-2">
          {/* Crop cận cảnh biển — đọc rõ ngay; object-contain để không cắt mất ký tự. */}
          {cropUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={cropUrl}
              alt={`${label} — cận cảnh biển`}
              className="h-20 w-full rounded-md border bg-muted object-contain"
            />
          )}
          {/* Ảnh full (đã vẽ khung + biển) cho ngữ cảnh chiếc xe. Presigned URL tự xác thực — dùng
              trực tiếp. eslint-disable next/no-img-element vì URL ngoài (MinIO) không qua next/image. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={url}
            alt={label}
            className="aspect-video w-full rounded-md border object-cover"
          />
        </div>
      ) : (
        <div className="flex aspect-video w-full items-center justify-center rounded-md border border-dashed text-sm text-muted-foreground">
          Không có ảnh
        </div>
      )}
    </div>
  );
}
