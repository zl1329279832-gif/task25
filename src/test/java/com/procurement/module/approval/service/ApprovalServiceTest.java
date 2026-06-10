package com.procurement.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.module.approval.entity.ApprovalRecord;
import com.procurement.module.approval.mapper.ApprovalRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private ApprovalRecordMapper approvalRecordMapper;

    @InjectMocks
    private ApprovalService service;

    @Test
    void record_shouldInsertApprovalRecord() {
        when(approvalRecordMapper.insert(any())).thenReturn(1);

        service.record("ORDER", 1L, "PO-20260101-0001", 1, 2L, "APPROVED", "Looks good");

        ArgumentCaptor<ApprovalRecord> captor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRecordMapper).insert(captor.capture());
        assertEquals("ORDER", captor.getValue().getBusinessType());
        assertEquals(1L, captor.getValue().getBusinessId());
        assertEquals("APPROVED", captor.getValue().getResult());
        assertEquals(1, captor.getValue().getApprovalLevel());
    }

    @Test
    void historyByBusiness_shouldReturnPage() {
        Page<ApprovalRecord> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());
        when(approvalRecordMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.historyByBusiness("ORDER", 1L, 1, 10);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
    }
}
