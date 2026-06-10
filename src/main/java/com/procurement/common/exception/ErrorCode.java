package com.procurement.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    BAD_REQUEST(400, "请求参数错误"),
    INTERNAL_ERROR(500, "系统内部错误"),

    USER_NOT_FOUND(1001, "用户不存在"),
    USER_DISABLED(1002, "用户已禁用"),
    PASSWORD_INCORRECT(1003, "密码错误"),
    USERNAME_EXISTS(1004, "用户名已存在"),

    SUPPLIER_NOT_FOUND(2001, "供应商不存在"),
    SUPPLIER_CODE_EXISTS(2002, "供应商编码已存在"),
    SUPPLIER_NOT_QUALIFIED(2003, "供应商资质不合格"),

    MATERIAL_NOT_FOUND(3001, "物料不存在"),
    MATERIAL_CODE_EXISTS(3002, "物料编码已存在"),
    CATEGORY_NOT_FOUND(3003, "分类不存在"),
    CATEGORY_HAS_CHILDREN(3004, "分类下存在子分类"),

    INQUIRY_NOT_FOUND(4001, "询价单不存在"),
    INVALID_STATUS_TRANSITION(4002, "无效的状态变更"),
    INQUIRY_DEADLINE_REQUIRED(4003, "发布询价需设置截止时间"),

    QUOTATION_NOT_FOUND(5001, "报价不存在"),
    QUOTATION_FROZEN(5002, "报价已冻结，不可修改"),
    QUOTATION_DEADLINE_PASSED(5003, "报价截止时间已过"),
    QUOTATION_NOT_INVITED(5004, "供应商未被邀请报价"),

    COMPARISON_NOT_FOUND(6001, "比价单不存在"),
    NO_FROZEN_QUOTATION(6002, "没有可比价的冻结报价"),

    ORDER_NOT_FOUND(7001, "订单不存在"),
    ORDER_CANCEL_HAS_RECEIVED(7002, "订单存在已收货明细，不可整单取消"),
    ORDER_APPROVAL_REQUIRED(7003, "订单金额需要审批"),

    DELIVERY_NOT_FOUND(8001, "到货单不存在"),
    DELIVERY_QUANTITY_EXCEEDS(8002, "到货数量超过订单剩余数量"),

    INSPECTION_NOT_FOUND(8501, "质检记录不存在"),

    RETURN_NOT_FOUND(9001, "退货单不存在"),
    RETURN_STATUS_ERROR(9002, "退货单状态不允许此操作"),

    INVOICE_NOT_FOUND(10001, "发票不存在"),
    INVOICE_AMOUNT_MISMATCH(10002, "发票金额与订单不匹配"),
    INVOICE_STATUS_ERROR(10003, "发票状态不允许此操作"),

    RECONCILIATION_NOT_FOUND(11001, "对账单不存在"),
    RECONCILIATION_ALREADY_EXISTS(11002, "该周期对账单已存在"),
    RECONCILIATION_STATUS_ERROR(11003, "对账单状态不允许此操作"),

    PARAM_ERROR(400, "参数错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
