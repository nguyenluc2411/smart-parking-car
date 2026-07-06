"use client";

import { LogOut, ChevronDown } from "lucide-react";
import { useAuthStore } from "@/lib/stores/authStore";
import { useLogout } from "@/lib/hooks/useAuth";
import { MobileNav } from "./MobileNav";
import { NotificationBell } from "./NotificationBell";
import { ThemeToggle } from "./ThemeToggle";
import { Breadcrumbs } from "./Breadcrumbs";
import { Badge } from "@/components/ui/badge";
import {
  Avatar,
  AvatarFallback,
} from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { CommandMenu } from "./CommandMenu";

export function Header() {
  const username = useAuthStore((s) => s.username);
  const role = useAuthStore((s) => s.role);
  const logout = useLogout();

  // Build avatar initials
  const initials = username
    ? username.slice(0, 2).toUpperCase()
    : "?";

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-4 border-b border-border/50 bg-background/80 px-4 sm:px-6 backdrop-blur-xl supports-[backdrop-filter]:bg-background/70">
      {/* Mobile menu */}
      <div className="flex items-center gap-3 md:hidden">
        <MobileNav />
      </div>

      {/* Breadcrumbs */}
      <div className="flex-1 flex items-center">
        <Breadcrumbs />
      </div>

      {/* Right actions */}
      <div className="flex items-center gap-1.5">
        <CommandMenu />
        <ThemeToggle />
        <NotificationBell />

        {/* Divider */}
        <div className="mx-1 h-6 w-px bg-border/70" />

        {/* User menu */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              className="flex items-center gap-2.5 rounded-lg border border-border/50 bg-muted/30 px-2.5 py-1.5 text-sm font-medium transition-all hover:bg-muted/60 hover:border-border focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="Menu tài khoản"
            >
              <Avatar className="h-7 w-7">
                <AvatarFallback className="text-[11px] font-bold bg-brand-gradient text-white">
                  {initials}
                </AvatarFallback>
              </Avatar>
              <div className="hidden sm:flex flex-col items-start leading-tight">
                <span className="text-[13px] font-semibold text-foreground">{username ?? "Tài khoản"}</span>
                {role && (
                  <span className="text-[10px] text-muted-foreground font-medium">{role}</span>
                )}
              </div>
              <ChevronDown className="h-3.5 w-3.5 text-muted-foreground hidden sm:block" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="font-normal">
              <div className="flex items-center gap-3 py-1">
                <Avatar className="h-9 w-9">
                  <AvatarFallback className="text-sm font-bold bg-brand-gradient text-white">
                    {initials}
                  </AvatarFallback>
                </Avatar>
                <div className="flex flex-col space-y-0.5">
                  <p className="text-sm font-semibold leading-none">{username ?? "Tài khoản"}</p>
                  {role && (
                    <Badge variant="secondary" className="w-fit text-[10px] px-1.5 py-0 h-4">
                      {role}
                    </Badge>
                  )}
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => logout.mutate()}
              className="text-destructive cursor-pointer focus:text-destructive focus:bg-destructive/10"
            >
              <LogOut className="mr-2 h-4 w-4" />
              Đăng xuất
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
