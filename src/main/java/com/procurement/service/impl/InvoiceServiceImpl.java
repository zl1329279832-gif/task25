package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceMapper invoiceMapper;
    private final InvoiceLineMapper invoiceLineMapper;
    private final PurchaseOrderMapper poMapper;

    @Override
    @Transactional
    @Auditable(action = "REGISTER_INVOICE", entityType = "Invoice")
    public Invoice register(Invoice invoice, List<InvoiceLine> lines) {
        // 校验采购订单存在且供应商匹配
        PurchaseOrder po = poMapper.selectById(invoice.getPoId());
        if (po == null) throw new BusinessException("采购订单不存在");
        if (!po.getSupplierId().equals(invoice.getSupplierId())) {
            throw new BusinessException("发票供应商与采购订单不匹配");
        }

        LoginUser user = getCurrentUser();
        invoice.setRegisteredBy(user.getUserId());
        invoice.setStatus("REGISTERED");
        invoiceMapper.insert(invoice);

        for (InvoiceLine line : lines) {
            line.setInvoiceId(invoice.getId());
            invoiceLineMapper.insert(line);
        }
        return invoice;
    }

    @Override
    @Auditable(action = "VERIFY_INVOICE", entityType = "Invoice")
    public void verify(Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw new BusinessException("发票不存在");
        if (!"REGISTERED".equals(invoice.getStatus())) {
            throw new BusinessException("只有已登记状态的发票才能审核: " + invoice.getStatus());
        }
        invoice.setStatus("VERIFIED");
        invoiceMapper.updateById(invoice);
    }

    @Override
    @Auditable(action = "REJECT_INVOICE", entityType = "Invoice")
    public void reject(Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw new BusinessException("发票不存在");
        if (!"REGISTERED".equals(invoice.getStatus())) {
            throw new BusinessException("只有已登记状态的发票才能驳回: " + invoice.getStatus());
        }
        invoice.setStatus("REJECTED");
        invoiceMapper.updateById(invoice);
    }

    @Override
    public Invoice getById(Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw new BusinessException("发票不存在");
        // 供应商权限隔离
        LoginUser user = getCurrentUser();
        if ("SUPPLIER".equals(user.getRole()) && !invoice.getSupplierId().equals(user.getSupplierId())) {
            throw new BusinessException("无权访问此发票");
        }
        return invoice;
    }

    @Override
    public List<InvoiceLine> getLines(Long invoiceId) {
        // 先验证访问权限
        getById(invoiceId);
        return invoiceLineMapper.selectList(
                new LambdaQueryWrapper<InvoiceLine>().eq(InvoiceLine::getInvoiceId, invoiceId));
    }

    @Override
    public List<Invoice> getByPoId(Long poId) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getPoId, poId);
        // 供应商权限隔离
        LoginUser user = getCurrentUser();
        if ("SUPPLIER".equals(user.getRole())) {
            wrapper.eq(Invoice::getSupplierId, user.getSupplierId());
        }
        return invoiceMapper.selectList(wrapper);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
