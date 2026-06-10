package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.InvoiceService;
import com.procurement.state.InvoiceStateMachine;
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

    @Override
    @Transactional
    @Auditable(action = "REGISTER_INVOICE", entityType = "Invoice")
    public Invoice register(Invoice invoice, List<InvoiceLine> lines) {
        LoginUser user = getCurrentUser();
        invoice.setRegisteredBy(user.getUserId());
        invoice.setStatus(InvoiceStatus.REGISTERED.name());
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
        InvoiceStateMachine.validateTransition(InvoiceStatus.valueOf(invoice.getStatus()), InvoiceStatus.VERIFIED);
        invoice.setStatus(InvoiceStatus.VERIFIED.name());
        invoiceMapper.updateById(invoice);
    }

    @Override
    @Auditable(action = "REJECT_INVOICE", entityType = "Invoice")
    public void reject(Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw new BusinessException("发票不存在");
        InvoiceStateMachine.validateTransition(InvoiceStatus.valueOf(invoice.getStatus()), InvoiceStatus.REJECTED);
        invoice.setStatus(InvoiceStatus.REJECTED.name());
        invoiceMapper.updateById(invoice);
    }

    @Override
    public Invoice getById(Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw new BusinessException("发票不存在");
        return invoice;
    }

    @Override
    public List<InvoiceLine> getLines(Long invoiceId) {
        return invoiceLineMapper.selectList(
                new LambdaQueryWrapper<InvoiceLine>().eq(InvoiceLine::getInvoiceId, invoiceId));
    }

    @Override
    public List<Invoice> getByPoId(Long poId) {
        return invoiceMapper.selectList(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getPoId, poId));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
