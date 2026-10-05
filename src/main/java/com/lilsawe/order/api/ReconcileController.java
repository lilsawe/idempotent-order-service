package com.lilsawe.order.api;

import com.lilsawe.order.reconcile.ReconcileReport;
import com.lilsawe.order.reconcile.ReconcileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconcile")
public class ReconcileController {

    private final ReconcileService reconcileService;

    public ReconcileController(ReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    /** 触发一次对账，返回一致数量与全部差异明细。 */
    @GetMapping
    public ReconcileReport reconcile() {
        return reconcileService.reconcile();
    }
}
