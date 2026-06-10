package com.procurement.module.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.supplier.dto.SupplierCreateDTO;
import com.procurement.module.supplier.dto.SupplierUpdateDTO;
import com.procurement.module.supplier.entity.Supplier;
import com.procurement.module.supplier.mapper.SupplierMapper;
import com.procurement.module.supplier.vo.SupplierVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierMapper supplierMapper;
    private final CodeGenerator codeGenerator;

    public Page<SupplierVO> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Supplier::getSupplierName, keyword)
                    .or().like(Supplier::getSupplierCode, keyword);
        }
        wrapper.orderByDesc(Supplier::getCreateTime);
        Page<Supplier> page = supplierMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<SupplierVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public SupplierVO getById(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) throw new BizException(ErrorCode.SUPPLIER_NOT_FOUND);
        return toVO(supplier);
    }

    @AuditLog(module = "SUPPLIER", operation = "CREATE")
    public void create(SupplierCreateDTO dto) {
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(codeGenerator.generate("SUP"));
        supplier.setSupplierName(dto.getSupplierName());
        supplier.setContactPerson(dto.getContactPerson());
        supplier.setContactPhone(dto.getContactPhone());
        supplier.setContactEmail(dto.getContactEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setBankName(dto.getBankName());
        supplier.setBankAccount(dto.getBankAccount());
        supplier.setRemark(dto.getRemark());
        supplier.setQualificationStatus("PENDING");
        supplier.setRating(BigDecimal.ZERO);
        supplierMapper.insert(supplier);
    }

    @AuditLog(module = "SUPPLIER", operation = "UPDATE")
    public void update(Long id, SupplierUpdateDTO dto) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) throw new BizException(ErrorCode.SUPPLIER_NOT_FOUND);
        if (dto.getSupplierName() != null) supplier.setSupplierName(dto.getSupplierName());
        if (dto.getContactPerson() != null) supplier.setContactPerson(dto.getContactPerson());
        if (dto.getContactPhone() != null) supplier.setContactPhone(dto.getContactPhone());
        if (dto.getContactEmail() != null) supplier.setContactEmail(dto.getContactEmail());
        if (dto.getAddress() != null) supplier.setAddress(dto.getAddress());
        if (dto.getBankName() != null) supplier.setBankName(dto.getBankName());
        if (dto.getBankAccount() != null) supplier.setBankAccount(dto.getBankAccount());
        if (dto.getRemark() != null) supplier.setRemark(dto.getRemark());
        supplierMapper.updateById(supplier);
    }

    @AuditLog(module = "SUPPLIER", operation = "STATUS_CHANGE")
    public void updateQualification(Long id, String status) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) throw new BizException(ErrorCode.SUPPLIER_NOT_FOUND);
        supplier.setQualificationStatus(status);
        supplierMapper.updateById(supplier);
    }

    public List<SupplierVO> listQualified() {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Supplier::getQualificationStatus, "QUALIFIED");
        return supplierMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    private SupplierVO toVO(Supplier s) {
        SupplierVO vo = new SupplierVO();
        vo.setId(s.getId());
        vo.setSupplierCode(s.getSupplierCode());
        vo.setSupplierName(s.getSupplierName());
        vo.setContactPerson(s.getContactPerson());
        vo.setContactPhone(s.getContactPhone());
        vo.setContactEmail(s.getContactEmail());
        vo.setAddress(s.getAddress());
        vo.setQualificationStatus(s.getQualificationStatus());
        vo.setQualificationExpireDate(s.getQualificationExpireDate());
        vo.setBankName(s.getBankName());
        vo.setBankAccount(s.getBankAccount());
        vo.setRating(s.getRating());
        vo.setRemark(s.getRemark());
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }
}
