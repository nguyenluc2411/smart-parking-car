package com.smartparking.billing.service;

import com.smartparking.billing.dto.event.SessionClosedEventDTO;
import com.smartparking.billing.dto.request.PayRequestDTO;
import com.smartparking.billing.dto.response.DriverPaymentResponseDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.dto.response.MoMoPaymentResponseDTO;
import com.smartparking.billing.dto.response.PageResponseDTO;
import com.smartparking.billing.dto.response.PayOsPaymentResponseDTO;
import com.smartparking.billing.dto.response.PaymentResponseDTO;
import com.smartparking.billing.entity.enums.InvoiceStatus;
import java.util.List;
import java.util.UUID;

/**
 * Invoice generation and payment for parking sessions.
 *
 * <p>Contract only — no business logic, no {@code @Transactional} here (CLAUDE.md §6.4).
 */
public interface BillingService {

    /**
     * Handle a {@code parking.session.closed} event: calculate the fee (BR-004), persist a PENDING
     * invoice and emit {@code billing.invoice.calculated} via the outbox. Idempotent on
     * {@code sessionId} (BR-004-6) — a duplicate event is a no-op.
     *
     * @param event the {@code parking.session.closed} payload
     */
    void handleSessionClosed(SessionClosedEventDTO event);

    /**
     * Return the invoice for a session.
     *
     * @throws com.smartparking.billing.exception.InvoiceNotFoundException if none exists
     */
    InvoiceResponseDTO getInvoiceBySession(UUID sessionId);

    /**
     * Confirm an operator-collected payment (BR-005): record a {@code payment}, mark the invoice
     * PAID and emit {@code billing.payment.completed} via the outbox.
     *
     * @param sessionId  the session whose invoice is being paid
     * @param request    method (CASH | QR_CODE, BR-005-1), amount paid, optional note
     * @param operatorId the user confirming payment (received_by)
     * @throws com.smartparking.billing.exception.InvoiceNotFoundException if no invoice exists
     * @throws com.smartparking.billing.exception.InvalidPaymentException  if not PENDING or amount short
     */
    PaymentResponseDTO pay(UUID sessionId, PayRequestDTO request, UUID operatorId);

    /** Driver "my invoices": only invoices whose plate is in the caller's verified {@code plates}. */
    PageResponseDTO<InvoiceResponseDTO> listInvoicesForDriver(List<String> plates, InvoiceStatus status,
                                                              int page, int size);

    /**
     * Operator/admin invoice list with optional filters (status, plate substring, exit-date) and
     * pagination, newest first. {@code null}/blank filters are ignored.
     *
     * @param date if non-null, only invoices whose exit falls on this date (configured zone)
     */
    PageResponseDTO<InvoiceResponseDTO> listInvoices(InvoiceStatus status, String plate,
                                                     java.time.LocalDate date, int page, int size);

    /**
     * Driver invoice detail by invoice id, scoped to the caller's plates.
     *
     * @throws com.smartparking.billing.exception.InvoiceNotFoundException if the invoice is unknown
     * @throws com.smartparking.billing.exception.ForbiddenException       if the plate is not the caller's
     */
    InvoiceResponseDTO getInvoiceForDriver(UUID invoiceId, List<String> plates);

    /**
     * Driver self-service online payment (ADR-010): record an ONLINE {@code payment}
     * (payer_type=DRIVER), mark the invoice PAID and emit {@code billing.payment.completed}.
     * Pays the full invoice amount.
     *
     * @throws com.smartparking.billing.exception.InvoiceNotFoundException if the invoice is unknown
     * @throws com.smartparking.billing.exception.ForbiddenException       if the plate is not the caller's
     * @throws com.smartparking.billing.exception.InvalidPaymentException  if the invoice is not PENDING
     */
    DriverPaymentResponseDTO payByDriver(UUID invoiceId, UUID driverId, List<String> plates);

    /**
     * Gate self-payment via MoMo: create a MoMo payment for the session's PENDING invoice and return
     * the {@code payUrl} / {@code qrCodeUrl} for the payer to scan at the exit. Does not change the
     * invoice status — confirmation happens on {@link #checkMoMoPayment(UUID)}.
     *
     * @throws com.smartparking.billing.exception.InvoiceNotFoundException if no invoice exists
     * @throws com.smartparking.billing.exception.InvalidPaymentException  if the invoice is not PENDING
     * @throws com.smartparking.billing.exception.MoMoGatewayException      if the gateway call fails
     */
    MoMoPaymentResponseDTO createMoMoPayment(UUID sessionId);

    /**
     * Reconcile a gate MoMo payment: query MoMo and, if the transaction is paid, record an ONLINE
     * {@code payment} (provider_ref = MoMo transId), mark the invoice PAID and emit
     * {@code billing.payment.completed}. Idempotent — a second call after PAID is a no-op.
     *
     * @throws com.smartparking.billing.exception.InvoiceNotFoundException if no invoice exists
     * @param orderId the order id returned by {@link #createMoMoPayment(UUID)} (must belong to the
     *                session's invoice)
     * @throws com.smartparking.billing.exception.MoMoGatewayException      if the gateway call fails
     */
    MoMoPaymentResponseDTO checkMoMoPayment(UUID sessionId, String orderId);

    /**
     * BR-005-2 (real-time): handle a MoMo IPN callback. Verifies the HMAC signature, and on a
     * successful ({@code resultCode == 0}) callback settles the matching PENDING invoice PAID and
     * emits {@code billing.payment.completed} (parking then opens the exit gate). Idempotent — a
     * repeat callback for an already-PAID invoice is a no-op. Invalid signature → rejected.
     *
     * @param ipn the IPN body MoMo POSTed (orderId carries the invoice id prefix)
     */
    void handleMoMoIpn(com.smartparking.billing.dto.momo.MoMoIpnRequestDTO ipn);

    /**
     * Gate self-payment via PayOS: create a payment link + QR for the session's PENDING invoice.
     * Does not change invoice status — confirmation via {@link #checkPayOsPayment(UUID, long)} or webhook.
     */
    PayOsPaymentResponseDTO createPayOsPayment(UUID sessionId);

    /**
     * Reconcile a PayOS payment: query PayOS and settle invoice PAID when confirmed. Idempotent.
     */
    PayOsPaymentResponseDTO checkPayOsPayment(UUID sessionId, long orderCode);

    /**
     * Handle PayOS webhook (checksum verified). On success ({@code code == "00"}) settles PENDING invoice PAID.
     */
    java.util.Map<String, Object> handlePayOsWebhook(java.util.Map<String, Object> payload);
}
