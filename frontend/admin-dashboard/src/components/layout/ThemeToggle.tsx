"use client";

import { useTheme } from "next-themes";
import { Sun, Moon, Monitor } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * ThemeToggle — chu kỳ: light → dark → system → light
 */
export function ThemeToggle() {
  const { theme, setTheme, resolvedTheme } = useTheme();

  const cycle = () => {
    if (theme === "light") setTheme("dark");
    else if (theme === "dark") setTheme("system");
    else setTheme("light");
  };

  const isDark = resolvedTheme === "dark";

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={cycle}
      className={cn(
        "h-9 w-9 rounded-lg transition-all",
        "text-muted-foreground hover:text-foreground hover:bg-muted/60"
      )}
      aria-label="Chuyển chế độ sáng/tối"
      title={`Chế độ: ${theme ?? "system"}`}
    >
      {theme === "system" ? (
        <Monitor className="h-4.5 w-4.5 transition-all duration-300" />
      ) : isDark ? (
        <Sun className="h-4.5 w-4.5 transition-all duration-300 text-amber-400" />
      ) : (
        <Moon className="h-4.5 w-4.5 transition-all duration-300" />
      )}
    </Button>
  );
}
