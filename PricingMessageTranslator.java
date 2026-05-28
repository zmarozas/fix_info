package com.barcap.eq.oms.optionpricergateway.server.bifrost;

import com.barclays.eq.roe.optionpricer.IOptionPricingSubscriptionRequestMessage;
import com.barclays.eq.roe.optionpricer.IOptionPricingSnapshotMessage;
import com.barclays.eq.roe.optionpricer.OptionPricerFactory;
// ROE enums - exact import paths to confirm when compiling
import com.barclays.eq.roe.common.PutOrCall;
import com.barclays.eq.roe.common.ExerciseType;
import com.barclays.eq.roe.common.SettlementCode;

// BiFrost protobuf - placeholder paths until risk-bo resolves in Nexus
import com.barcap.flow.risk.proto.PricingRequest;
import com.barcap.flow.risk.proto.ProductData;
import com.barcap.flow.risk.proto.RiskProfile;
import com.barcap.flow.risk.proto.FirstOrdGreeks;
import com.barcap.flow.risk.proto.SecondOrdGreeks;
import com.barcap.flow.risk.proto.OptionType;
import com.barcap.flow.risk.proto.OptionStyle;
import com.barcap.flow.risk.proto.SettlementType;
import com.barcap.flow.risk.proto.Date; // BiFrost {timestamp, tz} type

import javax.inject.Singleton;

/**
 * Pure translator between Blackbird Trading ROE messages and BiFrost protobuf messages.
 * No I/O, no state, no clock — all side effects and non-determinism are the caller's job.
 * Fully unit-testable in isolation.
 */
@Singleton
public final class PricingMessageTranslator {

    // TODO confirm w/ Bapu — fixed for all underlyings, or instrument-dependent?
    private static final String DEFAULT_TIMEZONE = "America/New_York";

    /**
     * ROE subscription request → BiFrost PricingRequest.
     * Caller supplies correlationId, productId (encodes RIC for stateless routing), and underlyingName.
     */
    public PricingRequest toBifrostRequest(IOptionPricingSubscriptionRequestMessage req,
                                           String correlationId,
                                           String productId,
                                           String underlyingName) {
        ProductData product = ProductData.newBuilder()
                .setProductId(productId)
                .setUnderlyingName(underlyingName)
                .setUnderlyingRic(req.getUnderlyingRIC())
                .setStrike(req.getOptionStrikePrice())
                .setExpiry(toBifrostDate(req.getExpireDateAsTimestamp()))
                .setOptionType(toBifrostOptionType(req.getPutOrCall()))
                .setOptionStyle(toBifrostOptionStyle(req.getExerciseType()))
                .setSettlementType(toBifrostSettlementType(req.getSettlementCode()))
                .build();

        return PricingRequest.newBuilder()
                .setCorrelationId(correlationId)
                .setProductData(product)
                .build();
    }

    /**
     * BiFrost RiskProfile → ROE snapshot. Used for both initial PricingResponse and ongoing updates.
     * Caller extracts RiskProfile from the wrapper (PricingResponse.getRiskProfile()),
     * decodes RIC from ProductId, and supplies SendingTime.
     */
    public IOptionPricingSnapshotMessage toRoeSnapshot(String ric, RiskProfile rp, long sendingTimeMillis) {
        IOptionPricingSnapshotMessage snap = OptionPricerFactory.createOptionPricingSnapshotMessage();
        snap.setRIC(ric);
        snap.setSendingTimeAsTimestamp(sendingTimeMillis);
        snap.setTheoValue(rp.getTv());

        FirstOrdGreeks g1 = rp.getFirstOrderGreeks();
        snap.setDelta(g1.getDelta());
        snap.setVega(g1.getVega());
        snap.setTheta(g1.getTheta());
        snap.setCashRho(g1.getCashRho());
        snap.setBorrowRho(g1.getBorrowRho());
        snap.setDivRho(g1.getDivRho());

        SecondOrdGreeks g2 = rp.getSecondOrderGreeks();
        snap.setGamma(g2.getGamma());

        return snap;
    }

    // --- enum translation ---

    private static OptionType toBifrostOptionType(PutOrCall src) {
        switch (src) {
            case PUT:  return OptionType.PUT;
            case CALL: return OptionType.CALL;
            default: throw new IllegalArgumentException("Unsupported PutOrCall: " + src);
        }
    }

    private static OptionStyle toBifrostOptionStyle(ExerciseType src) {
        switch (src) {
            case AMERICAN: return OptionStyle.AMERICAN;
            case EUROPEAN: return OptionStyle.EUROPEAN;
            default: throw new IllegalArgumentException("Unsupported ExerciseType: " + src);
        }
    }

    private static SettlementType toBifrostSettlementType(SettlementCode src) {
        switch (src) {
            case PHYSICAL: return SettlementType.PHYSICAL;
            case CASH:     return SettlementType.CASH;
            default: throw new IllegalArgumentException("Unsupported SettlementCode: " + src);
        }
    }

    private static Date toBifrostDate(long timestampMillis) {
        return Date.newBuilder()
                .setTimestamp(timestampMillis)
                .setTz(DEFAULT_TIMEZONE)
                .build();
    }
}