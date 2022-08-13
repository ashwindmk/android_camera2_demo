package com.ashwin.android.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class CameraController {
    private static final String TAG = "CameraController";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Context context;
    private Handler bgHandler;
    private HandlerThread bgHandlerThread;
    private CameraDevice mCameraDevice;
    private Size imageDimension;
    private ImageReader previewImgReader, captureImgReader;
    private SurfaceTexture surfaceTexture;
    private CameraCaptureSession previewSession;

    public CameraController(Context context) {
        this.context = context;
    }

    public void startPreview(SurfaceTexture surfaceTexture, Consumer<byte[]> bytesConsumer) {
        Log.d(TAG, "startPreview");
        this.surfaceTexture = surfaceTexture;
        openCamera(bytesConsumer, false);
    }

    public void startPreviewAndCapture(SurfaceTexture surfaceTexture, Consumer<byte[]> bytesConsumer) {
        Log.d(TAG, "startPreviewAndCapture");
        this.surfaceTexture = surfaceTexture;
        openCamera(bytesConsumer, true);
    }

    private void openCamera(Consumer<byte[]> bytesConsumer, boolean capture) {
        Log.d(TAG, "openCamera(surfaceTexture = " + surfaceTexture + ", bytesConsumer)");

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No camera permission");
            bytesConsumer.accept(null);
            return;
        }

        startBgThread();

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String cameraId = getCameraId(manager);
        if (cameraId == null) {
            Log.e(TAG, "No camera found, cameraId is null.");
            bytesConsumer.accept(null);
            return;
        }

        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            imageDimension = getImageDimension(characteristics);

            surfaceTexture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

            CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "CameraDevice.StateCallback onOpened");
                    mCameraDevice = camera;
                    startPreview(bytesConsumer, capture);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.e(TAG, "CameraDevice.StateCallback onDisconnected");
                    closeCamera();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "CameraDevice.StateCallback onError");
                    closeCamera();
                }
            };

            manager.openCamera(cameraId, cameraStateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Open camera exception", e);
            bytesConsumer.accept(null);
        }
    }

    private String getCameraId(CameraManager manager) {
        try {
            return manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            Log.e(TAG, "getCamera exception", e);
        }

        // Get primary camera
        /*try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation != CAMERACHOICE) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e){
            e.printStackTrace();
        }*/

        return null;
    }

    private void startPreview(Consumer<byte[]> bytesConsumer, boolean capture) {
        Log.d(TAG, "startCameraPreview");
        try {
            // Set-up preview image reader
            if (previewImgReader != null) {
                previewImgReader.close();
            }
            previewImgReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 1);
            ImageReader.OnImageAvailableListener imageAvailableListener = imageReader -> {
                // Bg thread
                Image image = imageReader.acquireLatestImage();
                // Close the image to free memory and prevent surfaceTexture from freezing.
                if (image != null) {
                    image.close();
                }
            };
            previewImgReader.setOnImageAvailableListener(imageAvailableListener, bgHandler);

            Surface surface = new Surface(surfaceTexture);

            CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(previewImgReader.getSurface());
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);

            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());
            Range<Integer> fpsRange = getFpsRange(characteristics);
            if (fpsRange != null) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            }

            CameraCaptureSession.StateCallback previewStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (mCameraDevice == null) {
                        bytesConsumer.accept(null);
                        return;
                    }

                    // Start preview
                    previewSession = cameraCaptureSession;
                    try {
                        previewSession.setRepeatingRequest(previewRequestBuilder.build(), null, bgHandler);
                    } catch (Exception e) {
                        Log.e(TAG, "startCameraPreview exception", e);
                        bytesConsumer.accept(null);
                    }

                    if (capture) {
                        // Take photo a few ms after the preview starts
                        new Handler().postDelayed(() -> capture(bytesConsumer, false), 1000L);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "startCameraPreview StateCallback onConfigureFailed");
                    bytesConsumer.accept(null);
                }
            };

            mCameraDevice.createCaptureSession(Arrays.asList(surface, previewImgReader.getSurface()), previewStateCallback, null);
        } catch (Exception e) {
            Log.e(TAG, "startCameraPreview exception", e);
            bytesConsumer.accept(null);
        }
    }

    public void capture(Consumer<byte[]> bytesConsumer, boolean startPreview) {
        Log.d(TAG, "capture");
        if (surfaceTexture == null) {
            Log.e(TAG, "capture: surfaceTexture is null, start the preview first.");
            bytesConsumer.accept(null);
            return;
        }

        if (mCameraDevice == null) {
            Log.e(TAG, "capture: CameraDevice is null, start the preview first.");
            bytesConsumer.accept(null);
            return;
        }

        try {
            // Set-up capture image reader
            if (captureImgReader != null) {
                captureImgReader.close();
            }
            captureImgReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 1);
            ImageReader.OnImageAvailableListener imageAvailableListener = imageReader -> {
                // Bg thread
                Log.d(TAG, "captureImgReader onImageAvailable");
                save(imageReader, bytesConsumer);
                if (!startPreview) {
                    // Close camera if you do not want to continue preview
                    closeCamera();
                }
            };
            captureImgReader.setOnImageAvailableListener(imageAvailableListener, bgHandler);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(captureImgReader.getSurface());
            outputSurfaces.add(new Surface(surfaceTexture));

            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(captureImgReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);

            // Capture listener
            final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.d(TAG, "onCaptureCompleted");
                    if (startPreview) {
                        startPreview(bytesConsumer, false);
                    }
                }
            };

            CameraCaptureSession.StateCallback captureStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "StateCallback.onConfigured");
                    if (mCameraDevice == null) {
                        bytesConsumer.accept(null);
                        return;
                    }

                    try {
                        // Capture photo
                        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, null);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "StateCallback.onConfigured exception", e);
                        bytesConsumer.accept(null);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "StateCallback.onConfigureFailed");
                    bytesConsumer.accept(null);
                }
            };

            mCameraDevice.createCaptureSession(outputSurfaces, captureStateCallback,null);
        } catch (Exception e) {
            Log.e(TAG, "capture exception", e);
            bytesConsumer.accept(null);
        }
    }

    private void save(ImageReader imageReader, Consumer<byte[]> bytesConsumer) {
        Log.d(TAG, "save image reader");

        Image image = null;
        byte[] bytes = null;
        try {
            image = imageReader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
        } catch (Exception e) {
            String error = "Save image to file exception";
            Log.e(TAG, error, e);
        } finally {
            if (image != null) {
                image.close();
            }
            imageReader.close();
            bytesConsumer.accept(bytes);
        }
    }

    private void startBgThread() {
        stopBgThread();
        bgHandlerThread = new HandlerThread("camera-bg-thread");
        bgHandlerThread.start();
        bgHandler = new Handler(bgHandlerThread.getLooper());
    }

    private void stopBgThread() {
        if (bgHandlerThread != null) {
            bgHandlerThread.quit();
        }
    }

    private Size getImageDimension(CameraCharacteristics characteristics) {
        Size screenSize = getScreenAspectRatio();
        Size maxSize = new Size(screenSize.getWidth(), screenSize.getHeight());

        StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (configurationMap == null) {
            return maxSize;
        }

        // Get max size which equals screen aspect ratio by WME
        float screenRatio = (float) screenSize.getWidth() / screenSize.getHeight();
        Size[] jpgSizeList = configurationMap.getOutputSizes(ImageFormat.JPEG);
        for (Size s : jpgSizeList) {
            float ratio = (float) s.getWidth() / s.getHeight();
            if (Math.abs(ratio - screenRatio) < 0.01f && (s.getWidth() > maxSize.getWidth() || s.getHeight() > maxSize.getHeight())) {
                maxSize = s;
            }
        }

        Log.d(TAG, "imageDimension w = " + maxSize.getWidth() + ", h = " + maxSize.getHeight());
        return maxSize;
    }

    private Size getScreenAspectRatio() {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return new Size(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    public void closeCamera() {
        Log.d(TAG, "close camera");
        if (previewSession != null) {
            try {
                previewSession.stopRepeating();
                previewSession.abortCaptures();
            } catch (Exception ignore) {
            }
            previewSession.close();
            previewSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (previewImgReader != null) {
            previewImgReader.close();
            previewImgReader = null;
        }

        if (captureImgReader != null) {
            captureImgReader.close();
            captureImgReader = null;
        }

        if (surfaceTexture != null) {
            surfaceTexture = null;
        }
    }

    private Range<Integer> getFpsRange(CameraCharacteristics characteristics) {
        Range<Integer> fpsRange = null;
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges != null) {
            for (Range<Integer> range : ranges) {
                //Log.d(TAG, "  [FPS Range Available]: " + range + ", upper = " + range.getUpper());
                if (fpsRange == null || (fpsRange.getUpper() < range.getUpper())) {
                    fpsRange = range;
                }
            }
        }

        Log.d(TAG, "Best FPS range = " + fpsRange);
        return fpsRange;
    }

    public void close() {
        closeCamera();
        stopBgThread();
    }
}
