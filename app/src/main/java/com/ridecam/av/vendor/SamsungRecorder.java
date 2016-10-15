package com.ridecam.av.vendor;

import com.ridecam.av.CameraEngine;
import com.ridecam.av.RecorderEngine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SamsungRecorder implements RecorderEngine {

    private Object mRecorder; // com.sec.android.secmediarecorder.SecMediaRecorder

    public SamsungRecorder(Object recorder) {
        mRecorder = recorder;
    }

    public SamsungRecorder() {
        try {
            mRecorder = Class.forName("com.sec.android.secmediarecorder.SecMediaRecorder").newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setOnErrorListener(final OnErrorListener onErrorListener) {
        // TODO
    }

    public void setOrientationHint(int orientationHint) {
        try {
            Method setOrientationHintMethod = klass().getDeclaredMethod("setOrientationHint", Integer.TYPE);
            setOrientationHintMethod.invoke(mRecorder, orientationHint);
        } catch (Exception e) {
            e.printStackTrace();
        };
    }

    public void setCamera(CameraEngine camera) {
        // The samsung camera app doesn't actually call setCamera()
        // when initializing recorder under dual cameras.
        // Instead it uses a non-standard API called registerRecordingSurface()
        // which must be called *after* recording begins
    }

    public void registerRecordingSurface(CameraEngine camera) {
        try {
            Method setRecordingSurfaceMethod = klass().getDeclaredMethod("registerRecordingSurface", SamsungCamera.klass());
            setRecordingSurfaceMethod.invoke(mRecorder, camera.getUnderlyingCamera());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void unregisterRecordingSurface(CameraEngine camera) {
        try {
            Method unsetRecordingSurfaceMethod = klass().getDeclaredMethod("unregisterRecordingSurface", SamsungCamera.klass());
            unsetRecordingSurfaceMethod.invoke(mRecorder, camera.getUnderlyingCamera());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setVideoSource(int videoSource) {
        try {
            Method setVideoSourceMethod = klass().getDeclaredMethod("setVideoSource", Integer.TYPE);
            if (videoSource == VideoSource.CAMERA) {
                // TODO: We should see if we can map this value to a framework constant
                setVideoSourceMethod.invoke(mRecorder, 2);
            } else {
                setVideoSourceMethod.invoke(mRecorder, videoSource);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setAudioSource(int videoSource) {
        // TODO
    }

    public void setOutputFormat(int outputFormat) {
        try {
            Method setOutputFormatMethod = klass().getDeclaredMethod("setOutputFormat", Integer.TYPE);
            if (outputFormat == OutputFormat.MPEG_4) {
                setOutputFormatMethod.invoke(mRecorder, Utils.getFieldValue("com.sec.android.secmediarecorder.SecMediaRecorder$OutputFormat.MPEG_4"));
            } else {
                setOutputFormatMethod.invoke(mRecorder, outputFormat);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setVideoSize(int width, int height) {
        try {
            Method setVideoSizeMethod = klass().getDeclaredMethod("setVideoSize", Integer.TYPE, Integer.TYPE);
            setVideoSizeMethod.invoke(mRecorder, width, height);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setVideoEncoder(int videoEncoder) {
        try {
            Method setVideoEncoderMethod = klass().getDeclaredMethod("setVideoEncoder", Integer.TYPE);
            if (videoEncoder == VideoEncoder.H264) {
                setVideoEncoderMethod.invoke(mRecorder, Utils.getFieldValue("com.sec.android.secmediarecorder.SecMediaRecorder$VideoEncoder.H264"));
            } else {
                setVideoEncoderMethod.invoke(mRecorder, videoEncoder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setVideoFrameRate(int frameRate) {
        try {
            Method setVideoFrameRateMethod = klass().getDeclaredMethod("setVideoFrameRate", Integer.TYPE);
            setVideoFrameRateMethod.invoke(mRecorder, frameRate);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setVideoEncodingBitRate(int bitRate) {
        try {
            Method setVideoEncodingBitRateMethod = klass().getDeclaredMethod("setVideoEncodingBitRate", Integer.TYPE);
            setVideoEncodingBitRateMethod.invoke(mRecorder, bitRate);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setOutputFile(String path) {
        try {
            Method setOutputFileMethod = klass().getDeclaredMethod("setOutputFile", String.class);
            setOutputFileMethod.invoke(mRecorder, path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            Method startMethod = klass().getDeclaredMethod("start");
            startMethod.invoke(mRecorder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            Method stopMethod = klass().getDeclaredMethod("stop");
            stopMethod.invoke(mRecorder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reset() {
        try {
            Method resetMethod = klass().getDeclaredMethod("reset");
            resetMethod.invoke(mRecorder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void release() {
        try {
            Method releaseMethod = klass().getDeclaredMethod("release");
            releaseMethod.invoke(mRecorder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void prepare() {
        try {
            Method prepareMethod = klass().getDeclaredMethod("prepare");
            prepareMethod.invoke(mRecorder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setMaxDuration(int maxDurationMs) {
        try {
            Method setMaxDurationMethod = klass().getDeclaredMethod("setMaxDuration", Integer.TYPE);
            setMaxDurationMethod.invoke(mRecorder, maxDurationMs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setMaxFileSize(long maxFileSizeBytes) {
        try {
            Method setMaxFileSizeMethod = klass().getDeclaredMethod("setMaxFileSize", Long.TYPE);
            setMaxFileSizeMethod.invoke(mRecorder, maxFileSizeBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Class<?> klass() throws ClassNotFoundException {
        return Class.forName("com.sec.android.secmediarecorder.SecMediaRecorder");
    }

    public static class Utils {

        public static Object getFieldValue(String path) throws Exception {
            int lastDot = path.lastIndexOf(".");
            String className = path.substring(0, lastDot);
            String fieldName = path.substring(lastDot + 1);
            Class myClass = Class.forName(className);
            Field myField = myClass.getDeclaredField(fieldName);
            return myField.get(null);
        }

    }
}
