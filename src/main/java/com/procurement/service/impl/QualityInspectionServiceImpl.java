package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.QualityInspectionService;
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

        // 更新到货行项的验收数量
        for (Map<String, Object> lr : lineResults) {
            Long lineId = Long.valueOf(lr.get("lineId").toString());
            BigDecimal acceptedQty = new BigDecimal(lr.get("acceptedQty").toString());
            ArrivalLine line = arrivalLineMapper.selectById(lineId);
            if (line != null) {
                line.setAcceptedQty(acceptedQty);
                arrivalLineMapper.updateById(line);
            }
        }

        // 更新到货单状态
        switch (result) {
            case "PASS" -> arrival.setStatus(ArrivalStatus.ACCEPTED.name());
            case "FAIL" -> arrival.setStatus(ArrivalStatus.REJECTED.name());
            case "CONDITIONAL" -> arrival.setStatus(ArrivalStatus.PARTIAL_ACCEPTED.name());
        }
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
