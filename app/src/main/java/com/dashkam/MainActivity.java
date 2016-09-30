package com.dashkam;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.support.v4.graphics.drawable.DrawableCompat;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import android.app.Service;

import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.R.attr.permission;
import static android.R.attr.start;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DashKam";
    private static int CAMERA_ID = 0;

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;

    private static Camera mCamera;

    private SurfaceView mSurfaceView;

    private WeakReference<CameraFragment> mCameraFragment;

    private WeakReference<DashKamFragment> mDashKamFragment;

    public static Camera getCamera() { return mCamera; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        boolean hasCam =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasGps =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasStorage =
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        if (hasCam && hasGps && hasStorage) {

            acquireCamera();

            // Fix for frozen preview when resuming from sleep button
            // See onPause for more
            if (mSurfaceView != null && mSurfaceView.getVisibility() != View.VISIBLE) {
                mSurfaceView.setVisibility(View.VISIBLE);
            }

        } else {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    1);

        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

        if (!isRecording()) {
            stopPreviewReleaseCamera();
        }

        destroySurfaceViewIfPowerButtonPushed();
    }

    public void acquireCamera() {
        if (mCamera == null) {
            try {
                mCamera = Camera.open(CAMERA_ID);
                MainActivity.setCameraDisplayOrientation(this, CAMERA_ID, mCamera);
                Camera.Parameters params = mCamera.getParameters();
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                mCamera.setParameters(params);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void startPreview() {
        if (mCamera != null && mSurfaceView != null) {
            try {
                mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                mCamera.startPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopPreviewReleaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public void toggleRecording() {
        if (mCamera != null && mSurfaceView != null) {
            if (isRecording()) {
                stopRecording();
            } else {
                startRecording();
            }
        }
    }

    public void startRecording() {
        ResultReceiver receiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                if (code == RecordService.RECORD_RESULT_OK) {
                    flash("Recording Started");
                } else {
                    flash("Recording Failed");
                }
                renderCameraFragment();
            }
        };
        RecordService.start(this, receiver, RecordService.COMMAND_START_RECORDING);
    }

    public void stopRecording() {
        ResultReceiver receiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                if (code == RecordService.RECORD_RESULT_OK) {
                    flash("Recording Stopped");
                } else {
                    flash("Recording Error");
                }
                renderCameraFragment();
            }
        };
        RecordService.start(this, receiver, RecordService.COMMAND_STOP_RECORDING);
    }

    public boolean isRecording() {
        return RecordService.mRecordingLock;
    }

    private void flash(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public void renderCameraFragment() {
        CameraFragment cameraFragment = mCameraFragment.get();
        if (cameraFragment != null) {
            cameraFragment.render();
        }
    }

    public void setCameraFragment(CameraFragment cameraFragment) {
        mCameraFragment = new WeakReference<CameraFragment>(cameraFragment);
    }

    public void setDashKamFragment(DashKamFragment dashKamFragment) {
        mDashKamFragment = new WeakReference<DashKamFragment>(dashKamFragment);
    }

    public void setPreviewView(SurfaceView surfaceView) {
        Log.d(TAG, "setPreviewView");
        mSurfaceView = surfaceView;
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                Log.d(TAG, "surfaceChanged");
                startPreview();
            }

            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });
    }

    // HACK: If the user pushes the power/sleep button then onStop() is not called
    // which means the preview's underlying SurfaceView isn't destroyed
    // which means surfaceChanged won't be called in onResume
    // which means startPreview won't be called back (resutling in a frozen preview)
    // So, if the screen is off during onPause, assume we are sleeping
    // and forcefully destory the preview
    public void destroySurfaceViewIfPowerButtonPushed() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean sleepButtonPushed = !pm.isScreenOn();
        if (sleepButtonPushed) {
            Log.d(TAG, "Sleep Button pushed, destroying surface view");
            if (mSurfaceView != null) {
                mSurfaceView.setVisibility(View.GONE);
            }
        }
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


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;
            }
        }

        // Very important!
        acquireCamera();
        startPreview();
    }

    public static class CameraFragment extends Fragment {

        View mRootView;

        public CameraFragment() {
        }

        public static CameraFragment factory() { return new CameraFragment(); }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_camera, container, false);

            SurfaceView previewView = (SurfaceView)mRootView.findViewById(R.id.camera_preview);

            final MainActivity activity = (MainActivity)getActivity();
            activity.setCameraFragment(this);
            activity.setPreviewView(previewView);

            previewView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    activity.toggleRecording();
                    return true;
                }
            });

            render();

            return mRootView;
        }

        public void render() {
            View previewView = mRootView.findViewById(R.id.camera_preview);
            Drawable previewViewBackground;

            final MainActivity activity = (MainActivity)getActivity();

            if (activity.isRecording()) {
                previewViewBackground = getResources().getDrawable(R.drawable.record_frame_on);
            } else {
                previewViewBackground = getResources().getDrawable(R.drawable.record_frame);
            }

            previewView.setBackground(previewViewBackground);
        }

    }

    public static class DashKamFragment extends Fragment {

        View mRootView;

        public DashKamFragment() {
        }

        public static DashKamFragment factory() { return new DashKamFragment(); }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_dashkam, container, false);

            final MainActivity activity = (MainActivity)getActivity();
            activity.setDashKamFragment(this);

            render();

            return mRootView;
        }

        public void render() {

        }

    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return CameraFragment.factory();
                case 1:
                    return DashKamFragment.factory();
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Camera";
                case 1:
                    return "DashKam";
            }
            return null;
        }

    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
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

    public static class RecordService extends Service {

        public static final String RESULT_RECEIVER = "resultReceiver";

        public static final int RECORD_RESULT_OK = 0;
        public static final int RECORD_RESULT_DEVICE_NO_CAMERA = 1;
        public static final int RECORD_RESULT_GET_CAMERA_FAILED = 2;
        public static final int RECORD_RESULT_ALREADY_RECORDING = 3;
        public static final int RECORD_RESULT_NOT_RECORDING = 4;

        private static final String START_SERVICE_COMMAND = "startServiceCommands";
        private static final int COMMAND_NONE = -1;
        private static final int COMMAND_START_RECORDING = 0;
        private static final int COMMAND_STOP_RECORDING = 1;

        public static boolean mRecordingLock;

        private MediaRecorder mMediaRecorder;

        public static void start(Context context, ResultReceiver receiver, int command) {
            Intent intent = new Intent(context, RecordService.class);
            intent.putExtra(START_SERVICE_COMMAND, command);
            intent.putExtra(RESULT_RECEIVER, receiver);
            context.startService(intent);
        }

        public RecordService() {
        }

        @Override
        public void onCreate() {
            Log.d(TAG, "RecordService onCreate");

        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.d(TAG, "RecordService onStartCommand");

            if (intent == null) {
                throw new UnsupportedOperationException("Cannot start service with null intent");
            }

            switch (intent.getIntExtra(START_SERVICE_COMMAND, COMMAND_NONE)) {
                case COMMAND_START_RECORDING:
                    handleStartRecordingCommand(intent);
                    break;
                case COMMAND_STOP_RECORDING:
                    handleStopRecordingCommand(intent);
                    break;
                default:
                    throw new UnsupportedOperationException("Cannot start service with illegal commands");
            }

            return START_STICKY;
        }

        public void handleStartRecordingCommand(Intent intent) {
            final ResultReceiver receiver = intent.getParcelableExtra(RESULT_RECEIVER);

            if (mRecordingLock) {
                receiver.send(RECORD_RESULT_ALREADY_RECORDING, null);
                return;
            }

            mRecordingLock = true;

            mMediaRecorder = new MediaRecorder();

            Log.d(TAG, "R: Obtaining Camera");
            Camera camera = MainActivity.getCamera();

            if (camera == null) {
                receiver.send(RECORD_RESULT_DEVICE_NO_CAMERA, null);
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
                receiver.send(RECORD_RESULT_GET_CAMERA_FAILED, null);
                mRecordingLock = false;
                return;
            } catch (IOException e) {
                Log.d(TAG, "IOException when preparing MediaRecorder: " + e.getMessage());
                receiver.send(RECORD_RESULT_GET_CAMERA_FAILED, null);
                mRecordingLock = false;
                return;
            }

            Log.d(TAG, "R: Start");
            mMediaRecorder.start();

            receiver.send(RECORD_RESULT_OK, null);

            Log.d(TAG, "R: Started");
        }

        public void handleStopRecordingCommand(Intent intent) {
            final ResultReceiver receiver = intent.getParcelableExtra(RESULT_RECEIVER);

            if (!mRecordingLock) {
                receiver.send(RECORD_RESULT_NOT_RECORDING, null);
                return;
            }

            Log.d(TAG, "R: Stopping");
            mMediaRecorder.stop();

            Log.d(TAG, "R: Releasing recorder");
            mMediaRecorder.release();

            mRecordingLock = false;

            receiver.send(RECORD_RESULT_OK, null);

            Log.d(TAG, "R: shutting down service");
            stopSelf();
        }

        @Override
        public IBinder onBind(Intent intent) {
            Log.d(TAG, "RecordService onBind");
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "RecordService onDestroy");
        }

        public static final int MEDIA_TYPE_IMAGE = 1;
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
