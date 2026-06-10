package com.procurement.module.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.AuditLog;
import com.procurement.common.enums.InvoiceStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.module.invoice.dto.InvoiceCreateDTO;
import com.procurement.module.invoice.entity.Invoice;
import com.procurement.module.invoice.entity.InvoiceItem;
import com.procurement.module.invoice.mapper.InvoiceItemMapper;
import com.procurement.module.invoice.mapper.InvoiceMapper;
import com.procurement.module.invoice.vo.InvoiceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceMapper invoiceMapper;
    private final InvoiceItemMapper invoiceItemMapper;

    @Transactional
    @AuditLog(module = "INVOICE", operation = "CREATE")
    public void create(InvoiceCreateDTO dto, Long registrarId) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(dto.getInvoiceNo());
        invoice.setInvoiceCode(dto.getInvoiceCode());
        invoice.setOrderId(dto.getOrderId());
        invoice.setSupplierId(dto.getSupplierId());
        invoice.setStatus(InvoiceStatus.REGISTERED.name());
        invoice.setInvoiceType(dto.getInvoiceType());
        invoice.setAmount(dto.getAmount());
        invoice.setTaxAmount(dto.getTaxAmount());
        invoice.setTotalAmount(dto.getTotalAmount());
        invoice.setInvoiceDate(dto.getInvoiceDate());
        invoice.setRegistrarId(registrarId);
        invoice.setCreateTime(LocalDateTime.now());
        invoice.setUpdateTime(LocalDateTime.now());
        invoiceMapper.insert(invoice);

        for (InvoiceCreateDTO.Item item : dto.getItems()) {
            InvoiceItem ii = new InvoiceItem();
            ii.setInvoiceId(invoice.getId());
            ii.setOrderItemId(item.getOrderItemId());
            ii.setMaterialId(item.getMaterialId());
            ii.setQuantity(item.getQuantity());
            ii.setUnitPrice(item.getUnitPrice());
            ii.setAmount(item.getAmount());
            ii.setTaxRate(item.getTaxRate());
            invoiceItemMapper.insert(ii);
        }
    }

    @AuditLog(module = "INVOICE", operation = "STATUS_CHANGE")
    public void verify(Long id) {
        Invoice invoice = getEntity(id);
        if (!InvoiceStatus.REGISTERED.name().equals(invoice.getStatus())) {
            throw new BizException(ErrorCode.INVOICE_STATUS_ERROR);
        }
        invoice.setStatus(InvoiceStatus.VERIFIED.name());
        invoice.setVerifyTime(LocalDateTime.now());
        invoice.setUpdateTime(LocalDateTime.now());
        invoiceMapper.updateById(invoice);
    }

    @AuditLog(module = "INVOICE", operation = "STATUS_CHANGE")
    public void reject(Long id, String reason) {
        Invoice invoice = getEntity(id);
        if (!InvoiceStatus.REGISTERED.name().equals(invoice.getStatus())) {
            throw new BizException(ErrorCode.INVOICE_STATUS_ERROR);
        }
        invoice.setStatus(InvoiceStatus.REJECTED.name());
        invoice.setRejectReason(reason);
        invoice.setUpdateTime(LocalDateTime.now());
        invoiceMapper.updateById(invoice);
    }

    public Page<InvoiceVO> page(int pageNum, int pageSize, Long orderId, String status) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<>();
        if (orderId != null) wrapper.eq(Invoice::getOrderId, orderId);
        if (status != null) wrapper.eq(Invoice::getStatus, status);
        wrapper.orderByDesc(Invoice::getCreateTime);
        Page<Invoice> page = invoiceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<InvoiceVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public InvoiceVO getById(Long id) {
        return toVO(getEntity(id));
    }

    public BigDecimal getInvoiceAmountBySupplierAndPeriod(Long supplierId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Invoice::getSupplierId, supplierId)
                .eq(Invoice::getStatus, InvoiceStatus.VERIFIED.name())
                .ge(Invoice::getInvoiceDate, start)
                .le(Invoice::getInvoiceDate, end);
        List<Invoice> invoices = invoiceMapper.selectList(wrapper);
        return invoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Invoice getEntity(Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) throw new BizException(ErrorCode.INVOICE_NOT_FOUND);
        return invoice;
    }

    private InvoiceVO toVO(Invoice inv) {
        InvoiceVO vo = new InvoiceVO();
        vo.setId(inv.getId());
        vo.setInvoiceNo(inv.getInvoiceNo());
        vo.setInvoiceCode(inv.getInvoiceCode());
        vo.setOrderId(inv.getOrderId());
        vo.setSupplierId(inv.getSupplierId());
        vo.setStatus(inv.getStatus());
        vo.setInvoiceType(inv.getInvoiceType());
        vo.setAmount(inv.getAmount());
        vo.setTaxAmount(inv.getTaxAmount());
        vo.setTotalAmount(inv.getTotalAmount());
        vo.setInvoiceDate(inv.getInvoiceDate());
        vo.setRegistrarId(inv.getRegistrarId());
        vo.setVerifyTime(inv.getVerifyTime());
        vo.setRejectReason(inv.getRejectReason());
        vo.setCreateTime(inv.getCreateTime());

        List<InvoiceItem> items = invoiceItemMapper.selectList(
                new LambdaQueryWrapper<InvoiceItem>().eq(InvoiceItem::getInvoiceId, inv.getId()));
        vo.setItems(items.stream().map(i -> {
            InvoiceVO.InvoiceItemVO iv = new InvoiceVO.InvoiceItemVO();
            iv.setId(i.getId());
            iv.setOrderItemId(i.getOrderItemId());
            iv.setMaterialId(i.getMaterialId());
            iv.setQuantity(i.getQuantity());
            iv.setUnitPrice(i.getUnitPrice());
            iv.setAmount(i.getAmount());
            iv.setTaxRate(i.getTaxRate());
            return iv;
        }).toList());
        return vo;
    }
}
