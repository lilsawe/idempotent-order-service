package com.lilsawe.orderdemo.reconcile;

/** 渠道侧（支付 / 平台）的结算记录，用于与本地订单对账。 */
public record ChannelOrder(String channelOrderNo, long amountCent) {
}
