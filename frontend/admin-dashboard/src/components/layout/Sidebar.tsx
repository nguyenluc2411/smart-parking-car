"use client";

import { BrandLogo } from "@/components/BrandLogo";
import { NavLinks } from "./NavLinks";

export function Sidebar() {
  return (
    <aside className="hidden w-60 shrink-0 flex-col md:flex bg-sidebar border-r border-sidebar-border">
      {/* Brand header */}
      <div className="flex h-16 items-center gap-3 px-5 border-b border-sidebar-border">
        <BrandLogo size={32} className="rounded-xl" />
        <div className="leading-tight">
          <span className="block text-[14px] font-bold tracking-tight text-foreground">
            Smart Parking
          </span>
          <span className="block text-[10px] font-medium text-muted-foreground uppercase tracking-widest">
            Admin Panel
          </span>
        </div>
      </div>

      {/* Nav */}
      <div className="flex-1 overflow-y-auto scrollbar-none py-4">
        <NavLinks />
      </div>

      {/* Footer */}
      <div className="px-4 pb-5 pt-3 border-t border-sidebar-border">
        <div className="flex items-center gap-2 rounded-lg bg-muted/60 px-3 py-2">
          <div className="h-2 w-2 rounded-full bg-green-500 shadow-[0_0_6px_2px_rgba(34,197,94,0.4)]" />
          <p className="text-[11px] font-medium text-muted-foreground">
            System v2.0 · Online
          </p>
        </div>
      </div>
    </aside>
  );
}
