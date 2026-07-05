import { useMutation, useQuery, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { billingApi } from "@/lib/api/billing";
import type { InvoiceFilterParams, PaymentRequest, UpdateRateRequest } from "@/types";

export function useInvoices(params: InvoiceFilterParams) {
  return useQuery({
    queryKey: ["billing", "invoices", params],
    queryFn: () => billingApi.listInvoices(params).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
}

export function useInvoice(sessionId: string) {
  return useQuery({
    queryKey: ["billing", "invoice", sessionId],
    queryFn: () => billingApi.getInvoice(sessionId).then((r) => r.data),
    enabled: !!sessionId,
  });
}

export function usePayInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, body }: { sessionId: string; body: PaymentRequest }) =>
      billingApi.pay(sessionId, body),
    onSuccess: (_res, vars) => {
      qc.invalidateQueries({ queryKey: ["billing", "invoice", vars.sessionId] });
      qc.invalidateQueries({ queryKey: ["billing", "invoices"] });
    },
  });
}

export function useRates() {
  return useQuery({
    queryKey: ["billing", "rates"],
    queryFn: () => billingApi.getRates().then((r) => r.data),
  });
}

export function useUpdateRates() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateRateRequest) => billingApi.updateRates(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["billing", "rates"] }),
  });
}
