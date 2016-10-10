package com.ridecam;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.ridecam.av.CameraEngine;
import com.ridecam.av.OSCamera;
import com.ridecam.av.OSRecorder;
import com.ridecam.av.RecorderEngine;
import com.ridecam.av.vendor.SamsungCamera;
import com.ridecam.av.vendor.SamsungRecorder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static SurfaceTexture sCachedSurfaceTexture;
    public static TextureView.SurfaceTextureListener sTextureViewListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // Starting up or resuming after destroyed (power button pushed or killed by OS)

        // We don't start the service here as we may not have camera permissions yet
        // Not to mention there is nothing interesting the service
        // would be interested about at this point

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        loadLayout();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        // Starting up or resuming after onPause()

        if (!hasPermissions()) {

            pauseToRequestPermissionsDialog();

        } else {

            if (sCachedSurfaceTexture == null) {

                // Important: we reload layout to make sure onSurfaceTextureAvailable()
                // is called back when resuming from permissions dialog
                loadLayout();

                TextureView textureView = (TextureView) findViewById(R.id.camera_preview);

                sTextureViewListener = new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                        sCachedSurfaceTexture = surfaceTexture;
                        transformImage(i, i1);
                        Intent intent = new Intent(MainActivity.this, CameraService.class);
                        intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_ACTIVITY_ONRESUME);
                        startService(intent);
                        render();
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                        // return "false" so that TextureView does not release cached SurfaceTexture
                        return (surfaceTexture != MainActivity.sCachedSurfaceTexture);
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {}

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
                };
                textureView.setSurfaceTextureListener(sTextureViewListener);

            } else {

                TextureView textureView = (TextureView) findViewById(R.id.camera_preview);

                textureView.setSurfaceTextureListener(sTextureViewListener);

                if (textureView.getSurfaceTexture() != sCachedSurfaceTexture) {
                    try {
                        textureView.setSurfaceTexture(sCachedSurfaceTexture);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // TODO add logging
                    }

                }
                Intent intent = new Intent(this, CameraService.class);
                intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_ACTIVITY_ONRESUME);
                startService(intent);
                render();

            }

        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();

        // Stopping from pushing home button, starting another activity or dialog

        Intent intent = new Intent(this, CameraService.class);
        intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_ACTIVITY_ONSTOP);
        startService(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    public void loadLayout() {
        setContentView(R.layout.fragment_camera);

        View view = findViewById(R.id.record_frame);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                return toggleRecording();
            }
        });
    }

    public boolean toggleRecording() {
        if (hasPermissions()) {
            Intent intent = new Intent(MainActivity.this, CameraService.class);
            intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_ACTIVITY_RECORD);
            intent.putExtra(CameraService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    render();
                }
            });
            startService(intent);
            return true;
        } else {
            return false;
        }
    }

    public void render() {
        View previewView = findViewById(R.id.record_frame);
        Drawable previewViewBackground;

        if (CameraService.isRecording()) {
            previewViewBackground = getResources().getDrawable(R.drawable.record_frame_on);
        } else {
            previewViewBackground = getResources().getDrawable(R.drawable.record_frame);
        }

        previewView.setBackgroundDrawable(previewViewBackground);


    }

    private void transformImage(int width, int height) {
        Matrix matrix = new Matrix();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, width, height);
        RectF previewRectF = new RectF(0, 0, height, width);
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();
        //if(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(centerX - previewRectF.centerX(),
                    centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)width / width,
                    (float)height / height);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90, centerX, centerY);
        //}
        TextureView textureView = (TextureView) findViewById(R.id.camera_preview);
        textureView.setTransform(matrix);
    }

    public boolean hasPermissions() {
        boolean hasCam =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasGps =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasStorage =
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        return hasCam && hasGps && hasStorage;
    }

    public void pauseToRequestPermissionsDialog() {
        // Show the permissions dialog -- we'll be sent to onPause()

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Success. We don't have to do anything as we should be transitioned
                    // to onResume() after the user accepts the permissions dialog


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;
            }
        }
    }


    public static class CameraService extends Service {

        private static final String TAG = "CameraService";

        private static final int NOTIFICATION_ID = 1;

        private static final String START_SERVICE_COMMAND = "startServiceCommands";
        private static final int COMMAND_NONE = -1;
        private static final int COMMAND_ACTIVITY_ONRESUME = 0;
        private static final int COMMAND_ACTIVITY_ONSTOP = 1;
        private static final int COMMAND_ACTIVITY_RECORD = 2;

        public static final String RESULT_RECEIVER = "resultReceiver";

        private static final boolean FORCE_NATIVE_CAMERA = false;

        private boolean mAcquiringCameraLock;
        private static boolean sRecordingLock;
        private CameraEngine mCamera;
        private RecorderEngine mRecorder;

        public static boolean isRecording() { return sRecordingLock; }

        public CameraService() {
        }

        @Override
        public void onCreate() {
            Log.d(TAG, "onCreate");

            // Called after we've acquired all permissions required
            showForegroundNotification("Not Recording");
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "CameraService onDestroy");

            // By design this should not be called much / only when app is killed

            try {
                if (sRecordingLock) {
                    // This is VERY bad
                    // We should *never* have the service
                    // shutdown while a recording is in progress
                    // TODO: Release recorder, notify user
                }

                releaseCamera();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void onCameraError(int errorType) {
            Log.e(TAG, "!!!!!!! onCameraError errorType: " + errorType);
            // TODO add logging
        }

        public void onRecorderError(int errorType, int errorCode) {
            Log.e(TAG, "!!!!!!! onRecorderError errorType: " + errorType + " errorCode: " + errorCode);
            // TODO add logging
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        // Activity state transitions

        public void handleOnResume() {
            if (!sRecordingLock) {
                acquireCamera();
            }
        }

        public void handleOnStop() {
            if (!sRecordingLock) {
                releaseCamera();
            }
        }

        public void acquireCamera() {
            if (mCamera == null && !mAcquiringCameraLock) {

                // Hold a lock to avoid double acquiring

                mAcquiringCameraLock = true;

                try {
                    if (sCachedSurfaceTexture != null) {

                        if (shouldUseSamsung()) {
                            mCamera = SamsungCamera.open();
                        } else {
                            mCamera = OSCamera.open();
                        }

                        mCamera.setErrorCallback(new CameraEngine.ErrorCallback() {
                            @Override
                            public void onError(int errorType, CameraEngine camera) {
                                onCameraError(errorType);
                            }
                        });
                        setCameraDisplayOrientation(mCamera);
                        CameraEngine.Parameters params = mCamera.getParameters();
                        params.setPreviewSize(1280, 720);
                        params.setPreviewFpsRange(24000, 24000);
                        params.setFocusMode(CameraEngine.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                        mCamera.setParameters(params);

                        mCamera.setPreviewTexture(sCachedSurfaceTexture);

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

        public void toggleRecording() {
            if (mCamera != null) {
                if (sRecordingLock) {
                    stopRecording();
                } else {
                    startRecording();
                }
            }
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.d(TAG, "CameraService onStartCommand");

            if (intent == null) {
                Log.e(TAG, "Cannot start service with null intent");
                return START_STICKY;
            }

            switch (intent.getIntExtra(START_SERVICE_COMMAND, COMMAND_NONE)) {
                case COMMAND_ACTIVITY_ONRESUME:
                    handleOnResume();
                    break;

                case COMMAND_ACTIVITY_ONSTOP:
                    handleOnStop();
                    break;

                case COMMAND_ACTIVITY_RECORD:
                    toggleRecording();
                    ResultReceiver receiver = intent.getParcelableExtra(RESULT_RECEIVER);
                    receiver.send(0, null);
                    break;

                default:
                    Log.e(TAG, "Cannot start service with illegal commands");
            }

            return START_STICKY;
        }

        public void startRecording() {
            if (sRecordingLock) {
                flash("Recording Failed: Already in progress");
                return;
            }

            sRecordingLock = true;

            try {
                showForegroundNotification("Recording");

                if (shouldUseSamsung()) {
                    mRecorder = new SamsungRecorder();
                } else {
                    mRecorder = new OSRecorder();
                }

                mRecorder.setOnErrorListener(new RecorderEngine.OnErrorListener() {
                    @Override
                    public void onError(RecorderEngine recorder, int errorType, int errorCode) {
                        onRecorderError(errorType, errorCode);
                    }
                });

                Log.d(TAG, "R: Obtaining Camera");
                CameraEngine camera = mCamera;

                if (camera == null) {
                    flash("Recording Failed: No Camera");
                    sRecordingLock = false;
                    return;
                }

                Log.d(TAG, "R: Unlocking Camera");
                camera.unlock();

                Log.d(TAG, "R: Setting Camera");
                mRecorder.setCamera(camera);

                Log.d(TAG, "R: Setting Camera orientation hint");
                mRecorder.setOrientationHint(90);

                Log.d(TAG, "R: Setting sources");
                //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mRecorder.setVideoSource(OSRecorder.VideoSource.CAMERA);

                Log.d(TAG, "R: Setting profile");
                mRecorder.setOutputFormat(OSRecorder.OutputFormat.MPEG_4);
                mRecorder.setVideoSize(1280, 720);
                mRecorder.setVideoEncoder(OSRecorder.VideoEncoder.H264);

                Log.d(TAG, "R: Setting output file");
                mRecorder.setOutputFile(getOutputMediaFilePath(MEDIA_TYPE_VIDEO));

                Log.d(TAG, "R: Prepare");
                try {
                    mRecorder.prepare();
                } catch (IllegalStateException e) {
                    Log.d(TAG, "IllegalStateException when preparing MediaRecorder: " + e.getMessage());
                    flash("Recording Failed: Internal Error");
                    sRecordingLock = false;
                    return;
                } catch (IOException e) {
                    Log.d(TAG, "IOException when preparing MediaRecorder: " + e.getMessage());
                    flash("Recording Failed: Internal Error");
                    sRecordingLock = false;
                    return;
                }

                Log.d(TAG, "R: Start");
                mRecorder.start();

                flash("Recording Started");

                Log.d(TAG, "R: Registering Recording Surface");
                mRecorder.registerRecordingSurface(mCamera);

                Log.d(TAG, "R: Started");
            } catch (Exception e) {
                e.printStackTrace();
                sRecordingLock = false;
            }
        }

        public void stopRecording() {
            if (!sRecordingLock) {
                flash("Stop Failed: Not Recording");
                return;
            }

            try {
                Log.d(TAG, "R: Stopping");
                mRecorder.stop();

                Log.d(TAG, "R: Releasing recorder");
                mRecorder.release();

                showForegroundNotification("Not Recording");
            } finally {
                sRecordingLock = false;
            }

            flash("Recording Stopped");

            Log.d(TAG, "R: Stopped");
        }

        private boolean shouldUseSamsung() {
            return (SamsungCamera.isAvailable() && !FORCE_NATIVE_CAMERA);
        }

        private void showForegroundNotification(String contentText) {
            // Create intent that will bring our app to the front, as if it was tapped in the app
            // launcher
            Intent showTaskIntent = new Intent(getApplicationContext(), MainActivity.class);
            showTaskIntent.setAction(Intent.ACTION_MAIN);
            showTaskIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            showTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent contentIntent = PendingIntent.getActivity(
                    getApplicationContext(),
                    0,
                    showTaskIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new Notification.Builder(getApplicationContext())
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(contentText)
                    .setWhen(System.currentTimeMillis())
                    .setContentIntent(contentIntent)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
        }

        private void flash(String text) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }

        public WindowManager getWindowManager() {
            return (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }

        public void setCameraDisplayOrientation(CameraEngine camera) {
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }
            int result;
            CameraEngine.CameraInfo info = camera.getCameraInfo();

            result = (info.getOrientation() - degrees + 360) % 360;
            Log.d(TAG, "Adjusting preview orientation degrees: " + String.valueOf(result));
            camera.setDisplayOrientation(result);
        }

        public static final int MEDIA_TYPE_VIDEO = 2;

        private static String getOutputMediaFilePath(int type){
            return getOutputMediaFile(type).getAbsolutePath();
        }

        private static File getOutputMediaFile(int type){
            // To be safe, you should check that the SDCard is mounted
            // using Environment.getExternalStorageState() before doing this.

            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), TAG);
            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.

            // Create the storage directory if it does not exist
            if (! mediaStorageDir.exists()){
                if (! mediaStorageDir.mkdirs()){
                    Log.d(TAG, "failed to create directory");
                    return null;
                }
            }

            // Create a media file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File mediaFile;
            if (type == MEDIA_TYPE_IMAGE){
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                        "IMG_"+ timeStamp + ".jpg");
            } else if(type == MEDIA_TYPE_VIDEO) {
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                        "VID_"+ timeStamp + ".mp4");
            } else {
                return null;
            }

            return mediaFile;
        }

    }
}