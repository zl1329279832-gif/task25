package com.procurement.entity;

import java.math.BigDecimal;

public enum ScoreLevel {
    EXCELLENT(new BigDecimal("90"), new BigDecimal("100")),
    GOOD(new BigDecimal("75"), new BigDecimal("89.99")),
    QUALIFIED(new BigDecimal("60"), new BigDecimal("74.99")),
    WARNING(new BigDecimal("40"), new BigDecimal("59.99")),
    BLOCKED(BigDecimal.ZERO, new BigDecimal("39.99"));

    private final BigDecimal minScore;
    private final BigDecimal maxScore;

    ScoreLevel(BigDecimal minScore, BigDecimal maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public static ScoreLevel fromScore(BigDecimal score) {
        if (score.compareTo(new BigDecimal("90")) >= 0) return EXCELLENT;
        if (score.compareTo(new BigDecimal("75")) >= 0) return GOOD;
        if (score.compareTo(new BigDecimal("60")) >= 0) return QUALIFIED;
        if (score.compareTo(new BigDecimal("40")) >= 0) return WARNING;
        return BLOCKED;
    }
}
