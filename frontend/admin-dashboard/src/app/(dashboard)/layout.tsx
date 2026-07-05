"use client";

import { Sidebar } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { AlertStreamListener } from "@/components/dashboard/AlertStreamListener";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { useRequireAuth } from "@/lib/hooks/useRequireAuth";

/** Skeleton shell của layout: hiển thị cấu trúc ngay lập tức thay vì full-screen spinner */
function LayoutSkeleton() {
  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Sidebar skeleton */}
      <div className="hidden w-60 shrink-0 flex-col border-r border-border/50 bg-background md:flex">
        <div className="flex h-16 items-center gap-3 border-b border-border/50 px-5">
          <div className="h-8 w-8 animate-pulse rounded-xl bg-muted" />
          <div className="space-y-1.5">
            <div className="h-3.5 w-24 animate-pulse rounded bg-muted" />
            <div className="h-2 w-16 animate-pulse rounded bg-muted" />
          </div>
        </div>
        <div className="flex flex-col gap-1 p-4 pt-5">
          <div className="h-2 w-12 animate-pulse rounded bg-muted mb-2" />
          {[...Array(6)].map((_, i) => (
            <div key={i} className="flex items-center gap-3 rounded-lg px-3 py-2.5">
              <div className="h-7 w-7 animate-pulse rounded-md bg-muted" />
              <div className="h-3 w-20 animate-pulse rounded bg-muted" style={{ animationDelay: `${i * 50}ms` }} />
            </div>
          ))}
        </div>
      </div>
      {/* Main area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Header skeleton */}
        <div className="flex h-16 items-center justify-between border-b border-border/50 bg-background px-4 sm:px-6">
          <div className="h-4 w-32 animate-pulse rounded bg-muted" />
          <div className="flex items-center gap-3">
            <div className="h-8 w-40 animate-pulse rounded-md bg-muted hidden md:block" />
            <div className="h-8 w-24 animate-pulse rounded-lg bg-muted" />
          </div>
        </div>
        {/* Content skeleton */}
        <main className="flex-1 overflow-y-auto bg-muted/20 p-6">
          <div className="space-y-6 max-w-7xl mx-auto">
            <div className="h-8 w-40 animate-pulse rounded-lg bg-muted" />
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-32 animate-pulse rounded-xl border border-border/50 bg-card" />
              ))}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isAuthenticated, hasHydrated } = useRequireAuth();

  if (!hasHydrated) {
    return <LayoutSkeleton />;
  }

  if (!isAuthenticated) return null;

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <AlertStreamListener />
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto bg-muted/20">
          <div className="mx-auto max-w-[1400px] p-6 sm:p-8">
            <ErrorBoundary>
              {children}
            </ErrorBoundary>
          </div>
        </main>
      </div>
    </div>
  );
}
