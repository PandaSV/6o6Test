package com.panda.a6o6test.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.panda.a6o6test.logic.MainUiStateMachine;
import com.panda.a6o6test.permissions.PermissionUtility;

import java.util.List;
import java.util.Vector;

/**
 * Takes care of Camera logic.
 * Keeps reference to CameraDevice to stop when Pause is propagated from Fragment.
 * Logic partially taken from online example.
 * Preview fitting to screen size was not implemented, to cut on development time.
 * TODO change SurfaceView to TextureView for easier scaling/fitting
 */
public class CameraLogic {

    private static final int IMG_BUFFER_SIZE = 4;

    private static CameraLogic instance;
    private CameraDevice mCameraDevice;
    private FaceDetector faceDetector;
    private ImageReader imageReader;

    private CameraLogic(){}

    /**
     * @return Singleton instance
     */
    public static CameraLogic getInstance(){
        if(instance == null){
            instance = new CameraLogic();
        }
        return instance;
    }

    /**
     * Attempts to start camera
     * @param context
     * @param surfaceView
     * @param handler
     */
    public void startCamera(Context context, CameraSurfaceView surfaceView, Handler handler) {
        if(context == null){
            MainUiStateMachine.getInstance().toError();
            return;
        }
        boolean hasPermission = PermissionUtility.checkCameraPermission(context);
        if(!hasPermission){
            MainUiStateMachine.getInstance().toNoPermission();
        }else{
            startCameraPreview(context, surfaceView, handler);
        }
    }

    /**
     * Stops camera. To be called from onPause.
     */
    public void stopCamera() {
        if(imageReader != null){
            imageReader.close();
            imageReader = null;
        }
        if(faceDetector != null){
            faceDetector.close();
            faceDetector = null;
        }
        if(mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    // Picks the selfie camera from the list by LENS_FACING_FRONT characteristic
    private String getSelfieCameraId(CameraManager manager) throws CameraAccessException {
        String[] cams = manager.getCameraIdList();
        for (String cam : cams) {
            CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cam);
            int cameraFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (cameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                return cam;
            }
        }
        return null;
    }

    private void startCameraPreview(Context context, CameraSurfaceView surfaceView, Handler handler){
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String selfieCameraId = getSelfieCameraId(manager);
            manager.openCamera(selfieCameraId, getCameraDeviceStateCallback(surfaceView, handler), null);
            FaceDetectorOptions options =
                    new FaceDetectorOptions.Builder()
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                            .setMinFaceSize(0.15f)
                            .enableTracking()
                            .build();
            faceDetector = FaceDetection.getClient(options);
            imageReader = ImageReader.newInstance(1080, 1920, ImageFormat.YUV_420_888, IMG_BUFFER_SIZE);
            imageReader.setOnImageAvailableListener(getOnImageAvailableListener(surfaceView), handler);
        } catch (CameraAccessException | SecurityException e) {
            MainUiStateMachine.getInstance().toNoPermission();
            e.printStackTrace();
        }
    }

    private ImageReader.OnImageAvailableListener getOnImageAvailableListener(FaceRectReceiver faceRectReceiver){
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //Log.d("MLLIB", "onImageAvailable");
                Image image = reader.acquireLatestImage();
                faceDetector.process(image, 0).addOnCanceledListener(new OnCanceledListener() {
                    @Override
                    public void onCanceled() {
                        Log.d("MLLIB", "onCanceled");
                    }
                }).addOnSuccessListener(new OnSuccessListener<List<com.google.mlkit.vision.face.Face>>() {
                    @Override
                    public void onSuccess(List<com.google.mlkit.vision.face.Face> faces) {
                        if(faces != null && faces.size()>0){
                            Log.d("MLLIB", "onSuccess "+faces.size());
                            faceRectReceiver.setFaceRect(faces.get(0).getBoundingBox());
                            MainUiStateMachine.getInstance().toAllGo();
                        }else{
                            faceRectReceiver.setFaceRect(null);
                            MainUiStateMachine.getInstance().toNoFaceFromDetection();
                        }
                    }
                });
                image.close();
            }
        };
    }

    private CameraDevice.StateCallback getCameraDeviceStateCallback(CameraSurfaceView surfaceView, Handler handler){
        return new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                mCameraDevice = cameraDevice;
                final CameraCaptureSession.StateCallback stateCallback = getCaptureSessionCallback(cameraDevice, surfaceView);

                Vector<Surface> surfaces = new Vector<>();
                surfaces.add(surfaceView.getHolder().getSurface());
                surfaces.add(imageReader.getSurface());
                try {
                    cameraDevice.createCaptureSession(surfaces, stateCallback, handler);
                    MainUiStateMachine.getInstance().toIdle();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    MainUiStateMachine.getInstance().toNoPermission();
                } catch (Exception e){
                    MainUiStateMachine.getInstance().toError();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                MainUiStateMachine.getInstance().toError();
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                MainUiStateMachine.getInstance().toError();
            }
        };
    }

    private CameraCaptureSession.StateCallback getCaptureSessionCallback(CameraDevice cameraDevice, CameraSurfaceView surfaceView){
        return new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                try {
                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                            CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                    builder.addTarget(surfaceView.getHolder().getSurface());
                    builder.addTarget(imageReader.getSurface());
                    cameraCaptureSession.setRepeatingRequest(builder.build(), getCaptureCallback(surfaceView), null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    MainUiStateMachine.getInstance().toError();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                MainUiStateMachine.getInstance().toError();
            }
        };
    }

    private CameraCaptureSession.CaptureCallback getCaptureCallback(FaceRectReceiver faceRectReceiver){
        return new CameraCaptureSession.CaptureCallback() {

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
//                lookForFaces(result);
            }

            void lookForFaces(CaptureResult result){
                Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
                if(faces != null && faces.length>0){
                    faceRectReceiver.setFaceRect(faces[0].getBounds());
                    MainUiStateMachine.getInstance().toAllGo();
                }else{
                    faceRectReceiver.setFaceRect(null);
                    MainUiStateMachine.getInstance().toNoFaceFromDetection();
                }
            }
        };
    }

}
