package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationMapper reconMapper;
    private final ReconciliationLineMapper reconLineMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderLineMapper poLineMapper;
    private final ArrivalLineMapper arrivalLineMapper;
    private final ArrivalMapper arrivalMapper;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceLineMapper invoiceLineMapper;
    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnLineMapper returnLineMapper;

    @Override
    @Transactional
    @Auditable(action = "GENERATE_RECONCILIATION", entityType = "Reconciliation")
    public Reconciliation generate(Long poId) {
        PurchaseOrder po = poMapper.selectById(poId);
        if (po == null) throw new BusinessException("采购订单不存在");

        // 校验 PO 状态：只有已收货（含部分）的订单才能对账
        PoStatus poStatus = PoStatus.valueOf(po.getStatus());
        if (poStatus != PoStatus.PARTIAL_RECEIVED && poStatus != PoStatus.RECEIVED) {
            throw new BusinessException("当前订单状态不允许对账: " + poStatus);
        }

        LoginUser user = getCurrentUser();

        // 获取订单行
        List<PurchaseOrderLine> poLines = poLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderLine>().eq(PurchaseOrderLine::getPoId, poId));

        // 获取所有到货行 - 只统计已验收(ACCEPTED/PARTIAL_ACCEPTED)的到货单
        List<Arrival> arrivals = arrivalMapper.selectList(
                new LambdaQueryWrapper<Arrival>()
                        .eq(Arrival::getPoId, poId)
                        .in(Arrival::getStatus,
                                ArrivalStatus.ACCEPTED.name(),
                                ArrivalStatus.PARTIAL_ACCEPTED.name()));
        Map<Long, BigDecimal> acceptedQtyMap = new HashMap<>();
        for (Arrival arr : arrivals) {
            List<ArrivalLine> arrLines = arrivalLineMapper.selectList(
                    new LambdaQueryWrapper<ArrivalLine>().eq(ArrivalLine::getArrivalId, arr.getId()));
            for (ArrivalLine al : arrLines) {
                acceptedQtyMap.merge(al.getPoLineId(), al.getAcceptedQty(), BigDecimal::add);
            }
        }

        // 扣减已退货数量（APPROVED 或 RETURNED 状态的退货单）
        List<ReturnOrder> returnOrders = returnOrderMapper.selectList(
                new LambdaQueryWrapper<ReturnOrder>()
                        .eq(ReturnOrder::getPoId, poId)
                        .in(ReturnOrder::getStatus, "APPROVED", "RETURNED"));
        Map<Long, BigDecimal> returnedQtyMap = new HashMap<>();
        for (ReturnOrder ro : returnOrders) {
            List<ReturnLine> retLines = returnLineMapper.selectList(
                    new LambdaQueryWrapper<ReturnLine>().eq(ReturnLine::getReturnId, ro.getId()));
            for (ReturnLine rl : retLines) {
                returnedQtyMap.merge(rl.getMaterialId(), rl.getQuantity(), BigDecimal::add);
            }
        }

        // 获取所有已验证的发票行（排除 REJECTED 状态的发票）
        List<Invoice> invoices = invoiceMapper.selectList(
                new LambdaQueryWrapper<Invoice>()
                        .eq(Invoice::getPoId, poId)
                        .eq(Invoice::getStatus, "VERIFIED"));
        Map<Long, BigDecimal> invoiceQtyMap = new HashMap<>();
        BigDecimal totalInvoiceAmount = BigDecimal.ZERO;
        for (Invoice inv : invoices) {
            totalInvoiceAmount = totalInvoiceAmount.add(inv.getAmount());
            List<InvoiceLine> invLines = invoiceLineMapper.selectList(
                    new LambdaQueryWrapper<InvoiceLine>().eq(InvoiceLine::getInvoiceId, inv.getId()));
            for (InvoiceLine il : invLines) {
                invoiceQtyMap.merge(il.getPoLineId(), il.getQuantity(), BigDecimal::add);
            }
        }

        // 计算总金额
        BigDecimal orderAmount = BigDecimal.ZERO;
        BigDecimal receiptAmount = BigDecimal.ZERO;

        Reconciliation recon = new Reconciliation();
        recon.setReconNo("RECON-" + System.currentTimeMillis());
        recon.setPoId(poId);
        recon.setSupplierId(po.getSupplierId());
        recon.setInvoiceAmount(totalInvoiceAmount);
        recon.setStatus("PENDING");
        recon.setCreatedBy(user.getUserId());

        reconMapper.insert(recon);

        // 创建行项
        for (PurchaseOrderLine poLine : poLines) {
            ReconciliationLine rl = new ReconciliationLine();
            rl.setReconId(recon.getId());
            rl.setMaterialId(poLine.getMaterialId());
            rl.setPoLineId(poLine.getId());
            rl.setOrderedQty(poLine.getQuantity());

            // 计算净验收数量 = 验收数量 - 退货数量
            BigDecimal accepted = acceptedQtyMap.getOrDefault(poLine.getId(), BigDecimal.ZERO);
            BigDecimal returned = returnedQtyMap.getOrDefault(poLine.getMaterialId(), BigDecimal.ZERO);
            BigDecimal netReceived = accepted.subtract(returned).max(BigDecimal.ZERO);

            rl.setReceivedQty(netReceived);
            rl.setInvoicedQty(invoiceQtyMap.getOrDefault(poLine.getId(), BigDecimal.ZERO));
            rl.setUnitPrice(poLine.getUnitPrice());

            BigDecimal lineOrderAmt = poLine.getQuantity().multiply(poLine.getUnitPrice());
            BigDecimal lineReceiptAmt = rl.getReceivedQty().multiply(poLine.getUnitPrice());
            BigDecimal lineInvoiceAmt = rl.getInvoicedQty().multiply(poLine.getUnitPrice());

            rl.setOrderAmount(lineOrderAmt);
            rl.setReceiptAmount(lineReceiptAmt);
            rl.setInvoiceAmount(lineInvoiceAmt);
            rl.setDiffAmount(lineInvoiceAmt.subtract(lineReceiptAmt));

            orderAmount = orderAmount.add(lineOrderAmt);
            receiptAmount = receiptAmount.add(lineReceiptAmt);

            reconLineMapper.insert(rl);
        }

        recon.setOrderAmount(orderAmount);
        recon.setReceiptAmount(receiptAmount);
        // diff_amount 由数据库生成列计算
        BigDecimal diffAmount = totalInvoiceAmount.subtract(receiptAmount);
        recon.setStatus(diffAmount.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "DIFFERENT");
        reconMapper.updateById(recon);

        return recon;
    }

    @Override
    @Auditable(action = "APPROVE_RECONCILIATION", entityType = "Reconciliation")
    public void approve(Long reconId) {
        Reconciliation recon = reconMapper.selectById(reconId);
        if (recon == null) throw new BusinessException("对账单不存在");
        recon.setStatus("APPROVED");
        reconMapper.updateById(recon);
    }

    @Override
    @Auditable(action = "REJECT_RECONCILIATION", entityType = "Reconciliation")
    public void reject(Long reconId, String remark) {
        Reconciliation recon = reconMapper.selectById(reconId);
        if (recon == null) throw new BusinessException("对账单不存在");
        recon.setStatus("REJECTED");
        recon.setRemark(remark);
        reconMapper.updateById(recon);
    }

    @Override
    public Reconciliation getById(Long id) {
        Reconciliation recon = reconMapper.selectById(id);
        if (recon == null) throw new BusinessException("对账单不存在");
        return recon;
    }

    @Override
    public List<ReconciliationLine> getLines(Long reconId) {
        return reconLineMapper.selectList(
                new LambdaQueryWrapper<ReconciliationLine>().eq(ReconciliationLine::getReconId, reconId));
    }

    @Override
    public List<Reconciliation> list(String status, int page, int size) {
        LambdaQueryWrapper<Reconciliation> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Reconciliation::getStatus, status);
        wrapper.orderByDesc(Reconciliation::getCreatedAt);
        return reconMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper).getRecords();
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
