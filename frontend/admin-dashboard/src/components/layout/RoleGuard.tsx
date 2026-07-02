"use client";

import type { Role } from "@/types";
import { useAuthStore } from "@/lib/stores/authStore";
import { Card, CardContent } from "@/components/ui/card";
import { ShieldAlert } from "lucide-react";

/** Chỉ render children nếu role hiện tại nằm trong `allow`. */
export function RoleGuard({
  allow,
  children,
}: {
  allow: Role[];
  children: React.ReactNode;
}) {
  const role = useAuthStore((s) => s.role);

  if (!role || !allow.includes(role)) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
          <ShieldAlert className="h-10 w-10" />
          <p className="font-medium">Không đủ quyền truy cập</p>
          <p className="text-sm">
            Trang này chỉ dành cho: {allow.join(", ")}
          </p>
        </CardContent>
      </Card>
    );
  }

  return <>{children}</>;
}
