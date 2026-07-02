import axios from "axios";
import { useAuthStore } from "@/lib/stores/authStore";

function createClient(baseURL: string) {
  const client = axios.create({ baseURL });

  client.interceptors.request.use((config) => {
    const token = useAuthStore.getState().token;
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  });

  client.interceptors.response.use(
    (res) => res,
    (err) => {
      if (err.response?.status === 401 && typeof window !== "undefined") {
        useAuthStore.getState().clearAuth();
        window.location.href = "/login";
      }
      return Promise.reject(err);
    }
  );

  return client;
}

export const parkingClient = createClient(
  process.env.NEXT_PUBLIC_PARKING_API_URL + "/api/v1"
);
export const billingClient = createClient(
  process.env.NEXT_PUBLIC_BILLING_API_URL + "/api/v1"
);
export const adminClient = createClient(
  process.env.NEXT_PUBLIC_ADMIN_API_URL + "/api/v1"
);
