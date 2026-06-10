package com.procurement.module.reconciliation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.OrderStatus;
import com.procurement.common.enums.ReconciliationStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.delivery.entity.Delivery;
import com.procurement.module.delivery.entity.DeliveryItem;
import com.procurement.module.delivery.mapper.DeliveryItemMapper;
import com.procurement.module.delivery.mapper.DeliveryMapper;
import com.procurement.module.invoice.entity.Invoice;
import com.procurement.module.invoice.mapper.InvoiceMapper;
import com.procurement.module.order.entity.OrderItem;
import com.procurement.module.order.entity.PurchaseOrder;
import com.procurement.module.order.mapper.OrderItemMapper;
import com.procurement.module.order.mapper.PurchaseOrderMapper;
import com.procurement.module.reconciliation.entity.Reconciliation;
import com.procurement.module.reconciliation.entity.ReconciliationItem;
import com.procurement.module.reconciliation.mapper.ReconciliationItemMapper;
import com.procurement.module.reconciliation.mapper.ReconciliationMapper;
import com.procurement.module.reconciliation.vo.ReconciliationVO;
import com.procurement.module.returns.service.ReturnOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final ReconciliationMapper reconciliationMapper;
    private final ReconciliationItemMapper reconciliationItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryItemMapper deliveryItemMapper;
    private final InvoiceMapper invoiceMapper;
    private final ReturnOrderService returnOrderService;
    private final CodeGenerator codeGenerator;

    @Transactional
    @AuditLog(module = "RECONCILIATION", operation = "CREATE")
    public void generate(Long supplierId, LocalDate periodStart, LocalDate periodEnd, Long operatorId) {
        Reconciliation recon = new Reconciliation();
        recon.setReconciliationNo(codeGenerator.generate("REC"));
        recon.setSupplierId(supplierId);
        recon.setPeriodStart(periodStart);
        recon.setPeriodEnd(periodEnd);
        recon.setStatus(ReconciliationStatus.DRAFT.name());
        recon.setOperatorId(operatorId);
        recon.setCreateTime(LocalDateTime.now());
        recon.setUpdateTime(LocalDateTime.now());
        reconciliationMapper.insert(recon);

        // Find all orders for this supplier in the period
        LambdaQueryWrapper<PurchaseOrder> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(PurchaseOrder::getSupplierId, supplierId)
                .ge(PurchaseOrder::getCreateTime, periodStart.atStartOfDay())
                .lt(PurchaseOrder::getCreateTime, periodEnd.plusDays(1).atStartOfDay())
                .in(PurchaseOrder::getStatus,
                        OrderStatus.CONFIRMED.name(), OrderStatus.PARTIAL_DELIVERED.name(),
                        OrderStatus.DELIVERED.name(), OrderStatus.COMPLETED.name());
        List<PurchaseOrder> orders = orderMapper.selectList(orderWrapper);

        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        BigDecimal totalDeliveryAmount = BigDecimal.ZERO;
        BigDecimal totalInvoiceAmount = BigDecimal.ZERO;
        boolean hasDiff = false;
        List<String> diffDescriptions = new ArrayList<>();

        for (PurchaseOrder order : orders) {
            BigDecimal orderAmount = order.getTotalAmount();
            totalOrderAmount = totalOrderAmount.add(orderAmount);

            // Calculate delivery amount: sum(actualQuantity * unitPrice) from delivery items joined with order items
            BigDecimal deliveredAmount = calculateDeliveryAmount(order.getId());
            totalDeliveryAmount = totalDeliveryAmount.add(deliveredAmount);

            // Calculate verified invoice amount
            BigDecimal invoicedAmount = calculateInvoiceAmount(order.getId());
            totalInvoiceAmount = totalInvoiceAmount.add(invoicedAmount);

            // Three-way diff detection
            boolean orderDeliveryDiff = orderAmount.compareTo(deliveredAmount) != 0;
            boolean deliveryInvoiceDiff = deliveredAmount.compareTo(invoicedAmount) != 0;

            String diffType;
            BigDecimal diffAmount;
            if (orderDeliveryDiff && deliveryInvoiceDiff) {
                diffType = "ALL";
                diffAmount = orderAmount.subtract(invoicedAmount).abs();
                hasDiff = true;
                diffDescriptions.add(order.getOrderNo() + ": 三方差异");
            } else if (orderDeliveryDiff) {
                diffType = "ORDER_DELIVERY";
                diffAmount = orderAmount.subtract(deliveredAmount).abs();
                hasDiff = true;
                diffDescriptions.add(order.getOrderNo() + ": 订单/到货差异 " + diffAmount);
            } else if (deliveryInvoiceDiff) {
                diffType = "DELIVERY_INVOICE";
                diffAmount = deliveredAmount.subtract(invoicedAmount).abs();
                hasDiff = true;
                diffDescriptions.add(order.getOrderNo() + ": 到货/发票差异 " + diffAmount);
            } else {
                diffType = "NONE";
                diffAmount = BigDecimal.ZERO;
            }

            ReconciliationItem item = new ReconciliationItem();
            item.setReconciliationId(recon.getId());
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setOrderAmount(orderAmount);
            item.setDeliveredAmount(deliveredAmount);
            item.setInvoicedAmount(invoicedAmount);
            item.setReturnAmount(BigDecimal.ZERO);
            item.setDiffAmount(diffAmount);
            item.setDiffType(diffType);
            reconciliationItemMapper.insert(item);
        }

        BigDecimal totalReturnAmount = returnOrderService.getReturnAmountBySupplierAndPeriod(
                supplierId, periodStart, periodEnd);

        recon.setOrderAmount(totalOrderAmount);
        recon.setDeliveryAmount(totalDeliveryAmount);
        recon.setInvoiceAmount(totalInvoiceAmount);
        recon.setReturnAmount(totalReturnAmount);
        recon.setNetAmount(totalOrderAmount.subtract(totalReturnAmount));
        recon.setDiffFlag(hasDiff ? 1 : 0);
        recon.setDiffDescription(hasDiff ? String.join("; ", diffDescriptions) : null);
        reconciliationMapper.updateById(recon);
    }

    @AuditLog(module = "RECONCILIATION", operation = "STATUS_CHANGE")
    public void confirm(Long id) {
        Reconciliation recon = getEntity(id);
        if (!ReconciliationStatus.DRAFT.name().equals(recon.getStatus())) {
            throw new BizException(ErrorCode.RECONCILIATION_STATUS_ERROR);
        }
        recon.setStatus(ReconciliationStatus.CONFIRMED.name());
        recon.setConfirmTime(LocalDateTime.now());
        recon.setUpdateTime(LocalDateTime.now());
        reconciliationMapper.updateById(recon);
    }

    @AuditLog(module = "RECONCILIATION", operation = "STATUS_CHANGE")
    public void dispute(Long id, String reason) {
        Reconciliation recon = getEntity(id);
        if (!ReconciliationStatus.DRAFT.name().equals(recon.getStatus())
                && !ReconciliationStatus.CONFIRMED.name().equals(recon.getStatus())) {
            throw new BizException(ErrorCode.RECONCILIATION_STATUS_ERROR);
        }
        recon.setStatus(ReconciliationStatus.DISPUTED.name());
        recon.setDiffDescription(reason);
        recon.setUpdateTime(LocalDateTime.now());
        reconciliationMapper.updateById(recon);
    }

    @AuditLog(module = "RECONCILIATION", operation = "STATUS_CHANGE")
    public void settle(Long id) {
        Reconciliation recon = getEntity(id);
        if (!ReconciliationStatus.CONFIRMED.name().equals(recon.getStatus())) {
            throw new BizException(ErrorCode.RECONCILIATION_STATUS_ERROR);
        }
        recon.setStatus(ReconciliationStatus.SETTLED.name());
        recon.setUpdateTime(LocalDateTime.now());
        reconciliationMapper.updateById(recon);
    }

    public Page<ReconciliationVO> page(int pageNum, int pageSize, Long supplierId, String status) {
        LambdaQueryWrapper<Reconciliation> wrapper = new LambdaQueryWrapper<>();
        if (supplierId != null) wrapper.eq(Reconciliation::getSupplierId, supplierId);
        if (status != null) wrapper.eq(Reconciliation::getStatus, status);
        wrapper.orderByDesc(Reconciliation::getCreateTime);
        Page<Reconciliation> page = reconciliationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ReconciliationVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public ReconciliationVO getById(Long id) {
        return toVO(getEntity(id));
    }

    private BigDecimal calculateDeliveryAmount(Long orderId) {
        // Build a map of orderItemId -> unitPrice
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        Map<Long, BigDecimal> unitPriceMap = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, OrderItem::getUnitPrice));

        // Sum actualQuantity * unitPrice across all deliveries for this order
        List<Delivery> deliveries = deliveryMapper.selectList(
                new LambdaQueryWrapper<Delivery>().eq(Delivery::getOrderId, orderId));

        BigDecimal total = BigDecimal.ZERO;
        for (Delivery d : deliveries) {
            List<DeliveryItem> items = deliveryItemMapper.selectList(
                    new LambdaQueryWrapper<DeliveryItem>().eq(DeliveryItem::getDeliveryId, d.getId()));
            for (DeliveryItem di : items) {
                BigDecimal unitPrice = unitPriceMap.getOrDefault(di.getOrderItemId(), BigDecimal.ZERO);
                total = total.add(di.getActualQuantity().multiply(unitPrice));
            }
        }
        return total;
    }

    private BigDecimal calculateInvoiceAmount(Long orderId) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Invoice::getOrderId, orderId)
                .eq(Invoice::getStatus, "VERIFIED");
        List<Invoice> invoices = invoiceMapper.selectList(wrapper);
        return invoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Reconciliation getEntity(Long id) {
        Reconciliation r = reconciliationMapper.selectById(id);
        if (r == null) throw new BizException(ErrorCode.RECONCILIATION_NOT_FOUND);
        return r;
    }

    private ReconciliationVO toVO(Reconciliation r) {
        ReconciliationVO vo = new ReconciliationVO();
        vo.setId(r.getId());
        vo.setReconciliationNo(r.getReconciliationNo());
        vo.setSupplierId(r.getSupplierId());
        vo.setPeriodStart(r.getPeriodStart());
        vo.setPeriodEnd(r.getPeriodEnd());
        vo.setStatus(r.getStatus());
        vo.setOrderAmount(r.getOrderAmount());
        vo.setDeliveryAmount(r.getDeliveryAmount());
        vo.setInvoiceAmount(r.getInvoiceAmount());
        vo.setReturnAmount(r.getReturnAmount());
        vo.setNetAmount(r.getNetAmount());
        vo.setDiffFlag(r.getDiffFlag());
        vo.setDiffDescription(r.getDiffDescription());
        vo.setConfirmTime(r.getConfirmTime());
        vo.setOperatorId(r.getOperatorId());
        vo.setCreateTime(r.getCreateTime());

        List<ReconciliationItem> items = reconciliationItemMapper.selectList(
                new LambdaQueryWrapper<ReconciliationItem>()
                        .eq(ReconciliationItem::getReconciliationId, r.getId()));
        vo.setItems(items.stream().map(i -> {
            ReconciliationVO.ReconciliationItemVO iv = new ReconciliationVO.ReconciliationItemVO();
            iv.setId(i.getId());
            iv.setOrderId(i.getOrderId());
            iv.setOrderNo(i.getOrderNo());
            iv.setOrderAmount(i.getOrderAmount());
            iv.setDeliveredAmount(i.getDeliveredAmount());
            iv.setInvoicedAmount(i.getInvoicedAmount());
            iv.setReturnAmount(i.getReturnAmount());
            iv.setDiffAmount(i.getDiffAmount());
            iv.setDiffType(i.getDiffType());
            iv.setRemark(i.getRemark());
            return iv;
        }).toList());
        return vo;
    }
}
