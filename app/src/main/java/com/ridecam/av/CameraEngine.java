package com.ridecam.av;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.ridecam.Knobs;
import com.ridecam.av.device.CameraDevice;
import com.ridecam.av.device.OSCamera;
import com.ridecam.av.device.vendor.SamsungDualCamera;

import static com.ridecam.ui.CameraFragment.sCachedSurfaceTexture;

public class CameraEngine {

    public interface ErrorListener {
        void onCameraError();
    }

    private static final String TAG = "CameraEngine";

    private Context mContext;
    private SurfaceTexture mSurfaceTexture;
    private CameraDevice mCamera;
    private boolean mAcquiringCameraLock;
    private ErrorListener mErrorListener;

    public CameraEngine(Context context, SurfaceTexture surfaceTexture) {
        mContext = context;
        mSurfaceTexture = surfaceTexture;
    }

    public static boolean usingSamsungDualCamera() {
        return (SamsungDualCamera.isAvailable() && !Knobs.FORCE_NATIVE_CAMERA);
    }

    public void acquireCamera() {
        if (mCamera == null && !mAcquiringCameraLock) {

            // Hold a lock to avoid double acquiring

            mAcquiringCameraLock = true;

            try {
                if (sCachedSurfaceTexture != null) {

                    if (usingSamsungDualCamera()) {
                        mCamera = SamsungDualCamera.open();
                    } else {
                        mCamera = OSCamera.open();
                    }

                    mCamera.setErrorCallback(new CameraDevice.ErrorCallback() {
                        @Override
                        public void onError(int errorType, CameraDevice camera) {
                        onCameraError(errorType);
                        }
                    });
                    setCameraDisplayOrientation(mCamera);
                    CameraDevice.Parameters params = mCamera.getParameters();
                    params.setPreviewSize(Knobs.PREVIEW_WIDTH, Knobs.PREVIEW_HEIGHT);
                    params.setPreviewFpsRange(Knobs.PREVIEW_FPS * 1000, Knobs.PREVIEW_FPS * 1000);
                    params.setFocusMode(CameraDevice.Parameters.FOCUS_MODE_INFINITY);
                    mCamera.setParameters(params);

                    mCamera.setPreviewTexture(mSurfaceTexture);

                    mCamera.startPreview();

                } else {

                    // Bad place to be
                    // In practice we should never acquire the camera before we
                    // cache a surface texture as we start service
                    // from onSurfaceTextureAvailable
                    // TODO add logging

                }
            } catch (Exception e) {
                e.printStackTrace();
                if (mErrorListener != null) {
                    mErrorListener.onCameraError();
                }
            } finally {
                mAcquiringCameraLock = false;
            }

        }
    }

    public void releaseCamera() {
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                mCamera.release();
            } finally {
                mCamera = null;
            }
        }
        mAcquiringCameraLock = false; // just to be safe
    }

    public void unlockCamera() {
        mCamera.unlock();
    }

    public void lockCamera() {
        mCamera.lock();
    }

    public CameraDevice getDevice() {
        return mCamera;
    }

    public void onCameraError(int errorType) {
        Log.e(TAG, "!!!!!!! onCameraError errorType: " + errorType);
        // TODO add logging
        if (mErrorListener != null) {
            mErrorListener.onCameraError();
        }
    }

    public void setCameraDisplayOrientation(CameraDevice camera) {
        int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        int result;
        CameraDevice.CameraInfo info = camera.getCameraInfo();

        result = (info.getOrientation() - degrees + 360) % 360;
        Log.d(TAG, "Adjusting preview orientation degrees: " + String.valueOf(result));
        camera.setDisplayOrientation(result);
    }

    public void setErrorListener(ErrorListener errorListener) {
        mErrorListener = errorListener;
    }
}
