package com.panda.a6o6test.permissions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * Just a utility concentrating permission-related code
 */
public class PermissionUtility {

    /**
     * @param context
     * @return whether app has permission to use Camera
     */
    public static boolean checkCameraPermission(Context context){
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Execute request permission
     * @param launcher kept from registering in {@link PermissionUtility#registerForCameraPermission(ActivityResultCaller, CameraPermissionCallback)}
     */
    public static void requestCameraPermission(ActivityResultLauncher<String> launcher){
        launcher.launch(Manifest.permission.CAMERA);
    }

    /**
     * Register component to receive permission
     * @param caller 
     * @param callback
     * @return launcher to be used in {@link PermissionUtility#requestCameraPermission(ActivityResultLauncher)}
     */
    public static ActivityResultLauncher<String> registerForCameraPermission(ActivityResultCaller caller, CameraPermissionCallback callback){
        return caller.registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                callback::onPermission);
    }

}
