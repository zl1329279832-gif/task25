package com.procurement.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdmissionResult {
    private String decision;
    private String reason;
    private BigDecimal currentScore;
    private boolean requiresExtraApproval;

    public static AdmissionResult allowed(BigDecimal score) {
        AdmissionResult r = new AdmissionResult();
        r.setDecision("ALLOWED");
        r.setReason("评分达标");
        r.setCurrentScore(score);
        r.setRequiresExtraApproval(false);
        return r;
    }

    public static AdmissionResult restricted(BigDecimal score, String reason) {
        AdmissionResult r = new AdmissionResult();
        r.setDecision("RESTRICTED");
        r.setReason(reason);
        r.setCurrentScore(score);
        r.setRequiresExtraApproval(true);
        return r;
    }

    public static AdmissionResult blocked(BigDecimal score, String reason) {
        AdmissionResult r = new AdmissionResult();
        r.setDecision("BLOCKED");
        r.setReason(reason);
        r.setCurrentScore(score);
        r.setRequiresExtraApproval(false);
        return r;
    }
}
