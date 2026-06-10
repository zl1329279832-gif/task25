package com.procurement.service;

import com.procurement.entity.QualityInspection;
import java.util.List;
import java.util.Map;

public interface QualityInspectionService {
    /**
     * 创建质检记录，更新到货行项的验收数量
     */
    QualityInspection inspect(Long arrivalId, String result, List<Map<String, Object>> lineResults, String remark);
    QualityInspection getByArrivalId(Long arrivalId);
}
