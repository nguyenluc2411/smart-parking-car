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
 * Mark tự chứa gradient Indigo→Sky (khớp --brand-from/--brand-to),
 * dùng được mọi nơi mà không phụ thuộc wrapper.
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
          <stop stopColor="#4F46E5" />   {/* Indigo-600 */}
          <stop offset="1" stopColor="#0EA5E9" /> {/* Sky-500 */}
        </linearGradient>
        <linearGradient id={`${gid}-text`} x1="0" y1="0" x2="48" y2="48" gradientUnits="userSpaceOnUse">
          <stop stopColor="#ffffff" stopOpacity="1" />
          <stop offset="1" stopColor="#e0f2fe" stopOpacity="0.9" />
        </linearGradient>
      </defs>

      {/* Background tile with rounded corners */}
      <rect width="48" height="48" rx="13" fill={`url(#${gid}-bg)`} />

      {/* Subtle inner highlight */}
      <rect width="48" height="24" rx="13" fill="white" fillOpacity="0.06" />

      {/* ALPR scan corners — top-left & bottom-right */}
      <path
        d="M11 17V13a2 2 0 0 1 2-2h4M37 31v4a2 2 0 0 1-2 2h-4"
        stroke="white"
        strokeOpacity="0.6"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Letter "P" — Parking */}
      <path
        d="M17 35V14h8a6.5 6.5 0 0 1 0 13H17"
        stroke={`url(#${gid}-text)`}
        strokeWidth="4.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Sensor dot — smart / ALPR detection */}
      <circle cx="30.5" cy="32" r="2.6" fill="white" fillOpacity="0.9" />
    </svg>
  );
}
