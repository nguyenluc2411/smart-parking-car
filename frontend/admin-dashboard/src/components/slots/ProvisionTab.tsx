"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { useProvisionZone } from "@/lib/hooks/useSlots";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
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

export function ProvisionTab({ slots }: { slots: Slot[] }) {
  const [zone, setZone] = useState("A");
  const [count, setCount] = useState("10");
  const provision = useProvisionZone();
  const { toast } = useToast();

  const zones = Array.from(
    slots.reduce((map, s) => {
      const z = map.get(s.zone) ?? { total: 0, occupied: 0 };
      z.total += 1;
      if (s.status === "OCCUPIED") z.occupied += 1;
      return map.set(s.zone, z);
    }, new Map<string, { total: number; occupied: number }>())
  ).sort((a, b) => a[0].localeCompare(b[0]));

  const onApply = () => {
    const n = Number(count);
    if (!zone.trim()) return toast("Nhập tên khu", { variant: "destructive" });
    if (!Number.isInteger(n) || n < 0)
      return toast("Số lượng không hợp lệ", { variant: "destructive" });
    provision.mutate(
      { zone: zone.trim().toUpperCase(), count: n },
      {
        onSuccess: (res) => {
          const d = res.data;
          toast(
            `Khu ${d.zone}: +${d.created} / -${d.removed} (tổng bãi ${d.total})`,
            { variant: "success" }
          );
        },
        onError: (e) => toast(getApiErrorMessage(e), { variant: "destructive" }),
      }
    );
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="space-y-4 pt-6">
          <p className="text-sm text-muted-foreground">
            Đặt một khu có đúng số slot mong muốn. Hệ thống tự sinh mã{" "}
            <code>{zone || "A"}01…</code>, tạo phần thiếu và xóa phần dư.
            Không xóa slot đang có xe.
          </p>
          <div className="flex flex-wrap items-end gap-3">
            <div className="space-y-2">
              <Label htmlFor="zone">Khu</Label>
              <Input
                id="zone"
                className="w-24"
                value={zone}
                maxLength={5}
                onChange={(e) => setZone(e.target.value)}
                placeholder="A"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="count">Số slot</Label>
              <Input
                id="count"
                className="w-28"
                type="number"
                min={0}
                value={count}
                onChange={(e) => setCount(e.target.value)}
              />
            </div>
            <Button onClick={onApply} disabled={provision.isPending}>
              {provision.isPending && (
                <Loader2 className="h-4 w-4 animate-spin" />
              )}
              Áp dụng
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <h3 className="mb-3 text-sm font-medium">Các khu hiện có</h3>
          {zones.length === 0 ? (
            <p className="text-sm text-muted-foreground">Chưa có slot nào.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Khu</TableHead>
                  <TableHead>Tổng slot</TableHead>
                  <TableHead>Đang có xe</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {zones.map(([z, info]) => (
                  <TableRow key={z}>
                    <TableCell className="font-medium">{z}</TableCell>
                    <TableCell>{info.total}</TableCell>
                    <TableCell>{info.occupied}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
