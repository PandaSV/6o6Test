package com.panda.a6o6test.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.panda.a6o6test.R;
import com.panda.a6o6test.camera.CameraLogic;
import com.panda.a6o6test.camera.CameraSurfaceView;
import com.panda.a6o6test.logic.MainUiStateMachine;
import com.panda.a6o6test.permissions.CameraPermissionCallback;
import com.panda.a6o6test.permissions.PermissionUtility;
import com.panda.a6o6test.sensors.OrientationManager;

public class CameraFragment extends Fragment implements CameraPermissionCallback{

    private CameraSurfaceView surfaceView;
    private ActivityResultLauncher<String> activityResultLauncher;
    private Handler cameraHandler;
    private HandlerThread handlerThread;
    private OrientationManager orientationManager;
    private boolean triedGettingPermission;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        activityResultLauncher = PermissionUtility.registerForCameraPermission(this, this);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.camera_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        surfaceView = view.findViewById(R.id.video_surface);
        if(getContext()!=null) {
            orientationManager = new OrientationManager(getContext());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MainUiStateMachine.getInstance().onResume();
        startBgThread();
        if(PermissionUtility.checkCameraPermission(getContext())) {
            surfaceView.post(() -> startCamera(cameraHandler));
        }else if(!triedGettingPermission){
            PermissionUtility.requestCameraPermission(activityResultLauncher);
            triedGettingPermission = true;
        }
        orientationManager.startListening(surfaceView);
    }

    @Override
    public void onPause() {
        MainUiStateMachine.getInstance().onPause();
        stopCamera();
        orientationManager.stopListening();
        stopBgThread();
        super.onPause();
    }

    private void startCamera(Handler handler){
        CameraLogic.getInstance().startCamera(this.getContext(), surfaceView, handler);
    }

    private void stopCamera(){
        CameraLogic.getInstance().stopCamera();
    }

    private void startBgThread(){
        handlerThread = new HandlerThread("Cam");
        handlerThread.start();
        cameraHandler = new Handler(handlerThread.getLooper());
    }

    private void stopBgThread(){
        handlerThread.quitSafely();

        try {
            handlerThread.join();
            handlerThread = null;
            cameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPermission(boolean hasPermission) {
        if(hasPermission){
            MainUiStateMachine.getInstance().toIdle();
            startCamera(cameraHandler);
        }else{
            stopCamera();
            MainUiStateMachine.getInstance().toNoPermission();
        }
        triedGettingPermission = true;
    }
}
