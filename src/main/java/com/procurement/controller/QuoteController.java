package com.procurement.controller;

import com.procurement.common.Result;
import com.procurement.entity.Quote;
import com.procurement.entity.QuoteLine;
import com.procurement.security.LoginUser;
import com.procurement.service.QuoteService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @Data
    public static class SubmitQuoteRequest {
        private Long rfqId;
        private List<QuoteLine> lines;
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('SUPPLIER')")
    public Result<Quote> submit(@RequestBody SubmitQuoteRequest req) {
        LoginUser user = getCurrentUser();
        return Result.ok(quoteService.submitQuote(req.getRfqId(), user.getSupplierId(), req.getLines()));
    }

    @GetMapping("/rfq/{rfqId}/latest")
    public Result<Quote> getLatest(@PathVariable Long rfqId, @RequestParam Long supplierId) {
        return Result.ok(quoteService.getLatestQuote(rfqId, supplierId));
    }

    @GetMapping("/rfq/{rfqId}")
    public Result<List<Quote>> getAllByRfq(@PathVariable Long rfqId) {
        return Result.ok(quoteService.getAllQuotesByRfq(rfqId));
    }

    @GetMapping("/{quoteId}/lines")
    public Result<List<QuoteLine>> getLines(@PathVariable Long quoteId) {
        return Result.ok(quoteService.getQuoteLines(quoteId));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
