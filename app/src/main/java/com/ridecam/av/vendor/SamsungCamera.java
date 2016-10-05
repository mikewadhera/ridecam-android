package com.ridecam.av.vendor;

import com.ridecam.av.CameraEngine;

import android.graphics.SurfaceTexture;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SamsungCamera implements CameraEngine<Object> {

    private static final int CAMERA_ID = 0;

    private Object mCamera; // com.sec.android.seccamera.SecCamera

    public static boolean isAvailable() {
        try {
            Class.forName("com.sec.android.seccamera.SecCamera");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static CameraEngine open() {
        try {
            Class<?> secCamera = Class.forName("com.sec.android.seccamera.SecCamera");
            Method open = secCamera.getDeclaredMethod("open", Integer.TYPE);
            Object camera = open.invoke(null, CAMERA_ID);
            return new SamsungCamera(camera);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public SamsungCamera(Object camera) {
        mCamera = camera;
    }

    public static class Parameters implements CameraEngine.Parameters<Object> {

        Object mParams; // com.sec.android.seccamera.SecCamera.Parameters

        public Parameters(Object params) {
            mParams = params;
        }

        public void setPreviewSize(int width, int height) {
            try {
                Method setPreviewSizeMethod = klass().getDeclaredMethod("setPreviewSize", Integer.TYPE, Integer.TYPE);
                setPreviewSizeMethod.invoke(mParams, width, height);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void setPreviewFpsRange(int min, int max) {
            try {
                Method setPreviewFpsRangeMethod = klass().getDeclaredMethod("setPreviewFpsRange", Integer.TYPE, Integer.TYPE);
                setPreviewFpsRangeMethod.invoke(mParams, min, max);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void setFocusMode(String value) {
            try {
                Method setFocusModeMethod = klass().getDeclaredMethod("setFocusMode", String.class);
                if (value == CameraEngine.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) {
                    setFocusModeMethod.invoke(mParams, Utils.getFieldValue("com.sec.android.seccamera.SecCamera$Parameters.FOCUS_MODE_CONTINUOUS_VIDEO"));
                } else {
                    setFocusModeMethod.invoke(mParams, value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public Object getUnderlyingParameters() {
            return mParams;
        }

        public static Class<?> klass() throws ClassNotFoundException {
            return Class.forName("com.sec.android.seccamera.SecCamera$Parameters");
        }

    }

    public static class CameraInfo implements CameraEngine.CameraInfo {

        Object mCameraInfo; // com.sec.android.seccamera.SecCamera.CameraInfo

        public CameraInfo(Object cameraInfo) {
            mCameraInfo = cameraInfo;
        }

        public int getOrientation() {
            try {
                Field orientationField = klass().getField("orientation");
                Object fieldValue = orientationField.get(mCameraInfo);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        public static Class<?> klass() throws ClassNotFoundException {
            return Class.forName("com.sec.android.seccamera.SecCamera$CameraInfo");
        }

    }

    public void setDisplayOrientation(int displayOrientation) {
        try {
            Method setDisplayOrientationMethod = klass().getDeclaredMethod("setDisplayOrientation", Integer.TYPE);
            setDisplayOrientationMethod.invoke(mCamera, displayOrientation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Parameters getParameters() {
        try {
            Method getParametersMethod = klass().getDeclaredMethod("getParameters");
            return new Parameters(getParametersMethod.invoke(mCamera));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void startPreview() {
        try {
            Method startPreviewMethod = klass().getDeclaredMethod("startPreview");
            startPreviewMethod.invoke(mCamera);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopPreview() {
        try {
            Method stopPreviewMethod = klass().getDeclaredMethod("stopPreview");
            stopPreviewMethod.invoke(mCamera);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setParameters(CameraEngine.Parameters parameters) {
        try {
            Method setParametersMethod = klass().getDeclaredMethod("setParameters", Parameters.klass());
            setParametersMethod.invoke(mCamera, parameters.getUnderlyingParameters());
        } catch (Exception e) {
            e.printStackTrace();
        };
    }

    public void release() {
        try {
            Method releaseMethod = klass().getDeclaredMethod("release");
            releaseMethod.invoke(mCamera);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException {
        try {
            Method setPreviewTextureMethod = klass().getDeclaredMethod("setPreviewTexture", SurfaceTexture.class);
            setPreviewTextureMethod.invoke(mCamera, surfaceTexture);
        } catch (Exception e) {
            e.printStackTrace();
        };
    }

    public void setErrorCallback(final ErrorCallback errorCallback) {
//        mCamera.setErrorCallback(new Camera.ErrorCallback() {
//            @Override
//            public void onError(int i, Camera camera) {
//                errorCallback.onError(i, new OSCamera(camera));
//            }
//        });
    }

    public Object getUnderlyingCamera() {
        return mCamera;
    }

    public void unlock() {
        try {
            Method unlockMethod = klass().getDeclaredMethod("unlock");
            unlockMethod.invoke(mCamera);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SamsungCamera.CameraInfo getCameraInfo() {
        try {
            Object cameraInfo = CameraInfo.klass().newInstance();
            Method getCameraInfoMethod = klass().getDeclaredMethod("getCameraInfo", Integer.TYPE, CameraInfo.klass());
            getCameraInfoMethod.invoke(null, CAMERA_ID, cameraInfo);
            return new SamsungCamera.CameraInfo(cameraInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Class<?> klass() throws ClassNotFoundException {
        return Class.forName("com.sec.android.seccamera.SecCamera");
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
