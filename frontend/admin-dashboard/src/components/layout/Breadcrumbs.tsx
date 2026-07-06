"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronRight, Home } from "lucide-react";
import { NAV } from "./NavLinks";
import { cn } from "@/lib/utils";

export function Breadcrumbs() {
  const pathname = usePathname();

  if (pathname === "/dashboard") {
    return (
      <div className="hidden md:flex items-center gap-2 text-sm">
        <div className="flex h-6 w-6 items-center justify-center rounded-md bg-primary/10">
          <Home className="h-3.5 w-3.5 text-primary" />
        </div>
        <span className="font-semibold text-foreground">Tổng quan</span>
      </div>
    );
  }

  const currentNav = NAV.find(item =>
    pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href))
  );

  return (
    <div className="hidden md:flex items-center gap-1.5 text-sm">
      <Link
        href="/dashboard"
        className="flex h-6 w-6 items-center justify-center rounded-md text-muted-foreground transition-all hover:bg-muted hover:text-foreground"
      >
        <Home className="h-3.5 w-3.5" />
      </Link>
      <ChevronRight className="h-3.5 w-3.5 text-muted-foreground/50 flex-shrink-0" />

      {currentNav ? (
        <div className="flex items-center gap-1.5">
          <span className={cn(
            "flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[13px] font-semibold",
            "bg-primary/10 text-primary"
          )}>
            {currentNav.label}
          </span>
        </div>
      ) : (
        <span className="rounded-md px-2 py-0.5 text-[13px] font-semibold text-foreground capitalize">
          {pathname.split("/").pop()}
        </span>
      )}
    </div>
  );
}
