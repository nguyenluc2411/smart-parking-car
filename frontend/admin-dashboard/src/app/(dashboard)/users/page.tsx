"use client";

import { useState } from "react";
import { Plus, UserX, UserCheck, Loader2 } from "lucide-react";
import {
  useUsers,
  useCreateUser,
  useUpdateUserRole,
  useDeleteUser,
  useActivateUser,
} from "@/lib/hooks/useUsers";
import type { Role } from "@/types";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { formatDateTime } from "@/lib/utils";

function CreateUserDialog() {
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("OPERATOR");
  const create = useCreateUser();
  const { toast } = useToast();

  const onSubmit = () => {
    if (!username.trim() || !email.trim() || !password.trim()) {
      toast("Vui lòng điền đủ thông tin", { variant: "destructive" });
      return;
    }
    create.mutate(
      { username, email, password, role },
      {
        onSuccess: () => {
          toast("Đã tạo người dùng", { variant: "success" });
          setOpen(false);
          setUsername("");
          setEmail("");
          setPassword("");
          setRole("OPERATOR");
        },
        onError: () => toast("Tạo thất bại", { variant: "destructive" }),
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4" />
          Tạo người dùng
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Tạo người dùng mới</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">Tên đăng nhập</Label>
            <Input
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Mật khẩu</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>Vai trò</Label>
            <Select value={role} onValueChange={(v) => setRole(v as Role)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="OPERATOR">OPERATOR</SelectItem>
                <SelectItem value="ADMIN">ADMIN</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={create.isPending}
          >
            Hủy
          </Button>
          <Button onClick={onSubmit} disabled={create.isPending}>
            {create.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Tạo
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function UsersPage() {
  const query = useUsers();
  const updateRole = useUpdateUserRole();
  const del = useDeleteUser();
  const activate = useActivateUser();
  const { toast } = useToast();

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

      <Card>
        <CardContent className="pt-6">
          {query.isError ? (
            <ErrorState onRetry={() => query.refetch()} />
          ) : query.isLoading || !query.data ? (
            <Spinner />
          ) : (
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
                          onClick={() => onDeactivate(u.id)}
                          disabled={del.isPending}
                        >
                          <UserX className="h-4 w-4" />
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-emerald-600"
                          title="Kích hoạt"
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
          )}
        </CardContent>
      </Card>
    </RoleGuard>
  );
}
