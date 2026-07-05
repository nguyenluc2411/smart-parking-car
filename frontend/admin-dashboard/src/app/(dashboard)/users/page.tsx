"use client";

import { useState } from "react";
import { UserX, UserCheck, ChevronLeft, ChevronRight } from "lucide-react";
import {
  useUsers,
  useUpdateUserRole,
  useDeleteUser,
  useActivateUser,
} from "@/lib/hooks/useUsers";
import type { Role } from "@/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { ErrorState } from "@/components/ui/error-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatDateTime } from "@/lib/utils";

const PAGE_SIZE = 20;

import { CreateUserDialog } from "@/components/users/CreateUserDialog";

export default function UsersPage() {
  const [page, setPage] = useState(0);
  const query = useUsers({ page, size: PAGE_SIZE });
  const updateRole = useUpdateUserRole();
  const del = useDeleteUser();
  const activate = useActivateUser();
  const { toast } = useToast();

  const totalPages = query.data?.totalPages ?? 0;
  const totalElements = query.data?.totalElements ?? 0;

  const onRoleChange = (id: string, role: Role) => {
    updateRole.mutate(
      { id, role },
      {
        onSuccess: () => toast("Đã đổi vai trò", { variant: "success" }),
        onError: () => toast("Đổi vai trò thất bại", { variant: "destructive" }),
      }
    );
  };

  const onDeactivate = (id: string) => {
    del.mutate(id, {
      onSuccess: () => toast("Đã vô hiệu hóa", { variant: "success" }),
      onError: () => toast("Thao tác thất bại", { variant: "destructive" }),
    });
  };

  const onActivate = (id: string) => {
    activate.mutate(id, {
      onSuccess: () => toast("Đã kích hoạt", { variant: "success" }),
      onError: () => toast("Thao tác thất bại", { variant: "destructive" }),
    });
  };

  return (
    <RoleGuard allow={["ADMIN"]}>
      <PageHeader
        title="Người dùng"
        description="Quản lý tài khoản và phân quyền"
        action={<CreateUserDialog />}
      />

      <Card className="border border-border/50 shadow-sm">
        <CardContent className="pt-6">
          {query.isError ? (
            <ErrorState onRetry={() => query.refetch()} />
          ) : query.isLoading || !query.data ? (
            <Spinner />
          ) : (
            <>
              {/* Info row */}
              <div className="mb-3 flex items-center justify-between text-sm text-muted-foreground">
                <span>Tổng cộng {totalElements} người dùng</span>
                {totalPages > 1 && (
                  <span>
                    Trang {page + 1} / {totalPages}
                  </span>
                )}
              </div>

              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tên đăng nhập</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Vai trò</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead>Ngày tạo</TableHead>
                    <TableHead className="w-12"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {query.data.content.map((u) => (
                    <TableRow key={u.id}>
                      <TableCell className="font-medium">{u.username}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {u.email}
                      </TableCell>
                      <TableCell>
                        <Select
                          value={u.role}
                          onValueChange={(v) => onRoleChange(u.id, v as Role)}
                        >
                          <SelectTrigger className="h-8 w-32">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="OPERATOR">OPERATOR</SelectItem>
                            <SelectItem value="ADMIN">ADMIN</SelectItem>
                          </SelectContent>
                        </Select>
                      </TableCell>
                      <TableCell>
                        {u.isActive ? (
                          <Badge variant="success">Hoạt động</Badge>
                        ) : (
                          <Badge variant="secondary">Vô hiệu</Badge>
                        )}
                      </TableCell>
                      <TableCell>{formatDateTime(u.createdAt)}</TableCell>
                      <TableCell>
                        {u.isActive ? (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-destructive"
                            title="Vô hiệu hóa"
                            aria-label="Vô hiệu hóa"
                            onClick={() => onDeactivate(u.id)}
                            disabled={del.isPending}
                          >
                            <UserX className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-success"
                            title="Kích hoạt"
                            aria-label="Kích hoạt"
                            onClick={() => onActivate(u.id)}
                            disabled={activate.isPending}
                          >
                            <UserCheck className="h-4 w-4" />
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Server-side Pagination */}
              {totalPages > 1 && (
                <div className="mt-4 flex items-center justify-end gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0 || query.isFetching}
                  >
                    <ChevronLeft className="h-4 w-4" />
                    Trước
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    {page + 1} / {totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1 || query.isFetching}
                  >
                    Sau
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </RoleGuard>
  );
}
