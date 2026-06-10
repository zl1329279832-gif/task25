package com.procurement.module.returns.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.ReturnStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.returns.dto.ReturnCreateDTO;
import com.procurement.module.returns.entity.ReturnItem;
import com.procurement.module.returns.entity.ReturnOrder;
import com.procurement.module.returns.mapper.ReturnItemMapper;
import com.procurement.module.returns.mapper.ReturnOrderMapper;
import com.procurement.module.returns.statemachine.ReturnStateMachine;
import com.procurement.module.returns.vo.ReturnOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnOrderService {

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnItemMapper returnItemMapper;
    private final ReturnStateMachine stateMachine;
    private final CodeGenerator codeGenerator;

    @Transactional
    @AuditLog(module = "RETURN", operation = "CREATE")
    public void create(ReturnCreateDTO dto, Long applicantId) {
        ReturnOrder ro = new ReturnOrder();
        ro.setReturnNo(codeGenerator.generate("RET"));
        ro.setOrderId(dto.getOrderId());
        ro.setSupplierId(dto.getSupplierId());
        ro.setDeliveryId(dto.getDeliveryId());
        ro.setInspectionId(dto.getInspectionId());
        ro.setStatus(ReturnStatus.PENDING.name());
        ro.setReason(dto.getReason());
        ro.setApplicantId(applicantId);
        ro.setCreateTime(LocalDateTime.now());
        ro.setUpdateTime(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        returnOrderMapper.insert(ro);

        for (ReturnCreateDTO.Item item : dto.getItems()) {
            ReturnItem ri = new ReturnItem();
            ri.setReturnId(ro.getId());
            ri.setMaterialId(item.getMaterialId());
            ri.setReturnQuantity(item.getReturnQuantity());
            ri.setUnitPrice(item.getUnitPrice());
            BigDecimal amount = item.getUnitPrice().multiply(item.getReturnQuantity());
            ri.setAmount(amount);
            ri.setReason(item.getReason());
            returnItemMapper.insert(ri);
            total = total.add(amount);
        }

        ro.setTotalAmount(total);
        returnOrderMapper.updateById(ro);
    }

    @AuditLog(module = "RETURN", operation = "STATUS_CHANGE")
    public void supplierConfirm(Long id) {
        ReturnOrder ro = getEntity(id);
        stateMachine.validateTransition(ReturnStatus.valueOf(ro.getStatus()), ReturnStatus.SUPPLIER_CONFIRMED);
        ro.setStatus(ReturnStatus.SUPPLIER_CONFIRMED.name());
        ro.setSupplierConfirmTime(LocalDateTime.now());
        ro.setUpdateTime(LocalDateTime.now());
        returnOrderMapper.updateById(ro);
    }

    @AuditLog(module = "RETURN", operation = "STATUS_CHANGE")
    public void ship(Long id) {
        ReturnOrder ro = getEntity(id);
        stateMachine.validateTransition(ReturnStatus.valueOf(ro.getStatus()), ReturnStatus.RETURNING);
        ro.setStatus(ReturnStatus.RETURNING.name());
        ro.setUpdateTime(LocalDateTime.now());
        returnOrderMapper.updateById(ro);
    }

    @AuditLog(module = "RETURN", operation = "STATUS_CHANGE")
    public void complete(Long id) {
        ReturnOrder ro = getEntity(id);
        stateMachine.validateTransition(ReturnStatus.valueOf(ro.getStatus()), ReturnStatus.COMPLETED);
        ro.setStatus(ReturnStatus.COMPLETED.name());
        ro.setCompleteTime(LocalDateTime.now());
        ro.setUpdateTime(LocalDateTime.now());
        returnOrderMapper.updateById(ro);
    }

    public Page<ReturnOrderVO> page(int pageNum, int pageSize, Long orderId) {
        LambdaQueryWrapper<ReturnOrder> wrapper = new LambdaQueryWrapper<>();
        if (orderId != null) wrapper.eq(ReturnOrder::getOrderId, orderId);
        wrapper.orderByDesc(ReturnOrder::getCreateTime);
        Page<ReturnOrder> page = returnOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ReturnOrderVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public ReturnOrderVO getById(Long id) {
        return toVO(getEntity(id));
    }

    public BigDecimal getReturnAmountBySupplierAndPeriod(Long supplierId, java.time.LocalDate start, java.time.LocalDate end) {
        LambdaQueryWrapper<ReturnOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReturnOrder::getSupplierId, supplierId)
                .eq(ReturnOrder::getStatus, ReturnStatus.COMPLETED.name())
                .ge(ReturnOrder::getCompleteTime, start.atStartOfDay())
                .lt(ReturnOrder::getCompleteTime, end.plusDays(1).atStartOfDay());
        List<ReturnOrder> returns = returnOrderMapper.selectList(wrapper);
        return returns.stream()
                .map(ReturnOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ReturnOrder getEntity(Long id) {
        ReturnOrder ro = returnOrderMapper.selectById(id);
        if (ro == null) throw new BizException(ErrorCode.RETURN_NOT_FOUND);
        return ro;
    }

    private ReturnOrderVO toVO(ReturnOrder ro) {
        ReturnOrderVO vo = new ReturnOrderVO();
        vo.setId(ro.getId());
        vo.setReturnNo(ro.getReturnNo());
        vo.setOrderId(ro.getOrderId());
        vo.setSupplierId(ro.getSupplierId());
        vo.setDeliveryId(ro.getDeliveryId());
        vo.setInspectionId(ro.getInspectionId());
        vo.setStatus(ro.getStatus());
        vo.setReason(ro.getReason());
        vo.setTotalAmount(ro.getTotalAmount());
        vo.setApplicantId(ro.getApplicantId());
        vo.setSupplierConfirmTime(ro.getSupplierConfirmTime());
        vo.setCompleteTime(ro.getCompleteTime());
        vo.setCreateTime(ro.getCreateTime());

        List<ReturnItem> items = returnItemMapper.selectList(
                new LambdaQueryWrapper<ReturnItem>().eq(ReturnItem::getReturnId, ro.getId()));
        vo.setItems(items.stream().map(i -> {
            ReturnOrderVO.ReturnItemVO iv = new ReturnOrderVO.ReturnItemVO();
            iv.setId(i.getId());
            iv.setMaterialId(i.getMaterialId());
            iv.setReturnQuantity(i.getReturnQuantity());
            iv.setUnitPrice(i.getUnitPrice());
            iv.setAmount(i.getAmount());
            iv.setReason(i.getReason());
            return iv;
        }).toList());
        return vo;
    }
}
