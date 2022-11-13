package com.panda.a6o6test.permissions;

public interface CameraPermissionCallback {
    /**
     * When permission reaction occurs
     * @param hasPermission camera permission
     */
    void onPermission(boolean hasPermission);
}
