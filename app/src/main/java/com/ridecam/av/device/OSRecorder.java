package com.ridecam.av.device;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.provider.MediaStore;

import java.io.IOException;

public class OSRecorder implements RecorderDevice {

    private MediaRecorder mRecorder;

    public OSRecorder() {
        this(new MediaRecorder());
    }

    public OSRecorder(MediaRecorder mediaRecorder) {
        mRecorder = mediaRecorder;
    }

    public void setOnInfoListener(final OnInfoListener onInfoListener) {
        mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
                // Only call back for duration and size reached callbacks
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    onInfoListener.onInfo(OSRecorder.this, what, extra);
                }
            }
        });
    }

    public void setOnErrorListener(final OnErrorListener onErrorListener) {
        mRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int errorType, int errorCode) {
                onErrorListener.onError(OSRecorder.this, errorType, errorCode);
            }
        });
    }

    public void setCamera(CameraDevice cameraDevice) {
        mRecorder.setCamera((Camera) cameraDevice.getUnderlyingCamera());
    }

    public void registerRecordingSurface(CameraDevice camera) {
        // no-op
    }

    public void unregisterRecordingSurface(CameraDevice camera) {
        // no-op
    }

    public void setOrientationHint(int orientationHint) {
        mRecorder.setOrientationHint(orientationHint);
    }

    public void setVideoSource(int videoSource) {
        if (videoSource == VideoSource.CAMERA) {
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        } else {
            mRecorder.setVideoSource(videoSource);
        }
    }

    public void setAudioSource(int audioSource) {
        mRecorder.setAudioSource(audioSource);
    }

    public void setOutputFormat(int outputFormat) {
        if (outputFormat == OutputFormat.MPEG_4) {
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        } else {
            mRecorder.setOutputFormat(outputFormat);
        }
    }

    public void setVideoSize(int width, int height) {
        mRecorder.setVideoSize(width, height);
    }

    public void setVideoEncoder(int videoEncoder) {
        if (videoEncoder == VideoEncoder.H264) {
            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        } else {
            mRecorder.setVideoEncoder(videoEncoder);
        }
    }

    public void setVideoFrameRate(int frameRate) {
        mRecorder.setVideoFrameRate(frameRate);
    }

    public void setVideoEncodingBitRate(int bitRate) {
        mRecorder.setVideoEncodingBitRate(bitRate);
    }

    public void setOutputFile(String path) {
        mRecorder.setOutputFile(path);
    }

    public void prepare() throws IllegalStateException, IOException {
        mRecorder.prepare();
    }

    public void start() {
        mRecorder.start();
    }

    public void stop() {
        mRecorder.stop();
    }

    public void reset() {
        mRecorder.reset();
    }

    public void release() {
        mRecorder.release();
    }

    public void setMaxDuration(int maxDurationMs) {
        mRecorder.setMaxDuration(maxDurationMs);
    }

    public void setMaxFileSize(long maxFileSizeBytes) {
        mRecorder.setMaxFileSize(maxFileSizeBytes);
    }

}
