package com.smartparking.billing.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.billing.dto.event.PaymentCompletedEventDTO;
import com.smartparking.billing.dto.event.SessionClosedEventDTO;
import com.smartparking.billing.dto.momo.MoMoCreateResultDTO;
import com.smartparking.billing.dto.momo.MoMoIpnRequestDTO;
import com.smartparking.billing.dto.momo.MoMoQueryResultDTO;
import com.smartparking.billing.dto.request.PayRequestDTO;
import com.smartparking.billing.dto.response.DriverPaymentResponseDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.dto.response.MoMoPaymentResponseDTO;
import com.smartparking.billing.dto.response.PayOsPaymentResponseDTO;
import com.smartparking.billing.dto.response.PageResponseDTO;
import com.smartparking.billing.dto.response.PaymentResponseDTO;
import com.smartparking.billing.entity.Invoice;
import com.smartparking.billing.entity.OutboxEvent;
import com.smartparking.billing.entity.Payment;
import com.smartparking.billing.entity.Rate;
import com.smartparking.billing.entity.RateSchedule;
import com.smartparking.billing.entity.enums.InvoiceStatus;
import com.smartparking.billing.entity.enums.PayerType;
import com.smartparking.billing.entity.enums.PaymentMethod;
import com.smartparking.billing.exception.ForbiddenException;
import com.smartparking.billing.exception.InvalidPaymentException;
import com.smartparking.billing.exception.InvoiceNotFoundException;
import com.smartparking.billing.exception.RateNotFoundException;
import com.smartparking.billing.mapper.InvoiceMapper;
import com.smartparking.billing.repository.InvoiceRepository;
import com.smartparking.billing.repository.OutboxEventRepository;
import com.smartparking.billing.repository.PaymentRepository;
import com.smartparking.billing.repository.RateRepository;
import com.smartparking.billing.repository.RateScheduleRepository;
import com.smartparking.billing.service.BillingService;
import com.smartparking.billing.service.FeeCalculation;
import com.smartparking.billing.service.FeeCalculator;
import com.smartparking.billing.service.MoMoGateway;
import com.smartparking.billing.service.PayOsGateway;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.webhooks.WebhookData;

/**
 * Calculates a parking fee when a session closes and records a PENDING invoice + outbox event.
 *
 * <p>Idempotent on {@code sessionId} (BR-004-6): a duplicate {@code parking.session.closed} is
 * logged and skipped. Missing rate config throws → retried → DLT (BR-007-1 alert).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final RateRepository rateRepository;
    private final RateScheduleRepository rateScheduleRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final FeeCalculator feeCalculator;
    private final InvoiceMapper invoiceMapper;
    private final ObjectMapper objectMapper;
    private final MoMoGateway moMoGateway;
    private final PayOsGateway payOsGateway;

    @Value("${app.kafka.topics.invoice-calculated}")
    private String invoiceCalculatedTopic;

    @Value("${app.kafka.topics.payment-completed}")
    private String paymentCompletedTopic;

    @Value("${app.billing.zone-id}")
    private String zoneId;

    @Override
    @Transactional
    public void handleSessionClosed(SessionClosedEventDTO event) {
        log.info("session.closed received: eventId={}, sessionId={}, plate={}",
                event.eventId(), event.sessionId(), event.plateNumber());

        // BR-004-6: one invoice per session — duplicate event is a no-op.
        if (invoiceRepository.existsBySessionId(event.sessionId())) {
            log.warn("BR-004-6: invoice already exists for sessionId={}, skipping", event.sessionId());
            return;
        }

        // BR-004-5: price at exit time, using the rate effective then.
        Rate rate = findEffectiveRate(event);
        // BR-004-7: peak windows from rate_schedules (config-driven, not hardcoded).
        List<RateSchedule> peakWindows = rateScheduleRepository.findByRateIdOrderByHourStartAsc(rate.getId());

        FeeCalculation fee = feeCalculator.calculate(event.entryTime(), event.exitTime(), rate, peakWindows);

        // BR-005-4: whitelist vehicle exits free — keep the computed duration/rate for traceability
        // but waive the charge (amount 0, status WAIVED) so reports never count uncollected money.
        boolean waived = event.whitelisted();
        BigDecimal amount = waived ? BigDecimal.ZERO : fee.amount();
        InvoiceStatus status = waived ? InvoiceStatus.WAIVED : InvoiceStatus.PENDING;

        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .sessionId(event.sessionId())
                .plateNumber(event.plateNumber())
                .rateId(rate.getId())
                .entryTime(event.entryTime())
                .exitTime(event.exitTime())
                .durationSeconds(fee.durationSeconds())
                .durationMinutes(fee.durationMinutes())
                .ratePerMin(fee.ratePerMin())
                .peakApplied(fee.peakApplied())
                .overnightApplied(fee.overnightApplied())
                .amount(amount)
                .status(status)
                // BR-004 breakdown + the tariff it was priced with, so the receipt stays
                // explainable after the rate table moves on.
                .blockMinutes(fee.blockMinutes())
                .normalBlocks(fee.normalBlocks())
                .peakBlocks(fee.peakBlocks())
                .overnightNights(fee.overnightNights())
                .peakMultiplier(rate.getPeakMultiplier())
                .overnightFlat(rate.getOvernightFlat())
                .minChargeApplied(fee.minChargeApplied())
                .build());

        recordOutbox(invoiceCalculatedTopic, "Invoice", invoice.getSessionId(),
                invoiceMapper.toInvoiceCalculatedEvent(invoice).toBuilder()
                        .eventId(UUID.randomUUID().toString())
                        .build());

        log.info("Invoice {}: invoiceId={}, sessionId={}, amount={}, peak={}, overnight={}",
                waived ? "WAIVED (whitelist)" : "calculated",
                invoice.getId(), invoice.getSessionId(), invoice.getAmount(),
                invoice.isPeakApplied(), invoice.isOvernightApplied());
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDTO getInvoiceBySession(UUID sessionId) {
        Invoice invoice = invoiceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "No invoice for sessionId=" + sessionId));
        return invoiceMapper.toInvoiceResponse(invoice, rateAppliedTo(invoice));
    }

    @Override
    @Transactional
    public PaymentResponseDTO pay(UUID sessionId, PayRequestDTO request, UUID operatorId) {
        // BR-005-2: lock the row — an online settlement can land while the operator takes cash.
        Invoice invoice = invoiceRepository.findBySessionIdForUpdate(sessionId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "No invoice for sessionId=" + sessionId));

        // BR-005-2: only a PENDING invoice can be paid.
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new InvalidPaymentException(
                    "Invoice %s is not payable (status=%s)".formatted(invoice.getId(), invoice.getStatus()));
        }
        if (request.amountPaid().compareTo(invoice.getAmount()) < 0) {
            throw new InvalidPaymentException(
                    "Amount paid %s is less than invoice amount %s"
                            .formatted(request.amountPaid(), invoice.getAmount()));
        }
        validateOfflineFields(request, invoice);

        Payment payment = paymentRepository.save(Payment.builder()
                .invoiceId(invoice.getId())
                .method(request.method())
                .amountPaid(request.amountPaid())
                .payerType(PayerType.OPERATOR)
                .receivedBy(operatorId)
                .offlineVoucherNo(request.offlineVoucherNo())
                .paidAt(request.paidAt())          // null -> @PrePersist stamps now (live payments)
                .note(request.note())
                .build());

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        recordOutbox(paymentCompletedTopic, "Payment", invoice.getId(),
                buildPaymentCompleted(invoice, payment));

        // BR-005-9: settling via one channel (here, an operator-recorded CASH or QR_CODE payment)
        // must cancel any PayOS order still outstanding for this invoice, so the driver can't also
        // complete a real transfer on an already-collected invoice.
        cancelOutstandingPayOs(invoice, "Da thanh toan " + payment.getMethod());

        log.info("Payment confirmed: invoiceId={}, sessionId={}, method={}, amountPaid={}, by={}",
                invoice.getId(), sessionId, payment.getMethod(), payment.getAmountPaid(), operatorId);

        return PaymentResponseDTO.builder()
                .invoiceId(invoice.getId())
                .status(invoice.getStatus())
                .paidAt(payment.getPaidAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<InvoiceResponseDTO> listInvoicesForDriver(List<String> plates,
                                                                     InvoiceStatus status, int page, int size) {
        List<String> normalized = normalizePlates(plates);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (normalized.isEmpty()) {
            // No verified plates yet -> nothing to show (and avoids an `IN ()` query).
            return new PageResponseDTO<>(List.of(), 0, 0, page, size);
        }
        Page<Invoice> result = invoiceRepository.searchByPlates(normalized, status, pageable);
        List<InvoiceResponseDTO> content = result.getContent().stream()
                .map(invoiceMapper::toInvoiceResponse).toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<InvoiceResponseDTO> listInvoices(InvoiceStatus status, String plate,
                                                            java.time.LocalDate date, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        // Always pass concrete values ("" = all plates, wide range = all dates) so the typed binds
        // don't trip Postgres null-type inference; only status remains nullable.
        String plateFilter = (plate == null || plate.isBlank()) ? "" : plate.strip();
        java.time.OffsetDateTime from = java.time.OffsetDateTime.parse("1970-01-01T00:00:00Z");
        java.time.OffsetDateTime to = java.time.OffsetDateTime.parse("9999-01-01T00:00:00Z");
        if (date != null) {
            java.time.ZoneId zone = java.time.ZoneId.of(zoneId);
            from = date.atStartOfDay(zone).toOffsetDateTime();
            to = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        }
        Page<Invoice> result = invoiceRepository.search(status, plateFilter, from, to, pageable);
        List<InvoiceResponseDTO> content = result.getContent().stream()
                .map(invoiceMapper::toInvoiceResponse).toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDTO getInvoiceForDriver(UUID invoiceId, List<String> plates) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("No invoice with id=" + invoiceId));
        requireOwnership(invoice, plates);
        return invoiceMapper.toInvoiceResponse(invoice, rateAppliedTo(invoice));
    }

    @Override
    @Transactional
    public DriverPaymentResponseDTO payByDriver(UUID invoiceId, UUID driverId, List<String> plates) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("No invoice with id=" + invoiceId));
        requireOwnership(invoice, plates);

        // BR-005-2: only a PENDING invoice can be paid.
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new InvalidPaymentException(
                    "Invoice %s is not payable (status=%s)".formatted(invoice.getId(), invoice.getStatus()));
        }

        // Online self-pay covers the full invoice amount. Gateway integration is out of scope (ADR-010):
        // the mock marks it PAID immediately with no provider_ref.
        Payment payment = paymentRepository.save(Payment.builder()
                .invoiceId(invoice.getId())
                .method(PaymentMethod.ONLINE)
                .amountPaid(invoice.getAmount())
                .payerType(PayerType.DRIVER)
                .driverId(driverId)
                .note("Online payment by driver")
                .build());

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        recordOutbox(paymentCompletedTopic, "Payment", invoice.getId(),
                buildPaymentCompleted(invoice, payment));

        // BR-005-9: this app-side self-pay is a distinct channel from a live PayOS QR — cancel any
        // outstanding one so the driver can't also complete that transfer on an already-paid invoice.
        cancelOutstandingPayOs(invoice, "Da thanh toan qua app");

        log.info("Driver online payment: invoiceId={}, sessionId={}, amount={}, driverId={}",
                invoice.getId(), invoice.getSessionId(), payment.getAmountPaid(), driverId);

        return DriverPaymentResponseDTO.builder()
                .paymentId(payment.getId())
                .invoiceId(invoice.getId())
                .status(invoice.getStatus())
                .method(payment.getMethod())
                .amountPaid(payment.getAmountPaid())
                .qrData(null)
                .paidAt(payment.getPaidAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MoMoPaymentResponseDTO createMoMoPayment(UUID sessionId) {
        Invoice invoice = invoiceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new InvoiceNotFoundException("No invoice for sessionId=" + sessionId));

        // BR-005-2: only a PENDING invoice can be paid.
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new InvalidPaymentException(
                    "Invoice %s is not payable (status=%s)".formatted(invoice.getId(), invoice.getStatus()));
        }

        // Unique per attempt (invoiceId + timestamp) so re-creating a QR never hits MoMo's
        // "duplicate orderId". The caller passes this orderId back to /momo/status to query — keeps
        // the flow stateless (no extra table/column). orderId always starts with the invoice id.
        String orderId = invoice.getId() + "-" + System.currentTimeMillis();
        long amount = invoice.getAmount().longValue();
        String orderInfo = "Thanh toan gui xe " + invoice.getPlateNumber();
        MoMoCreateResultDTO momo = moMoGateway.createPayment(orderId, orderId, amount, orderInfo, "");

        log.info("MoMo gate payment created: sessionId={}, invoiceId={}, amount={}", sessionId,
                invoice.getId(), amount);
        return MoMoPaymentResponseDTO.builder()
                .sessionId(sessionId)
                .invoiceId(invoice.getId())
                .amount(invoice.getAmount())
                .orderId(orderId)
                .payUrl(momo.payUrl())
                .qrCodeUrl(momo.qrCodeUrl())
                .deeplink(momo.deeplink())
                .status(invoice.getStatus())
                .message(momo.message())
                .build();
    }

    @Override
    @Transactional
    public MoMoPaymentResponseDTO checkMoMoPayment(UUID sessionId, String orderId) {
        Invoice invoice = invoiceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new InvoiceNotFoundException("No invoice for sessionId=" + sessionId));

        // Idempotent: already settled -> just report (no duplicate payment / event).
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            return momoStatus(invoice, orderId, "Invoice already " + invoice.getStatus());
        }
        // The orderId must be one issued for this invoice (createMoMoPayment prefixes it with the id).
        if (orderId == null || !orderId.startsWith(invoice.getId().toString())) {
            throw new InvalidPaymentException(
                    "orderId %s does not match invoice %s".formatted(orderId, invoice.getId()));
        }

        MoMoQueryResultDTO query = moMoGateway.queryPayment(orderId, UUID.randomUUID().toString());
        if (!query.isPaid()) {
            return momoStatus(invoice, orderId, "Not paid yet (MoMo: " + query.message() + ")");
        }

        settleOnlinePaid(invoice, query.transId() == null ? null : String.valueOf(query.transId()),
                "MoMo gate payment, orderId=" + orderId, false);
        log.info("MoMo payment reconciled PAID (poll): invoiceId={}, sessionId={}, transId={}",
                invoice.getId(), sessionId, query.transId());
        return momoStatus(invoice, orderId, "PAID");
    }

    @Override
    @Transactional
    public PayOsPaymentResponseDTO createPayOsPayment(UUID sessionId) {
        Invoice invoice = invoiceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new InvoiceNotFoundException("No invoice for sessionId=" + sessionId));

        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new InvalidPaymentException(
                    "Invoice %s is not payable (status=%s)".formatted(invoice.getId(), invoice.getStatus()));
        }

        long orderCode = generateUniquePayOsOrderCode();
        long amount = invoice.getAmount().longValue();
        if (amount < 1000) {
            throw new InvalidPaymentException("PayOS minimum amount is 1000 VND");
        }
        String description = "Phi gui xe " + invoice.getPlateNumber();
        var payos = payOsGateway.createPayment(orderCode, amount, description);

        invoice.setPayosOrderCode(orderCode);
        invoiceRepository.save(invoice);

        log.info("PayOS gate payment created: sessionId={}, invoiceId={}, orderCode={}, amount={}",
                sessionId, invoice.getId(), orderCode, amount);
        return PayOsPaymentResponseDTO.builder()
                .sessionId(sessionId)
                .invoiceId(invoice.getId())
                .amount(invoice.getAmount())
                .orderCode(String.valueOf(orderCode))
                .checkoutUrl(payos.checkoutUrl())
                .qrCode(payos.qrCode())
                .status(invoice.getStatus())
                .message("PayOS payment link created")
                .build();
    }

    @Override
    @Transactional
    public PayOsPaymentResponseDTO checkPayOsPayment(UUID sessionId, long orderCode) {
        Invoice invoice = invoiceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new InvoiceNotFoundException("No invoice for sessionId=" + sessionId));

        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            return payosStatus(invoice, orderCode, "Invoice already " + invoice.getStatus());
        }
        if (invoice.getPayosOrderCode() == null || !invoice.getPayosOrderCode().equals(orderCode)) {
            throw new InvalidPaymentException(
                    "orderCode %s does not match invoice %s".formatted(orderCode, invoice.getId()));
        }

        if (!payOsGateway.isPaid(orderCode)) {
            return payosStatus(invoice, orderCode, "Not paid yet (PayOS)");
        }

        settleOnlinePaid(invoice, payOsGateway.providerRef(orderCode),
                "PayOS gate payment, orderCode=" + orderCode, true);
        log.info("PayOS payment reconciled PAID (poll): invoiceId={}, sessionId={}, orderCode={}",
                invoice.getId(), sessionId, orderCode);
        return payosStatus(invoice, orderCode, "PAID");
    }

    @Override
    @Transactional
    public Map<String, Object> handlePayOsWebhook(Map<String, Object> payload) {
        WebhookData verified = payOsGateway.verifyWebhook(payload);
        if (verified == null) {
            log.warn("PayOS webhook signature verification failed");
            return webhookAck("signature verification failed");
        }

        String code = payload.get("code") == null ? "" : String.valueOf(payload.get("code"));
        if (code.isBlank() && verified.getCode() != null) {
            code = String.valueOf(verified.getCode());
        }
        Long orderCode = verified.getOrderCode();
        if (orderCode == null) {
            return webhookAck("orderCode missing");
        }

        Invoice invoice = invoiceRepository.findByPayosOrderCodeForUpdate(orderCode).orElse(null);
        if (invoice == null) {
            log.warn("PayOS webhook for unknown orderCode={}, ignored", orderCode);
            return webhookAck("invoice not found");
        }
        if (!"00".equals(code)) {
            log.info("PayOS webhook non-success code={} for invoice {}", code, invoice.getId());
            return webhookAck("payment not successful");
        }
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            log.info("PayOS webhook: invoice {} already {} — idempotent no-op",
                    invoice.getId(), invoice.getStatus());
            return webhookAck("already processed");
        }

        String providerRef = verified.getPaymentLinkId() != null
                ? verified.getPaymentLinkId()
                : String.valueOf(orderCode);
        settleOnlinePaid(invoice, providerRef, "PayOS webhook, orderCode=" + orderCode, true);
        log.info("PayOS webhook settled PAID: invoiceId={}, sessionId={}, orderCode={}",
                invoice.getId(), invoice.getSessionId(), orderCode);
        return webhookAck("success");
    }

    @Override
    @Transactional
    public void handleMoMoIpn(MoMoIpnRequestDTO ipn) {
        // BR-005-2 (real-time): MoMo POSTs this when a payment settles. Authenticate via HMAC before
        // trusting it, then settle the invoice PAID + emit payment.completed (parking opens the gate).
        if (!moMoGateway.verifyIpn(ipn)) {
            throw new InvalidPaymentException("Invalid MoMo IPN signature for orderId=" + ipn.orderId());
        }
        String orderId = ipn.orderId();
        if (orderId == null || orderId.length() < 36) {
            log.warn("MoMo IPN with malformed orderId={}, ignored", orderId);
            return;
        }
        UUID invoiceId;
        try {
            invoiceId = UUID.fromString(orderId.substring(0, 36));  // orderId = invoiceId + "-" + ts
        } catch (IllegalArgumentException ex) {
            log.warn("MoMo IPN orderId has no invoice-id prefix: {}", orderId);
            return;
        }
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElse(null);
        if (invoice == null) {
            log.warn("MoMo IPN for unknown invoice {} (orderId={}), ignored", invoiceId, orderId);
            return;
        }
        if (ipn.resultCode() == null || ipn.resultCode() != 0) {
            log.info("MoMo IPN non-success resultCode={} for invoice {} — no settlement",
                    ipn.resultCode(), invoiceId);
            return;
        }
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            log.info("MoMo IPN: invoice {} already {} — idempotent no-op", invoiceId, invoice.getStatus());
            return;
        }
        settleOnlinePaid(invoice, ipn.transId() == null ? null : String.valueOf(ipn.transId()),
                "MoMo IPN, orderId=" + orderId, false);
        log.info("MoMo IPN settled PAID (real-time push): invoiceId={}, sessionId={}, transId={}",
                invoiceId, invoice.getSessionId(), ipn.transId());
    }

    /**
     * Mark a PENDING invoice PAID via online gateway + emit payment.completed (MoMo/PayOS).
     *
     * @param viaPayOs true when PayOS itself is the settling channel (checkPayOsPayment/webhook) —
     *                 in that case the invoice's own {@code payosOrderCode} was just paid, so it must
     *                 NOT be cancelled. When false (MoMo poll/IPN), any outstanding PayOS order for
     *                 this invoice is cancelled (BR-005-9) since the other channel already paid.
     */
    private void settleOnlinePaid(Invoice invoice, String providerRef, String note, boolean viaPayOs) {
        Invoice locked = invoiceRepository.findByIdForUpdate(invoice.getId()).orElse(null);
        if (locked == null) {
            log.warn("settleOnlinePaid: invoice {} not found", invoice.getId());
            return;
        }
        if (locked.getStatus() != InvoiceStatus.PENDING) {
            log.info("settleOnlinePaid: invoice {} already {} — idempotent no-op",
                    locked.getId(), locked.getStatus());
            return;
        }
        Payment payment = paymentRepository.save(Payment.builder()
                .invoiceId(locked.getId())
                .method(PaymentMethod.ONLINE)
                .amountPaid(locked.getAmount())
                .payerType(PayerType.DRIVER)
                .providerRef(providerRef)
                .note(note)
                .build());
        locked.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(locked);
        recordOutbox(paymentCompletedTopic, "Payment", locked.getId(),
                buildPaymentCompleted(locked, payment));

        if (!viaPayOs) {
            cancelOutstandingPayOs(locked, "Da thanh toan qua MoMo");
        }
    }

    /** BR-005-9: cancel the invoice's outstanding PayOS order, if any — best-effort, never throws. */
    private void cancelOutstandingPayOs(Invoice invoice, String reason) {
        if (invoice.getPayosOrderCode() != null) {
            payOsGateway.cancelPayment(invoice.getPayosOrderCode(), reason);
        }
    }

    private MoMoPaymentResponseDTO momoStatus(Invoice invoice, String orderId, String message) {
        return MoMoPaymentResponseDTO.builder()
                .sessionId(invoice.getSessionId())
                .invoiceId(invoice.getId())
                .amount(invoice.getAmount())
                .orderId(orderId)
                .status(invoice.getStatus())
                .message(message)
                .build();
    }

    private PayOsPaymentResponseDTO payosStatus(Invoice invoice, long orderCode, String message) {
        return PayOsPaymentResponseDTO.builder()
                .sessionId(invoice.getSessionId())
                .invoiceId(invoice.getId())
                .amount(invoice.getAmount())
                .orderCode(String.valueOf(orderCode))
                .status(invoice.getStatus())
                .message(message)
                .build();
    }

    private long generateUniquePayOsOrderCode() {
        long orderCode = System.currentTimeMillis() / 1000;
        int attempts = 0;
        while (invoiceRepository.existsByPayosOrderCode(orderCode)) {
            orderCode++;
            attempts++;
            if (attempts > 10_000) {
                throw new InvalidPaymentException("Cannot generate unique PayOS orderCode");
            }
        }
        return orderCode;
    }

    private static Map<String, Object> webhookAck(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "00");
        body.put("message", message);
        return body;
    }

    private void requireOwnership(Invoice invoice, List<String> plates) {
        boolean owned = normalizePlates(plates).stream().anyMatch(p -> p.equals(invoice.getPlateNumber()));
        if (!owned) {
            throw new ForbiddenException("Invoice does not belong to the authenticated driver");
        }
    }

    /** BR-001-4 parity: uppercase + strip whitespace so claim plates match stored invoice plates. */
    private static List<String> normalizePlates(List<String> plates) {
        return plates.stream()
                .filter(Objects::nonNull)
                .map(p -> p.replaceAll("\\s+", "").toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private PaymentCompletedEventDTO buildPaymentCompleted(Invoice invoice, Payment payment) {
        return PaymentCompletedEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .paymentId(payment.getId())
                .invoiceId(invoice.getId())
                .sessionId(invoice.getSessionId())
                .plateNumber(invoice.getPlateNumber())
                .amountPaid(payment.getAmountPaid())
                .method(payment.getMethod())
                .status(invoice.getStatus())
                .paidAt(payment.getPaidAt())
                .build();
    }

    /**
     * BR-005-7: the outage-only fields belong to CASH_OFFLINE and nothing else. Live payments are
     * stamped by the server, so accepting a caller-supplied {@code paidAt} there would let an
     * operator backdate takings; and a CASH_OFFLINE row without a voucher serial cannot be
     * reconciled against the paper book, which is the only control on outage cash.
     */
    private void validateOfflineFields(PayRequestDTO request, Invoice invoice) {
        boolean offline = request.method() == PaymentMethod.CASH_OFFLINE;
        String voucher = request.offlineVoucherNo();

        if (!offline) {
            if (voucher != null || request.paidAt() != null) {
                throw new InvalidPaymentException(
                        "offlineVoucherNo/paidAt are only accepted for CASH_OFFLINE (method=%s)"
                                .formatted(request.method()));
            }
            return;
        }

        if (voucher == null || voucher.isBlank()) {
            throw new InvalidPaymentException("CASH_OFFLINE requires the paper voucher serial");
        }
        if (request.paidAt() == null) {
            throw new InvalidPaymentException(
                    "CASH_OFFLINE requires paidAt — the time the cash was actually taken");
        }
        if (request.paidAt().isAfter(OffsetDateTime.now())) {
            throw new InvalidPaymentException("paidAt is in the future: " + request.paidAt());
        }
        // The driver cannot have paid before their car left the barrier.
        if (request.paidAt().isBefore(invoice.getExitTime())) {
            throw new InvalidPaymentException(
                    "paidAt %s is before the session exit time %s"
                            .formatted(request.paidAt(), invoice.getExitTime()));
        }
    }

    /** The exact {@link Rate} that priced this invoice (BR-004-5 traceability) — never the current one. */
    private Rate rateAppliedTo(Invoice invoice) {
        return rateRepository.findById(invoice.getRateId())
                .orElseThrow(() -> new RateNotFoundException(
                        "Rate %s referenced by invoice %s not found".formatted(
                                invoice.getRateId(), invoice.getId())));
    }

    private Rate findEffectiveRate(SessionClosedEventDTO event) {
        List<Rate> rates = rateRepository.findEffective(event.exitTime(), PageRequest.of(0, 1));
        if (rates.isEmpty()) {
            throw new RateNotFoundException(
                    "No effective rate at %s for sessionId=%s".formatted(event.exitTime(), event.sessionId()));
        }
        return rates.get(0);
    }

    /** Stage an outbox event in the current transaction. {@code aggregateId} becomes the Kafka key. */
    private void recordOutbox(String eventType, String aggregateType, UUID aggregateId, Object payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(serialize(payload))
                .build());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
