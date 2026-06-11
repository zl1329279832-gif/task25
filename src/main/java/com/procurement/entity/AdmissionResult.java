package com.procurement.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdmissionResult {
    private String decision;
    private String reason;
    private BigDecimal currentScore;
    private boolean requiresExtraApproval;
    private Long snapshotId;

    public static AdmissionResult allowed(BigDecimal score) {
        return allowed(score, null);
    }

    public static AdmissionResult allowed(BigDecimal score, Long snapshotId) {
        AdmissionResult r = new AdmissionResult();
        r.setDecision("ALLOWED");
        r.setReason("评分达标");
        r.setCurrentScore(score);
        r.setRequiresExtraApproval(false);
        r.setSnapshotId(snapshotId);
        return r;
    }

    public static AdmissionResult restricted(BigDecimal score, String reason) {
        return restricted(score, reason, null);
    }

    public static AdmissionResult restricted(BigDecimal score, String reason, Long snapshotId) {
        AdmissionResult r = new AdmissionResult();
        r.setDecision("RESTRICTED");
        r.setReason(reason);
        r.setCurrentScore(score);
        r.setRequiresExtraApproval(true);
        r.setSnapshotId(snapshotId);
        return r;
    }

    public static AdmissionResult blocked(BigDecimal score, String reason) {
        return blocked(score, reason, null);
    }

    public static AdmissionResult blocked(BigDecimal score, String reason, Long snapshotId) {
        AdmissionResult r = new AdmissionResult();
        r.setDecision("BLOCKED");
        r.setReason(reason);
        r.setCurrentScore(score);
        r.setRequiresExtraApproval(false);
        r.setSnapshotId(snapshotId);
        return r;
    }
}
