package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.ArrivalService;
import com.procurement.state.ArrivalStateMachine;
import com.procurement.state.PurchaseOrderStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArrivalServiceImpl implements ArrivalService {

    private final ArrivalMapper arrivalMapper;
    private final ArrivalLineMapper arrivalLineMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderLineMapper poLineMapper;

    @Override
    @Transactional
    @Auditable(action = "CREATE_ARRIVAL", entityType = "Arrival")
    public Arrival createArrival(Arrival arrival, List<ArrivalLine> lines) {
        PurchaseOrder po = poMapper.selectById(arrival.getPoId());
        if (po == null) throw new BusinessException("采购订单不存在");

        PoStatus poStatus = PoStatus.valueOf(po.getStatus());
        // 只有 CONFIRMED 或 PARTIAL_RECEIVED 状态可以收货
        if (poStatus != PoStatus.CONFIRMED && poStatus != PoStatus.PARTIAL_RECEIVED) {
            throw new BusinessException("当前订单状态不允许收货: " + poStatus);
        }

        LoginUser user = getCurrentUser();
        arrival.setReceiverId(user.getUserId());
        arrival.setStatus(ArrivalStatus.PENDING.name());
        arrival.setArrivalNo("ARR-" + System.currentTimeMillis());

        // 计算批次号
        List<Arrival> existingArrivals = arrivalMapper.selectList(
                new LambdaQueryWrapper<Arrival>().eq(Arrival::getPoId, arrival.getPoId()));
        arrival.setBatchNo(existingArrivals.size() + 1);

        arrivalMapper.insert(arrival);

        // 处理行项 - 计算差异
        for (ArrivalLine line : lines) {
            line.setArrivalId(arrival.getId());

            // 获取对应的 PO Line
            PurchaseOrderLine poLine = poLineMapper.selectById(line.getPoLineId());
            if (poLine == null) throw new BusinessException("订单行项不存在");

            line.setOrderedQty(poLine.getQuantity());
            line.setMaterialId(poLine.getMaterialId());
            line.setAcceptedQty(BigDecimal.ZERO);
            // diff_qty 由数据库生成列自动计算

            arrivalLineMapper.insert(line);

            // 更新 PO 行的已收货数量
            poLine.setReceivedQty(poLine.getReceivedQty().add(line.getArrivedQty()));
            poLineMapper.updateById(poLine);
        }

        // 更新 PO 状态
        boolean allReceived = checkAllReceived(po.getId());
        PoStatus newPoStatus = allReceived ? PoStatus.RECEIVED : PoStatus.PARTIAL_RECEIVED;
        po.setStatus(newPoStatus.name());
        poMapper.updateById(po);

        return arrival;
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE_ARRIVAL_STATUS", entityType = "Arrival")
    public void updateStatus(Long arrivalId, String status) {
        Arrival arrival = arrivalMapper.selectById(arrivalId);
        if (arrival == null) throw new BusinessException("到货单不存在");
        ArrivalStatus current = ArrivalStatus.valueOf(arrival.getStatus());
        ArrivalStatus target = ArrivalStatus.valueOf(status);
        ArrivalStateMachine.validateTransition(current, target);
        arrival.setStatus(status);
        arrivalMapper.updateById(arrival);
    }

    @Override
    public Arrival getById(Long id) {
        Arrival a = arrivalMapper.selectById(id);
        if (a == null) throw new BusinessException("到货单不存在");
        return a;
    }

    @Override
    public List<ArrivalLine> getLines(Long arrivalId) {
        return arrivalLineMapper.selectList(
                new LambdaQueryWrapper<ArrivalLine>().eq(ArrivalLine::getArrivalId, arrivalId));
    }

    @Override
    public List<Arrival> getByPoId(Long poId) {
        return arrivalMapper.selectList(
                new LambdaQueryWrapper<Arrival>().eq(Arrival::getPoId, poId)
                        .orderByAsc(Arrival::getBatchNo));
    }

    private boolean checkAllReceived(Long poId) {
        List<PurchaseOrderLine> lines = poLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderLine>().eq(PurchaseOrderLine::getPoId, poId));
        return lines.stream().allMatch(l -> l.getReceivedQty().compareTo(l.getQuantity()) >= 0);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
