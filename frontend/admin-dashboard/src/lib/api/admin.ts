import { adminClient } from "./client";
import type {
  ApiResponse,
  Page,
  AuthResponse,
  User,
  CreateUserRequest,
  AuditLog,
  Role,
} from "@/types";

export const adminApi = {
  login: (username: string, password: string) =>
    adminClient
      .post<ApiResponse<AuthResponse>>("/auth/login", { username, password })
      .then((r) => r.data),
  logout: () => adminClient.post("/auth/logout").then((r) => r.data),

  listUsers: (params?: { page?: number; size?: number }) =>
    adminClient
      .get<ApiResponse<Page<User>>>("/users", { params })
      .then((r) => r.data),
  createUser: (body: CreateUserRequest) =>
    adminClient.post<ApiResponse<User>>("/users", body).then((r) => r.data),
  updateRole: (id: string, role: Role) =>
    adminClient
      .put<ApiResponse<unknown>>(`/users/${id}/role`, { role })
      .then((r) => r.data),
  deleteUser: (id: string) =>
    adminClient.delete(`/users/${id}`).then((r) => r.data),
  activateUser: (id: string) =>
    adminClient
      .put<ApiResponse<User>>(`/users/${id}/activate`)
      .then((r) => r.data),

  listAuditLogs: (params?: {
    action?: string;
    userId?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) =>
    adminClient
      .get<ApiResponse<Page<AuditLog>>>("/audit-logs", { params })
      .then((r) => r.data),
};
