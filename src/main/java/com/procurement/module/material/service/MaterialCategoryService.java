package com.procurement.module.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.module.material.entity.MaterialCategory;
import com.procurement.module.material.mapper.MaterialCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialCategoryService {

    private final MaterialCategoryMapper categoryMapper;

    public List<Map<String, Object>> tree() {
        List<MaterialCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<MaterialCategory>()
                .orderByAsc(MaterialCategory::getSortOrder));
        return buildTree(all, 0L);
    }

    private List<Map<String, Object>> buildTree(List<MaterialCategory> all, Long parentId) {
        return all.stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .map(c -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", c.getId());
                    node.put("categoryCode", c.getCategoryCode());
                    node.put("categoryName", c.getCategoryName());
                    node.put("children", buildTree(all, c.getId()));
                    return node;
                })
                .collect(Collectors.toList());
    }

    public void create(String categoryCode, String categoryName, Long parentId, Integer sortOrder) {
        MaterialCategory cat = new MaterialCategory();
        cat.setCategoryCode(categoryCode);
        cat.setCategoryName(categoryName);
        cat.setParentId(parentId != null ? parentId : 0L);
        cat.setSortOrder(sortOrder != null ? sortOrder : 0);
        categoryMapper.insert(cat);
    }

    public void update(Long id, String categoryName, Integer sortOrder) {
        MaterialCategory cat = categoryMapper.selectById(id);
        if (cat == null) throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        if (categoryName != null) cat.setCategoryName(categoryName);
        if (sortOrder != null) cat.setSortOrder(sortOrder);
        categoryMapper.updateById(cat);
    }

    public void delete(Long id) {
        long children = categoryMapper.selectCount(
                new LambdaQueryWrapper<MaterialCategory>().eq(MaterialCategory::getParentId, id));
        if (children > 0) throw new BizException(ErrorCode.CATEGORY_HAS_CHILDREN);
        categoryMapper.deleteById(id);
    }
}
