import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Role } from "@/types";

interface AuthState {
  token: string | null;
  role: Role | null;
  username: string | null;
  isAuthenticated: boolean;
  /** true once the persisted state has been read from localStorage (avoids a redirect-on-reload flash). */
  hasHydrated: boolean;
  setAuth: (token: string, role: Role, username: string) => void;
  clearAuth: () => void;
  setHasHydrated: (v: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      role: null,
      username: null,
      isAuthenticated: false,
      hasHydrated: false,
      setAuth: (token, role, username) =>
        set({ token, role, username, isAuthenticated: true }),
      clearAuth: () =>
        set({ token: null, role: null, username: null, isAuthenticated: false }),
      setHasHydrated: (v) => set({ hasHydrated: v }),
    }),
    {
      name: "auth-storage",
      // Don't persist the transient hydration flag — it must start false on each load.
      partialize: (s) => ({
        token: s.token,
        role: s.role,
        username: s.username,
        isAuthenticated: s.isAuthenticated,
      }),
      // Fires after localStorage has been read back into the store on the client.
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    }
  )
);
