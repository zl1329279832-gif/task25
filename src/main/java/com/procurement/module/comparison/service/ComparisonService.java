package com.procurement.module.comparison.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.QuotationStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.comparison.entity.Comparison;
import com.procurement.module.comparison.entity.ComparisonItem;
import com.procurement.module.comparison.mapper.ComparisonItemMapper;
import com.procurement.module.comparison.mapper.ComparisonMapper;
import com.procurement.module.comparison.strategy.ComparisonStrategy;
import com.procurement.module.comparison.vo.ComparisonResultVO;
import com.procurement.module.quotation.entity.Quotation;
import com.procurement.module.quotation.entity.QuotationItem;
import com.procurement.module.quotation.mapper.QuotationItemMapper;
import com.procurement.module.quotation.mapper.QuotationMapper;
import com.procurement.module.quotation.statemachine.QuotationStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final ComparisonMapper comparisonMapper;
    private final ComparisonItemMapper comparisonItemMapper;
    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final CodeGenerator codeGenerator;
    private final Map<String, ComparisonStrategy> strategies;

    @Transactional
    @AuditLog(module = "COMPARISON", operation = "CREATE")
    public ComparisonResultVO compare(Long inquiryId, String type, Long operatorId) {
        // find all frozen quotations for this inquiry
        List<Quotation> quotations = quotationMapper.selectList(
                new LambdaQueryWrapper<Quotation>()
                        .eq(Quotation::getInquiryId, inquiryId)
                        .eq(Quotation::getStatus, QuotationStatus.FROZEN.name()));

        if (quotations.isEmpty()) throw new BizException(ErrorCode.NO_FROZEN_QUOTATION);

        Comparison comparison = new Comparison();
        comparison.setComparisonNo(codeGenerator.generate("CMP"));
        comparison.setInquiryId(inquiryId);
        comparison.setComparisonType(type);
        comparison.setStatus("PENDING");
        comparison.setOperatorId(operatorId);
        comparison.setCreateTime(LocalDateTime.now());
        comparison.setUpdateTime(LocalDateTime.now());
        comparisonMapper.insert(comparison);

        // build comparison items from quotation items
        List<ComparisonItem> items = new ArrayList<>();
        for (Quotation q : quotations) {
            List<QuotationItem> qItems = quotationItemMapper.selectList(
                    new LambdaQueryWrapper<QuotationItem>().eq(QuotationItem::getQuotationId, q.getId()));
            for (QuotationItem qi : qItems) {
                ComparisonItem ci = new ComparisonItem();
                ci.setComparisonId(comparison.getId());
                ci.setQuotationId(q.getId());
                ci.setSupplierId(q.getSupplierId());
                ci.setMaterialId(qi.getMaterialId());
                ci.setUnitPrice(qi.getUnitPrice());
                ci.setIsSelected(0);
                items.add(ci);
            }
        }

        // apply strategy
        ComparisonStrategy strategy = strategies.get(type);
        if (strategy == null) strategy = strategies.get("LOWEST_PRICE");
        items = strategy.evaluate(items);

        for (ComparisonItem ci : items) {
            comparisonItemMapper.insert(ci);
        }

        comparison.setStatus("COMPLETED");
        comparisonMapper.updateById(comparison);

        // mark selected/rejected quotations
        for (Quotation q : quotations) {
            boolean selected = items.stream()
                    .anyMatch(ci -> ci.getQuotationId().equals(q.getId()) && ci.getIsSelected() == 1);
            if (selected) {
                QuotationStateMachine.transition(q, QuotationStatus.SELECTED);
            } else {
                QuotationStateMachine.transition(q, QuotationStatus.REJECTED);
            }
            quotationMapper.updateById(q);
        }

        return toVO(comparison);
    }

    public ComparisonResultVO getById(Long id) {
        Comparison c = comparisonMapper.selectById(id);
        if (c == null) throw new BizException(ErrorCode.COMPARISON_NOT_FOUND);
        return toVO(c);
    }

    private ComparisonResultVO toVO(Comparison c) {
        ComparisonResultVO vo = new ComparisonResultVO();
        vo.setId(c.getId());
        vo.setComparisonNo(c.getComparisonNo());
        vo.setInquiryId(c.getInquiryId());
        vo.setComparisonType(c.getComparisonType());
        vo.setStatus(c.getStatus());
        vo.setCreateTime(c.getCreateTime());

        List<ComparisonItem> items = comparisonItemMapper.selectList(
                new LambdaQueryWrapper<ComparisonItem>().eq(ComparisonItem::getComparisonId, c.getId()));
        vo.setItems(items.stream().map(ci -> {
            ComparisonResultVO.ComparisonItemVO iv = new ComparisonResultVO.ComparisonItemVO();
            iv.setId(ci.getId());
            iv.setQuotationId(ci.getQuotationId());
            iv.setSupplierId(ci.getSupplierId());
            iv.setMaterialId(ci.getMaterialId());
            iv.setUnitPrice(ci.getUnitPrice());
            iv.setScore(ci.getScore());
            iv.setPriceRank(ci.getPriceRank());
            iv.setSelected(ci.getIsSelected() == 1);
            return iv;
        }).toList());
        return vo;
    }
}
