package com.procurement.module.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.DeliveryStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.delivery.dto.DeliveryCreateDTO;
import com.procurement.module.delivery.entity.Delivery;
import com.procurement.module.delivery.entity.DeliveryDiff;
import com.procurement.module.delivery.entity.DeliveryItem;
import com.procurement.module.delivery.mapper.DeliveryDiffMapper;
import com.procurement.module.delivery.mapper.DeliveryItemMapper;
import com.procurement.module.delivery.mapper.DeliveryMapper;
import com.procurement.module.delivery.vo.DeliveryVO;
import com.procurement.module.order.entity.OrderItem;
import com.procurement.module.order.entity.PurchaseOrder;
import com.procurement.module.order.mapper.OrderItemMapper;
import com.procurement.module.order.mapper.PurchaseOrderMapper;
import com.procurement.module.order.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryMapper deliveryMapper;
    private final DeliveryItemMapper deliveryItemMapper;
    private final DeliveryDiffMapper diffMapper;
    private final PurchaseOrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PurchaseOrderService orderService;
    private final CodeGenerator codeGenerator;

    @Transactional
    @AuditLog(module = "DELIVERY", operation = "CREATE")
    public void create(DeliveryCreateDTO dto, Long receiverId) {
        PurchaseOrder order = orderMapper.selectById(dto.getOrderId());
        if (order == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);

        Delivery delivery = new Delivery();
        delivery.setDeliveryNo(codeGenerator.generate("DLV"));
        delivery.setOrderId(dto.getOrderId());
        delivery.setSupplierId(order.getSupplierId());
        delivery.setStatus(DeliveryStatus.PENDING.name());
        delivery.setDeliveryDate(dto.getDeliveryDate());
        delivery.setReceiverId(receiverId);
        delivery.setRemark(dto.getRemark());
        delivery.setCreateTime(LocalDateTime.now());
        delivery.setUpdateTime(LocalDateTime.now());
        deliveryMapper.insert(delivery);

        for (DeliveryCreateDTO.Item item : dto.getItems()) {
            DeliveryItem di = new DeliveryItem();
            di.setDeliveryId(delivery.getId());
            di.setOrderItemId(item.getOrderItemId());
            di.setMaterialId(item.getMaterialId());
            di.setExpectedQuantity(item.getExpectedQuantity());
            di.setActualQuantity(item.getActualQuantity());
            di.setRemark(item.getRemark());
            deliveryItemMapper.insert(di);

            // generate diff record if quantities don't match
            BigDecimal diff = item.getActualQuantity().subtract(item.getExpectedQuantity());
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                DeliveryDiff diffRecord = new DeliveryDiff();
                diffRecord.setDeliveryId(delivery.getId());
                diffRecord.setDeliveryItemId(di.getId());
                diffRecord.setMaterialId(item.getMaterialId());
                diffRecord.setDiffQuantity(diff.abs());
                diffRecord.setCreateTime(LocalDateTime.now());
                if (diff.compareTo(BigDecimal.ZERO) < 0) {
                    diffRecord.setDiffType("SHORTAGE");
                    diffRecord.setDescription("短缺 " + diff.abs() + " 个单位");
                } else {
                    diffRecord.setDiffType("EXCESS");
                    diffRecord.setDescription("多发 " + diff.abs() + " 个单位");
                }
                diffMapper.insert(diffRecord);
            }

            // update order item received quantity
            OrderItem orderItem = orderItemMapper.selectById(item.getOrderItemId());
            if (orderItem != null) {
                orderItem.setReceivedQuantity(
                        orderItem.getReceivedQuantity().add(item.getActualQuantity()));
                orderItemMapper.updateById(orderItem);
            }
        }

        // update order delivery status
        orderService.updateDeliveryStatus(dto.getOrderId());
    }

    @AuditLog(module = "DELIVERY", operation = "STATUS_CHANGE")
    public void receive(Long id, Long receiverId) {
        Delivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        delivery.setStatus(DeliveryStatus.INSPECTING.name());
        delivery.setReceiverId(receiverId);
        delivery.setReceiveTime(LocalDateTime.now());
        delivery.setUpdateTime(LocalDateTime.now());
        deliveryMapper.updateById(delivery);
    }

    public Page<DeliveryVO> page(int pageNum, int pageSize, Long orderId) {
        LambdaQueryWrapper<Delivery> wrapper = new LambdaQueryWrapper<>();
        if (orderId != null) wrapper.eq(Delivery::getOrderId, orderId);
        wrapper.orderByDesc(Delivery::getCreateTime);
        Page<Delivery> page = deliveryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<DeliveryVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public DeliveryVO getById(Long id) {
        Delivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        return toVO(delivery);
    }

    public void updateStatus(Long id, String status) {
        Delivery delivery = deliveryMapper.selectById(id);
        if (delivery != null) {
            delivery.setStatus(status);
            delivery.setUpdateTime(LocalDateTime.now());
            deliveryMapper.updateById(delivery);
        }
    }

    private DeliveryVO toVO(Delivery d) {
        DeliveryVO vo = new DeliveryVO();
        vo.setId(d.getId());
        vo.setDeliveryNo(d.getDeliveryNo());
        vo.setOrderId(d.getOrderId());
        vo.setSupplierId(d.getSupplierId());
        vo.setStatus(d.getStatus());
        vo.setDeliveryDate(d.getDeliveryDate());
        vo.setReceiverId(d.getReceiverId());
        vo.setReceiveTime(d.getReceiveTime());
        vo.setRemark(d.getRemark());
        vo.setCreateTime(d.getCreateTime());

        List<DeliveryItem> items = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<DeliveryItem>().eq(DeliveryItem::getDeliveryId, d.getId()));
        vo.setItems(items.stream().map(i -> {
            DeliveryVO.DeliveryItemVO iv = new DeliveryVO.DeliveryItemVO();
            iv.setId(i.getId());
            iv.setOrderItemId(i.getOrderItemId());
            iv.setMaterialId(i.getMaterialId());
            iv.setExpectedQuantity(i.getExpectedQuantity());
            iv.setActualQuantity(i.getActualQuantity());
            return iv;
        }).toList());

        List<DeliveryDiff> diffs = diffMapper.selectList(
                new LambdaQueryWrapper<DeliveryDiff>().eq(DeliveryDiff::getDeliveryId, d.getId()));
        vo.setDiffs(diffs.stream().map(df -> {
            DeliveryVO.DeliveryDiffVO dv = new DeliveryVO.DeliveryDiffVO();
            dv.setId(df.getId());
            dv.setMaterialId(df.getMaterialId());
            dv.setDiffType(df.getDiffType());
            dv.setDiffQuantity(df.getDiffQuantity());
            dv.setDescription(df.getDescription());
            dv.setCreateTime(df.getCreateTime());
            return dv;
        }).toList());
        return vo;
    }
}
