package com.chris64233.commodityoperations.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额/价格的统一精确计算规则。
 * 金额保留 6 位小数，价格（含升贴水）保留 8 位小数，统一使用 HALF_UP。
 * 所有计算只接受 BigDecimal，禁止使用 double/float。
 */
public final class MoneyCalculations {

    public static final int AMOUNT_SCALE = 6;
    public static final int PRICE_SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyCalculations() {
    }

    public static BigDecimal amount(BigDecimal price, BigDecimal qty) {
        return price.multiply(qty).setScale(AMOUNT_SCALE, ROUNDING);
    }

    public static BigDecimal normalizeAmount(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, ROUNDING);
    }
}
