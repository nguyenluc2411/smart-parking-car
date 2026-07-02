"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { useRates, useUpdateRates } from "@/lib/hooks/useBilling";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { ErrorState } from "@/components/ui/error-state";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

const schema = z.object({
  ratePerMin: z.number().positive("Phải lớn hơn 0"),
  peakMultiplier: z.number().min(1, "Tối thiểu 1.0"),
  overnightFlat: z.number().min(0),
  minCharge: z.number().min(0),
});

type RateForm = z.infer<typeof schema>;

export default function RatesPage() {
  const query = useRates();
  const update = useUpdateRates();
  const { toast } = useToast();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<RateForm>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (query.data) {
      reset({
        ratePerMin: query.data.ratePerMin,
        peakMultiplier: query.data.peakMultiplier,
        overnightFlat: query.data.overnightFlat,
        minCharge: query.data.minCharge,
      });
    }
  }, [query.data, reset]);

  const onSubmit = (data: RateForm) => {
    update.mutate(data, {
      onSuccess: () =>
        toast("Đã cập nhật bảng giá", { variant: "success" }),
      onError: () => toast("Cập nhật thất bại", { variant: "destructive" }),
    });
  };

  return (
    <RoleGuard allow={["ADMIN"]}>
      <PageHeader
        title="Bảng giá"
        description="Cấu hình đơn giá, hệ số giờ cao điểm và phí qua đêm"
      />

      {query.isError ? (
        <ErrorState onRetry={() => query.refetch()} />
      ) : query.isLoading || !query.data ? (
        <Spinner />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Cấu hình giá</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="ratePerMin">Đơn giá (VND/phút)</Label>
                  <Input
                    id="ratePerMin"
                    type="number"
                    step="0.01"
                    {...register("ratePerMin", { valueAsNumber: true })}
                  />
                  {errors.ratePerMin && (
                    <p className="text-xs text-destructive">
                      {errors.ratePerMin.message}
                    </p>
                  )}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="peakMultiplier">Hệ số cao điểm</Label>
                  <Input
                    id="peakMultiplier"
                    type="number"
                    step="0.1"
                    {...register("peakMultiplier", { valueAsNumber: true })}
                  />
                  {errors.peakMultiplier && (
                    <p className="text-xs text-destructive">
                      {errors.peakMultiplier.message}
                    </p>
                  )}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="overnightFlat">Phí qua đêm (VND)</Label>
                  <Input
                    id="overnightFlat"
                    type="number"
                    {...register("overnightFlat", { valueAsNumber: true })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="minCharge">Phí tối thiểu (VND)</Label>
                  <Input
                    id="minCharge"
                    type="number"
                    {...register("minCharge", { valueAsNumber: true })}
                  />
                </div>
                <Button type="submit" disabled={update.isPending}>
                  {update.isPending && (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  )}
                  Lưu thay đổi
                </Button>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Khung giờ cao điểm</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Từ giờ</TableHead>
                    <TableHead>Đến giờ</TableHead>
                    <TableHead>Loại ngày</TableHead>
                    <TableHead>Cao điểm</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {query.data.schedules.map((s, i) => (
                    <TableRow key={i}>
                      <TableCell>{s.hourStart}:00</TableCell>
                      <TableCell>{s.hourEnd}:00</TableCell>
                      <TableCell>{s.dayType}</TableCell>
                      <TableCell>
                        {s.isPeak ? (
                          <Badge variant="warning">Cao điểm</Badge>
                        ) : (
                          <Badge variant="secondary">Thường</Badge>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </div>
      )}
    </RoleGuard>
  );
}
