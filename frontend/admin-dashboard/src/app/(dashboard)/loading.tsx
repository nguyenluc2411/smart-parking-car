import { Spinner } from "@/components/ui/spinner";

export default function DashboardLoading() {
  return (
    <div className="flex h-[80vh] w-full items-center justify-center">
      <div className="flex flex-col items-center gap-2">
        <Spinner size="lg" />
        <p className="text-sm text-muted-foreground animate-pulse mt-4">
          Đang tải dữ liệu...
        </p>
      </div>
    </div>
  );
}
