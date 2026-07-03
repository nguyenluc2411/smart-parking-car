"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Car,
  Receipt,
  ListChecks,
  DoorOpen,
  BarChart3,
  Users,
  Tags,
  LayoutGrid,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/lib/stores/authStore";
import type { Role } from "@/types";

interface NavItem {
  href: string;
  label: string;
  icon: React.ElementType;
  roles: Role[];
}

export const NAV: NavItem[] = [
  { href: "/dashboard", label: "Tổng quan", icon: LayoutDashboard, roles: ["OPERATOR", "ADMIN"] },
  { href: "/sessions", label: "Phiên gửi xe", icon: Car, roles: ["OPERATOR", "ADMIN"] },
  { href: "/billing", label: "Hóa đơn", icon: Receipt, roles: ["OPERATOR", "ADMIN"] },
  { href: "/gates", label: "Cổng / Barie", icon: DoorOpen, roles: ["ADMIN"] },
  { href: "/slots", label: "Bãi xe / Slot", icon: LayoutGrid, roles: ["ADMIN"] },
  { href: "/vehicles", label: "Whitelist / Blacklist", icon: ListChecks, roles: ["ADMIN"] },
  { href: "/billing/rates", label: "Bảng giá", icon: Tags, roles: ["ADMIN"] },
  { href: "/reports", label: "Báo cáo", icon: BarChart3, roles: ["ADMIN"] },
  { href: "/users", label: "Người dùng", icon: Users, roles: ["ADMIN"] },
];

/** Danh sách link điều hướng, lọc theo role. Dùng chung cho Sidebar (desktop) và drawer (mobile). */
export function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const role = useAuthStore((s) => s.role);

  const items = NAV.filter((i) => (role ? i.roles.includes(role) : false));

  return (
    <nav className="flex flex-col gap-1 p-3">
      {items.map((item) => {
        const active =
          pathname === item.href ||
          (item.href !== "/dashboard" && pathname.startsWith(item.href));
        const Icon = item.icon;
        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onNavigate}
            className={cn(
              "group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all",
              active
                ? "bg-accent text-accent-foreground"
                : "text-muted-foreground hover:bg-accent/60 hover:text-foreground"
            )}
          >
            <span
              className={cn(
                "absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-primary transition-all",
                active ? "opacity-100" : "opacity-0"
              )}
            />
            <Icon
              className={cn(
                "h-4 w-4 transition-colors",
                active
                  ? "text-primary"
                  : "text-muted-foreground group-hover:text-foreground"
              )}
            />
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
