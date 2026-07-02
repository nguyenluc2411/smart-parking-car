"use client";

import { create } from "zustand";
import { cn } from "@/lib/utils";

type ToastVariant = "default" | "success" | "destructive";

interface ToastItem {
  id: number;
  title: string;
  description?: string;
  variant: ToastVariant;
}

interface ToastState {
  toasts: ToastItem[];
  push: (t: Omit<ToastItem, "id">) => void;
  dismiss: (id: number) => void;
}

const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (t) => {
    const id = Date.now() + Math.random();
    set((s) => ({ toasts: [...s.toasts, { ...t, id }] }));
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((x) => x.id !== id) }));
    }, 4000);
  },
  dismiss: (id) =>
    set((s) => ({ toasts: s.toasts.filter((x) => x.id !== id) })),
}));

/** Hook để bắn toast từ component. */
export function useToast() {
  const push = useToastStore((s) => s.push);
  return {
    toast: (
      title: string,
      opts?: { description?: string; variant?: ToastVariant }
    ) =>
      push({
        title,
        description: opts?.description,
        variant: opts?.variant ?? "default",
      }),
  };
}

const variantStyles: Record<ToastVariant, string> = {
  default: "border bg-background text-foreground",
  success: "border-transparent bg-green-600 text-white",
  destructive: "border-transparent bg-destructive text-destructive-foreground",
};

/** Render container — đặt 1 lần trong root layout. */
export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);

  return (
    <div className="fixed bottom-4 right-4 z-[100] flex w-full max-w-sm flex-col gap-2">
      {toasts.map((t) => (
        <button
          key={t.id}
          onClick={() => dismiss(t.id)}
          className={cn(
            "rounded-md p-4 text-left shadow-lg",
            variantStyles[t.variant]
          )}
        >
          <p className="text-sm font-semibold">{t.title}</p>
          {t.description && (
            <p className="mt-1 text-sm opacity-90">{t.description}</p>
          )}
        </button>
      ))}
    </div>
  );
}
