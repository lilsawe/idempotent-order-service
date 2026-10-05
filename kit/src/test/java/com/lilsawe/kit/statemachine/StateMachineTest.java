package com.lilsawe.kit.statemachine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
