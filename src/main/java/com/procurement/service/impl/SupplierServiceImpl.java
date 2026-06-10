package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.Supplier;
import com.procurement.entity.SupplierScore;
import com.procurement.mapper.SupplierMapper;
import com.procurement.mapper.SupplierScoreMapper;
import com.procurement.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierMapper supplierMapper;
    private final SupplierScoreMapper supplierScoreMapper;

    @Override
    @Auditable(action = "CREATE_SUPPLIER", entityType = "Supplier")
    public Supplier create(Supplier supplier) {
        supplier.setStatus("ACTIVE");
        supplierMapper.insert(supplier);
        return supplier;
    }

    @Override
    @Auditable(action = "UPDATE_SUPPLIER", entityType = "Supplier")
    public Supplier update(Long id, Supplier supplier) {
        Supplier existing = supplierMapper.selectById(id);
        if (existing == null) throw new BusinessException("供应商不存在");
        supplier.setId(id);
        supplierMapper.updateById(supplier);
        return supplier;
    }

    @Override
    @Auditable(action = "DISABLE_SUPPLIER", entityType = "Supplier")
    public void disable(Long id) {
        Supplier s = supplierMapper.selectById(id);
        if (s == null) throw new BusinessException("供应商不存在");
        s.setStatus("DISABLED");
        supplierMapper.updateById(s);
    }

    @Override
    @Auditable(action = "BLACKLIST_SUPPLIER", entityType = "Supplier")
    public void blacklist(Long id) {
        Supplier s = supplierMapper.selectById(id);
        if (s == null) throw new BusinessException("供应商不存在");
        s.setStatus("BLACKLISTED");
        supplierMapper.updateById(s);

        // 黑名单联动：将评分归零
        SupplierScore score = supplierScoreMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SupplierScore>()
                        .eq(SupplierScore::getSupplierId, id));
        if (score != null) {
            score.setTotalScore(BigDecimal.ZERO);
            score.setCalculatedAt(LocalDateTime.now());
            score.setSource("MANUAL");
            supplierScoreMapper.updateById(score);
        }
    }

    @Override
    public Supplier getById(Long id) {
        Supplier s = supplierMapper.selectById(id);
        if (s == null) throw new BusinessException("供应商不存在");
        return s;
    }

    @Override
    public Page<Supplier> list(String keyword, int page, int size) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Supplier::getName, keyword)
                   .or().like(Supplier::getCode, keyword);
        }
        wrapper.orderByDesc(Supplier::getCreatedAt);
        return supplierMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
