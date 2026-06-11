package com.procurement.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.service.QuoteService;
import com.procurement.service.SupplierScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务调度器
 * - 报价截止自动冻结
 * - 超时提醒（审批超时、收货超时）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcurementScheduler {

    private final RfqMapper rfqMapper;
    private final QuoteService quoteService;
    private final ApprovalMapper approvalMapper;
    private final ReminderMapper reminderMapper;
    private final PurchaseOrderMapper poMapper;
    private final SupplierScoreService supplierScoreService;

    /**
     * 每10分钟检查一次：报价截止后自动冻结所有报价
     */
    @Scheduled(cron = "0 */10 * * * ?")
    @Transactional
    public void freezeExpiredQuotes() {
        List<Rfq> expiredRfqs = rfqMapper.selectList(
                new LambdaQueryWrapper<Rfq>()
                        .eq(Rfq::getStatus, RfqStatus.PUBLISHED.name())
                        .le(Rfq::getDeadline, LocalDateTime.now()));

        for (Rfq rfq : expiredRfqs) {
            log.info("报价截止，冻结询价单 {} 的所有报价", rfq.getRfqNo());
            quoteService.freezeAllByRfq(rfq.getId());
            rfq.setStatus(RfqStatus.CLOSED.name());
            rfqMapper.updateById(rfq);
        }
    }

    /**
     * 每小时检查一次：审批超时提醒（超过48小时未审批）
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void checkApprovalTimeout() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(48);
        List<Approval> pendingApprovals = approvalMapper.selectList(
                new LambdaQueryWrapper<Approval>()
                        .eq(Approval::getStatus, "PENDING")
                        .le(Approval::getCreatedAt, threshold));

        for (Approval approval : pendingApprovals) {
            String idempotencyKey = "PO_APPROVAL:" + approval.getBusinessType() + ":" + approval.getBusinessId();

            Long count = reminderMapper.selectCount(
                    new LambdaQueryWrapper<Reminder>()
                            .eq(Reminder::getIdempotencyKey, idempotencyKey));
            if (count != null && count > 0) continue;

            Reminder reminder = new Reminder();
            reminder.setType("PO_APPROVAL");
            reminder.setBusinessType(approval.getBusinessType());
            reminder.setBusinessId(approval.getBusinessId());
            reminder.setTargetUserId(approval.getApproverId());
            reminder.setMessage(String.format("审批超时提醒：%s 编号 %d 已超过48小时未审批",
                    approval.getBusinessType(), approval.getBusinessId()));
            reminder.setStatus("PENDING");
            reminder.setTriggerTime(LocalDateTime.now());
            reminder.setIdempotencyKey(idempotencyKey);
            reminderMapper.insert(reminder);
            log.info("创建审批超时提醒: businessType={}, businessId={}",
                    approval.getBusinessType(), approval.getBusinessId());
        }
    }

    /**
     * 每天 8:00 检查：订单已确认但超过预期时间未到货
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void checkArrivalOverdue() {
        // 查询 CONFIRMED 状态超过 30 天未到货的订单
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        List<PurchaseOrder> overduePOs = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getStatus, PoStatus.CONFIRMED.name())
                        .le(PurchaseOrder::getCreatedAt, threshold));

        for (PurchaseOrder po : overduePOs) {
            String idempotencyKey = "ARRIVAL_OVERDUE:PO:" + po.getId();

            Long count = reminderMapper.selectCount(
                    new LambdaQueryWrapper<Reminder>()
                            .eq(Reminder::getIdempotencyKey, idempotencyKey));
            if (count != null && count > 0) continue;

            Reminder reminder = new Reminder();
            reminder.setType("ARRIVAL_OVERDUE");
            reminder.setBusinessType("PO");
            reminder.setBusinessId(po.getId());
            reminder.setTargetUserId(po.getCreatedBy());
            reminder.setMessage(String.format("到货超时提醒：采购订单 %s 已确认超过30天尚未到货", po.getPoNo()));
            reminder.setStatus("PENDING");
            reminder.setTriggerTime(LocalDateTime.now());
            reminder.setIdempotencyKey(idempotencyKey);
            reminderMapper.insert(reminder);
            log.info("创建到货超时提醒: poNo={}", po.getPoNo());
        }
    }

    /**
     * 每天凌晨2:00重算所有活跃供应商的履约评分
     * 不在此方法上加 @Transactional，事务由内部 ScoreCalculationService 的 REQUIRES_NEW 管理，
     * 避免长事务持锁导致并发准入检查读到部分更新的评分。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void recalculateSupplierScores() {
        log.info("开始重算供应商履约评分...");
        supplierScoreService.recalculateAll();
        log.info("供应商履约评分重算完成");
    }
}
