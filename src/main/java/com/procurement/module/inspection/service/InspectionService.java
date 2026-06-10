package com.procurement.module.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.DeliveryStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.delivery.service.DeliveryService;
import com.procurement.module.inspection.dto.InspectionCreateDTO;
import com.procurement.module.inspection.entity.Inspection;
import com.procurement.module.inspection.mapper.InspectionMapper;
import com.procurement.module.inspection.vo.InspectionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionMapper inspectionMapper;
    private final DeliveryService deliveryService;
    private final CodeGenerator codeGenerator;

    @Transactional
    @AuditLog(module = "INSPECTION", operation = "CREATE")
    public void create(InspectionCreateDTO dto, Long inspectorId) {
        Inspection inspection = new Inspection();
        inspection.setInspectionNo(codeGenerator.generate("INS"));
        inspection.setDeliveryId(dto.getDeliveryId());
        inspection.setDeliveryItemId(dto.getDeliveryItemId());
        inspection.setMaterialId(dto.getMaterialId());
        inspection.setInspectQuantity(dto.getInspectQuantity());
        inspection.setQualifiedQuantity(dto.getQualifiedQuantity());
        inspection.setUnqualifiedQuantity(dto.getUnqualifiedQuantity() != null ?
                dto.getUnqualifiedQuantity() :
                dto.getInspectQuantity().subtract(dto.getQualifiedQuantity()));
        inspection.setResult(dto.getResult());
        inspection.setInspectorId(inspectorId);
        inspection.setInspectTime(LocalDateTime.now());
        inspection.setRemark(dto.getRemark());
        inspection.setCreateTime(LocalDateTime.now());
        inspectionMapper.insert(inspection);

        // Update delivery status based on result
        String deliveryStatus;
        switch (dto.getResult()) {
            case "QUALIFIED" -> deliveryStatus = DeliveryStatus.ACCEPTED.name();
            case "UNQUALIFIED" -> deliveryStatus = DeliveryStatus.REJECTED.name();
            case "CONCESSION_ACCEPT" -> deliveryStatus = DeliveryStatus.PARTIAL_ACCEPTED.name();
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        }
        deliveryService.updateStatus(dto.getDeliveryId(), deliveryStatus);
    }

    public Page<InspectionVO> page(int pageNum, int pageSize, Long deliveryId) {
        LambdaQueryWrapper<Inspection> wrapper = new LambdaQueryWrapper<>();
        if (deliveryId != null) wrapper.eq(Inspection::getDeliveryId, deliveryId);
        wrapper.orderByDesc(Inspection::getCreateTime);
        Page<Inspection> page = inspectionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<InspectionVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public InspectionVO getById(Long id) {
        Inspection ins = inspectionMapper.selectById(id);
        if (ins == null) throw new BizException(ErrorCode.PARAM_ERROR);
        return toVO(ins);
    }

    private InspectionVO toVO(Inspection ins) {
        InspectionVO vo = new InspectionVO();
        vo.setId(ins.getId());
        vo.setInspectionNo(ins.getInspectionNo());
        vo.setDeliveryId(ins.getDeliveryId());
        vo.setDeliveryItemId(ins.getDeliveryItemId());
        vo.setMaterialId(ins.getMaterialId());
        vo.setInspectQuantity(ins.getInspectQuantity());
        vo.setQualifiedQuantity(ins.getQualifiedQuantity());
        vo.setUnqualifiedQuantity(ins.getUnqualifiedQuantity());
        vo.setResult(ins.getResult());
        vo.setInspectorId(ins.getInspectorId());
        vo.setInspectTime(ins.getInspectTime());
        vo.setRemark(ins.getRemark());
        vo.setCreateTime(ins.getCreateTime());
        return vo;
    }
}
