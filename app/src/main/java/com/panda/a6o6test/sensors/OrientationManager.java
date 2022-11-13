package com.panda.a6o6test.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.Nullable;

import com.panda.a6o6test.logic.MainUiStateMachine;

/**
 * Listens to sensors, processes orientation.
 * Logic partially taken from online sample.
 */
public class OrientationManager implements SensorEventListener {

    private static final int SENSOR_DELAY_MICROS = 16 * 1000; // 16ms for roughly 60fps

    private final SensorManager mSensorManager;

    @Nullable
    private final Sensor rotationVectorSensor;

    private int mLastAccuracy;
    private RotationOrientationListener orientationListener;

    public OrientationManager(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Activity.SENSOR_SERVICE);
        rotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    /**
     *
     * @param listener to receive orientation changes
     */
    public void startListening(RotationOrientationListener listener) {
        if (orientationListener == listener) {
            return;
        }
        orientationListener = listener;
        if (rotationVectorSensor == null) {
            MainUiStateMachine.getInstance().toError();
            return;
        }
        mSensorManager.registerListener(this, rotationVectorSensor, SENSOR_DELAY_MICROS);
    }

    /**
     * Stops listening to sensors
     */
    public void stopListening() {
        mSensorManager.unregisterListener(this);
        orientationListener = null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (mLastAccuracy != accuracy) {
            mLastAccuracy = accuracy;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (orientationListener == null) {
            return;
        }
        if (mLastAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return;
        }
        if (event.sensor == rotationVectorSensor) {
            processEvent(event.values);
        }
    }

    // this part was largely taken from an example
    private void processEvent(float[] rotationVector) {
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);

        final int worldAxisForDeviceAxisX = SensorManager.AXIS_X;
        final int worldAxisForDeviceAxisY = SensorManager.AXIS_Z;

        float[] adjustedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                worldAxisForDeviceAxisY, adjustedRotationMatrix);

        // Transform rotation matrix into azimuth/pitch/roll
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);

        float pitch = (float) Math.toDegrees(orientation[1]) * -1;
        float roll = (float) Math.toDegrees(orientation[2]) * -1;

        orientationListener.onOrientationChanged(pitch, roll);

        if(Math.abs(pitch) < SensorConstants.PITCH_TOLERANCE_DEG && Math.abs(roll) < SensorConstants.ROLL_TOLERANCE_DEG){
            MainUiStateMachine.getInstance().toGoodAngle();
        }else{
            MainUiStateMachine.getInstance().toBadAngle();
        }
    }
}