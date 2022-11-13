package com.panda.a6o6test.ui;

import android.content.Context;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

import com.panda.a6o6test.sensors.SensorConstants;

/**
 * A progress bar automatically counting down upon becoming visible
 */
public class CountdownBar extends ProgressBar {

    private static final int MAX = SensorConstants.UPRIGHT_STABLE_TIME_MILLIS;
    private static final int TICK_MILLIS = 32;

    private CountDownTimer countDownTimer;

    public CountdownBar(Context context) {
        super(context);
        init();
    }

    public CountdownBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CountdownBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        setMax(MAX);
        setIndeterminate(false);
        setProgress(MAX);
        countDownTimer = new CountDownTimer(MAX, TICK_MILLIS) {
            @Override
            public void onTick(long millisUntilFinished) {
                setProgress((int) millisUntilFinished);
            }

            @Override
            public void onFinish() {

            }
        };
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if(visibility == VISIBLE){
            setProgress(MAX);
            countDownTimer.start();
        }else{
            countDownTimer.cancel();
        }
    }
}
