"use client";

import { BrandLogo } from "@/components/BrandLogo";
import { NavLinks } from "./NavLinks";

export function Sidebar() {
  return (
    <aside className="hidden w-64 shrink-0 border-r bg-card md:flex md:flex-col">
      <div className="flex h-16 items-center gap-3 border-b px-5">
        <BrandLogo size={36} className="rounded-xl shadow-sm" />
        <div className="leading-tight">
          <span className="block text-sm font-bold tracking-tight">
            Smart Parking
          </span>
          <span className="block text-[11px] font-medium text-muted-foreground">
            ALPR Admin
          </span>
        </div>
      </div>
      <NavLinks />
      <div className="mt-auto border-t p-4">
        <p className="text-[11px] leading-relaxed text-muted-foreground">
          Hệ thống bãi xe thông minh
          <br />
          <span className="font-medium text-foreground/70">RBL-SPS-2026</span>
        </p>
      </div>
    </aside>
  );
}
