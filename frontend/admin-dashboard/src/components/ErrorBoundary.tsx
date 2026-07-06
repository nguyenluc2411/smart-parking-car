"use client";

import React from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

interface Props {
  children: React.ReactNode;
  /** Tên module/section để hiển thị trong thông báo lỗi */
  moduleName?: string;
}

interface State {
  hasError: boolean;
  error?: Error;
}

/**
 * Error Boundary bắt lỗi runtime của React component tree.
 * Bọc xung quanh các route pages để tránh crash toàn bộ layout.
 */
export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // Trong production có thể gửi lên monitoring service (Sentry...)
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: undefined });
  };

  render() {
    if (this.state.hasError) {
      return (
        <Card className="m-6">
          <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-destructive/10">
              <AlertTriangle className="h-7 w-7 text-destructive" />
            </div>
            <div>
              <p className="font-semibold">
                {this.props.moduleName
                  ? `Lỗi tại: ${this.props.moduleName}`
                  : "Đã xảy ra lỗi không mong muốn"}
              </p>
              <p className="mt-1 text-sm text-muted-foreground">
                {this.state.error?.message ?? "Vui lòng thử lại hoặc liên hệ kỹ thuật"}
              </p>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" onClick={this.handleReset}>
                <RefreshCw className="mr-2 h-4 w-4" />
                Thử lại
              </Button>
              <Button variant="ghost" onClick={() => window.location.reload()}>
                Tải lại trang
              </Button>
            </div>
          </CardContent>
        </Card>
      );
    }
    return this.props.children;
  }
}
