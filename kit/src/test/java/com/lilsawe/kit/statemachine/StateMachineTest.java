package com.lilsawe.kit.statemachine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest {

    enum Status { CREATED, PAID, SHIPPED, CANCELLED }

    enum Event { PAY, SHIP, CANCEL }

    private final StateMachine<Status, Event> machine = StateMachine.<Status, Event>builder()
            .on(Status.CREATED, Event.PAY, Status.PAID)
            .on(Status.CREATED, Event.CANCEL, Status.CANCELLED)
            .on(Status.PAID, Event.SHIP, Status.SHIPPED)
            .on(Status.PAID, Event.CANCEL, Status.CANCELLED)
            .terminal(Status.SHIPPED)
            .terminal(Status.CANCELLED)
            .build();

    @Test
    @DisplayName("按事件推进：CREATED --PAY--> PAID")
    void nextFollowsEventTransition() {
        assertEquals(Status.PAID, machine.next(Status.CREATED, Event.PAY));
    }

    @Test
    @DisplayName("非法事件抛 IllegalStateException")
    void illegalEventThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> machine.next(Status.SHIPPED, Event.PAY));
        assertTrue(ex.getMessage().contains("非法流转"));
    }

    @Test
    @DisplayName("直接流转校验：CREATED 不能直接到 SHIPPED")
    void assertCanTransitRejectsIllegalJump() {
        assertTrue(machine.canTransit(Status.CREATED, Status.PAID));
        assertFalse(machine.canTransit(Status.CREATED, Status.SHIPPED));
        assertThrows(IllegalStateException.class,
                () -> machine.assertCanTransit(Status.CREATED, Status.SHIPPED));
    }

    @Test
    @DisplayName("终态没有后继状态")
    void terminalHasNoSuccessors() {
        assertEquals(Set.of(), machine.successors(Status.SHIPPED));
    }

    @Test
    @DisplayName("默认允许流转到同一状态（幂等），可关闭")
    void sameStateBehaviourIsConfigurable() {
        assertTrue(machine.canTransit(Status.PAID, Status.PAID));

        StateMachine<Status, Event> strict = StateMachine.<Status, Event>builder()
                .allowSameState(false)
                .on(Status.CREATED, Event.PAY, Status.PAID)
                .build();
        assertFalse(strict.canTransit(Status.PAID, Status.PAID));
    }


    @Test
    @DisplayName("target 对未登记的事件返回 null")
    void targetReturnsNullForUnknownEvent() {
        assertEquals(Status.PAID, machine.target(Status.CREATED, Event.PAY));
        assertNull(machine.target(Status.CREATED, Event.SHIP));
        assertNull(machine.target(Status.SHIPPED, Event.PAY));
    }

    @Test
    @DisplayName("successors 返回全部后继状态；未知状态返回空集合")
    void successorsListsAllTargets() {
        assertEquals(Set.of(Status.PAID, Status.CANCELLED), machine.successors(Status.CREATED));
        assertEquals(Set.of(), machine.successors(Status.CANCELLED));
    }

    @Test
    @DisplayName("allow 可直接登记「状态 -> 状态」的转移")
    void allowRegistersDirectTransitions() {
        StateMachine<Status, Event> direct = StateMachine.<Status, Event>builder()
                .allow(Status.CREATED, Status.PAID)
                .terminal(Status.PAID)
                .build();

        assertTrue(direct.canTransit(Status.CREATED, Status.PAID));
        assertFalse(direct.canTransit(Status.PAID, Status.CREATED));
    }

    @Test
    @DisplayName("allowSameState(false) 时流转到同一状态被拒绝")
    void strictMachineRejectsSameStateTransition() {
        StateMachine<Status, Event> strict = StateMachine.<Status, Event>builder()
                .allowSameState(false)
                .allow(Status.CREATED, Status.PAID)
                .build();

        assertFalse(strict.canTransit(Status.PAID, Status.PAID));
        assertThrows(IllegalStateException.class, () -> strict.assertCanTransit(Status.PAID, Status.PAID));
    }

    @Test
    @DisplayName("canTransit 对 null 参数返回 false")
    void canTransitHandlesNull() {
        assertFalse(machine.canTransit(null, Status.PAID));
        assertFalse(machine.canTransit(Status.CREATED, null));
    }
}
