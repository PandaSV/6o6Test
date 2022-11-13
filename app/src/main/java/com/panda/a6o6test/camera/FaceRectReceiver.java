package com.panda.a6o6test.camera;

import android.graphics.Rect;

public interface FaceRectReceiver {

    /**
     * @param rect Bounds of the detected face
     */
    void setFaceRect(Rect rect);
}
