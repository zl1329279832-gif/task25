package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.AdmissionControlService;
import com.procurement.service.PurchaseOrderService;
import com.procurement.service.SupplierScoreService;
import com.procurement.state.PurchaseOrderStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderLineMapper poLineMapper;
    private final ApprovalMapper approvalMapper;
    private final AdmissionControlService admissionControlService;
    private final SupplierScoreService supplierScoreService;

    @Override
    @Transactional
    @Auditable(action = "CREATE_PO", entityType = "PurchaseOrder")
    public PurchaseOrder create(PurchaseOrder po, List<PurchaseOrderLine> lines) {
        LoginUser user = getCurrentUser();
        po.setCreatedBy(user.getUserId());
        po.setStatus(PoStatus.DRAFT.name());
        po.setPoNo("PO-" + System.currentTimeMillis());

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderLine line : lines) {
            line.setAmount(line.getUnitPrice().multiply(line.getQuantity()));
            line.setReceivedQty(BigDecimal.ZERO);
            line.setAcceptedQty(BigDecimal.ZERO);
            line.setRejectedQty(BigDecimal.ZERO);
            total = total.add(line.getAmount());
        }
        po.setTotalAmount(total);
        poMapper.insert(po);

        for (PurchaseOrderLine line : lines) {
            line.setPoId(po.getId());
            poLineMapper.insert(line);
        }

        // 创建供应商评分快照 — 失败时回滚PO创建（保证PO与快照原子绑定）
        SupplierScoreSnapshot snapshot = supplierScoreService.createSnapshot(po.getId(), po.getSupplierId());
        po.setSupplierScore(snapshot.getTotalScore());
        po.setScoreRuleVersion(snapshot.getRuleVersionNo());
        poMapper.updateById(po);

        return po;
    }

    @Override
    @Transactional
    @Auditable(action = "SUBMIT_PO_APPROVAL", entityType = "PurchaseOrder")
    public void submitForApproval(Long poId) {
        PurchaseOrder po = poMapper.selectById(poId);
        if (po == null) throw new BusinessException("采购订单不存在");
        PurchaseOrderStateMachine.validateTransition(
                PoStatus.valueOf(po.getStatus()), PoStatus.PENDING_APPROVAL);
        po.setStatus(PoStatus.PENDING_APPROVAL.name());
        poMapper.updateById(po);

        // 创建审批记录
        Approval approval = new Approval();
        approval.setBusinessType("PO");
        approval.setBusinessId(poId);
        approval.setStep(1);
        approval.setStatus("PENDING");
        // 审批人由主管担任（实际系统中应查找具体主管）
        approval.setApproverId(0L);
        approvalMapper.insert(approval);
    }

    @Override
    @Transactional
    @Auditable(action = "APPROVE_PO", entityType = "PurchaseOrder")
    public void approve(Long poId, Long approverId) {
        PurchaseOrder po = poMapper.selectById(poId);
        if (po == null) throw new BusinessException("采购订单不存在");
        PurchaseOrderStateMachine.validateTransition(
                PoStatus.valueOf(po.getStatus()), PoStatus.APPROVED);
        po.setStatus(PoStatus.APPROVED.name());
        po.setApprovedBy(approverId);
        po.setApprovedAt(LocalDateTime.now());
        poMapper.updateById(po);

        updateApproval(poId, "PO", "APPROVED", null);
    }

    @Override
    @Transactional
    @Auditable(action = "REJECT_PO", entityType = "PurchaseOrder")
    public void reject(Long poId, Long approverId, String comment) {
        PurchaseOrder po = poMapper.selectById(poId);
        if (po == null) throw new BusinessException("采购订单不存在");
        PurchaseOrderStateMachine.validateTransition(
                PoStatus.valueOf(po.getStatus()), PoStatus.REJECTED);
        po.setStatus(PoStatus.REJECTED.name());
        poMapper.updateById(po);

        updateApproval(poId, "PO", "REJECTED", comment);
    }

    @Override
    @Transactional
    @Auditable(action = "CONFIRM_PO", entityType = "PurchaseOrder")
    public void confirm(Long poId) {
        PurchaseOrder po = poMapper.selectById(poId);
        if (po == null) throw new BusinessException("采购订单不存在");

        // 准入检查：黑名单/停用/低分供应商会被拦截
        admissionControlService.checkAdmission(po.getSupplierId(), "PO_CONFIRM", poId);

        PurchaseOrderStateMachine.validateTransition(
                PoStatus.valueOf(po.getStatus()), PoStatus.CONFIRMED);
        po.setStatus(PoStatus.CONFIRMED.name());
        poMapper.updateById(po);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL_PO", entityType = "PurchaseOrder")
    public void cancel(Long poId, String reason) {
        PurchaseOrder po = poMapper.selectById(poId);
        if (po == null) throw new BusinessException("采购订单不存在");

        PoStatus currentStatus = PoStatus.valueOf(po.getStatus());
        // 已收货部分不能取消
        if (!PurchaseOrderStateMachine.canCancel(currentStatus)) {
            throw new BusinessException("订单已有收货记录，不允许取消");
        }
        PurchaseOrderStateMachine.validateTransition(currentStatus, PoStatus.CANCELLED);
        po.setStatus(PoStatus.CANCELLED.name());
        po.setCancelReason(reason);
        poMapper.updateById(po);
    }

    @Override
    public PurchaseOrder getById(Long id) {
        PurchaseOrder po = poMapper.selectById(id);
        if (po == null) throw new BusinessException("采购订单不存在");
        return po;
    }

    @Override
    public List<PurchaseOrderLine> getLines(Long poId) {
        return poLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderLine>().eq(PurchaseOrderLine::getPoId, poId));
    }

    @Override
    public Page<PurchaseOrder> list(String status, Long supplierId, int page, int size) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) wrapper.eq(PurchaseOrder::getStatus, status);
        if (supplierId != null) wrapper.eq(PurchaseOrder::getSupplierId, supplierId);
        wrapper.orderByDesc(PurchaseOrder::getCreatedAt);
        return poMapper.selectPage(new Page<>(page, size), wrapper);
    }

    private void updateApproval(Long businessId, String businessType, String status, String comment) {
        Approval approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<Approval>()
                        .eq(Approval::getBusinessId, businessId)
                        .eq(Approval::getBusinessType, businessType)
                        .eq(Approval::getStatus, "PENDING"));
        if (approval != null) {
            approval.setStatus(status);
            approval.setComment(comment);
            approval.setDecidedAt(LocalDateTime.now());
            approvalMapper.updateById(approval);
        }
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
