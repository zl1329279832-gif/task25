package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.Material;
import com.procurement.mapper.MaterialMapper;
import com.procurement.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;

    @Override
    @Auditable(action = "CREATE_MATERIAL", entityType = "Material")
    public Material create(Material material) {
        materialMapper.insert(material);
        return material;
    }

    @Override
    @Auditable(action = "UPDATE_MATERIAL", entityType = "Material")
    public Material update(Long id, Material material) {
        Material existing = materialMapper.selectById(id);
        if (existing == null) throw new BusinessException("物料不存在");
        material.setId(id);
        materialMapper.updateById(material);
        return material;
    }

    @Override
    public Material getById(Long id) {
        Material m = materialMapper.selectById(id);
        if (m == null) throw new BusinessException("物料不存在");
        return m;
    }

    @Override
    public Page<Material> list(String keyword, String category, int page, int size) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Material::getName, keyword)
                   .or().like(Material::getCode, keyword);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Material::getCategory, category);
        }
        wrapper.orderByDesc(Material::getCreatedAt);
        return materialMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
