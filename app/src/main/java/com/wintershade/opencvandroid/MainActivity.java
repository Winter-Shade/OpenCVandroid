package com.wintershade.opencvandroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;

import android.hardware.camera2.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int PREVIEW_WIDTH = 1280;
    private static final int PREVIEW_HEIGHT = 720;

    static {
        // Make sure OpenCV is initialized early
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV init failed");
        } else {
            Log.d(TAG, "OpenCV loaded");
        }
    }

    private MyGLSurfaceView glSurfaceView;
    private MyGLRenderer glRenderer;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession previewSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private ImageReader imageReader;
    private boolean showCanny = true;

    private TextView fpsText;
    private Button toggleButton;

    // native binding
    static {
        System.loadLibrary("app"); // C++ native lib
    }
    public native void FindFeatures(long matGrayAddr, long matRgbaAddr);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout content = new FrameLayout(this);
        setContentView(content);

        // 1. GL Surface
        glSurfaceView = new MyGLSurfaceView(this);
        glRenderer = glSurfaceView.getRenderer();
        content.addView(glSurfaceView);

        // 2. FPS Text (bottom-right)
        fpsText = new TextView(this);
        fpsText.setText("FPS: --");
        fpsText.setTextColor(0xFFFFFFFF);
        fpsText.setTextSize(18f);
        fpsText.setPadding(8,8,8,8);
        fpsText.setBackgroundColor(0x80000000);
        FrameLayout.LayoutParams fpsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        fpsLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        fpsLp.setMargins(0,0,16,16); // end, bottom margin
        content.addView(fpsText, fpsLp);

        // 3. Toggle button (bottom-center)
        toggleButton = new Button(this);
        toggleButton.setText("Toggle Canny");
        toggleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        toggleButton.setTextColor(0xFFFFFFFF);
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btnLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        btnLp.setMargins(0,0,0,32); // bottom margin
        content.addView(toggleButton, btnLp);

        toggleButton.setOnClickListener(v -> showCanny = !showCanny);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        // Ensure overlays appear above GL
        fpsText.bringToFront();
        toggleButton.bringToFront();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        glSurfaceView.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        glSurfaceView.onPause();
        super.onPause();
    }

    private void openCamera() {
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size previewSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageReader.class)),
                    (o1, o2) -> Integer.compare(o1.getWidth() * o1.getHeight(), o2.getWidth() * o2.getHeight()));

            setupImageReader(PREVIEW_WIDTH, PREVIEW_HEIGHT);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "openCamera: " + e.getMessage(), e);
        }
    }

    private void setupImageReader(int w, int h) {
        imageReader = ImageReader.newInstance(w, h, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    processImage(image);
                }
            } catch (Exception e) {
                Log.e(TAG, "image avail error: " + e.getMessage(), e);
            } finally {
                if (image != null) image.close();
            }
        }, backgroundHandler);
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }
        @Override public void onDisconnected(CameraDevice camera) {
            cameraDevice.close(); cameraDevice = null;
        }
        @Override public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: " + error);
            cameraDevice.close(); cameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = new SurfaceTexture(0);
            // We do not use SurfaceTexture for preview on camera2 path here; instead attach ImageReader surface and a dummy SurfaceTexture to camera
            Surface surface = imageReader.getSurface();

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            previewSession = session;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                previewSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Session config failed", e);
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Session config failed");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "create session failed", e);
        }
    }

    private float avgFps = 0f;
    private final float fpsSmoothing = 0.1f; // adjust 0.05..0.2

    private void processImage(Image image) {
        long start = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();

        Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        byte[] yBytes = new byte[ySize];
        yBuffer.get(yBytes);

        byte[] nv21Bytes = new byte[ySize + ySize / 2];
        System.arraycopy(yBytes, 0, nv21Bytes, 0, ySize);

        int uvIndex = ySize;
        uBuffer.rewind();
        vBuffer.rewind();
        int rowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();
        int uvRowBytes = (width / 2) * uvPixelStride;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                nv21Bytes[uvIndex++] = (byte) uBuffer.get();  // U
                nv21Bytes[uvIndex++] = (byte) vBuffer.get();  // V
            }
            if (rowStride > uvRowBytes) {
                uBuffer.position(uBuffer.position() + (rowStride - uvRowBytes));
                vBuffer.position(vBuffer.position() + (rowStride - uvRowBytes));
            }
        }

        Mat yuvMat = new Mat(height * 3 / 2, width, CvType.CV_8UC1);
        yuvMat.put(0, 0, nv21Bytes);
        Mat rgbaMat = new Mat(height, width, CvType.CV_8UC4);
        Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21);

        Mat grayMat = new Mat();
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        if (showCanny) {
            FindFeatures(grayMat.getNativeObjAddr(), rgbaMat.getNativeObjAddr());
        }

        // Prepare byte array
        byte[] processedBytes = new byte[rgbaMat.rows() * rgbaMat.cols() * 4];
        rgbaMat.get(0, 0, processedBytes);
        ByteBuffer processedByteBuffer = ByteBuffer.allocateDirect(processedBytes.length).order(ByteOrder.nativeOrder());
        processedByteBuffer.put(processedBytes);
        processedByteBuffer.position(0);

        int frameWidth = rgbaMat.cols();
        int frameHeight = rgbaMat.rows();

        // Release mats
        grayMat.release();
        rgbaMat.release();
        yuvMat.release();

        // Send to GL thread
        glSurfaceView.queueEvent(() -> {
            glRenderer.updateTexture(processedByteBuffer, frameWidth, frameHeight);
        });

        long end = System.currentTimeMillis();
        float currentFps = 1000f / Math.max(1, (end - start));

        if (avgFps == 0f) {
            avgFps = currentFps;
        } else {
            avgFps = avgFps * (1 - fpsSmoothing) + currentFps * fpsSmoothing;
        }

        runOnUiThread(() -> {
            fpsText.setText(String.format("FPS: %.1f", avgFps));
            fpsText.invalidate(); // force redraw
        });

    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrupted", e);
            }
        }
    }

    private void closeCamera() {
        if (previewSession != null) {
            previewSession.close();
            previewSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    // simple fallback used to get a SurfaceTexture if needed (not used for rendering here)
    // keep a minimal access in renderer
}
