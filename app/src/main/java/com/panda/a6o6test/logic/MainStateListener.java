package com.panda.a6o6test.logic;

public interface MainStateListener {

    /**
     *
     * @param state reported state of the main FSM
     */
    void onStateChanged(MainUiStateMachine.MainLogicState state);
}
