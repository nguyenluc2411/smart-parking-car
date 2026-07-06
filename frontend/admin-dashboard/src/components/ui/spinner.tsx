import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

const sizeClasses = {
  sm: "h-4 w-4",
  md: "h-6 w-6",
  lg: "h-8 w-8",
};

export function Spinner({
  className,
  size = "md",
}: {
  className?: string;
  size?: keyof typeof sizeClasses;
}) {
  return (
    <div className="flex items-center justify-center py-12">
      <Loader2 className={cn(sizeClasses[size], "animate-spin text-muted-foreground", className)} />
    </div>
  );
}
