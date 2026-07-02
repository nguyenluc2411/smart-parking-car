import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { adminApi } from "@/lib/api/admin";
import { useAuthStore } from "@/lib/stores/authStore";

export function useLogin() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);

  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      adminApi.login(username, password),
    onSuccess: (res, vars) => {
      setAuth(res.data.accessToken, res.data.role, vars.username);
      router.push("/dashboard");
    },
  });
}

export function useLogout() {
  const router = useRouter();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  return useMutation({
    mutationFn: () => adminApi.logout(),
    onSettled: () => {
      clearAuth();
      router.push("/login");
    },
  });
}
