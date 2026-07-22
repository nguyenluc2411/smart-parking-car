package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;

/**
 * Money actually collected in a period, split by where it landed.
 *
 * <p><b>This is not the same number as {@code totalRevenue}</b> on the surrounding report, and the
 * two are meant to be compared rather than reconciled away:
 * <ul>
 *   <li>{@code totalRevenue} is what was <em>billed</em> — invoice amounts for sessions that exited
 *       in the period, including invoices still PENDING.</li>
 *   <li>{@code CollectionSummaryDTO} is what was <em>taken</em> — payments stamped in the period,
 *       including cash from an outage on an earlier day (BR-005-7).</li>
 * </ul>
 * A gap between them is the point: it is unpaid exits, or takings not yet keyed in.
 *
 * <p>{@code cashTotal} is the figure an operator's till is counted against; {@code gatewayTotal}
 * is money that arrived at MoMo/PayOS and never passed through anyone's hands.
 */
public record CollectionSummaryDTO(
        /** CASH + CASH_OFFLINE — physical notes someone is accountable for. */
        BigDecimal cashTotal,
        /** QR_CODE + ONLINE — settled by the payment gateway. */
        BigDecimal gatewayTotal,
        BigDecimal total,
        List<MethodAmount> byMethod
) {
    public record MethodAmount(PaymentMethod method, BigDecimal amount, long count) {
    }
}
