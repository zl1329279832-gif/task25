package com.procurement.module.inquiry.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.InquiryStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.inquiry.dto.InquiryCreateDTO;
import com.procurement.module.inquiry.entity.Inquiry;
import com.procurement.module.inquiry.entity.InquiryItem;
import com.procurement.module.inquiry.entity.InquirySupplier;
import com.procurement.module.inquiry.mapper.InquiryItemMapper;
import com.procurement.module.inquiry.mapper.InquiryMapper;
import com.procurement.module.inquiry.mapper.InquirySupplierMapper;
import com.procurement.module.inquiry.statemachine.InquiryStateMachine;
import com.procurement.module.inquiry.vo.InquiryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;
    private final InquirySupplierMapper supplierMapper;
    private final CodeGenerator codeGenerator;

    public Page<InquiryVO> page(int pageNum, int pageSize, String status, Long buyerId) {
        LambdaQueryWrapper<Inquiry> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Inquiry::getStatus, status);
        if (buyerId != null) wrapper.eq(Inquiry::getBuyerId, buyerId);
        wrapper.orderByDesc(Inquiry::getCreateTime);
        Page<Inquiry> page = inquiryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<InquiryVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public InquiryVO getById(Long id) {
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) throw new BizException(ErrorCode.INQUIRY_NOT_FOUND);
        return toVO(inquiry);
    }

    @Transactional
    @AuditLog(module = "INQUIRY", operation = "CREATE")
    public void create(InquiryCreateDTO dto, Long buyerId) {
        Inquiry inquiry = new Inquiry();
        inquiry.setInquiryNo(codeGenerator.generate("INQ"));
        inquiry.setTitle(dto.getTitle());
        inquiry.setStatus(InquiryStatus.DRAFT.name());
        inquiry.setDeadline(dto.getDeadline());
        inquiry.setBuyerId(buyerId);
        inquiry.setRemark(dto.getRemark());
        inquiryMapper.insert(inquiry);

        for (InquiryCreateDTO.Item item : dto.getItems()) {
            InquiryItem entity = new InquiryItem();
            entity.setInquiryId(inquiry.getId());
            entity.setMaterialId(item.getMaterialId());
            entity.setQuantity(item.getQuantity());
            entity.setExpectedPrice(item.getExpectedPrice());
            entity.setRequiredDate(item.getRequiredDate());
            entity.setRemark(item.getRemark());
            itemMapper.insert(entity);
        }

        if (dto.getSupplierIds() != null) {
            for (Long supplierId : dto.getSupplierIds()) {
                InquirySupplier is = new InquirySupplier();
                is.setInquiryId(inquiry.getId());
                is.setSupplierId(supplierId);
                supplierMapper.insert(is);
            }
        }
    }

    @AuditLog(module = "INQUIRY", operation = "STATUS_CHANGE")
    public void publish(Long id) {
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) throw new BizException(ErrorCode.INQUIRY_NOT_FOUND);
        if (inquiry.getDeadline() == null) throw new BizException(ErrorCode.INQUIRY_DEADLINE_REQUIRED);
        InquiryStateMachine.transition(inquiry, InquiryStatus.PUBLISHED);
        inquiry.setPublishTime(LocalDateTime.now());
        inquiryMapper.updateById(inquiry);
    }

    @AuditLog(module = "INQUIRY", operation = "STATUS_CHANGE")
    public void close(Long id) {
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) throw new BizException(ErrorCode.INQUIRY_NOT_FOUND);
        InquiryStateMachine.transition(inquiry, InquiryStatus.CLOSED);
        inquiry.setCloseTime(LocalDateTime.now());
        inquiryMapper.updateById(inquiry);
    }

    @AuditLog(module = "INQUIRY", operation = "STATUS_CHANGE")
    public void cancel(Long id) {
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) throw new BizException(ErrorCode.INQUIRY_NOT_FOUND);
        InquiryStateMachine.transition(inquiry, InquiryStatus.CANCELLED);
        inquiryMapper.updateById(inquiry);
    }

    public void transitionToQuoting(Long id) {
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry != null && InquiryStatus.PUBLISHED.name().equals(inquiry.getStatus())) {
            InquiryStateMachine.transition(inquiry, InquiryStatus.QUOTING);
            inquiryMapper.updateById(inquiry);
        }
    }

    public Page<InquiryVO> pageForSupplier(int pageNum, int pageSize, Long supplierId) {
        List<InquirySupplier> invited = supplierMapper.selectList(
                new LambdaQueryWrapper<InquirySupplier>().eq(InquirySupplier::getSupplierId, supplierId));
        List<Long> inquiryIds = invited.stream().map(InquirySupplier::getInquiryId).toList();
        if (inquiryIds.isEmpty()) return new Page<>(pageNum, pageSize, 0);

        LambdaQueryWrapper<Inquiry> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Inquiry::getId, inquiryIds)
                .in(Inquiry::getStatus, List.of(InquiryStatus.PUBLISHED.name(), InquiryStatus.QUOTING.name()))
                .orderByDesc(Inquiry::getCreateTime);
        Page<Inquiry> page = inquiryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<InquiryVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    private InquiryVO toVO(Inquiry inquiry) {
        InquiryVO vo = new InquiryVO();
        vo.setId(inquiry.getId());
        vo.setInquiryNo(inquiry.getInquiryNo());
        vo.setTitle(inquiry.getTitle());
        vo.setStatus(inquiry.getStatus());
        vo.setPublishTime(inquiry.getPublishTime());
        vo.setDeadline(inquiry.getDeadline());
        vo.setCloseTime(inquiry.getCloseTime());
        vo.setBuyerId(inquiry.getBuyerId());
        vo.setRemark(inquiry.getRemark());
        vo.setCreateTime(inquiry.getCreateTime());

        List<InquiryItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InquiryItem>().eq(InquiryItem::getInquiryId, inquiry.getId()));
        vo.setItems(items.stream().map(i -> {
            InquiryVO.InquiryItemVO itemVO = new InquiryVO.InquiryItemVO();
            itemVO.setId(i.getId());
            itemVO.setMaterialId(i.getMaterialId());
            itemVO.setQuantity(i.getQuantity());
            itemVO.setExpectedPrice(i.getExpectedPrice());
            itemVO.setRequiredDate(i.getRequiredDate());
            itemVO.setRemark(i.getRemark());
            return itemVO;
        }).toList());

        List<InquirySupplier> suppliers = supplierMapper.selectList(
                new LambdaQueryWrapper<InquirySupplier>().eq(InquirySupplier::getInquiryId, inquiry.getId()));
        vo.setSupplierIds(suppliers.stream().map(InquirySupplier::getSupplierId).toList());

        return vo;
    }
}
