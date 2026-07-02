"use client";

import { useId } from "react";
import { cn } from "@/lib/utils";

interface BrandLogoProps {
  /** Cạnh của logo (px). Mặc định 36. */
  size?: number;
  className?: string;
}

/**
 * Logo thương hiệu Smart Parking — ALPR.
 *
 * Mark tự chứa gradient Indigo→Cyan (khớp `--brand-from`/`--brand-to`),
 * dùng được mọi nơi mà không phụ thuộc wrapper: chữ "P" nét đều (Parking)
 * + chấm cảm biến gợi nhận diện biển số tự động (ALPR / "smart").
 */
export function BrandLogo({ size = 36, className }: BrandLogoProps) {
  const gid = useId().replace(/:/g, "");

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      role="img"
      aria-label="Smart Parking"
      className={cn("shrink-0", className)}
    >
      <defs>
        <linearGradient id={`${gid}-bg`} x1="0" y1="0" x2="48" y2="48" gradientUnits="userSpaceOnUse">
          <stop stopColor="#4F46E5" />
          <stop offset="1" stopColor="#06B6D4" />
        </linearGradient>
      </defs>

      {/* Tile thương hiệu */}
      <rect width="48" height="48" rx="13" fill={`url(#${gid}-bg)`} />

      {/* Khung quét ALPR (góc trên-trái + dưới-phải) */}
      <path
        d="M11 16.5V13a2 2 0 0 1 2-2h3.5M37 31.5V35a2 2 0 0 1-2 2h-3.5"
        stroke="#fff"
        strokeOpacity="0.55"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Chữ "P" — Parking */}
      <path
        d="M17.5 35V14H25a6.5 6.5 0 0 1 0 13H17.5"
        stroke="#fff"
        strokeWidth="4.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Chấm cảm biến — "smart" / điểm bắt biển số */}
      <circle cx="30.5" cy="32" r="2.4" fill="#fff" />
    </svg>
  );
}
