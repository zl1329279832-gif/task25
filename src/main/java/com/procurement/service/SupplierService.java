package com.procurement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.entity.Supplier;

public interface SupplierService {
    Supplier create(Supplier supplier);
    Supplier update(Long id, Supplier supplier);
    void disable(Long id);
    void blacklist(Long id);
    Supplier getById(Long id);
    Page<Supplier> list(String keyword, int page, int size);
}
