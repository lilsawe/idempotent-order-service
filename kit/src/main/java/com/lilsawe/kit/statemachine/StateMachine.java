package com.lilsawe.kit.statemachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 通用有限状态机。
 *
 * <p>把「状态 + 事件 -> 新状态」的转移表集中在一处，业务代码不再散落 if-else。
 * 典型用法：
 *
 * <pre>{@code
 * StateMachine<OrderStatus, OrderEvent> machine = StateMachine.<OrderStatus, OrderEvent>builder()
 *         .on(CREATED, PAY, PAID)
 *         .on(CREATED, CANCEL, CANCELLED)
 *         .on(PAID, SHIP, SHIPPED)
 *         .build();
 *
 * OrderStatus next = machine.next(CREATED, PAY);   // PAID
 * machine.next(SHIPPED, PAY);                      // 抛 IllegalStateException
 * }</pre>
 *
 * <p>对象构建后不可变，可安全地被多线程共享（例如作为 static final 常量）。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 */
public final class StateMachine<S, E> {

    private final Map<S, Map<E, S>> byEvent;
    private final Map<S, Set<S>> byTarget;
    private final boolean allowSameState;

    private StateMachine(Map<S, Map<E, S>> byEvent, Map<S, Set<S>> byTarget, boolean allowSameState) {
        this.byEvent = byEvent;
        this.byTarget = byTarget;
        this.allowSameState = allowSameState;
    }

    public static <S, E> Builder<S, E> builder() {
        return new Builder<>();
    }

    /** 按事件推进状态；非法流转抛 {@link IllegalStateException}。 */
    public S next(S from, E event) {
        Map<E, S> candidates = byEvent.get(from);
        S to = candidates == null ? null : candidates.get(event);
        if (to == null) {
            throw new IllegalStateException("非法流转: " + from + " --" + event + "--> ?");
        }
        return to;
    }

    /** 是否允许从 from 直接流转到 to。 */
    public boolean canTransit(S from, S to) {
        if (Objects.equals(from, to)) {
            return allowSameState;
        }
        return byTarget.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    /** 校验直接流转，不合法则抛 {@link IllegalStateException}。 */
    public void assertCanTransit(S from, S to) {
        if (!canTransit(from, to)) {
            throw new IllegalStateException("非法状态流转: " + from + " -> " + to);
        }
    }

    /** 某个状态的全部后继状态（只读，用于画图或校验）。 */
    public Set<S> successors(S from) {
        return Collections.unmodifiableSet(byTarget.getOrDefault(from, Collections.emptySet()));
    }

    /** 某个状态在给定事件下的目标状态；不可达时返回 null。 */
    public S target(S from, E event) {
        Map<E, S> candidates = byEvent.get(from);
        return candidates == null ? null : candidates.get(event);
    }

    public static final class Builder<S, E> {

        private final Map<S, Map<E, S>> byEvent = new LinkedHashMap<>();
        private final Map<S, Set<S>> byTarget = new LinkedHashMap<>();
        private boolean allowSameState = true;

        /** 是否允许「流转到同一状态」视为幂等成功，默认 true。 */
        public Builder<S, E> allowSameState(boolean allow) {
            this.allowSameState = allow;
            return this;
        }

        /** 登记一条事件驱动转移：from --event--> to。 */
        public Builder<S, E> on(S from, E event, S to) {
            byEvent.computeIfAbsent(from, key -> new LinkedHashMap<>()).put(event, to);
            return allow(from, to);
        }

        /** 登记终态（无后继状态），保证它出现在状态表里。 */
        public Builder<S, E> terminal(S state) {
            byTarget.computeIfAbsent(state, key -> new LinkedHashSet<>());
            return this;
        }

        /** 登记一条允许的直接转移（无事件语义时使用）。 */
        public Builder<S, E> allow(S from, S to) {
            byTarget.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(to);
            byTarget.computeIfAbsent(to, key -> new LinkedHashSet<>());
            return this;
        }

        public StateMachine<S, E> build() {
            Map<S, Map<E, S>> events = new LinkedHashMap<>();
            byEvent.forEach((from, targets) ->
                    events.put(from, Collections.unmodifiableMap(new LinkedHashMap<>(targets))));
            Map<S, Set<S>> targets = new LinkedHashMap<>();
            byTarget.forEach((from, tos) ->
                    targets.put(from, Collections.unmodifiableSet(new LinkedHashSet<>(tos))));
            return new StateMachine<>(
                    Collections.unmodifiableMap(events),
                    Collections.unmodifiableMap(targets),
                    allowSameState);
        }
    }
}
