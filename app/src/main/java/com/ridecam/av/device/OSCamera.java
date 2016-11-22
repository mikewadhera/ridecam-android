package com.ridecam.av.device;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.io.IOException;

public class OSCamera implements CameraDevice<Camera> {

    private static final int CAMERA_ID = 0;

    private Camera mCamera;

    public static CameraDevice open() {
        return new OSCamera(Camera.open(CAMERA_ID));
    }

    public OSCamera(Camera camera) {
        mCamera = camera;
    }

    public class Parameters implements CameraDevice.Parameters<Camera.Parameters> {

        Camera.Parameters mParams;

        public Parameters(Camera.Parameters params) {
            mParams = params;
        }

        public void setPreviewSize(int width, int height) {
            mParams.setPreviewSize(width, height);
        }

        public void setPreviewFpsRange(int minFps, int maxFps) {
            mParams.setPreviewFpsRange(minFps, maxFps);
        }

        public void setFocusMode(String value) {
            if (value == FOCUS_MODE_INFINITY) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                mParams.setFocusMode(value);
            }
        }

        @Override
        public void setVideoStabilization(boolean toggle) {
            if (mParams.isVideoStabilizationSupported()) {
                mParams.setVideoStabilization(toggle);
            }
        }

        public Camera.Parameters getUnderlyingParameters() {
            return mParams;
        }

    }

    public class CameraInfo implements CameraDevice.CameraInfo {

        Camera.CameraInfo mCameraInfo;

        public CameraInfo(Camera.CameraInfo cameraInfo) {
            mCameraInfo = cameraInfo;
        }

        public int getOrientation() {
            return mCameraInfo.orientation;
        }

    }

    public void setDisplayOrientation(int displayOrientation) {
        mCamera.setDisplayOrientation(displayOrientation);
    }

    public Parameters getParameters() {
        return new Parameters(mCamera.getParameters());
    }

    public void startPreview() {
        mCamera.startPreview();
    }

    public void stopPreview() {
        mCamera.stopPreview();
    }

    public void setParameters(CameraDevice.Parameters parameters) {
        mCamera.setParameters((Camera.Parameters)parameters.getUnderlyingParameters());
    }

    public void release() {
        mCamera.release();
    }

    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException {
        mCamera.setPreviewTexture(surfaceTexture);
    }

    public void setErrorCallback(final ErrorCallback errorCallback) {
        mCamera.setErrorCallback(new Camera.ErrorCallback() {
            @Override
            public void onError(int i, Camera camera) {
                errorCallback.onError(i, new OSCamera(camera));
            }
        });
    }

    public Camera getUnderlyingCamera() {
        return mCamera;
    }

    public void unlock() {
        mCamera.unlock();
    }

    public void lock() {
        mCamera.lock();
    }

    public CameraInfo getCameraInfo() {
        android.hardware.Camera.CameraInfo info = new Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(CAMERA_ID, info);
        return new CameraInfo(info);
    }

}
