package com.procurement.module.quotation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.InquiryStatus;
import com.procurement.common.enums.QuotationStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.inquiry.entity.Inquiry;
import com.procurement.module.inquiry.entity.InquirySupplier;
import com.procurement.module.inquiry.mapper.InquiryMapper;
import com.procurement.module.inquiry.mapper.InquirySupplierMapper;
import com.procurement.module.inquiry.service.InquiryService;
import com.procurement.module.quotation.dto.QuotationSubmitDTO;
import com.procurement.module.quotation.entity.Quotation;
import com.procurement.module.quotation.entity.QuotationItem;
import com.procurement.module.quotation.mapper.QuotationItemMapper;
import com.procurement.module.quotation.mapper.QuotationMapper;
import com.procurement.module.quotation.statemachine.QuotationStateMachine;
import com.procurement.module.quotation.vo.QuotationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper itemMapper;
    private final InquiryMapper inquiryMapper;
    private final InquirySupplierMapper inquirySupplierMapper;
    private final InquiryService inquiryService;
    private final CodeGenerator codeGenerator;

    @Transactional
    @AuditLog(module = "QUOTATION", operation = "CREATE")
    public void submit(QuotationSubmitDTO dto, Long supplierId) {
        Inquiry inquiry = inquiryMapper.selectById(dto.getInquiryId());
        if (inquiry == null) throw new BizException(ErrorCode.INQUIRY_NOT_FOUND);

        // check deadline
        if (inquiry.getDeadline() != null && LocalDateTime.now().isAfter(inquiry.getDeadline())) {
            throw new BizException(ErrorCode.QUOTATION_DEADLINE_PASSED);
        }

        // check invited
        long invited = inquirySupplierMapper.selectCount(
                new LambdaQueryWrapper<InquirySupplier>()
                        .eq(InquirySupplier::getInquiryId, dto.getInquiryId())
                        .eq(InquirySupplier::getSupplierId, supplierId));
        if (invited == 0) throw new BizException(ErrorCode.QUOTATION_NOT_INVITED);

        // check status allows quoting
        String status = inquiry.getStatus();
        if (!InquiryStatus.PUBLISHED.name().equals(status) && !InquiryStatus.QUOTING.name().equals(status)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "Inquiry not open for quoting");
        }

        // transition inquiry to QUOTING if PUBLISHED
        inquiryService.transitionToQuoting(inquiry.getId());

        // find existing quotation and increment version
        Quotation existing = quotationMapper.selectOne(
                new LambdaQueryWrapper<Quotation>()
                        .eq(Quotation::getInquiryId, dto.getInquiryId())
                        .eq(Quotation::getSupplierId, supplierId)
                        .orderByDesc(Quotation::getVersion)
                        .last("LIMIT 1"));

        if (existing != null && QuotationStatus.FROZEN.name().equals(existing.getStatus())) {
            throw new BizException(ErrorCode.QUOTATION_FROZEN);
        }

        int newVersion = (existing != null) ? existing.getVersion() + 1 : 1;

        Quotation quotation = new Quotation();
        quotation.setQuotationNo(codeGenerator.generate("QUO"));
        quotation.setInquiryId(dto.getInquiryId());
        quotation.setSupplierId(supplierId);
        quotation.setVersion(newVersion);
        quotation.setStatus(QuotationStatus.SUBMITTED.name());
        quotation.setValidUntil(dto.getValidUntil());
        quotation.setSubmitTime(LocalDateTime.now());
        quotation.setRemark(dto.getRemark());
        quotationMapper.insert(quotation);

        BigDecimal total = BigDecimal.ZERO;
        for (QuotationSubmitDTO.Item item : dto.getItems()) {
            QuotationItem qi = new QuotationItem();
            qi.setQuotationId(quotation.getId());
            qi.setInquiryItemId(item.getInquiryItemId());
            qi.setMaterialId(item.getMaterialId());
            qi.setUnitPrice(item.getUnitPrice());
            qi.setQuantity(item.getQuantity());
            qi.setAmount(item.getUnitPrice().multiply(item.getQuantity()));
            qi.setDeliveryDays(item.getDeliveryDays());
            qi.setRemark(item.getRemark());
            itemMapper.insert(qi);
            total = total.add(qi.getAmount());
        }
        quotation.setTotalAmount(total);
        quotationMapper.updateById(quotation);
    }

    public QuotationVO getById(Long id) {
        Quotation q = quotationMapper.selectById(id);
        if (q == null) throw new BizException(ErrorCode.QUOTATION_NOT_FOUND);
        return toVO(q);
    }

    public List<QuotationVO> listByInquiry(Long inquiryId) {
        List<Quotation> list = quotationMapper.selectList(
                new LambdaQueryWrapper<Quotation>()
                        .eq(Quotation::getInquiryId, inquiryId)
                        .orderByAsc(Quotation::getSupplierId)
                        .orderByDesc(Quotation::getVersion));
        return list.stream().map(this::toVO).toList();
    }

    public Page<QuotationVO> myQuotations(int pageNum, int pageSize, Long supplierId) {
        LambdaQueryWrapper<Quotation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Quotation::getSupplierId, supplierId).orderByDesc(Quotation::getCreateTime);
        Page<Quotation> page = quotationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<QuotationVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public void freezeByInquiry(Long inquiryId) {
        List<Quotation> list = quotationMapper.selectList(
                new LambdaQueryWrapper<Quotation>()
                        .eq(Quotation::getInquiryId, inquiryId)
                        .eq(Quotation::getStatus, QuotationStatus.SUBMITTED.name()));
        for (Quotation q : list) {
            QuotationStateMachine.transition(q, QuotationStatus.FROZEN);
            q.setFrozenTime(LocalDateTime.now());
            quotationMapper.updateById(q);
        }
    }

    private QuotationVO toVO(Quotation q) {
        QuotationVO vo = new QuotationVO();
        vo.setId(q.getId());
        vo.setQuotationNo(q.getQuotationNo());
        vo.setInquiryId(q.getInquiryId());
        vo.setSupplierId(q.getSupplierId());
        vo.setVersion(q.getVersion());
        vo.setStatus(q.getStatus());
        vo.setTotalAmount(q.getTotalAmount());
        vo.setValidUntil(q.getValidUntil());
        vo.setSubmitTime(q.getSubmitTime());
        vo.setFrozenTime(q.getFrozenTime());
        vo.setRemark(q.getRemark());
        vo.setCreateTime(q.getCreateTime());

        List<QuotationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<QuotationItem>().eq(QuotationItem::getQuotationId, q.getId()));
        vo.setItems(items.stream().map(i -> {
            QuotationVO.QuotationItemVO iv = new QuotationVO.QuotationItemVO();
            iv.setId(i.getId());
            iv.setInquiryItemId(i.getInquiryItemId());
            iv.setMaterialId(i.getMaterialId());
            iv.setUnitPrice(i.getUnitPrice());
            iv.setQuantity(i.getQuantity());
            iv.setAmount(i.getAmount());
            iv.setDeliveryDays(i.getDeliveryDays());
            iv.setRemark(i.getRemark());
            return iv;
        }).toList());
        return vo;
    }
}
