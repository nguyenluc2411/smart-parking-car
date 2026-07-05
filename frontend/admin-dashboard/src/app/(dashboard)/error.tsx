"use client";

import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { AlertTriangle } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export default function DashboardError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Dashboard error:", error);
  }, [error]);

  return (
    <div className="flex h-[80vh] w-full items-center justify-center p-6">
      <Card className="w-full max-w-md border-destructive/20 shadow-sm">
        <CardContent className="flex flex-col items-center justify-center pt-10 pb-8 text-center space-y-4">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10">
            <AlertTriangle className="h-8 w-8 text-destructive" />
          </div>
          <div className="space-y-2">
            <h2 className="text-xl font-semibold tracking-tight">Đã có lỗi xảy ra</h2>
            <p className="text-sm text-muted-foreground">
              {error.message || "Lỗi hệ thống. Vui lòng thử lại sau."}
            </p>
          </div>
          <Button onClick={reset} variant="default" className="mt-4">
            Thử lại
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
