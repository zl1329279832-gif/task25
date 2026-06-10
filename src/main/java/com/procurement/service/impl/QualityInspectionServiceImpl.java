package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.QualityInspectionService;
import com.procurement.state.ArrivalStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QualityInspectionServiceImpl implements QualityInspectionService {

    private final QualityInspectionMapper inspectionMapper;
    private final ArrivalMapper arrivalMapper;
    private final ArrivalLineMapper arrivalLineMapper;
    private final PurchaseOrderLineMapper poLineMapper;

    @Override
    @Transactional
    @Auditable(action = "QUALITY_INSPECTION", entityType = "QualityInspection")
    public QualityInspection inspect(Long arrivalId, String result,
                                      List<Map<String, Object>> lineResults, String remark) {
        Arrival arrival = arrivalMapper.selectById(arrivalId);
        if (arrival == null) throw new BusinessException("到货单不存在");

        LoginUser user = getCurrentUser();
        QualityInspection inspection = new QualityInspection();
        inspection.setInspectionNo("QI-" + System.currentTimeMillis());
        inspection.setArrivalId(arrivalId);
        inspection.setInspectorId(user.getUserId());
        inspection.setResult(result);
        inspection.setRemark(remark);
        inspection.setInspectedAt(LocalDateTime.now());
        inspectionMapper.insert(inspection);

        // 更新到货行项的验收数量，同步回写 PO 行的验收/退回数量
        for (Map<String, Object> lr : lineResults) {
            Long lineId = Long.valueOf(lr.get("lineId").toString());
            BigDecimal acceptedQty = new BigDecimal(lr.get("acceptedQty").toString());
            ArrivalLine line = arrivalLineMapper.selectById(lineId);
            if (line != null) {
                line.setAcceptedQty(acceptedQty);
                arrivalLineMapper.updateById(line);

                // 回写 PO 行的 acceptedQty / rejectedQty
                PurchaseOrderLine poLine = poLineMapper.selectById(line.getPoLineId());
                if (poLine != null) {
                    BigDecimal rejectedQty = line.getArrivedQty().subtract(acceptedQty);
                    poLine.setAcceptedQty(poLine.getAcceptedQty().add(acceptedQty));
                    poLine.setRejectedQty(poLine.getRejectedQty().add(rejectedQty));
                    poLineMapper.updateById(poLine);
                }
            }
        }

        // 更新到货单状态（使用状态机校验）
        ArrivalStatus currentStatus = ArrivalStatus.valueOf(arrival.getStatus());
        ArrivalStatus newStatus;
        switch (result) {
            case "PASS" -> newStatus = ArrivalStatus.ACCEPTED;
            case "FAIL" -> newStatus = ArrivalStatus.REJECTED;
            case "CONDITIONAL" -> newStatus = ArrivalStatus.PARTIAL_ACCEPTED;
            default -> throw new BusinessException("无效质检结果: " + result);
        }
        ArrivalStateMachine.validateTransition(currentStatus, newStatus);
        arrival.setStatus(newStatus.name());
        arrivalMapper.updateById(arrival);

        return inspection;
    }

    @Override
    public QualityInspection getByArrivalId(Long arrivalId) {
        return inspectionMapper.selectOne(
                new LambdaQueryWrapper<QualityInspection>()
                        .eq(QualityInspection::getArrivalId, arrivalId));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
