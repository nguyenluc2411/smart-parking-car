import axios from "axios";
import { useAuthStore } from "@/lib/stores/authStore";

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string | null) => void;
  reject: (err: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

function createClient(baseURL: string) {
  const client = axios.create({ baseURL, timeout: 15000 });

  client.interceptors.request.use((config) => {
    const token = useAuthStore.getState().token;
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  });

  client.interceptors.response.use(
    (res) => res,
    async (err) => {
      const originalRequest = err.config;

      if (err.response?.status === 401 && !originalRequest._retry && typeof window !== "undefined") {
        // If the unauthorized request is already a login or refresh request, don't attempt to refresh
        if (
          originalRequest.url?.includes("/auth/refresh") ||
          originalRequest.url?.includes("/auth/login")
        ) {
          useAuthStore.getState().clearAuth();
          window.location.href = "/login";
          return Promise.reject(err);
        }

        if (isRefreshing) {
          return new Promise<string | null>((resolve, reject) => {
            failedQueue.push({ resolve, reject });
          })
            .then((token) => {
              if (token) {
                originalRequest.headers.Authorization = `Bearer ${token}`;
                return client(originalRequest);
              }
              return Promise.reject(err);
            })
            .catch((error) => {
              return Promise.reject(error);
            });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        const refreshToken = useAuthStore.getState().refreshToken;
        if (!refreshToken) {
          useAuthStore.getState().clearAuth();
          window.location.href = "/login";
          isRefreshing = false;
          return Promise.reject(err);
        }

        try {
          const refreshURL =
            (process.env.NEXT_PUBLIC_ADMIN_API_URL || "") + "/api/v1/auth/refresh";
          
          // Request a new access token using raw axios
          const res = await axios.post(refreshURL, { refreshToken });

          if (res.data?.success && res.data?.data) {
            const { accessToken, refreshToken: newRefreshToken, role } = res.data.data;
            const username = useAuthStore.getState().username || "";
            
            // Save new tokens
            useAuthStore.getState().setAuth(accessToken, newRefreshToken, role, username);
            
            processQueue(null, accessToken);

            originalRequest.headers.Authorization = `Bearer ${accessToken}`;
            return client(originalRequest);
          } else {
            throw new Error("Token refresh response structure invalid");
          }
        } catch (refreshErr) {
          processQueue(refreshErr, null);
          useAuthStore.getState().clearAuth();
          window.location.href = "/login";
          return Promise.reject(refreshErr);
        } finally {
          isRefreshing = false;
        }
      }

      return Promise.reject(err);
    }
  );

  return client;
}

export const parkingClient = createClient(
  (process.env.NEXT_PUBLIC_PARKING_API_URL || "") + "/api/v1"
);
export const billingClient = createClient(
  (process.env.NEXT_PUBLIC_BILLING_API_URL || "") + "/api/v1"
);
export const adminClient = createClient(
  (process.env.NEXT_PUBLIC_ADMIN_API_URL || "") + "/api/v1"
);
