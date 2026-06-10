package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.AdmissionControlService;
import com.procurement.service.QuoteService;
import com.procurement.service.RfqService;
import com.procurement.state.RfqStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RfqServiceImpl implements RfqService {

    private final RfqMapper rfqMapper;
    private final RfqLineMapper rfqLineMapper;
    private final RfqSupplierMapper rfqSupplierMapper;
    private final QuoteService quoteService;
    private final AdmissionControlService admissionControlService;

    @Override
    @Transactional
    @Auditable(action = "CREATE_RFQ", entityType = "Rfq")
    public Rfq create(Rfq rfq, List<RfqLine> lines, List<Long> supplierIds) {
        LoginUser user = getCurrentUser();
        rfq.setPurchaserId(user.getUserId());
        rfq.setStatus(RfqStatus.DRAFT.name());
        rfq.setRfqNo(generateRfqNo());
        rfqMapper.insert(rfq);

        for (RfqLine line : lines) {
            line.setRfqId(rfq.getId());
            rfqLineMapper.insert(line);
        }

        for (Long sid : supplierIds) {
            // 准入检查：黑名单和停用供应商会被拦截（抛异常）
            // 低分供应商允许邀请但记录日志
            try {
                admissionControlService.checkAdmission(sid, "RFQ_INVITE", rfq.getId());
            } catch (BusinessException e) {
                // 跳过被拦截的供应商，不中断整个RFQ创建
                continue;
            }

            RfqSupplier rs = new RfqSupplier();
            rs.setRfqId(rfq.getId());
            rs.setSupplierId(sid);
            rs.setInvitedAt(LocalDateTime.now());
            rfqSupplierMapper.insert(rs);
        }

        return rfq;
    }

    @Override
    @Auditable(action = "PUBLISH_RFQ", entityType = "Rfq")
    public Rfq publish(Long rfqId) {
        Rfq rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) throw new BusinessException("询价单不存在");
        RfqStateMachine.validateTransition(RfqStatus.valueOf(rfq.getStatus()), RfqStatus.PUBLISHED);
        rfq.setStatus(RfqStatus.PUBLISHED.name());
        rfqMapper.updateById(rfq);
        return rfq;
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE_RFQ", entityType = "Rfq")
    public void close(Long rfqId) {
        Rfq rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) throw new BusinessException("询价单不存在");
        RfqStateMachine.validateTransition(RfqStatus.valueOf(rfq.getStatus()), RfqStatus.CLOSED);
        rfq.setStatus(RfqStatus.CLOSED.name());
        rfqMapper.updateById(rfq);
        quoteService.freezeAllByRfq(rfqId);
    }

    @Override
    @Auditable(action = "CANCEL_RFQ", entityType = "Rfq")
    public void cancel(Long rfqId) {
        Rfq rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) throw new BusinessException("询价单不存在");
        RfqStateMachine.validateTransition(RfqStatus.valueOf(rfq.getStatus()), RfqStatus.CANCELLED);
        rfq.setStatus(RfqStatus.CANCELLED.name());
        rfqMapper.updateById(rfq);
    }

    @Override
    public Rfq getById(Long id) {
        Rfq rfq = rfqMapper.selectById(id);
        if (rfq == null) throw new BusinessException("询价单不存在");
        return rfq;
    }

    @Override
    public Page<Rfq> list(String status, int page, int size) {
        LambdaQueryWrapper<Rfq> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) wrapper.eq(Rfq::getStatus, status);
        wrapper.orderByDesc(Rfq::getCreatedAt);
        return rfqMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<RfqLine> getLines(Long rfqId) {
        return rfqLineMapper.selectList(
                new LambdaQueryWrapper<RfqLine>().eq(RfqLine::getRfqId, rfqId));
    }

    private String generateRfqNo() {
        return "RFQ-" + System.currentTimeMillis();
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
