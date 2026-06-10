package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.ReturnOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnOrderServiceImpl implements ReturnOrderService {

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnLineMapper returnLineMapper;

    @Override
    @Transactional
    @Auditable(action = "CREATE_RETURN", entityType = "ReturnOrder")
    public ReturnOrder create(ReturnOrder ro, List<ReturnLine> lines) {
        LoginUser user = getCurrentUser();
        ro.setCreatedBy(user.getUserId());
        ro.setStatus("PENDING");
        ro.setReturnNo("RET-" + System.currentTimeMillis());
        returnOrderMapper.insert(ro);

        for (ReturnLine line : lines) {
            line.setReturnId(ro.getId());
            returnLineMapper.insert(line);
        }
        return ro;
    }

    @Override
    @Auditable(action = "APPROVE_RETURN", entityType = "ReturnOrder")
    public void approve(Long id) {
        ReturnOrder ro = returnOrderMapper.selectById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        if (!"PENDING".equals(ro.getStatus())) throw new BusinessException("当前状态不允许审批");
        ro.setStatus("APPROVED");
        returnOrderMapper.updateById(ro);
    }

    @Override
    @Auditable(action = "REJECT_RETURN", entityType = "ReturnOrder")
    public void reject(Long id) {
        ReturnOrder ro = returnOrderMapper.selectById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        ro.setStatus("REJECTED");
        returnOrderMapper.updateById(ro);
    }

    @Override
    @Auditable(action = "MARK_RETURNED", entityType = "ReturnOrder")
    public void markReturned(Long id) {
        ReturnOrder ro = returnOrderMapper.selectById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        if (!"APPROVED".equals(ro.getStatus())) throw new BusinessException("退货单未审批通过");
        ro.setStatus("RETURNED");
        returnOrderMapper.updateById(ro);
    }

    @Override
    public ReturnOrder getById(Long id) {
        ReturnOrder ro = returnOrderMapper.selectById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        return ro;
    }

    @Override
    public List<ReturnLine> getLines(Long returnId) {
        return returnLineMapper.selectList(
                new LambdaQueryWrapper<ReturnLine>().eq(ReturnLine::getReturnId, returnId));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
