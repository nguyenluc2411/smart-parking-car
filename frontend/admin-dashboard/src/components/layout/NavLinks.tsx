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
  WifiOff,
  Map,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/lib/stores/authStore";
import type { Role } from "@/types";

interface NavItem {
  href: string;
  label: string;
  icon: React.ElementType;
  roles: Role[];
  group?: string;
}

export const NAV: NavItem[] = [
  { href: "/parking-map", label: "Quản lý bãi xe", icon: Map, roles: ["ADMIN"], group: "Quản lý" },
  { href: "/outage", label: "Chế độ sự cố", icon: WifiOff, roles: ["OPERATOR", "ADMIN"], group: "Chính" },
  { href: "/dashboard", label: "Tổng quan", icon: LayoutDashboard, roles: ["OPERATOR", "ADMIN"], group: "Chính" },
  { href: "/sessions", label: "Phiên gửi xe", icon: Car, roles: ["OPERATOR", "ADMIN"], group: "Chính" },
  { href: "/billing", label: "Hóa đơn", icon: Receipt, roles: ["OPERATOR", "ADMIN"], group: "Chính" },
  { href: "/gates", label: "Cổng / Barie", icon: DoorOpen, roles: ["ADMIN"], group: "Quản lý" },
  { href: "/vehicles", label: "Whitelist / Blacklist", icon: ListChecks, roles: ["ADMIN"], group: "Quản lý" },
  { href: "/billing/rates", label: "Bảng giá", icon: Tags, roles: ["ADMIN"], group: "Quản lý" },
  { href: "/reports", label: "Báo cáo", icon: BarChart3, roles: ["ADMIN"], group: "Phân tích" },
  { href: "/users", label: "Người dùng", icon: Users, roles: ["ADMIN"], group: "Phân tích" },
];

export function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const role = useAuthStore((s) => s.role);

  const items = NAV.filter((i) => (role ? i.roles.includes(role) : false));

  // Group items
  const groups = items.reduce<Record<string, NavItem[]>>((acc, item) => {
    const g = item.group ?? "Menu";
    if (!acc[g]) acc[g] = [];
    acc[g].push(item);
    return acc;
  }, {});

  return (
    <nav className="flex flex-col gap-5 px-3">
      {Object.entries(groups).map(([group, groupItems]) => (
        <div key={group}>
          <p className="mb-1.5 px-3 text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground/60">
            {group}
          </p>
          <div className="flex flex-col gap-0.5">
            {groupItems.map((item) => {
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
                    "group flex items-center gap-3 rounded-lg px-3 py-2.5 text-[13px] font-medium transition-all duration-150",
                    active
                      ? "nav-active-indicator bg-primary/10 text-primary font-semibold"
                      : "text-muted-foreground hover:bg-muted/60 hover:text-foreground"
                  )}
                >
                  <span className={cn(
                    "flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-all",
                    active
                      ? "bg-brand-gradient text-white shadow-sm"
                      : "text-muted-foreground group-hover:text-foreground"
                  )}>
                    <Icon className="h-[15px] w-[15px]" />
                  </span>
                  {item.label}
                </Link>
              );
            })}
          </div>
        </div>
      ))}
    </nav>
  );
}
