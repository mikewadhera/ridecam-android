package com.ridecam.av.device;

import android.graphics.SurfaceTexture;

import java.io.IOException;

public interface CameraDevice<E> {

    public interface ErrorCallback {
        void onError(int errorType, CameraDevice cameraDevice);
    }

    public interface Parameters<P> {

        public static final String FOCUS_MODE_INFINITY = "infinity";

        public void setPreviewSize(int width, int height);
        public void setPreviewFpsRange(int minFps, int maxFps);
        public void setFocusMode(String value);
        public void setVideoStabilization(boolean toggle);
        //public void setRecordingHint(boolean recordingHint);
        //public void setPictureSize(int width, int height);
        public P getUnderlyingParameters();
    }

    public interface CameraInfo {
        public int getOrientation();
    }

    public void setErrorCallback(ErrorCallback errorCallback);
    public void setDisplayOrientation(int orientation);
    public CameraDevice.Parameters getParameters();
    public void setParameters(CameraDevice.Parameters params);
    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException;
    public void startPreview();
    public void stopPreview();
    public void release();
    public E getUnderlyingCamera();
    public void unlock();
    public void lock();

    // Added
    public CameraInfo getCameraInfo();

}