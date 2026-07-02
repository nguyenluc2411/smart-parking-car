import { billingClient } from "./client";
import type {
  ApiResponse,
  Invoice,
  InvoiceFilterParams,
  Page,
  PaymentRequest,
  PaymentResult,
  Rate,
  UpdateRateRequest,
  DailyReport,
  MonthlyReport,
} from "@/types";

export const billingApi = {
  listInvoices: (params: InvoiceFilterParams) =>
    billingClient
      .get<ApiResponse<Page<Invoice>>>("/billing/invoices", { params })
      .then((r) => r.data),
  getInvoice: (sessionId: string) =>
    billingClient
      .get<ApiResponse<Invoice>>(`/billing/sessions/${sessionId}`)
      .then((r) => r.data),
  pay: (sessionId: string, body: PaymentRequest) =>
    billingClient
      .post<ApiResponse<PaymentResult>>(`/billing/sessions/${sessionId}/pay`, body)
      .then((r) => r.data),

  getRates: () =>
    billingClient.get<ApiResponse<Rate>>("/billing/rates").then((r) => r.data),
  updateRates: (body: UpdateRateRequest) =>
    billingClient
      .put<ApiResponse<Rate>>("/billing/rates", body)
      .then((r) => r.data),

  getDailyReport: (date?: string) =>
    billingClient
      .get<ApiResponse<DailyReport>>("/billing/report/daily", {
        params: date ? { date } : undefined,
      })
      .then((r) => r.data),
  getMonthlyReport: (month?: string) =>
    billingClient
      .get<ApiResponse<MonthlyReport>>("/billing/report/monthly", {
        params: month ? { month } : undefined,
      })
      .then((r) => r.data),
};
