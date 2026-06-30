package com.smartparking.billing.service.impl;

import com.smartparking.billing.config.MoMoProperties;
import com.smartparking.billing.dto.momo.MoMoCreateResultDTO;
import com.smartparking.billing.dto.momo.MoMoIpnRequestDTO;
import com.smartparking.billing.dto.momo.MoMoQueryResultDTO;
import com.smartparking.billing.exception.MoMoGatewayException;
import com.smartparking.billing.service.MoMoGateway;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * MoMo AIO gateway client. Signs each request with HMAC-SHA256 over the MoMo-mandated field order
 * and calls the sandbox/production endpoints via {@link RestClient}. No extra dependency — uses the
 * JDK {@link Mac} and Spring's built-in HTTP client.
 */
@Slf4j
@Service
public class MoMoGatewayImpl implements MoMoGateway {

    private final MoMoProperties props;
    private final RestClient restClient;

    public MoMoGatewayImpl(MoMoProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().baseUrl(props.getEndpoint()).build();
    }

    @Override
    public MoMoCreateResultDTO createPayment(String orderId, String requestId, long amount,
                                             String orderInfo, String extraData) {
        String data = extraData == null ? "" : extraData;
        // MoMo create signature: fields in this exact alphabetical order (per MoMo AIO spec).
        String raw = "accessKey=" + props.getAccessKey()
                + "&amount=" + amount
                + "&extraData=" + data
                + "&ipnUrl=" + props.getIpnUrl()
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + props.getPartnerCode()
                + "&redirectUrl=" + props.getRedirectUrl()
                + "&requestId=" + requestId
                + "&requestType=" + props.getRequestType();
        String signature = hmacSha256(raw);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", props.getPartnerCode());
        body.put("accessKey", props.getAccessKey());
        body.put("requestId", requestId);
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", props.getRedirectUrl());
        body.put("ipnUrl", props.getIpnUrl());
        body.put("extraData", data);
        body.put("requestType", props.getRequestType());
        body.put("signature", signature);
        body.put("lang", "vi");

        MoMoCreateResultDTO result = post(props.getCreatePath(), body, MoMoCreateResultDTO.class);
        if (result.resultCode() == null || result.resultCode() != 0) {
            throw new MoMoGatewayException("MoMo create failed: resultCode="
                    + (result.resultCode()) + " message=" + result.message());
        }
        log.info("MoMo payment created: orderId={}, payUrl present={}, qr present={}",
                orderId, result.payUrl() != null, result.qrCodeUrl() != null);
        return result;
    }

    @Override
    public MoMoQueryResultDTO queryPayment(String orderId, String requestId) {
        // MoMo query signature order.
        String raw = "accessKey=" + props.getAccessKey()
                + "&orderId=" + orderId
                + "&partnerCode=" + props.getPartnerCode()
                + "&requestId=" + requestId;
        String signature = hmacSha256(raw);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", props.getPartnerCode());
        body.put("accessKey", props.getAccessKey());
        body.put("requestId", requestId);
        body.put("orderId", orderId);
        body.put("signature", signature);
        body.put("lang", "vi");

        return post(props.getQueryPath(), body, MoMoQueryResultDTO.class);
    }

    @Override
    public boolean verifyIpn(MoMoIpnRequestDTO ipn) {
        // MoMo IPN signature: fields in this exact alphabetical order (per MoMo AIO spec). accessKey is
        // NOT sent in the IPN body — it comes from our partner config.
        String raw = "accessKey=" + props.getAccessKey()
                + "&amount=" + num(ipn.amount())
                + "&extraData=" + str(ipn.extraData())
                + "&message=" + str(ipn.message())
                + "&orderId=" + str(ipn.orderId())
                + "&orderInfo=" + str(ipn.orderInfo())
                + "&orderType=" + str(ipn.orderType())
                + "&partnerCode=" + str(ipn.partnerCode())
                + "&payType=" + str(ipn.payType())
                + "&requestId=" + str(ipn.requestId())
                + "&responseTime=" + num(ipn.responseTime())
                + "&resultCode=" + (ipn.resultCode() == null ? "" : ipn.resultCode())
                + "&transId=" + num(ipn.transId());
        String expected = hmacSha256(raw);
        boolean ok = ipn.signature() != null && expected.equalsIgnoreCase(ipn.signature());
        if (!ok) {
            log.warn("MoMo IPN signature mismatch for orderId={}", ipn.orderId());
        }
        return ok;
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }

    private static String num(Long v) {
        return v == null ? "" : String.valueOf(v);
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> type) {
        try {
            T result = restClient.post().uri(path).body(body).retrieve().body(type);
            if (result == null) {
                throw new MoMoGatewayException("MoMo returned an empty body for " + path);
            }
            return result;
        } catch (RestClientException ex) {
            throw new MoMoGatewayException("MoMo call failed for " + path + ": " + ex.getMessage(), ex);
        }
    }

    /** Hex HMAC-SHA256 of {@code raw} keyed by the partner secret. */
    private String hmacSha256(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new MoMoGatewayException("Failed to sign MoMo request", ex);
        }
    }
}
