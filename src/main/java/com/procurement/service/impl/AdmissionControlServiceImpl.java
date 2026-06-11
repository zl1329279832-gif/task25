package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.AdmissionControlService;
import com.procurement.service.SupplierScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdmissionControlServiceImpl implements AdmissionControlService {

    private final SupplierMapper supplierMapper;
    private final SupplierScoreMapper scoreMapper;
    private final SupplierAdmissionLogMapper admissionLogMapper;
    private final ScoringRuleVersionMapper ruleVersionMapper;

    @Override
    @Transactional
    public AdmissionResult checkAdmission(Long supplierId, String checkpoint, Long businessId) {
        // 1. 检查供应商状态
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }

        // 黑名单硬拦截
        if ("BLACKLISTED".equals(supplier.getStatus())) {
            AdmissionResult result = AdmissionResult.blocked(null, "供应商已被列入黑名单");
            logAdmission(supplierId, checkpoint, result, null, businessId, null);
            throw new BusinessException("供应商已被列入黑名单，不允许操作");
        }

        // 停用硬拦截
        if ("DISABLED".equals(supplier.getStatus())) {
            AdmissionResult result = AdmissionResult.blocked(null, "供应商已被停用");
            logAdmission(supplierId, checkpoint, result, null, businessId, null);
            throw new BusinessException("供应商已被停用，不允许操作");
        }

        // 2. 获取评分
        SupplierScore score = scoreMapper.selectOne(
                new LambdaQueryWrapper<SupplierScore>()
                        .eq(SupplierScore::getSupplierId, supplierId));

        // 无评分记录，默认通过
        if (score == null) {
            AdmissionResult result = AdmissionResult.allowed(null);
            logAdmission(supplierId, checkpoint, result, null, businessId, null);
            return result;
        }

        BigDecimal currentScore = score.getTotalScore();

        // 3. 获取阈值
        ScoringRuleVersion rule = ruleVersionMapper.selectOne(
                new LambdaQueryWrapper<ScoringRuleVersion>()
                        .eq(ScoringRuleVersion::getStatus, "ACTIVE")
                        .orderByDesc(ScoringRuleVersion::getVersionNo)
                        .last("LIMIT 1"));

        BigDecimal blacklistThreshold = BigDecimal.valueOf(20);
        BigDecimal restrictedThreshold = BigDecimal.valueOf(50);
        BigDecimal extraApprovalThreshold = BigDecimal.valueOf(70);

        if (rule != null) {
            blacklistThreshold = parseThreshold(rule.getThresholds(), "blacklistScore", 20);
            restrictedThreshold = parseThreshold(rule.getThresholds(), "restrictedScore", 50);
            extraApprovalThreshold = parseThreshold(rule.getThresholds(), "extraApprovalScore", 70);
        }

        String thresholdSnap = "{\"blacklistScore\":" + blacklistThreshold
                + ",\"restrictedScore\":" + restrictedThreshold
                + ",\"extraApprovalScore\":" + extraApprovalThreshold + "}";

        // 4. 决策
        AdmissionResult result;
        if (currentScore.compareTo(blacklistThreshold) < 0) {
            result = AdmissionResult.blocked(currentScore,
                    "供应商评分(" + currentScore + ")低于黑名单阈值(" + blacklistThreshold + ")");
            logAdmission(supplierId, checkpoint, result, rule, businessId, thresholdSnap);
            throw new BusinessException(result.getReason());
        } else if (currentScore.compareTo(extraApprovalThreshold) < 0) {
            result = AdmissionResult.restricted(currentScore,
                    "供应商评分(" + currentScore + ")低于准入阈值(" + extraApprovalThreshold + ")，需额外审批");
            logAdmission(supplierId, checkpoint, result, rule, businessId, thresholdSnap);
        } else {
            result = AdmissionResult.allowed(currentScore);
            logAdmission(supplierId, checkpoint, result, rule, businessId, thresholdSnap);
        }

        return result;
    }

    @Override
    public Page<SupplierAdmissionLog> getAdmissionLogs(Long supplierId, int page, int size) {
        LambdaQueryWrapper<SupplierAdmissionLog> wrapper = new LambdaQueryWrapper<>();
        if (supplierId != null) {
            wrapper.eq(SupplierAdmissionLog::getSupplierId, supplierId);
        }
        wrapper.orderByDesc(SupplierAdmissionLog::getCreatedAt);
        return admissionLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    private void logAdmission(Long supplierId, String checkpoint, AdmissionResult result,
                               ScoringRuleVersion rule, Long businessId, String thresholdSnapshot) {
        SupplierAdmissionLog log = new SupplierAdmissionLog();
        log.setSupplierId(supplierId);
        log.setCheckpoint(checkpoint);
        log.setDecision(result.getDecision());
        log.setScoreAtTime(result.getCurrentScore());
        log.setRuleVersionNo(rule != null ? rule.getVersionNo() : null);
        log.setReason(result.getReason());
        log.setBusinessId(businessId);
        log.setThresholdSnapshot(thresholdSnapshot);
        try {
            LoginUser user = (LoginUser) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            log.setOperatorId(user.getUserId());
        } catch (Exception e) {
            log.setOperatorId(0L);
        }
        admissionLogMapper.insert(log);
    }

    private BigDecimal parseThreshold(String json, String key, int defaultValue) {
        if (json == null || json.isBlank()) return BigDecimal.valueOf(defaultValue);
        String cleaned = json.replace("{", "").replace("}", "").replace("\"", "");
        String[] pairs = cleaned.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split(":");
            if (kv.length == 2 && kv[0].trim().equals(key)) {
                try {
                    return new BigDecimal(kv[1].trim());
                } catch (NumberFormatException e) {
                    return BigDecimal.valueOf(defaultValue);
                }
            }
        }
        return BigDecimal.valueOf(defaultValue);
    }
}
