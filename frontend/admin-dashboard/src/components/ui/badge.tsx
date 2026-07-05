import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium transition-colors focus:outline-none",
  {
    variants: {
      variant: {
        default: "border-transparent bg-primary text-primary-foreground",
        secondary: "border-border/50 bg-secondary/50 text-secondary-foreground",
        destructive: "border-destructive/20 bg-destructive/10 text-red-600 dark:text-red-400",
        outline: "border-border bg-transparent text-foreground",
        success: "border-success/20 bg-success/10 text-emerald-700 dark:text-emerald-400",
        warning: "border-warning/30 bg-warning/10 text-amber-700 dark:text-amber-400",
        info: "border-info/20 bg-info/10 text-blue-700 dark:text-blue-400",
      },
    },
    defaultVariants: { variant: "default" },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  );
}

export { Badge, badgeVariants };
