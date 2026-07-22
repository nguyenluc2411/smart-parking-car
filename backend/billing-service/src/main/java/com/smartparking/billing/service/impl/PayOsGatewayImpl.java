package com.smartparking.billing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.billing.config.PayOsProperties;
import com.smartparking.billing.dto.payos.PayOsCreateResultDTO;
import com.smartparking.billing.exception.PayOsGatewayException;
import com.smartparking.billing.service.PayOsGateway;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.exception.APIException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.WebhookData;

@Slf4j
@Service
public class PayOsGatewayImpl implements PayOsGateway {

    private final PayOsProperties props;
    private final ObjectProvider<PayOS> payOSProvider;
    private final ObjectMapper objectMapper;

    public PayOsGatewayImpl(PayOsProperties props, ObjectProvider<PayOS> payOSProvider,
                              ObjectMapper objectMapper) {
        this.props = props;
        this.payOSProvider = payOSProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public PayOsCreateResultDTO createPayment(long orderCode, long amount, String description) {
        PayOS payOS = requireClient();
        try {
            PaymentLinkItem item = PaymentLinkItem.builder()
                    .name(truncate(description, 25))
                    .quantity(1)
                    .price(amount)
                    .build();
            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount)
                    .description(truncate(description, 25))
                    .item(item)
                    .returnUrl(props.getReturnUrl())
                    .cancelUrl(props.getCancelUrl())
                    .build();
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);
            String checkoutUrl = response.getCheckoutUrl();
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                throw new PayOsGatewayException("PayOS did not return checkoutUrl for orderCode=" + orderCode);
            }
            log.info("PayOS payment created: orderCode={}, qr present={}", orderCode, response.getQrCode() != null);
            return PayOsCreateResultDTO.builder()
                    .orderCode(orderCode)
                    .checkoutUrl(checkoutUrl)
                    .qrCode(response.getQrCode())
                    .paymentLinkId(response.getPaymentLinkId())
                    .build();
        } catch (APIException ex) {
            throw new PayOsGatewayException("PayOS create failed: " + ex.getMessage(), ex);
        } catch (PayOsGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PayOsGatewayException("PayOS create failed for orderCode=" + orderCode, ex);
        }
    }

    @Override
    public boolean isPaid(long orderCode) {
        return "PAID".equalsIgnoreCase(extractStatus(fetchPayment(orderCode)));
    }

    @Override
    public String providerRef(long orderCode) {
        Map<String, Object> data = fetchPayment(orderCode);
        if (data == null) {
            return null;
        }
        Object id = data.get("paymentLinkId");
        if (id == null) {
            id = data.get("id");
        }
        return id == null ? String.valueOf(orderCode) : String.valueOf(id);
    }

    @Override
    public void cancelPayment(long orderCode, String reason) {
        PayOS payOS = payOSProvider.getIfAvailable();
        if (payOS == null) {
            log.warn("PayOS cancel skipped for orderCode={}: client not configured", orderCode);
            return;
        }
        try {
            payOS.paymentRequests().cancel(orderCode, reason);
            log.info("PayOS payment link cancelled: orderCode={}, reason={}", orderCode, reason);
        } catch (Exception ex) {
            // Best-effort (BR-005-9): the invoice is already settled via another channel either
            // way, so a cancel failure (already paid/expired/cancelled at PayOS) must not surface.
            log.warn("PayOS cancel failed for orderCode={}: {}", orderCode, ex.getMessage());
        }
    }

    @Override
    public WebhookData verifyWebhook(Map<String, Object> payload) {
        PayOS payOS = payOSProvider.getIfAvailable();
        if (payOS == null) {
            return null;
        }
        try {
            return payOS.webhooks().verify(payload);
        } catch (Exception ex) {
            log.warn("PayOS webhook verification failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> fetchPayment(long orderCode) {
        PayOS payOS = requireClient();
        try {
            Object paymentLink = payOS.paymentRequests().get(orderCode);
            return objectMapper.convertValue(paymentLink, Map.class);
        } catch (Exception ex) {
            throw new PayOsGatewayException("PayOS query failed for orderCode=" + orderCode, ex);
        }
    }

    private static String extractStatus(Map<String, Object> paymentData) {
        if (paymentData == null) {
            return "";
        }
        for (String key : List.of("status", "paymentStatus", "payment_status")) {
            Object value = paymentData.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private PayOS requireClient() {
        if (isBlank(props.getClientId()) || isBlank(props.getApiKey()) || isBlank(props.getChecksumKey())) {
            throw new PayOsGatewayException("PayOS is not configured (client id / api key / checksum key)");
        }
        PayOS payOS = payOSProvider.getIfAvailable();
        if (payOS == null) {
            throw new PayOsGatewayException("PayOS client bean is not available");
        }
        return payOS;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
