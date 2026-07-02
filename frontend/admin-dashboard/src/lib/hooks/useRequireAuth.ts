"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/stores/authStore";

/**
 * Redirect về /login nếu chưa đăng nhập — nhưng CHỜ đọc xong localStorage (hasHydrated) rồi mới
 * quyết định, để reload trang không bị đá ra login oan trong lúc state đang khôi phục.
 */
export function useRequireAuth() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const hasHydrated = useAuthStore((s) => s.hasHydrated);

  useEffect(() => {
    if (hasHydrated && !isAuthenticated) router.replace("/login");
  }, [hasHydrated, isAuthenticated, router]);

  return { isAuthenticated, hasHydrated };
}
