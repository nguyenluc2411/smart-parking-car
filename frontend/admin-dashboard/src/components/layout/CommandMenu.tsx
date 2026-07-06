"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Command } from "cmdk";
import { Search, LayoutDashboard, Car, Receipt, Users, DoorOpen } from "lucide-react";
import { Dialog, DialogContent } from "@/components/ui/dialog";

export function CommandMenu() {
  const [open, setOpen] = React.useState(false);
  const router = useRouter();

  React.useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((open) => !open);
      }
    };
    document.addEventListener("keydown", down);
    return () => document.removeEventListener("keydown", down);
  }, []);

  const runCommand = React.useCallback(
    (command: () => unknown) => {
      setOpen(false);
      command();
    },
    []
  );

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="hidden md:flex relative h-8 w-full justify-start rounded-md border border-border/50 bg-muted/30 hover:bg-muted/50 px-3 py-1.5 text-sm font-normal text-muted-foreground shadow-none sm:pr-12 md:w-56 lg:w-64 transition-colors items-center"
      >
        <Search className="mr-2 h-4 w-4" />
        <span>Tìm kiếm...</span>
        <kbd className="pointer-events-none absolute right-[0.3rem] top-[0.3rem] hidden h-5 select-none items-center gap-1 rounded border border-border bg-background px-1.5 font-mono text-[10px] font-medium opacity-100 sm:flex">
          <span className="text-xs">⌘</span>K
        </kbd>
      </button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="p-0 overflow-hidden shadow-2xl border-border bg-background max-w-xl">
          <Command className="flex h-full w-full flex-col overflow-hidden bg-transparent">
            <div className="flex items-center border-b border-border/50 px-3">
              <Search className="mr-2 h-4 w-4 shrink-0 text-muted-foreground" />
              <Command.Input 
                placeholder="Nhập lệnh hoặc tìm kiếm..." 
                className="flex h-12 w-full rounded-md bg-transparent py-3 text-sm outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
              />
            </div>
            
            <Command.List className="max-h-[300px] overflow-y-auto overflow-x-hidden p-2">
              <Command.Empty className="py-6 text-center text-sm text-muted-foreground">
                Không tìm thấy kết quả.
              </Command.Empty>
              
              <Command.Group heading="Trang chính" className="px-2 py-1.5 text-xs font-medium text-muted-foreground">
                <Command.Item 
                  onSelect={() => runCommand(() => router.push("/dashboard"))}
                  className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm aria-selected:bg-accent aria-selected:text-accent-foreground mt-1"
                >
                  <LayoutDashboard className="h-4 w-4" />
                  Tổng quan Dashboard
                </Command.Item>
                <Command.Item 
                  onSelect={() => runCommand(() => router.push("/sessions"))}
                  className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm aria-selected:bg-accent aria-selected:text-accent-foreground mt-1"
                >
                  <Car className="h-4 w-4" />
                  Phiên gửi xe (Sessions)
                </Command.Item>
                <Command.Item 
                  onSelect={() => runCommand(() => router.push("/billing"))}
                  className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm aria-selected:bg-accent aria-selected:text-accent-foreground mt-1"
                >
                  <Receipt className="h-4 w-4" />
                  Quản lý hóa đơn
                </Command.Item>
              </Command.Group>
              
              <Command.Group heading="Cấu hình hệ thống" className="px-2 py-1.5 text-xs font-medium text-muted-foreground mt-2">
                <Command.Item 
                  onSelect={() => runCommand(() => router.push("/gates"))}
                  className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm aria-selected:bg-accent aria-selected:text-accent-foreground mt-1"
                >
                  <DoorOpen className="h-4 w-4" />
                  Quản lý Cổng / Barie
                </Command.Item>
                <Command.Item 
                  onSelect={() => runCommand(() => router.push("/users"))}
                  className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm aria-selected:bg-accent aria-selected:text-accent-foreground mt-1"
                >
                  <Users className="h-4 w-4" />
                  Tài khoản hệ thống
                </Command.Item>
              </Command.Group>
            </Command.List>
          </Command>
        </DialogContent>
      </Dialog>
    </>
  );
}
