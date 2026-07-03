"use client";

import { useState, useEffect } from "react";
import { Mail, ShieldAlert, Key, Settings, Loader2, Save, Send } from "lucide-react";
import { useSmtpSettings, useSaveSmtpSettings, useSendTestEmail } from "@/lib/hooks/useNotifications";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";

export default function NotificationSettingsPage() {
  const { data: smtpSettings, isLoading } = useSmtpSettings();
  const saveMutation = useSaveSmtpSettings();
  const testEmailMutation = useSendTestEmail();
  const { toast } = useToast();

  const [host, setHost] = useState("");
  const [port, setPort] = useState(587);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [senderEmail, setSenderEmail] = useState("");
  const [receiverEmail, setReceiverEmail] = useState("");
  const [enableSmtp, setEnableSmtp] = useState(true);
  const [alertTypes, setAlertTypes] = useState<string[]>([]);
  const [testEmailAddr, setTestEmailAddr] = useState("");
  const [showTestModal, setShowTestModal] = useState(false);

  useEffect(() => {
    if (smtpSettings) {
      setHost(smtpSettings.host);
      setPort(smtpSettings.port);
      setUsername(smtpSettings.username);
      setSenderEmail(smtpSettings.senderEmail);
      setReceiverEmail(smtpSettings.receiverEmail);
      setEnableSmtp(smtpSettings.enableSmtp);
      setAlertTypes(smtpSettings.alertTypes);
    }
  }, [smtpSettings]);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    saveMutation.mutate(
      {
        host,
        port,
        username,
        password: password || undefined,
        senderEmail,
        receiverEmail,
        enableSmtp,
        alertTypes,
      },
      {
        onSuccess: () => {
          toast("Lưu cấu hình SMTP thành công!", { variant: "success" });
        },
        onError: (err) => {
          toast("Không thể lưu cấu hình SMTP", { variant: "destructive" });
        },
      }
    );
  };

  const handleSendTest = () => {
    if (!testEmailAddr) {
      toast("Vui lòng nhập email người nhận", { variant: "destructive" });
      return;
    }
    testEmailMutation.mutate(testEmailAddr, {
      onSuccess: (res) => {
        if (res.success) {
          toast(res.message || "Đã gửi email thử nghiệm!", { variant: "success" });
          setShowTestModal(false);
          setTestEmailAddr("");
        } else {
          toast("Lỗi gửi email thử nghiệm", { variant: "destructive" });
        }
      },
      onError: () => {
        toast("Gửi email thử nghiệm thất bại", { variant: "destructive" });
      },
    });
  };

  const toggleAlertType = (type: string) => {
    setAlertTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]
    );
  };

  if (isLoading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
        <span className="ml-2 text-sm text-muted-foreground">Đang tải cấu hình...</span>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6 max-w-4xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Cấu hình SMTP & Thông báo</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Quản lý cài đặt cấu hình SMTP gửi email thông báo tự động cho các sự kiện khẩn cấp.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <div className="md:col-span-2 space-y-6">
          <form onSubmit={handleSave}>
            <Card className="border-border/60 shadow-md">
              <CardHeader className="bg-muted/30 pb-4 border-b">
                <CardTitle className="text-base font-semibold flex items-center gap-2">
                  <Settings className="h-4 w-4 text-primary" />
                  Cấu hình máy chủ SMTP (Gmail / Custom)
                </CardTitle>
                <CardDescription>
                  Thiết lập kết nối với server mail để gửi cảnh báo tự động.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 pt-6">
                <div className="flex items-center space-x-2 pb-2">
                  <input
                    type="checkbox"
                    id="enableSmtp"
                    checked={enableSmtp}
                    onChange={(e) => setEnableSmtp(e.target.checked)}
                    className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                  />
                  <Label htmlFor="enableSmtp" className="text-sm font-semibold cursor-pointer">
                    Kích hoạt gửi thông báo qua Email
                  </Label>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="host">SMTP Host</Label>
                    <Input
                      id="host"
                      disabled={!enableSmtp}
                      placeholder="e.g. smtp.gmail.com"
                      value={host}
                      onChange={(e) => setHost(e.target.value)}
                      required
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="port">Port</Label>
                    <Input
                      id="port"
                      type="number"
                      disabled={!enableSmtp}
                      placeholder="e.g. 587 hoặc 465"
                      value={port}
                      onChange={(e) => setPort(Number(e.target.value))}
                      required
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="username">Tài khoản SMTP</Label>
                    <Input
                      id="username"
                      type="email"
                      disabled={!enableSmtp}
                      placeholder="username@gmail.com"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      required
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="password">Mật khẩu ứng dụng (App Password)</Label>
                    <Input
                      id="password"
                      type="password"
                      disabled={!enableSmtp}
                      placeholder="••••••••••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="senderEmail">Email người gửi (From)</Label>
                    <Input
                      id="senderEmail"
                      type="email"
                      disabled={!enableSmtp}
                      placeholder="no-reply@parking.vn"
                      value={senderEmail}
                      onChange={(e) => setSenderEmail(e.target.value)}
                      required
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="receiverEmail">Email quản trị viên nhận (To)</Label>
                    <Input
                      id="receiverEmail"
                      type="email"
                      disabled={!enableSmtp}
                      placeholder="admin@parking.vn"
                      value={receiverEmail}
                      onChange={(e) => setReceiverEmail(e.target.value)}
                      required
                    />
                  </div>
                </div>
              </CardContent>
              <CardFooter className="border-t bg-muted/20 px-6 py-4 flex justify-between">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!enableSmtp}
                  onClick={() => setShowTestModal(true)}
                  className="gap-2"
                >
                  <Send className="h-4 w-4" />
                  Gửi Test Email
                </Button>

                <Button
                  type="submit"
                  size="sm"
                  disabled={saveMutation.isPending}
                  className="gap-2"
                >
                  {saveMutation.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Save className="h-4 w-4" />
                  )}
                  Lưu cấu hình
                </Button>
              </CardFooter>
            </Card>
          </form>
        </div>

        <div className="space-y-6">
          <Card className="border-border/60 shadow-md">
            <CardHeader className="bg-muted/30 pb-4 border-b">
              <CardTitle className="text-base font-semibold flex items-center gap-2">
                <ShieldAlert className="h-4 w-4 text-destructive" />
                Cảnh báo kích hoạt gửi Mail
              </CardTitle>
              <CardDescription>
                Chọn các loại sự kiện sẽ tự động gửi email cho Admin.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4 pt-6">
              <div className="space-y-3">
                <div className="flex items-start space-x-3 rounded-lg border p-3 hover:bg-muted/30 transition-colors">
                  <input
                    type="checkbox"
                    id="type-blacklist"
                    checked={alertTypes.includes("BLACKLIST_HIT")}
                    onChange={() => toggleAlertType("BLACKLIST_HIT")}
                    className="mt-1 h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                  />
                  <div className="grid gap-1.5 leading-none">
                    <Label htmlFor="type-blacklist" className="text-sm font-semibold cursor-pointer text-destructive">
                      Blacklist Hit (Nguy hiểm)
                    </Label>
                    <p className="text-xs text-muted-foreground">
                      Gửi mail ngay lập tức khi phát hiện xe nằm trong danh sách đen đi vào bãi xe.
                    </p>
                  </div>
                </div>

                <div className="flex items-start space-x-3 rounded-lg border p-3 hover:bg-muted/30 transition-colors">
                  <input
                    type="checkbox"
                    id="type-duplicate"
                    checked={alertTypes.includes("DUPLICATE_ACTIVE_ENTRY")}
                    onChange={() => toggleAlertType("DUPLICATE_ACTIVE_ENTRY")}
                    className="mt-1 h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                  />
                  <div className="grid gap-1.5 leading-none">
                    <Label htmlFor="type-duplicate" className="text-sm font-semibold cursor-pointer text-amber-600">
                      Trùng phiên gửi xe (Nghi vấn)
                    </Label>
                    <p className="text-xs text-muted-foreground">
                      Cảnh báo nếu một biển số xe xuất hiện 2 lần cùng trạng thái đang gửi (nghi ngờ giả biển số).
                    </p>
                  </div>
                </div>

                <div className="flex items-start space-x-3 rounded-lg border p-3 hover:bg-muted/30 transition-colors">
                  <input
                    type="checkbox"
                    id="type-unmatched"
                    checked={alertTypes.includes("UNMATCHED_EXIT")}
                    onChange={() => toggleAlertType("UNMATCHED_EXIT")}
                    className="mt-1 h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                  />
                  <div className="grid gap-1.5 leading-none">
                    <Label htmlFor="type-unmatched" className="text-sm font-semibold cursor-pointer text-amber-500">
                      Xe ra không có phiên vào
                    </Label>
                    <p className="text-xs text-muted-foreground">
                      Thông báo khi xe đi ra khỏi bãi nhưng hệ thống không tìm thấy phiên lúc vào.
                    </p>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {showTestModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <Card className="w-full max-w-md mx-4 shadow-xl border-border bg-card">
            <CardHeader>
              <CardTitle className="text-lg flex items-center gap-2">
                <Mail className="h-5 w-5 text-primary" />
                Gửi Email kiểm tra kết nối
              </CardTitle>
              <CardDescription>
                Nhập địa chỉ email người nhận để gửi một email thử nghiệm thông qua cấu hình máy chủ hiện tại.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="test-email">Email nhận test</Label>
                <Input
                  id="test-email"
                  type="email"
                  placeholder="operator@smartparking.vn"
                  value={testEmailAddr}
                  onChange={(e) => setTestEmailAddr(e.target.value)}
                  required
                />
              </div>
            </CardContent>
            <CardFooter className="flex justify-end gap-2 border-t pt-4">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowTestModal(false)}
              >
                Hủy bỏ
              </Button>
              <Button
                size="sm"
                disabled={testEmailMutation.isPending}
                onClick={handleSendTest}
                className="gap-2"
              >
                {testEmailMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Send className="h-4 w-4" />
                )}
                Gửi thử
              </Button>
            </CardFooter>
          </Card>
        </div>
      )}
    </div>
  );
}
