package com.ridecam.av.device.vendor;

import android.graphics.SurfaceTexture;
import android.util.Pair;
import android.view.SurfaceHolder;

import com.ridecam.av.device.CameraDevice;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SamsungDualCamera implements CameraDevice<Object> {

    private static final int CAMERA_ID = 10;

    private Object mCamera; // com.sec.android.seccamera.SecCamera

    public static boolean isAvailable() {
        try {
            Class.forName("com.sec.android.seccamera.SecCamera");
            // TODO also check /system/cameradata/camera-feature-v5.xml for CAMERA_DUALCAMERA (see FeatureLoader)
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static CameraDevice open() {
        try {
            Class<?> secCamera = Class.forName("com.sec.android.seccamera.SecCamera");
            Method open = secCamera.getDeclaredMethod("open", Integer.TYPE);
            Object camera = open.invoke(null, CAMERA_ID);
            SamsungDualCamera samsungCamera = new SamsungDualCamera(camera);
            samsungCamera.enableDualEffect();
            return samsungCamera;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public SamsungDualCamera(Object camera) {
        mCamera = camera;
    }

    public static class Parameters implements CameraDevice.Parameters<Object> {

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
                if (value == CameraDevice.Parameters.FOCUS_MODE_INFINITY) {
                    setFocusModeMethod.invoke(mParams, Utils.getFieldValue("com.sec.android.seccamera.SecCamera$Parameters.FOCUS_MODE_INFINITY"));
                } else {
                    setFocusModeMethod.invoke(mParams, value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setVideoStabilization(boolean toggle) {
            try {
                Method setVideoStabilizationMethod = klass().getDeclaredMethod("setVideoStabilization", Boolean.TYPE);
                setVideoStabilizationMethod.invoke(mParams, toggle);
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

    public static class CameraInfo implements CameraDevice.CameraInfo {

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

    public void enableDualEffect() {
        try {
            Method setMode = klass().getDeclaredMethod("setShootingMode", Integer.TYPE);
            setMode.invoke(mCamera, 47);
            Method setDualEffect = klass().getDeclaredMethod("setSecImagingEffect", Integer.TYPE);
            setDualEffect.invoke(mCamera, 200);
            Method effectVisibleForRecordingMethod = klass().getDeclaredMethod("setSecImagingEffectVisibleForRecording", Boolean.TYPE);
            effectVisibleForRecordingMethod.invoke(mCamera, true);
            Pair<Integer, Integer> coordinates = Utils.coordinateSyncforDual(890, 1460, 480, 854);
            Method effectCoordinatesMethod = klass().getDeclaredMethod("setDualEffectCoordinate", Integer.TYPE, Integer.TYPE);
            effectCoordinatesMethod.invoke(mCamera, coordinates.first, coordinates.second);
        } catch (Exception e) {
            e.printStackTrace();
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

    public void setParameters(CameraDevice.Parameters parameters) {
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

    public void setPreviewDisplay(SurfaceHolder surfaceHolder) throws IOException {
        try {
            Method setPreviewDisplayMethod = klass().getDeclaredMethod("setPreviewDisplay", SurfaceHolder.class);
            setPreviewDisplayMethod.invoke(mCamera, surfaceHolder);
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

    public void lock() {
        try {
            Method lockMethod = klass().getDeclaredMethod("lock");
            lockMethod.invoke(mCamera);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SamsungDualCamera.CameraInfo getCameraInfo() {
        try {
            Object cameraInfo = CameraInfo.klass().newInstance();
            Method getCameraInfoMethod = klass().getDeclaredMethod("getCameraInfo", Integer.TYPE, CameraInfo.klass());
            getCameraInfoMethod.invoke(null, 0, cameraInfo);
            return new SamsungDualCamera.CameraInfo(cameraInfo);
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

        public static Pair<Integer, Integer> coordinateSyncforDual(float arg8, float arg9, float arg10, float arg11) {
            int v0 = (((int)arg9)) << 15 | (((int)arg8)) & 32767;
            int v1 = (((int)(arg9 + arg11))) << 15 | (((int)(arg8 + arg10))) & 32767;
            v0 |= -2147483648;
            return new Pair<Integer, Integer>(new Integer(v0), new Integer(v1));
        }

    }

}
