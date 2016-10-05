package com.ridecam.av;

import android.media.MediaRecorder;

import java.io.IOException;

public interface RecorderEngine {

    public interface OnErrorListener {
        public void onError(RecorderEngine mediaRecorder, int errorType, int errorCode);
    }

    public final class VideoSource {
        public static final int CAMERA = 1;
    }

    public final class OutputFormat {
        public static final int MPEG_4 = 2;
    }

    public final class VideoEncoder {
        public static final int H264 = 2;
    }

    public void setOnErrorListener(OnErrorListener errorListener);
    public void setCamera(CameraEngine cameraEngine);
    public void setOrientationHint(int orientationHint);
    public void setVideoSource(int videoSource);
    public void setAudioSource(int audioSource);
    public void setOutputFormat(int outputFormat);
    public void setVideoSize(int width, int height);
    public void setVideoEncoder(int videoEncoder);
    public void setOutputFile(String path);
    public void prepare() throws IllegalStateException, IOException;
    public void start();
    public void stop();
    public void release();

}
