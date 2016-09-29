package com.dashkam;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
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

import java.lang.ref.WeakReference;

import static android.R.attr.permission;
import static android.R.attr.start;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DashKam";
    private static int CAMERA_ID = 0;
    private static final int PERMISSIONS_ID_CAMERA = 0;
    private static final int PERMISSIONS_ID_GPS = 1;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    /**
     *
     * The underlying {@link android.graphics.Camera} owned by activity
     *
     */
    private static Camera mCamera;

    private static SurfaceView mSurfaceView;

    private static boolean mIsRecording;

    private WeakReference<CameraFragment> mCameraFragment;

    private WeakReference<DashKamFragment> mDashKamFragment;

    public static Camera getCamera() {
        return mCamera;
    }

    public static SurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    public static boolean isRecording() {
        return mIsRecording;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Full screen layout
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
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

        if (hasCam) {
            if (hasGps) {

                acquireCamera();

                // Fix for frozen preview when resuming from sleep button
                // See onPause for more
                if (mSurfaceView != null && mSurfaceView.getVisibility() != View.VISIBLE) {
                    mSurfaceView.setVisibility(View.VISIBLE);
                }

            } else {
                pauseToRequestPermission(this, Manifest.permission.ACCESS_FINE_LOCATION, PERMISSIONS_ID_GPS);
            }
        } else {
            pauseToRequestPermission(this, Manifest.permission.CAMERA, PERMISSIONS_ID_CAMERA);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

        //if (!mIsRecording) {
            stopPreviewReleaseCamera();
        //}

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

    public void setRecording(boolean isRecording) {
        if (isRecording != mIsRecording) {
            mIsRecording = isRecording;
            renderCameraFragment();
        }
    }

    public void toggleRecording() {
        if (mCamera != null && mSurfaceView != null) {
            if (mIsRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        }
    }

    public void startRecording() {
        setRecording(true);
        ResultReceiver receiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                if (code == RecordService.RECORD_RESULT_OK) {
                    flash("Recording Started");
                } else {
                    flash("Recording Failed");
                    setRecording(false);
                }
            }
        };
        RecordService.start(this, receiver, RecordService.COMMAND_START_RECORDING);
    }

    public void stopRecording() {
        setRecording(false);
        ResultReceiver receiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                if (code == RecordService.RECORD_RESULT_OK) {
                    flash("Recording Stopped");
                } else {
                    flash("Recording Error");
                }
            }
        };
        RecordService.start(this, receiver, RecordService.COMMAND_STOP_RECORDING);
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
            case PERMISSIONS_ID_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    acquireCamera();
                    startPreview();


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            case PERMISSIONS_ID_GPS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }

                acquireCamera();
                startPreview();

                return;
            }
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class CameraFragment extends Fragment {

        View mRootView;

        public CameraFragment() {
        }

        public static CameraFragment factory() {
            CameraFragment cameraFragment = new CameraFragment();
            return cameraFragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_camera, container, false);

            SurfaceView previewView = (SurfaceView)mRootView.findViewById(R.id.camera_preview);

            final MainActivity activity = (MainActivity)getActivity();
            activity.setCameraFragment(this);
            activity.setPreviewView(previewView);

            previewView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    activity.toggleRecording();
                }
            });

            render();

            return mRootView;
        }

        public void render() {
            View previewView = mRootView.findViewById(R.id.camera_preview);
            Drawable previewViewBackground;

            if (MainActivity.isRecording()) {
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

        public static DashKamFragment factory() {
            DashKamFragment dashKamFragment = new DashKamFragment();
            return dashKamFragment;
        }

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

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
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

    public static void pauseToRequestPermission(Activity context, String permission, int requestCode) {
        ActivityCompat.requestPermissions(context,
                new String[]{permission},
                requestCode);
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
        public static final String VIDEO_PATH = "recordedVideoPath";

        public static final int RECORD_RESULT_OK = 0;
        public static final int RECORD_RESULT_DEVICE_NO_CAMERA = 1;
        public static final int RECORD_RESULT_GET_CAMERA_FAILED = 2;
        public static final int RECORD_RESULT_ALREADY_RECORDING = 3;
        public static final int RECORD_RESULT_NOT_RECORDING = 4;

        private static final String START_SERVICE_COMMAND = "startServiceCommands";
        private static final int COMMAND_NONE = -1;
        private static final int COMMAND_START_RECORDING = 0;
        private static final int COMMAND_STOP_RECORDING = 1;

        private boolean mRecordingLock;

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

            // ...

            receiver.send(RECORD_RESULT_OK, null);
        }

        public void handleStopRecordingCommand(Intent intent) {
            final ResultReceiver receiver = intent.getParcelableExtra(RESULT_RECEIVER);

            if (!mRecordingLock) {
                receiver.send(RECORD_RESULT_NOT_RECORDING, null);
                return;
            }

            // ...

            mRecordingLock = false;

            receiver.send(RECORD_RESULT_OK, null);
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

    }
}
