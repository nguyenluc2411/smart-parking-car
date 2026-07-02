"use client";

import { useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Menu, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { BrandLogo } from "@/components/BrandLogo";
import { NavLinks } from "./NavLinks";

/** Hamburger + drawer điều hướng, chỉ hiện trên màn hình nhỏ (md:hidden). */
export function MobileNav() {
  const [open, setOpen] = useState(false);

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="md:hidden"
          aria-label="Mở menu điều hướng"
        >
          <Menu className="h-5 w-5" />
        </Button>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/80 md:hidden" />
        <Dialog.Content className="fixed inset-y-0 left-0 z-50 w-64 border-r bg-background shadow-lg focus:outline-none md:hidden">
          <Dialog.Title className="sr-only">Điều hướng</Dialog.Title>
          <div className="flex h-16 items-center justify-between border-b px-4">
            <div className="flex items-center gap-2">
              <BrandLogo size={28} />
              <span className="font-semibold">Smart Parking</span>
            </div>
            <Dialog.Close asChild>
              <Button variant="ghost" size="icon" aria-label="Đóng menu">
                <X className="h-5 w-5" />
              </Button>
            </Dialog.Close>
          </div>
          <NavLinks onNavigate={() => setOpen(false)} />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
