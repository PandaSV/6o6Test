package com.panda.a6o6test.camera;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.panda.a6o6test.logic.MainUiStateMachine;
import com.panda.a6o6test.permissions.PermissionUtility;
import com.panda.a6o6test.ui.HudView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Takes care of Camera logic.
 * Keeps reference to CameraDevice to stop when Pause is propagated from Fragment.
 * Logic partially taken from online example.
 * Preview fitting to screen size was not implemented, to cut on development time.
 */
public class CameraLogic {

    private static final int IMG_BUFFER_SIZE = 4;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static CameraLogic instance;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private FaceDetector faceDetector;
    private ImageReader imageReader;
    private Size mPreviewSize;

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
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e("CameraLogic", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(Activity activity, AutoFitTextureView textureView, HudView hudView, Handler handler, int width, int height) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                FaceDetectorOptions options =
                        new FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                .setMinFaceSize(0.15f)
                                .enableTracking()
                                .build();
                faceDetector = FaceDetection.getClient(options);

                imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.YUV_420_888, IMG_BUFFER_SIZE);
//                imageReader.setOnImageAvailableListener(
//                        getOnImageAvailableListener(), mBackgroundHandler);
                imageReader.setOnImageAvailableListener(getOnImageAvailableListener(hudView, width, height), handler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                int mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e("CameraLogic", "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = activity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    textureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    public void configureTransform(Activity activity, TextureView textureView, int viewWidth, int viewHeight) {
        if (null == textureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession(AutoFitTextureView textureView, HudView hudView, Handler handler) {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            CaptureRequest.Builder builder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.addTarget(imageReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the camera preview.
                                CaptureRequest previewRequest = builder.build();
                                mCaptureSession.setRepeatingRequest(previewRequest,
                                        null, handler);
                                MainUiStateMachine.getInstance().toIdle();
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                                MainUiStateMachine.getInstance().toNoPermission();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            //TODO
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Attempts to start camera
     * @param activity
     * @param surfaceView
     * @param handler
     */
    public void startCamera(Activity activity, AutoFitTextureView surfaceView, HudView hudView, Handler handler, int width, int height) {
        if(activity == null){
            MainUiStateMachine.getInstance().toError();
            return;
        }
        boolean hasPermission = PermissionUtility.checkCameraPermission(activity);
        if(!hasPermission){
            MainUiStateMachine.getInstance().toNoPermission();
        }else{
            startCameraPreview(activity, surfaceView, hudView, handler, width, height);
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

    private void startCameraPreview(Activity activity, AutoFitTextureView surfaceView, HudView hudView, Handler handler, int width, int height){
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String selfieCameraId = getSelfieCameraId(manager);
            setUpCameraOutputs(activity, surfaceView, hudView, handler, width, height);
            configureTransform(activity, surfaceView, width, height);
            manager.openCamera(selfieCameraId, getCameraDeviceStateCallback(surfaceView, hudView, handler, width, height), null);
        } catch (CameraAccessException | SecurityException e) {
            MainUiStateMachine.getInstance().toNoPermission();
            e.printStackTrace();
        }
    }

    private ImageReader.OnImageAvailableListener getOnImageAvailableListener(FaceRectReceiver faceRectReceiver, int width, int height){
        return new ImageReader.OnImageAvailableListener() {
            private boolean isProcessing = false;
            @Override
            public void onImageAvailable(ImageReader reader) {
                if(isProcessing){
                    return;
                }
                Image image = reader.acquireLatestImage();
                isProcessing = true;
                faceDetector.process(image, 0).addOnCanceledListener(new OnCanceledListener() {
                    @Override
                    public void onCanceled() {
                        Log.d("MLLIB", "onCanceled");
                    }
                }).addOnSuccessListener(faces -> {
                    if(faces != null && faces.size()>0){
                        Log.d("MLLIB", "onSuccess "+faces.size());
                        faceRectReceiver.setFaceRect(faces.get(0).getBoundingBox(), 90, width, height);
                        MainUiStateMachine.getInstance().toAllGo();
                    }else{
                        faceRectReceiver.resetFaceRect();
                        MainUiStateMachine.getInstance().toNoFaceFromDetection();
                    }
                }).addOnCompleteListener(new OnCompleteListener<List<com.google.mlkit.vision.face.Face>>() {
                    @Override
                    public void onComplete(@NonNull Task<List<com.google.mlkit.vision.face.Face>> task) {
                        Log.d("MLLIB", "onComplete ");
                        image.close();
                        isProcessing = false;
                    }
                }).addOnFailureListener(e -> {
                    Log.d("MLLIB", "onFailure "+e.getMessage());
                });
            }
        };
    }

    private CameraDevice.StateCallback getCameraDeviceStateCallback(AutoFitTextureView textureView, HudView hudView, Handler handler,  int width, int height){
        return new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                mCameraDevice = cameraDevice;
                createCameraPreviewSession(textureView, hudView, handler);
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

}
