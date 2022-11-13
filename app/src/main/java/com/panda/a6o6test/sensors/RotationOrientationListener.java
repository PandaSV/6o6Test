package com.panda.a6o6test.sensors;

public interface RotationOrientationListener {

    /**
     *
     * @param pitch in degrees as delta from correct upright orientation
     * @param roll in degrees as delta from correct upright orientation
     */
    void onOrientationChanged(float pitch, float roll);
}
