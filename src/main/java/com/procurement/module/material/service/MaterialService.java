package com.procurement.module.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.material.dto.MaterialCreateDTO;
import com.procurement.module.material.entity.Material;
import com.procurement.module.material.entity.MaterialCategory;
import com.procurement.module.material.mapper.MaterialCategoryMapper;
import com.procurement.module.material.mapper.MaterialMapper;
import com.procurement.module.material.vo.MaterialVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final CodeGenerator codeGenerator;

    public Page<MaterialVO> page(int pageNum, int pageSize, String keyword, Long categoryId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Material::getMaterialName, keyword)
                    .or().like(Material::getMaterialCode, keyword);
        }
        if (categoryId != null) {
            wrapper.eq(Material::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(Material::getCreateTime);
        Page<Material> page = materialMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<MaterialVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public MaterialVO getById(Long id) {
        Material m = materialMapper.selectById(id);
        if (m == null) throw new BizException(ErrorCode.MATERIAL_NOT_FOUND);
        return toVO(m);
    }

    @AuditLog(module = "MATERIAL", operation = "CREATE")
    public void create(MaterialCreateDTO dto) {
        Material m = new Material();
        m.setMaterialCode(codeGenerator.generate("MAT"));
        m.setCategoryId(dto.getCategoryId());
        m.setMaterialName(dto.getMaterialName());
        m.setSpecification(dto.getSpecification());
        m.setUnit(dto.getUnit());
        m.setReferencePrice(dto.getReferencePrice());
        m.setDescription(dto.getDescription());
        m.setStatus(1);
        materialMapper.insert(m);
    }

    @AuditLog(module = "MATERIAL", operation = "UPDATE")
    public void update(Long id, MaterialCreateDTO dto) {
        Material m = materialMapper.selectById(id);
        if (m == null) throw new BizException(ErrorCode.MATERIAL_NOT_FOUND);
        if (dto.getCategoryId() != null) m.setCategoryId(dto.getCategoryId());
        if (dto.getMaterialName() != null) m.setMaterialName(dto.getMaterialName());
        if (dto.getSpecification() != null) m.setSpecification(dto.getSpecification());
        if (dto.getUnit() != null) m.setUnit(dto.getUnit());
        if (dto.getReferencePrice() != null) m.setReferencePrice(dto.getReferencePrice());
        if (dto.getDescription() != null) m.setDescription(dto.getDescription());
        materialMapper.updateById(m);
    }

    private MaterialVO toVO(Material m) {
        MaterialVO vo = new MaterialVO();
        vo.setId(m.getId());
        vo.setCategoryId(m.getCategoryId());
        vo.setMaterialCode(m.getMaterialCode());
        vo.setMaterialName(m.getMaterialName());
        vo.setSpecification(m.getSpecification());
        vo.setUnit(m.getUnit());
        vo.setReferencePrice(m.getReferencePrice());
        vo.setDescription(m.getDescription());
        vo.setStatus(m.getStatus());
        vo.setCreateTime(m.getCreateTime());
        MaterialCategory cat = categoryMapper.selectById(m.getCategoryId());
        if (cat != null) vo.setCategoryName(cat.getCategoryName());
        return vo;
    }
}
