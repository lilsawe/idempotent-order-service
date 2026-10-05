package com.lilsawe.orderdemo.reconcile;

import java.util.List;

/** 渠道对账单来源。生产环境对接支付网关 / 平台账单接口，测试中可直接用 lambda 桩实现。 */
@FunctionalInterface
public interface ChannelGateway {

    List<ChannelOrder> fetchSettlements();
}
