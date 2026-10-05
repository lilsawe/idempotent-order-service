package com.lilsawe.order.reconcile;

import com.lilsawe.kit.reconcile.DiffType;
import com.lilsawe.kit.reconcile.ReconcileDiff;
import com.lilsawe.kit.reconcile.ReconcileReport;
import com.lilsawe.kit.reconcile.Reconciler;
import com.lilsawe.order.domain.OrderEntity;
import com.lilsawe.order.domain.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对账服务：本地订单 vs 渠道结算记录双向比对。
 *
 * <p>比对逻辑直接复用 kit 的通用 {@link Reconciler}——业务侧只需要提供「取业务键」与
 * 「取金额」两个函数，不再自己写双重循环与差异分类。
 */
@Service
public class ReconcileService {

    private final OrderRepository orderRepository;
    private final ChannelGateway channelGateway;

    public ReconcileService(OrderRepository orderRepository, ChannelGateway channelGateway) {
        this.orderRepository = orderRepository;
        this.channelGateway = channelGateway;
    }

    public ReconcileView reconcile() {
        List<OrderEntity> localOrders = orderRepository.findAll();
        List<ChannelOrder> settlements = channelGateway.fetchSettlements();

        ReconcileReport report = Reconciler.compare(
                localOrders,
                settlements,
                OrderEntity::getOrderNo,
                OrderEntity::getAmountCent,
                ChannelOrder::channelOrderNo,
                ChannelOrder::amountCent);

        List<ReconcileView.Diff> diffs = report.diffs().stream()
                .map(ReconcileService::toView)
                .toList();

        return new ReconcileView(report.leftCount(), report.rightCount(), report.matched(), diffs);
    }

    private static ReconcileView.Diff toView(ReconcileDiff diff) {
        return new ReconcileView.Diff(
                diff.key(),
                toKind(diff.type()),
                diff.leftAmount(),
                diff.rightAmount());
    }

    /** 把 kit 的通用差异类型翻译成订单领域的说法。 */
    private static DiffKind toKind(DiffType type) {
        return switch (type) {
            case MISSING_IN_RIGHT -> DiffKind.MISSING_IN_CHANNEL;
            case MISSING_IN_LEFT -> DiffKind.MISSING_IN_LOCAL;
            case AMOUNT_MISMATCH -> DiffKind.AMOUNT_MISMATCH;
        };
    }
}
