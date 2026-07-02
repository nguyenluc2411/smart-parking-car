"use client";

import { useAlertStream } from "@/lib/hooks/useAlerts";

/** Renders nothing — keeps the live SSE alert subscription open while the dashboard is mounted. */
export function AlertStreamListener() {
  useAlertStream();
  return null;
}
