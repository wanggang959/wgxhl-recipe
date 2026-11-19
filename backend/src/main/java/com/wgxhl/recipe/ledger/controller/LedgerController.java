package com.wgxhl.recipe.ledger.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.ledger.dto.LedgerPageQuery;
import com.wgxhl.recipe.ledger.entity.LedgerEntry;
import com.wgxhl.recipe.ledger.service.LedgerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ledger")
public class LedgerController {
    private final LedgerService service;
    public LedgerController(LedgerService service) { this.service = service; }

    @PostMapping("/page")
    public ApiResponse<Page<LedgerEntry>> page(@RequestBody LedgerPageQuery query) { return service.page(query); }
    @PostMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestBody LedgerPageQuery query) { return service.summary(query.getMonth()); }
    @PostMapping("/create")
    public ApiResponse<LedgerEntry> create(@RequestBody LedgerEntry entry) { return service.createManual(entry); }
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody LedgerEntry entry) { return service.update(entry); }
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) { return service.delete(dto.getId()); }
}
