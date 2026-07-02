import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export function StatCard({
  title,
  value,
  icon: Icon,
  hint,
  className,
}: {
  title: string;
  value: React.ReactNode;
  icon: React.ElementType;
  hint?: string;
  className?: string;
}) {
  return (
    <Card className={cn("hover:shadow-card-hover", className)}>
      <CardContent className="flex items-start justify-between gap-4 p-5">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">{title}</p>
          <p className="mt-2 truncate text-3xl font-bold tracking-tight">
            {value}
          </p>
          {hint && (
            <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
          )}
        </div>
        <div className="brand-gradient flex h-11 w-11 shrink-0 items-center justify-center rounded-xl shadow-sm">
          <Icon className="h-5 w-5 text-white" />
        </div>
      </CardContent>
    </Card>
  );
}
