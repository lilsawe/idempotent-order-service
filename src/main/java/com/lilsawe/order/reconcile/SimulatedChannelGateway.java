package com.lilsawe.order.reconcile;

import com.lilsawe.order.domain.OrderEntity;
import com.lilsawe.order.domain.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟渠道对账单：以本地订单为蓝本，故意制造 1 笔金额差异和 1 笔渠道多单，
 * 方便直接看到对账效果。生产环境替换为真实渠道账单接口即可。
 */
@Component
public class SimulatedChannelGateway implements ChannelGateway {

    private static final long DEMO_EXTRA_AMOUNT_CENT = 8888L;

    private final OrderRepository orderRepository;

    public SimulatedChannelGateway(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public List<ChannelOrder> fetchSettlements() {
        List<OrderEntity> orders = orderRepository.findAll();
        List<ChannelOrder> settlements = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            OrderEntity order = orders.get(i);
            long amount = (i == 0) ? Math.max(0L, order.getAmountCent() - 100L) : order.getAmountCent();
            settlements.add(new ChannelOrder(order.getOrderNo(), amount));
        }
        settlements.add(new ChannelOrder("CH-ONLY-0001", DEMO_EXTRA_AMOUNT_CENT));
        return settlements;
    }
}
