package com.lilsawe.order.reconcile;

import com.lilsawe.order.domain.OrderEntity;
import com.lilsawe.order.domain.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账服务：把本地订单与渠道结算记录按订单号做双向比对，输出四类结果
 * （一致 / 本地多 / 渠道多 / 金额不一致）。
 */
@Service
public class ReconcileService {

    private final OrderRepository orderRepository;
    private final ChannelGateway channelGateway;

    public ReconcileService(OrderRepository orderRepository, ChannelGateway channelGateway) {
        this.orderRepository = orderRepository;
        this.channelGateway = channelGateway;
    }

    public ReconcileReport reconcile() {
        List<OrderEntity> localOrders = orderRepository.findAll();
        Map<String, Long> local = new HashMap<>();
        for (OrderEntity order : localOrders) {
            local.put(order.getOrderNo(), order.getAmountCent());
        }

        Map<String, Long> channel = new HashMap<>();
        for (ChannelOrder settlement : channelGateway.fetchSettlements()) {
            channel.put(settlement.channelOrderNo(), settlement.amountCent());
        }

        int matched = 0;
        List<ReconcileDiff> diffs = new ArrayList<>();

        for (Map.Entry<String, Long> entry : local.entrySet()) {
            Long channelAmount = channel.get(entry.getKey());
            if (channelAmount == null) {
                diffs.add(new ReconcileDiff(entry.getKey(), DiffType.MISSING_IN_CHANNEL, entry.getValue(), null));
            } else if (!channelAmount.equals(entry.getValue())) {
                diffs.add(new ReconcileDiff(entry.getKey(), DiffType.AMOUNT_MISMATCH, entry.getValue(), channelAmount));
            } else {
                matched++;
            }
        }

        for (Map.Entry<String, Long> entry : channel.entrySet()) {
            if (!local.containsKey(entry.getKey())) {
                diffs.add(new ReconcileDiff(entry.getKey(), DiffType.MISSING_IN_LOCAL, null, entry.getValue()));
            }
        }

        return new ReconcileReport(local.size(), channel.size(), matched, diffs);
    }
}
