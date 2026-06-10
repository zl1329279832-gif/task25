package com.procurement.module.approval.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApprovalRecordVO {
    private Long id;
    private String businessType;
    private Long businessId;
    private String businessNo;
    private Integer approvalLevel;
    private Long approverId;
    private String approverName;
    private String result;
    private String opinion;
    private LocalDateTime createTime;
}
