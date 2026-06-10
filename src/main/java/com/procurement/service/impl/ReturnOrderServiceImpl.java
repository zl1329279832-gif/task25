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

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnOrderServiceImpl implements ReturnOrderService {

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnLineMapper returnLineMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderLineMapper poLineMapper;

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
    @Transactional
    @Auditable(action = "MARK_RETURNED", entityType = "ReturnOrder")
    public void markReturned(Long id) {
        ReturnOrder ro = returnOrderMapper.selectById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        if (!"APPROVED".equals(ro.getStatus())) throw new BusinessException("退货单未审批通过");
        ro.setStatus("RETURNED");
        returnOrderMapper.updateById(ro);

        // 回退 PO 行的已收货数量
        if (ro.getPoId() != null) {
            List<ReturnLine> retLines = returnLineMapper.selectList(
                    new LambdaQueryWrapper<ReturnLine>().eq(ReturnLine::getReturnId, id));
            List<PurchaseOrderLine> poLines = poLineMapper.selectList(
                    new LambdaQueryWrapper<PurchaseOrderLine>().eq(PurchaseOrderLine::getPoId, ro.getPoId()));

            for (ReturnLine rl : retLines) {
                // 按 materialId 匹配 PO 行
                for (PurchaseOrderLine poLine : poLines) {
                    if (poLine.getMaterialId().equals(rl.getMaterialId())) {
                        BigDecimal newReceivedQty = poLine.getReceivedQty().subtract(rl.getQuantity());
                        poLine.setReceivedQty(newReceivedQty.max(BigDecimal.ZERO));
                        poLineMapper.updateById(poLine);
                        break;
                    }
                }
            }

            // 重新评估 PO 状态
            PurchaseOrder po = poMapper.selectById(ro.getPoId());
            if (po != null) {
                boolean allReceived = poLines.stream()
                        .allMatch(l -> l.getReceivedQty().compareTo(l.getQuantity()) >= 0);
                boolean anyReceived = poLines.stream()
                        .anyMatch(l -> l.getReceivedQty().compareTo(BigDecimal.ZERO) > 0);
                if (allReceived) {
                    po.setStatus(PoStatus.RECEIVED.name());
                } else if (anyReceived) {
                    po.setStatus(PoStatus.PARTIAL_RECEIVED.name());
                } else {
                    po.setStatus(PoStatus.CONFIRMED.name());
                }
                poMapper.updateById(po);
            }
        }
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
