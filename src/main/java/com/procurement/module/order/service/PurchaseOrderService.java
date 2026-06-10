package com.procurement.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.OrderStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.comparison.entity.ComparisonItem;
import com.procurement.module.comparison.mapper.ComparisonItemMapper;
import com.procurement.module.order.dto.OrderCreateDTO;
import com.procurement.module.order.entity.OrderItem;
import com.procurement.module.order.entity.PurchaseOrder;
import com.procurement.module.order.mapper.OrderItemMapper;
import com.procurement.module.order.mapper.PurchaseOrderMapper;
import com.procurement.module.order.statemachine.OrderStateMachine;
import com.procurement.module.order.vo.PurchaseOrderVO;
import com.procurement.module.quotation.entity.QuotationItem;
import com.procurement.module.quotation.mapper.QuotationItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final BigDecimal LEVEL_1_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal LEVEL_2_THRESHOLD = new BigDecimal("100000");

    private final PurchaseOrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ComparisonItemMapper comparisonItemMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final CodeGenerator codeGenerator;

    @Transactional
    @AuditLog(module = "ORDER", operation = "CREATE")
    public void create(OrderCreateDTO dto, Long buyerId) {
        // get selected comparison items
        List<ComparisonItem> selected = comparisonItemMapper.selectList(
                new LambdaQueryWrapper<ComparisonItem>()
                        .eq(ComparisonItem::getComparisonId, dto.getComparisonId())
                        .eq(ComparisonItem::getIsSelected, 1));

        if (selected.isEmpty()) throw new BizException(ErrorCode.COMPARISON_NOT_FOUND);

        // group by supplier
        Long supplierId = selected.get(0).getSupplierId();

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(codeGenerator.generate("PO"));
        order.setComparisonId(dto.getComparisonId());
        order.setSupplierId(supplierId);
        order.setBuyerId(buyerId);
        order.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        order.setRemark(dto.getRemark());
        order.setPaidAmount(BigDecimal.ZERO);

        BigDecimal totalAmount = BigDecimal.ZERO;
        orderMapper.insert(order);

        for (ComparisonItem ci : selected) {
            // find quotation item for quantity
            QuotationItem qi = quotationItemMapper.selectList(
                    new LambdaQueryWrapper<QuotationItem>()
                            .eq(QuotationItem::getQuotationId, ci.getQuotationId())
                            .eq(QuotationItem::getMaterialId, ci.getMaterialId())
                            .last("LIMIT 1")).stream().findFirst().orElse(null);

            BigDecimal qty = qi != null ? qi.getQuantity() : BigDecimal.ONE;
            BigDecimal amount = ci.getUnitPrice().multiply(qty);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setMaterialId(ci.getMaterialId());
            item.setUnitPrice(ci.getUnitPrice());
            item.setQuantity(qty);
            item.setReceivedQuantity(BigDecimal.ZERO);
            item.setAmount(amount);
            orderItemMapper.insert(item);
            totalAmount = totalAmount.add(amount);
        }

        order.setTotalAmount(totalAmount);

        // auto-approval logic
        if (totalAmount.compareTo(LEVEL_1_THRESHOLD) <= 0) {
            order.setStatus(OrderStatus.APPROVED.name());
            order.setApprovalThreshold("AUTO");
        } else if (totalAmount.compareTo(LEVEL_2_THRESHOLD) <= 0) {
            order.setStatus(OrderStatus.PENDING_APPROVAL.name());
            order.setApprovalThreshold("LEVEL_1");
        } else {
            order.setStatus(OrderStatus.PENDING_APPROVAL.name());
            order.setApprovalThreshold("LEVEL_2");
        }
        orderMapper.updateById(order);
    }

    public Page<PurchaseOrderVO> page(int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(PurchaseOrder::getStatus, status);
        wrapper.orderByDesc(PurchaseOrder::getCreateTime);
        Page<PurchaseOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<PurchaseOrderVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public PurchaseOrderVO getById(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        return toVO(order);
    }

    @AuditLog(module = "ORDER", operation = "STATUS_CHANGE")
    public void approve(Long id, boolean approved, String opinion, Long approverId) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);

        if (approved) {
            OrderStateMachine.transition(order, OrderStatus.APPROVED);
        } else {
            OrderStateMachine.transition(order, OrderStatus.CANCELLED);
        }
        orderMapper.updateById(order);
    }

    @AuditLog(module = "ORDER", operation = "STATUS_CHANGE")
    public void confirm(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        OrderStateMachine.transition(order, OrderStatus.CONFIRMED);
        order.setConfirmTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @AuditLog(module = "ORDER", operation = "STATUS_CHANGE")
    public void cancel(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);

        // check if any items have been received
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        boolean hasReceived = items.stream()
                .anyMatch(i -> i.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (hasReceived) {
            throw new BizException(ErrorCode.ORDER_CANCEL_HAS_RECEIVED);
        }

        OrderStateMachine.transition(order, OrderStatus.CANCELLED);
        orderMapper.updateById(order);
    }

    public Page<PurchaseOrderVO> pageForSupplier(int pageNum, int pageSize, Long supplierId) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseOrder::getSupplierId, supplierId)
                .orderByDesc(PurchaseOrder::getCreateTime);
        Page<PurchaseOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<PurchaseOrderVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public void updateDeliveryStatus(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) return;

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        boolean allDelivered = items.stream()
                .allMatch(i -> i.getReceivedQuantity().compareTo(i.getQuantity()) >= 0);
        boolean anyDelivered = items.stream()
                .anyMatch(i -> i.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);

        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (allDelivered && (current == OrderStatus.PARTIAL_DELIVERED || current == OrderStatus.CONFIRMED)) {
            order.setStatus(OrderStatus.DELIVERED.name());
            orderMapper.updateById(order);
        } else if (anyDelivered && current == OrderStatus.CONFIRMED) {
            order.setStatus(OrderStatus.PARTIAL_DELIVERED.name());
            orderMapper.updateById(order);
        }
    }

    private PurchaseOrderVO toVO(PurchaseOrder order) {
        PurchaseOrderVO vo = new PurchaseOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setComparisonId(order.getComparisonId());
        vo.setSupplierId(order.getSupplierId());
        vo.setStatus(order.getStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPaidAmount(order.getPaidAmount());
        vo.setBuyerId(order.getBuyerId());
        vo.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        vo.setActualDeliveryDate(order.getActualDeliveryDate());
        vo.setConfirmTime(order.getConfirmTime());
        vo.setApprovalThreshold(order.getApprovalThreshold());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        vo.setItems(items.stream().map(i -> {
            PurchaseOrderVO.OrderItemVO iv = new PurchaseOrderVO.OrderItemVO();
            iv.setId(i.getId());
            iv.setMaterialId(i.getMaterialId());
            iv.setUnitPrice(i.getUnitPrice());
            iv.setQuantity(i.getQuantity());
            iv.setReceivedQuantity(i.getReceivedQuantity());
            iv.setAmount(i.getAmount());
            return iv;
        }).toList());
        return vo;
    }
}
