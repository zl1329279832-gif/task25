package com.procurement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.entity.AdmissionResult;
import com.procurement.entity.SupplierAdmissionLog;

public interface AdmissionControlService {

    AdmissionResult checkAdmission(Long supplierId, String checkpoint, Long businessId);

    Page<SupplierAdmissionLog> getAdmissionLogs(Long supplierId, int page, int size);
}
