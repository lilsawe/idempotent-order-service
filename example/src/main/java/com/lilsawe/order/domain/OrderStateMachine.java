package com.lilsawe.order.domain;

import com.lilsawe.kit.statemachine.StateMachine;

/**
 * 订单状态机：转移表集中定义，校验逻辑复用 kit 的通用 {@link StateMachine}。
 *
 * <p>业务侧只声明"哪些流转合法"，不再自己写 Map/EnumSet 与判断代码。
 */
public final class OrderStateMachine {

    /** 订单状态的合法流转表（不可变，可安全共享）。 */
    public static final StateMachine<OrderStatus, OrderEvent> MACHINE =
            StateMachine.<OrderStatus, OrderEvent>builder()
                    .on(OrderStatus.CREATED, OrderEvent.PAY, OrderStatus.PAID)
                    .on(OrderStatus.CREATED, OrderEvent.CANCEL, OrderStatus.CANCELLED)
                    .on(OrderStatus.PAID, OrderEvent.SHIP, OrderStatus.SHIPPED)
                    .on(OrderStatus.PAID, OrderEvent.CANCEL, OrderStatus.CANCELLED)
                    .terminal(OrderStatus.SHIPPED)
                    .terminal(OrderStatus.CANCELLED)
                    .build();

    private OrderStateMachine() {
    }

    /** 是否允许该流转（重复流转到同一状态视为幂等成功）。 */
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return MACHINE.canTransit(from, to);
    }

    /** 校验流转，非法则抛异常。 */
    public static void assertTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            throw new IllegalStateException("状态不能为空");
        }
        MACHINE.assertCanTransit(from, to);
    }

    /** 按事件推进状态。 */
    public static OrderStatus onEvent(OrderStatus from, OrderEvent event) {
        return MACHINE.next(from, event);
    }
}
