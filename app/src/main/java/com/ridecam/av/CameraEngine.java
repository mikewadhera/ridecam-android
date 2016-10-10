package com.ridecam.av;

import android.graphics.SurfaceTexture;
import android.view.SurfaceHolder;

import java.io.IOException;

public interface CameraEngine<E> {

    public interface ErrorCallback {
        void onError(int errorType, CameraEngine cameraEngine);
    }

    public interface Parameters<P> {

        public static final String FOCUS_MODE_CONTINUOUS_VIDEO = "continuous-video";

        public void setPreviewSize(int width, int height);
        public void setPreviewFpsRange(int minFps, int maxFps);
        public void setFocusMode(String value);
        //public void setRecordingHint(boolean recordingHint);
        //public void setPictureSize(int width, int height);
        public P getUnderlyingParameters();
    }

    public interface CameraInfo {
        public int getOrientation();
    }

    public void setErrorCallback(ErrorCallback errorCallback);
    public void setDisplayOrientation(int orientation);
    public CameraEngine.Parameters getParameters();
    public void setParameters(CameraEngine.Parameters params);
    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException;
    public void setPreviewDisplay(SurfaceHolder surfaceHolder) throws IOException;
    public void startPreview();
    public void stopPreview();
    public void release();
    public E getUnderlyingCamera();
    public void unlock();

    // Added
    public CameraInfo getCameraInfo();

}