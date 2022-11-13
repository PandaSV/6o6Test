package com.panda.a6o6test.ui;

import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.ALL_GO;
import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.BAD_ANGLE;
import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.ERROR;
import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.CONFIRMED_ANGLE;
import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.NO_FACE;
import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.NO_PERMISSION;
import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.TEST;
import static com.panda.a6o6test.logic.MainUiStateMachine.MainLogicState.WAITING_ANGLE;

import com.panda.a6o6test.R;
import com.panda.a6o6test.logic.MainUiStateMachine;

import java.util.HashMap;
import java.util.Map;

/**
 * A utility to map UI views to UI states
 */
public class StateViewMap {

    private static final Map<MainUiStateMachine.MainLogicState, Integer> map = new HashMap<>();

    static {
        map.put(TEST, R.id.testing_view);
        map.put(NO_FACE, R.id.no_face_view);
        map.put(NO_PERMISSION, R.id.no_permission_view);
        map.put(ALL_GO, R.id.all_go_view);
        map.put(CONFIRMED_ANGLE, R.id.good_angle_view);
        map.put(WAITING_ANGLE, R.id.waiting_upright_view);
        map.put(BAD_ANGLE, R.id.not_upright_view);
        map.put(ERROR, R.id.error_view);
    }

    /**
     * @param state
     * @return UI view id for given UI state
     */
    public static int getIdForState(MainUiStateMachine.MainLogicState state){
        Integer result = map.get(state);
        if(result == null){
            result = R.id.error_view;
        }
        return result;
    }
}
