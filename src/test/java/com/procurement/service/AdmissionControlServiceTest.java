package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.AdmissionControlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionControlServiceTest {

    @InjectMocks
    private AdmissionControlServiceImpl admissionService;

    @Mock private SupplierMapper supplierMapper;
    @Mock private SupplierScoreMapper scoreMapper;
    @Mock private SupplierAdmissionLogMapper admissionLogMapper;
    @Mock private ScoringRuleVersionMapper ruleVersionMapper;
    @Mock private SupplierScoreSnapshotMapper snapshotMapper;

    private ScoringRuleVersion activeRule;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(2L, "manager01", "PURCHASE_MANAGER", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));

        activeRule = new ScoringRuleVersion();
        activeRule.setId(1L);
        activeRule.setVersionNo(1);
        activeRule.setThresholds("{\"blacklistScore\":20,\"restrictedScore\":50,\"extraApprovalScore\":70}");
        activeRule.setStatus("ACTIVE");
    }

    @Test
    void check_blacklistedSupplier_blocked() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("BLACKLISTED");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        assertThrows(BusinessException.class,
                () -> admissionService.checkAdmission(1L, "RFQ_INVITE", 100L));
    }

    @Test
    void check_disabledSupplier_blocked() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("DISABLED");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        assertThrows(BusinessException.class,
                () -> admissionService.checkAdmission(1L, "PO_CONFIRM", 100L));
    }

    @Test
    void check_lowScore_blocked() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("15.00")); // 低于blacklistScore=20
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(activeRule);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        assertThrows(BusinessException.class,
                () -> admissionService.checkAdmission(1L, "QUOTE_ACCEPT", 100L));
    }

    @Test
    void check_mediumScore_restricted() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("55.00")); // 在50-70之间
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(activeRule);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionResult result = admissionService.checkAdmission(1L, "RFQ_INVITE", 100L);

        assertEquals("RESTRICTED", result.getDecision());
        assertTrue(result.isRequiresExtraApproval());
        assertEquals(new BigDecimal("55.00"), result.getCurrentScore());
    }

    @Test
    void check_highScore_allowed() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("85.00")); // >= 70
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(activeRule);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionResult result = admissionService.checkAdmission(1L, "PO_CONFIRM", 100L);

        assertEquals("ALLOWED", result.getDecision());
        assertFalse(result.isRequiresExtraApproval());
        assertEquals(new BigDecimal("85.00"), result.getCurrentScore());
    }

    @Test
    void check_noScore_allowed() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionResult result = admissionService.checkAdmission(1L, "RFQ_INVITE", 100L);

        assertEquals("ALLOWED", result.getDecision());
        assertNull(result.getCurrentScore());
    }

    @Test
    void check_logsDecision() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("80.00"));
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(activeRule);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        admissionService.checkAdmission(1L, "PO_CONFIRM", 100L);

        verify(admissionLogMapper).insert(any(SupplierAdmissionLog.class));
    }

    @Test
    void check_nonexistentSupplier_throws() {
        when(supplierMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> admissionService.checkAdmission(99L, "RFQ_INVITE", 100L));
    }

    @Test
    void check_scoreBelowRestrictedAboveBlacklist_restricted() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("35.00")); // 在20-70之间
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(activeRule);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionResult result = admissionService.checkAdmission(1L, "QUOTE_ACCEPT", 100L);

        assertEquals("RESTRICTED", result.getDecision());
        assertTrue(result.isRequiresExtraApproval());
    }
}
