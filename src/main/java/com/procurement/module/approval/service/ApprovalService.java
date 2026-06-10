package com.procurement.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.module.approval.entity.ApprovalRecord;
import com.procurement.module.approval.mapper.ApprovalRecordMapper;
import com.procurement.module.approval.vo.ApprovalRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRecordMapper approvalRecordMapper;

    public void record(String businessType, Long businessId, String businessNo,
                       int level, Long approverId, String result, String opinion) {
        ApprovalRecord record = new ApprovalRecord();
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setBusinessNo(businessNo);
        record.setApprovalLevel(level);
        record.setApproverId(approverId);
        record.setResult(result);
        record.setOpinion(opinion);
        record.setCreateTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);
    }

    public Page<ApprovalRecordVO> pendingPage(int pageNum, int pageSize) {
        // In a real system, this would filter by pending business items
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ApprovalRecord::getCreateTime);
        Page<ApprovalRecord> page = approvalRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ApprovalRecordVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public Page<ApprovalRecordVO> historyByBusiness(String type, Long businessId, int pageNum, int pageSize) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRecord::getBusinessType, type)
                .eq(ApprovalRecord::getBusinessId, businessId)
                .orderByDesc(ApprovalRecord::getCreateTime);
        Page<ApprovalRecord> page = approvalRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ApprovalRecordVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    private ApprovalRecordVO toVO(ApprovalRecord r) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setId(r.getId());
        vo.setBusinessType(r.getBusinessType());
        vo.setBusinessId(r.getBusinessId());
        vo.setBusinessNo(r.getBusinessNo());
        vo.setApprovalLevel(r.getApprovalLevel());
        vo.setApproverId(r.getApproverId());
        vo.setResult(r.getResult());
        vo.setOpinion(r.getOpinion());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }
}
