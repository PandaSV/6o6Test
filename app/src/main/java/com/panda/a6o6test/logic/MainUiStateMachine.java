package com.panda.a6o6test.logic;

import android.os.Handler;
import android.os.Looper;

import com.panda.a6o6test.sensors.SensorConstants;
import com.panda.a6o6test.ui.SimplePauseResumeListener;

import java.util.ArrayList;
import java.util.List;

/**
 * A FSM to coordinate transitions between all possible states of the UI
 */
public class MainUiStateMachine implements SimplePauseResumeListener {

    private static final MainUiStateMachine instance = new MainUiStateMachine();
    private final List<MainStateListener> listeners = new ArrayList<>();
    private MainLogicState state = MainLogicState.NO_PERMISSION;
    private Handler handler;

    private MainUiStateMachine(){}

    public static MainUiStateMachine getInstance(){
        return instance;
    }

    private void setState(MainLogicState state){
        this.state = state;
        notifyListeners();

        decideOnTimedTransition(this.state);
    }

    private void notifyListeners(){
        for (MainStateListener listener: listeners) {
            listener.onStateChanged(state);
        }
    }

    /**
     * On bad orientation of device
     */
    public void toBadAngle(){
        if(state.equals(MainLogicState.IDLE) || state.equals(MainLogicState.NO_FACE)
                || state.equals(MainLogicState.CONFIRMED_ANGLE) || state.equals(MainLogicState.ALL_GO)
                || state.equals(MainLogicState.WAITING_ANGLE)){
            setState(MainLogicState.BAD_ANGLE);
        }
    }

    /**
     * On permission or error resolved, allowing further work
     */
    public void toIdle(){
        if(state.equals(MainLogicState.NO_PERMISSION) || state.equals(MainLogicState.ERROR)){
            setState(MainLogicState.IDLE);
        }
    }

    /**
     * Progress to face detection from proper orientation upon button press.
     * Distinct from {@link MainUiStateMachine#toNoFaceFromDetection()} to use different condition
     */
    public void toNoFaceFromButton(){
        if(state.equals(MainLogicState.CONFIRMED_ANGLE)){
            setState(MainLogicState.NO_FACE);
        }
    }

    /**
     * Proceed with face detection after detected face disappeared
     * Distinct from {@link MainUiStateMachine#toNoFaceFromButton()} to use different condition
     */
    public void toNoFaceFromDetection(){
        if(state.equals(MainLogicState.ALL_GO)){
            setState(MainLogicState.NO_FACE);
        }
    }


    // On orientation being within acceptable bounds for the timer duration.
    // Private because the transition is governed internally in the state machine.
    private void confirmAngle(){
        if(state.equals(MainLogicState.WAITING_ANGLE)){
            setState(MainLogicState.CONFIRMED_ANGLE);
        }
    }

    /**
     * On orientation within acceptable bounds
     */
    public void toGoodAngle(){
        if(state.equals(MainLogicState.BAD_ANGLE)){
            setState(MainLogicState.WAITING_ANGLE);
        }
    }

    /**
     * On face detected, allowing to proceed to test
     */
    public void toAllGo(){
        if(state.equals(MainLogicState.NO_FACE)){
            setState(MainLogicState.ALL_GO);
        }
    }

    /**
     * On button press to start test
     */
    public void toTest(){
        if(state.equals(MainLogicState.ALL_GO)){
            setState(MainLogicState.TEST);
        }
    }

    /**
     * On any error except missing permission
     */
    public void toError(){
        setState(MainLogicState.ERROR);
    }

    /**
     * On missing camera permission
     */
    public void toNoPermission(){
        if(!state.equals(MainLogicState.TEST)){
            setState(MainLogicState.NO_PERMISSION);
        }
    }

    /**
     *
     * @param listener to receive notifications upon changing states
     */
    public void addStateListener(MainStateListener listener){
        listeners.add(listener);
    }

    /**
     * Remove listener
     * @param listener
     */
    public void removeListener(MainStateListener listener){
        listeners.remove(listener);
    }

    @Override
    public void onPause() {
        resetTimedTransition();
        handler = null;
    }

    @Override
    public void onResume() {
        handler = new Handler(Looper.getMainLooper());
    }

    // schedule transition if phone started being upright, cancel on any other state
    private void decideOnTimedTransition(MainLogicState state){
        if(!state.equals(MainLogicState.WAITING_ANGLE)){
            resetTimedTransition();
        }else{
            postTimedTransition();
        }
    }

    private void resetTimedTransition(){
        if(handler != null){
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void postTimedTransition(){
        if(handler != null){
            handler.postDelayed(this::confirmAngle, SensorConstants.UPRIGHT_STABLE_TIME_MILLIS);
        }
    }

    /**
     * Possible UI states
     */
    public enum MainLogicState{
        NO_PERMISSION, ERROR, IDLE, BAD_ANGLE, WAITING_ANGLE, CONFIRMED_ANGLE, NO_FACE, ALL_GO, TEST
    }
}
