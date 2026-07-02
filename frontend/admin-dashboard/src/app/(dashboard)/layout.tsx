"use client";

import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { AlertStreamListener } from "@/components/dashboard/AlertStreamListener";
import { useRequireAuth } from "@/lib/hooks/useRequireAuth";
import { Spinner } from "@/components/ui/spinner";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isAuthenticated, hasHydrated } = useRequireAuth();

  // Chờ đọc xong phiên đăng nhập từ localStorage (tránh đá ra /login khi reload).
  if (!hasHydrated) {
    return (
      <div className="flex h-screen items-center justify-center">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }
  if (!isAuthenticated) return null;

  return (
    <div className="flex h-screen overflow-hidden">
      <AlertStreamListener />
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto bg-muted/30 p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
