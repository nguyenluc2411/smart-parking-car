"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/layout/PageHeader";
import { RoleGuard } from "@/components/layout/RoleGuard";
import { Card, CardContent } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import {
  useWhitelist,
  useDeleteWhitelist,
  useBlacklist,
  useDeleteBlacklist,
} from "@/lib/hooks/useVehicles";

import { AddVehicleDialog, KIND_LABEL, Kind } from "@/components/vehicles/AddVehicleDialog";
import { VehicleTableContent } from "@/components/vehicles/VehicleTableContent";

function WhitelistTable() {
  const query = useWhitelist();
  const del = useDeleteWhitelist();
  return <VehicleTableContent kind="whitelist" query={query} del={del} />;
}

function BlacklistTable() {
  const query = useBlacklist();
  const del = useDeleteBlacklist();
  return <VehicleTableContent kind="blacklist" query={query} del={del} />;
}

function VehiclesContent() {
  const params = useSearchParams();
  // Deep-link from a BLACKLIST_HIT alert: /vehicles?tab=blacklist
  const tabParam: Kind = params.get("tab") === "blacklist" ? "blacklist" : "whitelist";
  const [kind, setKind] = useState<Kind>(tabParam);

  useEffect(() => {
    setKind(tabParam);
  }, [tabParam]);

  return (
    <RoleGuard allow={["ADMIN"]}>
      <PageHeader
        title="Whitelist / Blacklist"
        description="Quản lý xe ưu tiên (mở ngay, miễn phí) và xe cấm (từ chối vào)"
        action={<AddVehicleDialog kind={kind} />}
      />

      <div className="mb-6 inline-flex rounded-md border border-border/50 bg-secondary/30 p-1">
        {(["whitelist", "blacklist"] as const).map((k) => (
          <button
            key={k}
            type="button"
            onClick={() => setKind(k)}
            className={cn(
              "rounded-[4px] px-4 py-1.5 text-sm font-medium transition-colors",
              kind === k
                ? "bg-background text-foreground shadow-[0_1px_2px_rgba(0,0,0,0.04)] border border-border/30"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            {KIND_LABEL[k]}
          </button>
        ))}
      </div>

      <Card className="border border-border/50 shadow-sm">
        <CardContent className="pt-6">
          {kind === "whitelist" ? (
            <WhitelistTable />
          ) : (
            <BlacklistTable />
          )}
        </CardContent>
      </Card>
    </RoleGuard>
  );
}

export default function VehiclesPage() {
  // useSearchParams requires a Suspense boundary in the App Router.
  return (
    <Suspense fallback={<Spinner />}>
      <VehiclesContent />
    </Suspense>
  );
}
