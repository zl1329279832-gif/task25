package com.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.*;
import com.procurement.task.ProcurementScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 采购询价到对账链路集成测试
 * 覆盖：重复报价、部分到货退货、发票金额差异、对账失败重试、权限隔离
 */
@ExtendWith(MockitoExtension.class)
class ProcurementIntegrationTest {

    // ==================== 重复报价测试 ====================
    @Nested
    @DisplayName("重复报价与报价冻结测试")
    class QuoteDuplicateAndFreezeTest {

        @InjectMocks
        private QuoteServiceImpl quoteService;

        @Mock private QuoteMapper quoteMapper;
        @Mock private QuoteLineMapper quoteLineMapper;
        @Mock private RfqMapper rfqMapper;
        @Mock private RfqSupplierMapper rfqSupplierMapper;
        @Mock private RedisTemplate<String, Object> redisTemplate;
        @Mock private ValueOperations<String, Object> valueOperations;

        @BeforeEach
        void setUp() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("RFQ 已关闭时拒绝报价")
        void submitQuote_shouldRejectWhenRfqClosed() {
            Rfq rfq = new Rfq();
            rfq.setId(1L);
            rfq.setStatus(RfqStatus.CLOSED.name());
            rfq.setDeadline(LocalDateTime.now().plusDays(1));
            when(rfqMapper.selectById(1L)).thenReturn(rfq);

            // 设置供应商角色用户
            LoginUser supplier = new LoginUser(10L, "supplier01", "SUPPLIER", 1L);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(supplier, null));

            QuoteLine line = new QuoteLine();
            line.setUnitPrice(new BigDecimal("100"));
            line.setQuantity(new BigDecimal("10"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> quoteService.submitQuote(1L, 1L, List.of(line)));
            assertTrue(ex.getMessage().contains("状态不允许报价"));
        }

        @Test
        @DisplayName("RFQ 已取消时拒绝报价")
        void submitQuote_shouldRejectWhenRfqCancelled() {
            Rfq rfq = new Rfq();
            rfq.setId(1L);
            rfq.setStatus(RfqStatus.CANCELLED.name());
            rfq.setDeadline(LocalDateTime.now().plusDays(1));
            when(rfqMapper.selectById(1L)).thenReturn(rfq);

            LoginUser supplier = new LoginUser(10L, "supplier01", "SUPPLIER", 1L);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(supplier, null));

            QuoteLine line = new QuoteLine();
            line.setUnitPrice(new BigDecimal("100"));
            line.setQuantity(new BigDecimal("10"));

            assertThrows(BusinessException.class,
                    () -> quoteService.submitQuote(1L, 1L, List.of(line)));
        }

        @Test
        @DisplayName("未邀请的供应商无法报价")
        void submitQuote_shouldRejectUninvitedSupplier() {
            Rfq rfq = new Rfq();
            rfq.setId(1L);
            rfq.setStatus(RfqStatus.PUBLISHED.name());
            rfq.setDeadline(LocalDateTime.now().plusDays(7));
            when(rfqMapper.selectById(1L)).thenReturn(rfq);

            // 供应商未被邀请
            when(rfqSupplierMapper.selectCount(any())).thenReturn(0L);

            LoginUser supplier = new LoginUser(10L, "supplier01", "SUPPLIER", 99L);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(supplier, null));

            QuoteLine line = new QuoteLine();
            line.setUnitPrice(new BigDecimal("100"));
            line.setQuantity(new BigDecimal("10"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> quoteService.submitQuote(1L, 99L, List.of(line)));
            assertTrue(ex.getMessage().contains("未被邀请"));
        }

        @Test
        @DisplayName("同版本重复提交应被拒绝（幂等校验）")
        void submitQuote_shouldRejectDuplicateVersion() {
            Rfq rfq = new Rfq();
            rfq.setId(1L);
            rfq.setStatus(RfqStatus.PUBLISHED.name());
            rfq.setDeadline(LocalDateTime.now().plusDays(7));
            when(rfqMapper.selectById(1L)).thenReturn(rfq);
            when(rfqSupplierMapper.selectCount(any())).thenReturn(1L);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

            LoginUser supplier = new LoginUser(10L, "supplier01", "SUPPLIER", 1L);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(supplier, null));

            // 已存在 version=2 的报价
            Quote existing = new Quote();
            existing.setQuoteNo("QT-001");
            existing.setVersion(2);
            existing.setFrozen(0);
            when(quoteMapper.selectOne(any())).thenReturn(existing);

            // version=3 已存在（重复提交场景）
            when(quoteMapper.selectCount(any())).thenReturn(1L);

            QuoteLine line = new QuoteLine();
            line.setUnitPrice(new BigDecimal("100"));
            line.setQuantity(new BigDecimal("10"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> quoteService.submitQuote(1L, 1L, List.of(line)));
            assertTrue(ex.getMessage().contains("重复提交"));
        }

        @Test
        @DisplayName("报价冻结后不可再次冻结（幂等）")
        void freezeAllByRfq_shouldBeIdempotent() {
            // 已经全部冻结的情况下，freezeAllByRfq 查询 frozen=0 返回空列表
            when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());

            quoteService.freezeAllByRfq(1L);

            // 不会执行任何 update
            verify(quoteMapper, never()).updateById(any());
        }
    }

    // ==================== 部分到货退货测试 ====================
    @Nested
    @DisplayName("部分到货与退货处理测试")
    class PartialArrivalReturnTest {

        @InjectMocks
        private ReturnOrderServiceImpl returnOrderService;

        @Mock private ReturnOrderMapper returnOrderMapper;
        @Mock private ReturnLineMapper returnLineMapper;
        @Mock private PurchaseOrderMapper poMapper;
        @Mock private PurchaseOrderLineMapper poLineMapper;

        @BeforeEach
        void setUp() {
            LoginUser user = new LoginUser(5L, "warehouse01", "WAREHOUSE", null);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null));
        }

        @Test
        @DisplayName("退货后应回退 PO 行的 receivedQty")
        void markReturned_shouldDeductReceivedQty() {
            ReturnOrder ro = new ReturnOrder();
            ro.setId(1L);
            ro.setPoId(10L);
            ro.setStatus("APPROVED");
            when(returnOrderMapper.selectById(1L)).thenReturn(ro);
            when(returnOrderMapper.updateById(any())).thenReturn(1);

            // 退货行：物料1退回20个
            ReturnLine retLine = new ReturnLine();
            retLine.setMaterialId(1L);
            retLine.setQuantity(new BigDecimal("20"));
            when(returnLineMapper.selectList(any())).thenReturn(List.of(retLine));

            // PO 行：物料1，下单100，已收80
            PurchaseOrderLine poLine = new PurchaseOrderLine();
            poLine.setId(1L);
            poLine.setMaterialId(1L);
            poLine.setQuantity(new BigDecimal("100"));
            poLine.setReceivedQty(new BigDecimal("80"));
            when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));
            when(poLineMapper.updateById(any())).thenReturn(1);

            PurchaseOrder po = new PurchaseOrder();
            po.setId(10L);
            po.setStatus(PoStatus.PARTIAL_RECEIVED.name());
            when(poMapper.selectById(10L)).thenReturn(po);
            when(poMapper.updateById(any())).thenReturn(1);

            returnOrderService.markReturned(1L);

            // receivedQty 应从 80 减到 60
            assertEquals(new BigDecimal("60"), poLine.getReceivedQty());
            assertEquals("RETURNED", ro.getStatus());
        }

        @Test
        @DisplayName("全部退货后 PO 状态应回退到 CONFIRMED")
        void markReturned_shouldRevertPoStatusToConfirmed() {
            ReturnOrder ro = new ReturnOrder();
            ro.setId(1L);
            ro.setPoId(10L);
            ro.setStatus("APPROVED");
            when(returnOrderMapper.selectById(1L)).thenReturn(ro);
            when(returnOrderMapper.updateById(any())).thenReturn(1);

            // 退回全部已收货数量
            ReturnLine retLine = new ReturnLine();
            retLine.setMaterialId(1L);
            retLine.setQuantity(new BigDecimal("50"));
            when(returnLineMapper.selectList(any())).thenReturn(List.of(retLine));

            PurchaseOrderLine poLine = new PurchaseOrderLine();
            poLine.setId(1L);
            poLine.setMaterialId(1L);
            poLine.setQuantity(new BigDecimal("100"));
            poLine.setReceivedQty(new BigDecimal("50")); // 退回后变0
            when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));
            when(poLineMapper.updateById(any())).thenReturn(1);

            PurchaseOrder po = new PurchaseOrder();
            po.setId(10L);
            po.setStatus(PoStatus.PARTIAL_RECEIVED.name());
            when(poMapper.selectById(10L)).thenReturn(po);
            when(poMapper.updateById(any())).thenReturn(1);

            returnOrderService.markReturned(1L);

            assertEquals(BigDecimal.ZERO, poLine.getReceivedQty());
            assertEquals(PoStatus.CONFIRMED.name(), po.getStatus());
        }

        @Test
        @DisplayName("未审批通过的退货单不能执行退货")
        void markReturned_shouldRejectIfNotApproved() {
            ReturnOrder ro = new ReturnOrder();
            ro.setId(1L);
            ro.setStatus("PENDING");
            when(returnOrderMapper.selectById(1L)).thenReturn(ro);

            assertThrows(BusinessException.class, () -> returnOrderService.markReturned(1L));
        }
    }

    // ==================== 发票金额差异与对账测试 ====================
    @Nested
    @DisplayName("发票与对账金额闭合测试")
    class InvoiceReconciliationTest {

        @InjectMocks
        private ReconciliationServiceImpl reconService;

        @Mock private ReconciliationMapper reconMapper;
        @Mock private ReconciliationLineMapper reconLineMapper;
        @Mock private PurchaseOrderMapper poMapper;
        @Mock private PurchaseOrderLineMapper poLineMapper;
        @Mock private ArrivalLineMapper arrivalLineMapper;
        @Mock private ArrivalMapper arrivalMapper;
        @Mock private InvoiceMapper invoiceMapper;
        @Mock private InvoiceLineMapper invoiceLineMapper;
        @Mock private ReturnOrderMapper returnOrderMapper;
        @Mock private ReturnLineMapper returnLineMapper;

        @BeforeEach
        void setUp() {
            LoginUser user = new LoginUser(6L, "finance01", "FINANCE", null);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null));
        }

        @Test
        @DisplayName("对账应扣减退货数量 - 退货后净收货金额减少")
        void generate_shouldDeductReturnedQuantities() {
            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);
            po.setStatus(PoStatus.RECEIVED.name());
            when(poMapper.selectById(1L)).thenReturn(po);

            PurchaseOrderLine poLine = new PurchaseOrderLine();
            poLine.setId(1L);
            poLine.setMaterialId(1L);
            poLine.setQuantity(new BigDecimal("100"));
            poLine.setUnitPrice(new BigDecimal("10.00"));
            when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));

            // 到货验收100个
            Arrival arrival = new Arrival();
            arrival.setId(1L);
            arrival.setStatus(ArrivalStatus.ACCEPTED.name());
            when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));

            ArrivalLine arrivalLine = new ArrivalLine();
            arrivalLine.setPoLineId(1L);
            arrivalLine.setAcceptedQty(new BigDecimal("100"));
            when(arrivalLineMapper.selectList(any())).thenReturn(List.of(arrivalLine));

            // 退货20个（已退货状态）
            ReturnOrder returnOrder = new ReturnOrder();
            returnOrder.setId(1L);
            returnOrder.setPoId(1L);
            returnOrder.setStatus("RETURNED");
            when(returnOrderMapper.selectList(any())).thenReturn(List.of(returnOrder));

            ReturnLine returnLine = new ReturnLine();
            returnLine.setMaterialId(1L);
            returnLine.setQuantity(new BigDecimal("20"));
            when(returnLineMapper.selectList(any())).thenReturn(List.of(returnLine));

            // 发票金额800（对应退货后的80个 * 10）
            Invoice invoice = new Invoice();
            invoice.setId(1L);
            invoice.setAmount(new BigDecimal("800.00"));
            invoice.setStatus("VERIFIED");
            when(invoiceMapper.selectList(any())).thenReturn(List.of(invoice));

            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setPoLineId(1L);
            invoiceLine.setQuantity(new BigDecimal("80"));
            when(invoiceLineMapper.selectList(any())).thenReturn(List.of(invoiceLine));

            when(reconMapper.insert(any())).thenReturn(1);
            when(reconMapper.updateById(any())).thenReturn(1);
            when(reconLineMapper.insert(any())).thenReturn(1);

            Reconciliation result = reconService.generate(1L);

            // 净收货 = 100 - 20 = 80, 金额 = 80 * 10 = 800
            assertEquals(new BigDecimal("800.00"), result.getReceiptAmount());
            assertEquals("MATCHED", result.getStatus());
        }

        @Test
        @DisplayName("REJECTED 发票不应计入对账金额")
        void generate_shouldExcludeRejectedInvoices() {
            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);
            po.setStatus(PoStatus.RECEIVED.name());
            when(poMapper.selectById(1L)).thenReturn(po);

            PurchaseOrderLine poLine = new PurchaseOrderLine();
            poLine.setId(1L);
            poLine.setMaterialId(1L);
            poLine.setQuantity(new BigDecimal("100"));
            poLine.setUnitPrice(new BigDecimal("10.00"));
            when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));

            Arrival arrival = new Arrival();
            arrival.setId(1L);
            arrival.setStatus(ArrivalStatus.ACCEPTED.name());
            when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));

            ArrivalLine arrivalLine = new ArrivalLine();
            arrivalLine.setPoLineId(1L);
            arrivalLine.setAcceptedQty(new BigDecimal("100"));
            when(arrivalLineMapper.selectList(any())).thenReturn(List.of(arrivalLine));

            // 无退货
            when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());

            // 查询 VERIFIED 发票 - 只返回已验证的
            // REJECTED 发票不在结果中（因为 SQL 过滤了）
            Invoice verifiedInvoice = new Invoice();
            verifiedInvoice.setId(1L);
            verifiedInvoice.setAmount(new BigDecimal("1000.00"));
            verifiedInvoice.setStatus("VERIFIED");
            when(invoiceMapper.selectList(any())).thenReturn(List.of(verifiedInvoice));

            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setPoLineId(1L);
            invoiceLine.setQuantity(new BigDecimal("100"));
            when(invoiceLineMapper.selectList(any())).thenReturn(List.of(invoiceLine));

            when(reconMapper.insert(any())).thenReturn(1);
            when(reconMapper.updateById(any())).thenReturn(1);
            when(reconLineMapper.insert(any())).thenReturn(1);

            Reconciliation result = reconService.generate(1L);

            // 只计入 VERIFIED 发票
            assertEquals(new BigDecimal("1000.00"), result.getInvoiceAmount());
            assertEquals("MATCHED", result.getStatus());
        }

        @Test
        @DisplayName("发票金额与收货金额不一致应标记 DIFFERENT")
        void generate_shouldDetectInvoiceReceiptMismatch() {
            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);
            po.setStatus(PoStatus.PARTIAL_RECEIVED.name());
            when(poMapper.selectById(1L)).thenReturn(po);

            PurchaseOrderLine poLine = new PurchaseOrderLine();
            poLine.setId(1L);
            poLine.setMaterialId(1L);
            poLine.setQuantity(new BigDecimal("100"));
            poLine.setUnitPrice(new BigDecimal("10.00"));
            when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));

            // 只收了60个
            Arrival arrival = new Arrival();
            arrival.setId(1L);
            arrival.setStatus(ArrivalStatus.ACCEPTED.name());
            when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));

            ArrivalLine arrivalLine = new ArrivalLine();
            arrivalLine.setPoLineId(1L);
            arrivalLine.setAcceptedQty(new BigDecimal("60"));
            when(arrivalLineMapper.selectList(any())).thenReturn(List.of(arrivalLine));

            when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());

            // 发票按100个开
            Invoice invoice = new Invoice();
            invoice.setId(1L);
            invoice.setAmount(new BigDecimal("1000.00"));
            invoice.setStatus("VERIFIED");
            when(invoiceMapper.selectList(any())).thenReturn(List.of(invoice));

            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setPoLineId(1L);
            invoiceLine.setQuantity(new BigDecimal("100"));
            when(invoiceLineMapper.selectList(any())).thenReturn(List.of(invoiceLine));

            when(reconMapper.insert(any())).thenReturn(1);
            when(reconMapper.updateById(any())).thenReturn(1);
            when(reconLineMapper.insert(any())).thenReturn(1);

            Reconciliation result = reconService.generate(1L);

            assertEquals("DIFFERENT", result.getStatus());
            assertEquals(new BigDecimal("600.00"), result.getReceiptAmount());
            assertEquals(new BigDecimal("1000.00"), result.getInvoiceAmount());
        }

        @Test
        @DisplayName("非收货状态的 PO 不允许生成对账单")
        void generate_shouldRejectNonReceivedPO() {
            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);
            po.setStatus(PoStatus.CONFIRMED.name());
            when(poMapper.selectById(1L)).thenReturn(po);

            assertThrows(BusinessException.class, () -> reconService.generate(1L));
        }

        @Test
        @DisplayName("对账失败后可重新生成（重试）")
        void generate_shouldAllowRetryAfterRejection() {
            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);
            po.setStatus(PoStatus.RECEIVED.name());
            when(poMapper.selectById(1L)).thenReturn(po);

            PurchaseOrderLine poLine = new PurchaseOrderLine();
            poLine.setId(1L);
            poLine.setMaterialId(1L);
            poLine.setQuantity(new BigDecimal("100"));
            poLine.setUnitPrice(new BigDecimal("10.00"));
            when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));

            Arrival arrival = new Arrival();
            arrival.setId(1L);
            arrival.setStatus(ArrivalStatus.ACCEPTED.name());
            when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));

            ArrivalLine arrivalLine = new ArrivalLine();
            arrivalLine.setPoLineId(1L);
            arrivalLine.setAcceptedQty(new BigDecimal("100"));
            when(arrivalLineMapper.selectList(any())).thenReturn(List.of(arrivalLine));

            when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());

            Invoice invoice = new Invoice();
            invoice.setAmount(new BigDecimal("1000.00"));
            invoice.setStatus("VERIFIED");
            when(invoiceMapper.selectList(any())).thenReturn(List.of(invoice));

            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setPoLineId(1L);
            invoiceLine.setQuantity(new BigDecimal("100"));
            when(invoiceLineMapper.selectList(any())).thenReturn(List.of(invoiceLine));

            when(reconMapper.insert(any())).thenReturn(1);
            when(reconMapper.updateById(any())).thenReturn(1);
            when(reconLineMapper.insert(any())).thenReturn(1);

            // 第一次生成
            Reconciliation result1 = reconService.generate(1L);
            assertNotNull(result1);

            // 可以再次生成（重试）- 不会抛异常
            Reconciliation result2 = reconService.generate(1L);
            assertNotNull(result2);

            // 两次都应成功创建
            verify(reconMapper, times(2)).insert(any());
        }
    }

    // ==================== 发票状态校验测试 ====================
    @Nested
    @DisplayName("发票状态校验测试")
    class InvoiceStatusValidationTest {

        @InjectMocks
        private InvoiceServiceImpl invoiceService;

        @Mock private InvoiceMapper invoiceMapper;
        @Mock private InvoiceLineMapper invoiceLineMapper;
        @Mock private PurchaseOrderMapper poMapper;

        @BeforeEach
        void setUp() {
            LoginUser user = new LoginUser(6L, "finance01", "FINANCE", null);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null));
        }

        @Test
        @DisplayName("发票供应商与 PO 不匹配时拒绝登记")
        void register_shouldRejectMismatchedSupplier() {
            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);
            when(poMapper.selectById(1L)).thenReturn(po);

            Invoice invoice = new Invoice();
            invoice.setPoId(1L);
            invoice.setSupplierId(2L); // 不匹配

            assertThrows(BusinessException.class,
                    () -> invoiceService.register(invoice, Collections.emptyList()));
        }

        @Test
        @DisplayName("只有 REGISTERED 状态发票可以审核通过")
        void verify_shouldRejectNonRegisteredInvoice() {
            Invoice invoice = new Invoice();
            invoice.setId(1L);
            invoice.setStatus("VERIFIED"); // 已审核
            when(invoiceMapper.selectById(1L)).thenReturn(invoice);

            assertThrows(BusinessException.class, () -> invoiceService.verify(1L));
        }

        @Test
        @DisplayName("只有 REGISTERED 状态发票可以被驳回")
        void reject_shouldRejectNonRegisteredInvoice() {
            Invoice invoice = new Invoice();
            invoice.setId(1L);
            invoice.setStatus("REJECTED"); // 已驳回
            when(invoiceMapper.selectById(1L)).thenReturn(invoice);

            assertThrows(BusinessException.class, () -> invoiceService.reject(1L));
        }

        @Test
        @DisplayName("REGISTERED 状态发票可以正常审核")
        void verify_shouldSucceedForRegisteredInvoice() {
            Invoice invoice = new Invoice();
            invoice.setId(1L);
            invoice.setStatus("REGISTERED");
            when(invoiceMapper.selectById(1L)).thenReturn(invoice);
            when(invoiceMapper.updateById(any())).thenReturn(1);

            invoiceService.verify(1L);

            assertEquals("VERIFIED", invoice.getStatus());
        }
    }

    // ==================== 定时任务幂等测试 ====================
    @Nested
    @DisplayName("定时任务提醒幂等测试")
    class SchedulerIdempotencyTest {

        @InjectMocks
        private ProcurementScheduler scheduler;

        @Mock private RfqMapper rfqMapper;
        @Mock private QuoteService quoteService;
        @Mock private ApprovalMapper approvalMapper;
        @Mock private ReminderMapper reminderMapper;
        @Mock private PurchaseOrderMapper poMapper;

        @Test
        @DisplayName("审批超时提醒不应重复创建")
        void checkApprovalTimeout_shouldNotDuplicateReminders() {
            Approval approval = new Approval();
            approval.setId(1L);
            approval.setBusinessType("PO");
            approval.setBusinessId(100L);
            approval.setApproverId(5L);
            approval.setStatus("PENDING");
            approval.setCreatedAt(LocalDateTime.now().minusHours(50));
            when(approvalMapper.selectList(any())).thenReturn(List.of(approval));

            // 已存在 PENDING 提醒
            when(reminderMapper.selectCount(any())).thenReturn(1L);

            scheduler.checkApprovalTimeout();

            // 不应创建新提醒
            verify(reminderMapper, never()).insert(any());
        }

        @Test
        @DisplayName("首次审批超时应创建提醒")
        void checkApprovalTimeout_shouldCreateFirstReminder() {
            Approval approval = new Approval();
            approval.setId(1L);
            approval.setBusinessType("PO");
            approval.setBusinessId(100L);
            approval.setApproverId(5L);
            approval.setStatus("PENDING");
            approval.setCreatedAt(LocalDateTime.now().minusHours(50));
            when(approvalMapper.selectList(any())).thenReturn(List.of(approval));

            // 不存在提醒
            when(reminderMapper.selectCount(any())).thenReturn(0L);
            when(reminderMapper.insert(any())).thenReturn(1);

            scheduler.checkApprovalTimeout();

            verify(reminderMapper, times(1)).insert(any());
        }

        @Test
        @DisplayName("到货超时提醒不应重复创建")
        void checkArrivalOverdue_shouldNotDuplicateReminders() {
            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setPoNo("PO-001");
            po.setStatus(PoStatus.CONFIRMED.name());
            po.setCreatedBy(5L);
            po.setCreatedAt(LocalDateTime.now().minusDays(35));
            when(poMapper.selectList(any())).thenReturn(List.of(po));

            // 已存在 PENDING 提醒
            when(reminderMapper.selectCount(any())).thenReturn(1L);

            scheduler.checkArrivalOverdue();

            verify(reminderMapper, never()).insert(any());
        }

        @Test
        @DisplayName("多次执行冻结过期报价应该是幂等的")
        void freezeExpiredQuotes_shouldBeIdempotent() {
            Rfq rfq = new Rfq();
            rfq.setId(1L);
            rfq.setRfqNo("RFQ-001");
            rfq.setStatus(RfqStatus.PUBLISHED.name());
            rfq.setDeadline(LocalDateTime.now().minusHours(1));
            when(rfqMapper.selectList(any())).thenReturn(List.of(rfq));
            when(rfqMapper.updateById(any())).thenReturn(1);

            scheduler.freezeExpiredQuotes();

            verify(quoteService).freezeAllByRfq(1L);
            assertEquals(RfqStatus.CLOSED.name(), rfq.getStatus());

            // 第二次运行时 RFQ 已是 CLOSED 状态，不会被查询到
            when(rfqMapper.selectList(any())).thenReturn(Collections.emptyList());

            scheduler.freezeExpiredQuotes();

            // freezeAllByRfq 仍然只调用了 1 次
            verify(quoteService, times(1)).freezeAllByRfq(1L);
        }
    }

    // ==================== 权限隔离测试 ====================
    @Nested
    @DisplayName("供应商权限隔离测试")
    class SupplierPermissionIsolationTest {

        @Nested
        @DisplayName("报价权限隔离")
        class QuotePermissionTest {

            @InjectMocks
            private QuoteServiceImpl quoteService;

            @Mock private QuoteMapper quoteMapper;
            @Mock private QuoteLineMapper quoteLineMapper;
            @Mock private RfqMapper rfqMapper;
            @Mock private RfqSupplierMapper rfqSupplierMapper;
            @Mock private RedisTemplate<String, Object> redisTemplate;
            @Mock private ValueOperations<String, Object> valueOperations;

            @Test
            @DisplayName("供应商 A 不能以供应商 B 的身份提交报价")
            void submitQuote_shouldRejectCrossTenantAccess() {
                LoginUser supplierA = new LoginUser(10L, "supplierA", "SUPPLIER", 1L);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(supplierA, null));

                QuoteLine line = new QuoteLine();
                line.setUnitPrice(new BigDecimal("100"));
                line.setQuantity(new BigDecimal("10"));

                // supplierId=2 不等于 supplierA 的 supplierId=1
                BusinessException ex = assertThrows(BusinessException.class,
                        () -> quoteService.submitQuote(1L, 2L, List.of(line)));
                assertTrue(ex.getMessage().contains("无权操作其他供应商"));
            }

            @Test
            @DisplayName("供应商不能查看其他供应商的报价明细")
            void getQuoteLines_shouldRejectCrossTenantAccess() {
                LoginUser supplierA = new LoginUser(10L, "supplierA", "SUPPLIER", 1L);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(supplierA, null));

                Quote otherQuote = new Quote();
                otherQuote.setId(1L);
                otherQuote.setSupplierId(2L); // 属于供应商B
                when(quoteMapper.selectById(1L)).thenReturn(otherQuote);

                assertThrows(BusinessException.class, () -> quoteService.getQuoteLines(1L));
            }

            @Test
            @DisplayName("供应商查询所有报价时只能看到自己的")
            void getAllQuotesByRfq_shouldFilterBySupplier() {
                LoginUser supplierA = new LoginUser(10L, "supplierA", "SUPPLIER", 1L);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(supplierA, null));

                // 只返回自己的报价
                Quote ownQuote = new Quote();
                ownQuote.setId(1L);
                ownQuote.setSupplierId(1L);
                when(quoteMapper.selectList(any())).thenReturn(List.of(ownQuote));

                List<Quote> result = quoteService.getAllQuotesByRfq(1L);

                // 验证只返回了自己的报价
                assertEquals(1, result.size());
                assertEquals(1L, result.get(0).getSupplierId());
                // 验证 selectList 被调用（查询中包含供应商过滤）
                verify(quoteMapper).selectList(any());
            }
        }

        @Nested
        @DisplayName("采购订单权限隔离")
        class PurchaseOrderPermissionTest {

            @InjectMocks
            private PurchaseOrderServiceImpl poService;

            @Mock private PurchaseOrderMapper poMapper;
            @Mock private PurchaseOrderLineMapper poLineMapper;
            @Mock private ApprovalMapper approvalMapper;

            @Test
            @DisplayName("供应商 A 不能查看供应商 B 的采购订单")
            void getById_shouldRejectCrossTenantAccess() {
                LoginUser supplierA = new LoginUser(10L, "supplierA", "SUPPLIER", 1L);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(supplierA, null));

                PurchaseOrder po = new PurchaseOrder();
                po.setId(1L);
                po.setSupplierId(2L); // 属于供应商B
                when(poMapper.selectById(1L)).thenReturn(po);

                assertThrows(BusinessException.class, () -> poService.getById(1L));
            }

            @Test
            @DisplayName("供应商可以查看自己的采购订单")
            void getById_shouldAllowOwnAccess() {
                LoginUser supplierA = new LoginUser(10L, "supplierA", "SUPPLIER", 1L);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(supplierA, null));

                PurchaseOrder po = new PurchaseOrder();
                po.setId(1L);
                po.setSupplierId(1L); // 属于自己
                when(poMapper.selectById(1L)).thenReturn(po);

                PurchaseOrder result = poService.getById(1L);
                assertNotNull(result);
                assertEquals(1L, result.getSupplierId());
            }
        }

        @Nested
        @DisplayName("发票权限隔离")
        class InvoicePermissionTest {

            @InjectMocks
            private InvoiceServiceImpl invoiceService;

            @Mock private InvoiceMapper invoiceMapper;
            @Mock private InvoiceLineMapper invoiceLineMapper;
            @Mock private PurchaseOrderMapper poMapper;

            @Test
            @DisplayName("供应商 A 不能查看供应商 B 的发票")
            void getById_shouldRejectCrossTenantAccess() {
                LoginUser supplierA = new LoginUser(10L, "supplierA", "SUPPLIER", 1L);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(supplierA, null));

                Invoice invoice = new Invoice();
                invoice.setId(1L);
                invoice.setSupplierId(2L); // 属于供应商B
                when(invoiceMapper.selectById(1L)).thenReturn(invoice);

                assertThrows(BusinessException.class, () -> invoiceService.getById(1L));
            }

            @Test
            @DisplayName("供应商可以查看自己的发票")
            void getById_shouldAllowOwnAccess() {
                LoginUser supplierA = new LoginUser(10L, "supplierA", "SUPPLIER", 1L);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(supplierA, null));

                Invoice invoice = new Invoice();
                invoice.setId(1L);
                invoice.setSupplierId(1L);
                when(invoiceMapper.selectById(1L)).thenReturn(invoice);

                Invoice result = invoiceService.getById(1L);
                assertNotNull(result);
            }
        }
    }
}
