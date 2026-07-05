import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Format số tiền VND. */
export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(amount);
}

/** Định dạng datetime ISO → dd/MM/yyyy HH:mm (giờ địa phương). */
export function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso));
}

/** ISO → thời gian tương đối: "vừa xong" / "5 phút trước" / "2 giờ trước"; >24h rơi về ngày giờ. */
export function timeAgo(iso: string | null): string {
  if (!iso) return "—";
  const sec = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (sec < 60) return "vừa xong";
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} phút trước`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr} giờ trước`;
  return formatDateTime(iso);
}

/** Đổi tổng số giây → chuỗi "Xh Ym". */
export function formatDuration(seconds: number | null): string {
  if (seconds === null || seconds === undefined) return "—";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

/**
 * Trích xuất thông điệp lỗi từ response API hoặc trả về fallback.
 * Dùng nhất quán trong tất cả mutation onError callback.
 */
export function getApiErrorMessage(
  e: unknown,
  fallback = "Thao tác thất bại"
): string {
  const err = e as { response?: { data?: { error?: { message?: string } } } };
  return err?.response?.data?.error?.message ?? fallback;
}

