package com.lilsawe.order.api;

import com.lilsawe.order.reconcile.ReconcileReport;
import com.lilsawe.order.reconcile.ReconcileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/reconcile")
@Tag(name = "对账", description = "本地订单与渠道结算记录双向比对")
public class ReconcileController {

    private final ReconcileService reconcileService;

    public ReconcileController(ReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    /** 触发一次对账，返回一致数量与全部差异明细。 */
    @GetMapping
    @Operation(summary = "触发对账", description = "返回一致数量与差异明细（本地多 / 渠道多 / 金额不一致）")
    public ReconcileReport reconcile() {
        return reconcileService.reconcile();
    }
}
