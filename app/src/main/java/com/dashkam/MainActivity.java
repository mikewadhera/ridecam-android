package com.dashkam;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static SurfaceTexture sCachedSurfaceTexture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // Starting up or resuming after destroyed (power button pushed or killed by OS)

        // We don't start the service here as we may not have camera permissions yet
        // Not to mention there is nothing interesting the service
        // would be interested about at this point

        setContentView(R.layout.fragment_camera);

        View view = findViewById(R.id.camera_preview);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (hasPermissions()) {
                    Intent intent = new Intent(MainActivity.this, CameraService.class);
                    intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_ACTIVITY_RECORD);
                    startService(intent);
                    return true;
                } else {
                    return false;
                }
            }
        });

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        // Starting up or resuming after onPause()

        if (!hasPermissions()) {

            pauseToRequestPermissionsDialog();

        } else {

            TextureView textureView = (TextureView) findViewById(R.id.camera_preview);

            final Intent intent = new Intent(this, CameraService.class);
            intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_ACTIVITY_ONRESUME);

            if (sCachedSurfaceTexture != null) {

                if (textureView.getSurfaceTexture() != sCachedSurfaceTexture) {
                    textureView.setSurfaceTexture(sCachedSurfaceTexture);
                }
                startService(intent);

            } else {

                textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                        sCachedSurfaceTexture = surfaceTexture;
                        startService(intent);
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {}

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                        // "false" so that TextureView will not release SurfaceTexture we cache
                        return false;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
                });

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

        // Shutting down activity, either by system or from pressing power button

        // The service is built for main activity to come and go as it pleases
        // so the service doesn't care to know about a destroy state
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

        public static final String RESULT_RECEIVER = "resultReceiver";

        private static final String START_SERVICE_COMMAND = "startServiceCommands";
        private static final int COMMAND_NONE = -1;
        private static final int COMMAND_ACTIVITY_ONRESUME = 0;
        private static final int COMMAND_ACTIVITY_ONSTOP = 1;
        private static final int COMMAND_ACTIVITY_RECORD = 2;

        public static boolean sRunningLock;
        private boolean mAcquiringCameraLock;
        private boolean mRecordingLock;
        private Camera mCamera;
        private MediaRecorder mMediaRecorder;

        public static boolean hasStarted() {
            return sRunningLock;
        }

        public CameraService() {
        }

        @Override
        public void onCreate() {
            Log.d(TAG, "onCreate");

            // Called after we've acquired all permissions required

            // TODO: Add startForeground()

            sRunningLock = true;
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "CameraService onDestroy");

            // By design this should not be called much / only when app is killed

            try {
                if (mRecordingLock) {
                    // This is VERY bad
                    // We should *never* have the service
                    // shutdown while a recording is in progress
                    // TODO: Release recorder, notify user
                }

                releaseCamera();
            } finally {
                sRunningLock = false;
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        // Activity state transitions

        public void handleOnResume() {
            if (!mRecordingLock) {
                acquireCamera();
            }

        }

        public void handleOnStop() {
            if (!mRecordingLock) {
                releaseCamera();
            }

        }

        public void acquireCamera() {
            if (mCamera == null && !mAcquiringCameraLock) {

                // Hold a lock to avoid double acquiring

                mAcquiringCameraLock = true;

                try {
                    if (sCachedSurfaceTexture != null) {

                        int cameraId = 0;
                        mCamera = Camera.open(cameraId);
                        setCameraDisplayOrientation(cameraId, mCamera);
                        Camera.Parameters params = mCamera.getParameters();
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                        mCamera.setParameters(params);
                        mCamera.setPreviewTexture(sCachedSurfaceTexture);
                        mCamera.startPreview();

                    } else {

                        // Bad place to be
                        // In practice we should never acquire the camera before we
                        // cache a surface texture as we start service
                        // from onSurfaceTextureAvailable

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
                if (mRecordingLock) {
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
                throw new UnsupportedOperationException("Cannot start service with null intent");
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
                    break;

                default:
                    throw new UnsupportedOperationException("Cannot start service with illegal commands");
            }

            return START_STICKY;
        }

        public void startRecording() {
            if (mRecordingLock) {
                flash("Recording Failed: Already in progress");
                return;
            }

            mRecordingLock = true;

            try {
                // TODO: update startForeground()

                mMediaRecorder = new MediaRecorder();

                Log.d(TAG, "R: Obtaining Camera");
                Camera camera = mCamera;

                if (camera == null) {
                    flash("Recording Failed: No Camera");
                    mRecordingLock = false;
                    return;
                }

                Log.d(TAG, "R: Unlocking Camera");
                camera.unlock();

                Log.d(TAG, "R: Setting Camera");
                mMediaRecorder.setCamera(camera);

                Log.d(TAG, "R: Setting sources");
                //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                Log.d(TAG, "R: Setting profile");
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setVideoSize(1920, 1080);
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);

                Log.d(TAG, "R: Setting output file");
                mMediaRecorder.setOutputFile(getOutputMediaFilePath(MEDIA_TYPE_VIDEO));

                Log.d(TAG, "R: Prepare");
                try {
                    mMediaRecorder.prepare();
                } catch (IllegalStateException e) {
                    Log.d(TAG, "IllegalStateException when preparing MediaRecorder: " + e.getMessage());
                    flash("Recording Failed: Internal Error");
                    mRecordingLock = false;
                    return;
                } catch (IOException e) {
                    Log.d(TAG, "IOException when preparing MediaRecorder: " + e.getMessage());
                    flash("Recording Failed: Internal Error");
                    mRecordingLock = false;
                    return;
                }

                Log.d(TAG, "R: Start");
                mMediaRecorder.start();

                flash("Recording Started");

                Log.d(TAG, "R: Started");
            } catch (Exception e) {
                e.printStackTrace();
                mRecordingLock = false;
            }
        }

        public void stopRecording() {
            if (!mRecordingLock) {
                flash("Stop Failed: Not Recording");
                return;
            }

            try {
                Log.d(TAG, "R: Stopping");
                mMediaRecorder.stop();

                Log.d(TAG, "R: Releasing recorder");
                mMediaRecorder.release();

                // TODO: update stopForeground()
            } finally {
                mRecordingLock = false;
            }

            flash("Recording Stopped");

            Log.d(TAG, "R: Stopped");
        }

        private void flash(String text) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }

        public WindowManager getWindowManager() {
            return (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }

        public void setCameraDisplayOrientation(int cameraId, android.hardware.Camera camera) {
            android.hardware.Camera.CameraInfo info =
                    new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
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