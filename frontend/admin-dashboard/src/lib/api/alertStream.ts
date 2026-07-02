import { useAuthStore } from "@/lib/stores/authStore";
import type { Alert } from "@/types";

/**
 * Subscribe to the parking-service Server-Sent Events alert stream.
 *
 * The browser's native EventSource cannot send an Authorization header, so we consume the stream
 * with fetch() (which can) and parse the SSE frames ourselves. Reconnects with a short backoff
 * until the AbortSignal fires. `useAlerts` polling is the safety net while a reconnect is pending.
 */
export async function subscribeAlerts(
  onAlert: (alert: Alert) => void,
  signal: AbortSignal
): Promise<void> {
  const base = process.env.NEXT_PUBLIC_PARKING_API_URL;

  while (!signal.aborted) {
    try {
      const token = useAuthStore.getState().token;
      const res = await fetch(`${base}/api/v1/alerts/stream`, {
        headers: { Authorization: `Bearer ${token ?? ""}`, Accept: "text/event-stream" },
        signal,
      });

      if (res.ok && res.body) {
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (!signal.aborted) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          // SSE frames are separated by a blank line; keep the trailing partial in the buffer.
          const frames = buffer.split("\n\n");
          buffer = frames.pop() ?? "";
          for (const frame of frames) {
            const dataLine = frame.split("\n").find((l) => l.startsWith("data:"));
            if (!dataLine) continue;
            try {
              onAlert(JSON.parse(dataLine.slice(5).trim()) as Alert);
            } catch {
              /* ignore a malformed frame */
            }
          }
        }
      }
    } catch {
      /* network error or abort — fall through to reconnect */
    }
    if (signal.aborted) break;
    await new Promise((r) => setTimeout(r, 3000)); // reconnect backoff
  }
}
