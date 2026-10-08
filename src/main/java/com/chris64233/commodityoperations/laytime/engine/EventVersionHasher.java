package com.chris64233.commodityoperations.laytime.engine;

import com.chris64233.commodityoperations.laytime.domain.CountingBasis;
import com.chris64233.commodityoperations.laytime.domain.OperationEvent;
import com.chris64233.commodityoperations.laytime.domain.Voyage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 计算结算单固定的「合同规则 + 事件版本」指纹。
 */
public final class EventVersionHasher {

    private EventVersionHasher() {
    }

    public static String hashEvents(List<OperationEvent> events) {
        StringBuilder sb = new StringBuilder();
        events.stream()
                .sorted(java.util.Comparator.comparing(OperationEvent::getOccurredAt)
                        .thenComparing(OperationEvent::getExternalEventNo))
                .forEach(e -> sb.append(e.getExternalEventNo()).append('|')
                        .append(e.getType()).append('|')
                        .append(e.getOccurredAt()).append('|')
                        .append(e.getReceivedAt()).append(';'));
        return sha256(sb.toString());
    }

    public static String hashContract(Voyage v) {
        return sha256(String.join("|",
                v.getVoyageCode(),
                String.valueOf(v.getAllowedLaytimeSeconds()),
                v.getDemurrageRatePerDay().toPlainString(),
                v.getCurrency(),
                v.getCountingBasis().name()));
    }

    public static String contractSnapshot(Voyage v) {
        return "{\"voyageCode\":\"" + v.getVoyageCode() + "\","
                + "\"allowedLaytimeSeconds\":" + v.getAllowedLaytimeSeconds() + ","
                + "\"demurrageRatePerDay\":" + v.getDemurrageRatePerDay().toPlainString() + ","
                + "\"currency\":\"" + v.getCurrency() + "\","
                + "\"countingBasis\":\"" + safeBasis(v) + "\"}";
    }

    private static String safeBasis(Voyage v) {
        return v.getCountingBasis() == null ? CountingBasis.ON_BERTH.name() : v.getCountingBasis().name();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
