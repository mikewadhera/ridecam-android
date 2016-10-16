package com.ridecam;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ridecam.av.AVUtils;
import com.ridecam.av.CameraEngine;
import com.ridecam.av.RecorderEngine;
import com.ridecam.fs.FSUtils;
import com.ridecam.geo.GPSEngine;
import com.ridecam.model.Trip;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class TripActivity extends AppCompatActivity {

    private static final String TAG = "TripActivity";

    public static final String RERENDER_EVENT = "rerenderEvent";

    public static SurfaceTexture sCachedSurfaceTexture;
    public static TextureView.SurfaceTextureListener sTextureViewListener;
    public static int sTextureViewWidth;
    public static int sTextureViewHeight;

    private BroadcastReceiver mReRenderReceiver;
    private boolean mToggleLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // Starting up or resuming after destroyed (power button pushed or killed by OS)

        // We don't start the service here as we may not have camera permissions yet
        // Not to mention there is nothing interesting the service
        // would be interested about at this point

        // Construct a local broadcast receiver that listens for re-render events from the service
        mReRenderReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                render();
            }
        };

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        loadLayout();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        // Register for re-render events from service
        LocalBroadcastManager.getInstance(this).registerReceiver(mReRenderReceiver, new IntentFilter(RERENDER_EVENT));
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
                    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int surfaceWidth, int surfaceHeight) {
                        sCachedSurfaceTexture = surfaceTexture;
                        sTextureViewWidth = surfaceWidth;
                        sTextureViewHeight = surfaceHeight;
                        manuallyRotatePreviewIfNeeded();
                        Intent intent = new Intent(TripActivity.this, TripService.class);
                        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONRESUME);
                        startService(intent);
                        render();
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                        // return "false" so that TextureView does not release cached SurfaceTexture
                        return (surfaceTexture != TripActivity.sCachedSurfaceTexture);
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

                manuallyRotatePreviewIfNeeded();
                Intent intent = new Intent(this, TripService.class);
                intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONRESUME);
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

        Intent intent = new Intent(this, TripService.class);
        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONSTOP);
        startService(intent);

        // Unregister for re-render events from service
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReRenderReceiver);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    public void loadLayout() {
        setContentView(R.layout.fragment_camera);

        Button recordButton = (Button)findViewById(R.id.record_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecording();
            }
        });
    }

    public void toggleRecording() {
        if (hasPermissions()) {
            if (mToggleLock) return;
            mToggleLock = true;
            Intent intent = new Intent(TripActivity.this, TripService.class);
            intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_TOGGLE_TRIP);
            intent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    mToggleLock = false;
                }
            });
            startService(intent);
        } else {
            // TODO add logging
        }
    }

    public void render() {
        if (!hasPermissions()) {
            return;
        }

        final Button buttonView = (Button)findViewById(R.id.record_button);
        final View previewView = findViewById(R.id.record_frame);
        final TextView capacityView = (TextView)findViewById(R.id.record_capacity);

        final int capacityHours = AVUtils.estimateVideoDurationHours(Knobs.REC_BITRATE, Knobs.getMaximumRecordingFileSizeBytes(this));
        capacityView.setText(capacityHours + "HRS");

        Intent intent = new Intent(this, TripService.class);
        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
        intent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                boolean isTripInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                if (isTripInProgress) {
                    previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame_on));
                    buttonView.setText("FINISH");
                } else {
                    previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame));
                    buttonView.setText("START");
                }
            }
        });
        startService(intent);

    }

    private void manuallyRotatePreviewIfNeeded() {
        if (CameraEngine.usingSamsungDualCamera()) {
            int width = sTextureViewWidth;
            int height = sTextureViewHeight;
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
    }

    public boolean hasPermissions() {
        boolean hasCam =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasGps =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        return hasCam && hasGps;
    }

    public void pauseToRequestPermissionsDialog() {
        // Show the permissions dialog -- we'll be sent to onPause()

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION
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


    public static class TripService extends Service implements CameraEngine.ErrorListener, RecorderEngine.ErrorListener, GPSEngine.LocationListener {

        private static final String TAG = "TripService";

        private static final int NOTIFICATION_ID = 1;

        public static final String START_SERVICE_COMMAND = "startServiceCommands";
        public static final int COMMAND_NONE = -1;
        public static final int COMMAND_ACTIVITY_ONRESUME = 0;
        public static final int COMMAND_ACTIVITY_ONSTOP = 1;
        public static final int COMMAND_TOGGLE_TRIP = 2;
        public static final int COMMAND_IS_TRIP_IN_PROGRESS = 3;
        public static final int COMMAND_ALARM_LOW_STORAGE = 4;

        public static final String RESULT_IS_TRIP_IN_PROGRESS = "isTripInProgressResult";

        public static final String RESULT_RECEIVER = "resultReceiver";

        private CameraEngine mCameraEngine;
        private RecorderEngine mRecorder;
        private GPSEngine mGPSEngine;
        private Trip.Coordinate mLastCoordinate;
        private Trip mTrip;
        private PendingIntent mLowStorageAlarmIntent;

        public TripService() {
        }

        @Override
        public void onCreate() {
            Log.d(TAG, "onCreate");

            // Called after we've acquired all permissions required
            showForegroundNotification("Not Recording");
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "TripService onDestroy");

            // By design this should not be called much / only when app is killed

            try {
                if (isTripInProgress()) {
                    // This is VERY bad
                    // We should *never* have the service
                    // shutdown while a trip is in progress
                    // TODO: add logging
                }

                releaseCamera();
            } catch (Exception e) {
                e.printStackTrace();
                // TODO add logging
            }
        }

        public boolean isTripInProgress() {
            if (mRecorder == null) {
                return false;
            } else {
                return mRecorder.isRecording();
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        // Activity state transitions

        public void handleOnResume() {
            if (!isTripInProgress()) {
                acquireCamera();
                startLocationUpdates();
            }
        }

        public void handleOnStop() {
            mLastCoordinate = null;
            if (!isTripInProgress()) {
                releaseCamera();
                stopLocationUpdates();
            }
        }

        // Alarm callbacks

        public void handleLowStorageCheck() {
            Log.d(TAG, "Checking storage...");
            if (isTripInProgress()) {
                long freeBytes = FSUtils.freeBytesAvailable(FSUtils.getVideoDirectory(this).getAbsolutePath());
                boolean hasLowStorage = freeBytes < Knobs.LOW_STORAGE_FLOOR_BYTES;
                Log.d(TAG, "Free bytes: " + freeBytes);
                if (hasLowStorage) {
                    Log.d(TAG, "Under LOW_STORAGE_FLOOR_BYTES");
                    onLowStorageError();
                }
            }
        }

        public void acquireCamera() {
            if (mCameraEngine == null) {
                mCameraEngine = new CameraEngine(this, sCachedSurfaceTexture);
                mCameraEngine.setErrorListener(this);
                mCameraEngine.acquireCamera();
            } else {
                // TODO add logging
            }
        }

        public void releaseCamera() {
            if (mCameraEngine != null) {
                try {
                    mCameraEngine.releaseCamera();
                } catch (Exception e) {
                    e.printStackTrace();
                    // TODO add logging
                } finally {
                    mCameraEngine = null;
                }
            } else {
                // TODO add logging
            }
        }

        public void toggleTrip() {
            if (mCameraEngine != null) {
                if (!isTripInProgress()) {
                    startTrip();
                } else {
                    stopTrip();
                }
            } else {
                // TODO add logging
            }
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.d(TAG, "TripService onStartCommand");

            if (intent == null) {
                Log.e(TAG, "Cannot start service with null intent");
                return START_STICKY;
            }

            ResultReceiver receiver;

            switch (intent.getIntExtra(START_SERVICE_COMMAND, COMMAND_NONE)) {
                case COMMAND_ACTIVITY_ONRESUME:
                    handleOnResume();
                    break;

                case COMMAND_ACTIVITY_ONSTOP:
                    handleOnStop();
                    break;

                case COMMAND_TOGGLE_TRIP:
                    toggleTrip();
                    reRenderActivity();
                    receiver = intent.getParcelableExtra(RESULT_RECEIVER);
                    receiver.send(0, null);
                    break;

                case COMMAND_IS_TRIP_IN_PROGRESS:
                    boolean isTripInProgress = isTripInProgress();
                    receiver = intent.getParcelableExtra(RESULT_RECEIVER);
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(RESULT_IS_TRIP_IN_PROGRESS, isTripInProgress);
                    receiver.send(0, bundle);
                    break;

                case COMMAND_ALARM_LOW_STORAGE:
                    handleLowStorageCheck();
                    break;

                default:
                    // TODO add logging
                    Log.e(TAG, "Cannot start service with illegal commands");
            }

            return START_STICKY;
        }

        public void reRenderActivity() {
            Intent intent = new Intent(TripActivity.RERENDER_EVENT);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }

        public void startTrip() {
            if (mCameraEngine != null) {
                String tripId = Trip.allocateId();
                mRecorder = new RecorderEngine(this, mCameraEngine, tripId);
                mRecorder.setErrorListener(this);
                mRecorder.startRecording();
                if (mRecorder.isRecording()) {
                    flash("Recording");
                    showForegroundNotification("Recording");
                    mTrip = new Trip(tripId);
                    mTrip.setStartTimestamp(System.currentTimeMillis());
                    if (mLastCoordinate != null) {
                        mTrip.addCoordinate(mLastCoordinate);
                    }
                    startLowStorageAlarm();
                } else {
                    flash("Unable to Start Record");
                    showForegroundNotification("Recording Failed");
                    // TODO add logging
                }
            } else {
                // TODO add logging
            }
        }

        public void stopTrip() {
            if (mRecorder != null) {
                mRecorder.stopRecording();
                if (!mRecorder.isRecording()) {
                    mRecorder = null;
                    stopLowStorageAlarm();
                    showForegroundNotification("Finished Recording");
                    if (mTrip != null) {
                        mTrip.setEndTimestamp(System.currentTimeMillis());
                        Trip.SaveCommand saveCommand = new Trip.SaveCommand(mTrip);
                        saveCommand.run();
                        startSummaryActivity(mTrip.getId());
                        mTrip = null;
                    } else {
                        // TODO add logging
                    }
                } else {
                    // TODO add logging
                }
            } else {
                // TODO add logging
            }
        }

        public void startLowStorageAlarm() {
            // Set a repeating alarm that wakes up the device
            // Sends low storage check command to ourself
            // Fires first after 5 seconds and then a pre-defined OS interval from then
            // We use 5 seconds rather than 0 to allow recorder to fully start up
            Intent intent = new Intent(this, TripService.class);
            intent.putExtra(START_SERVICE_COMMAND, COMMAND_ALARM_LOW_STORAGE);
            mLowStorageAlarmIntent = PendingIntent.getService(this, 1, intent, 0);
            getAlarmManager().setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 5*1000,
                     Knobs.LOW_STORAGE_ALARM_INTERVAL, mLowStorageAlarmIntent);
            Log.d(TAG, "Started Alarm");
        }

        public void stopLowStorageAlarm() {
            if (mLowStorageAlarmIntent != null) {
                getAlarmManager().cancel(mLowStorageAlarmIntent);
                Log.d(TAG, "Stopped Alarm");
            }
        }

        @Override
        public void onCameraError() {
            // TODO add logging
            flash("Camera Error");
            foregroundTripActivity();
        }

        @Override
        public void onRecorderError() {
            // TODO add logging
            stopTrip();
            flash("Trip Stopped (Error)");
            foregroundTripActivity();
        }

        public void onLowStorageError() {
            // TODO add logging
            stopTrip();
            flash("Trip Stopped (Low Storage)");
            foregroundTripActivity();
        }

        public void startSummaryActivity(String tripId) {
            Intent intent = new Intent(getBaseContext(), TripSummaryActivity.class);
            intent.putExtra(TripSummaryActivity.TRIP_ID_EXTRA, tripId);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        public void foregroundTripActivity() {
            Intent intent = new Intent(getApplicationContext(), TripActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            getApplicationContext().startActivity(intent);
            reRenderActivity(); // in case activity already foregrounded
        }

        public void startLocationUpdates() {
            mGPSEngine = new GPSEngine(this);
            mGPSEngine.setLocationListener(this);
            mGPSEngine.startLocationUpdates();
        }

        public void stopLocationUpdates() {
            mGPSEngine.stopLocationUpdates();
        }

        @Override
        public void onLocationUpdate(long timestamp, double latitude, double longitude, float bearing, String title) {
            Trip.Coordinate coordinate = new Trip.Coordinate();
            coordinate.latitude = latitude;
            coordinate.longitude = longitude;
            coordinate.timestamp = timestamp;
            coordinate.bearing = bearing;
            coordinate.title = title;
            if (mTrip != null) {
                mTrip.addCoordinate(coordinate);
            } else {
                mLastCoordinate = coordinate;
            }
        }

        private void showForegroundNotification(String contentText) {
            // Create intent that will bring our app to the front, as if it was tapped in the app
            // launcher
            Intent showTaskIntent = new Intent(getApplicationContext(), TripActivity.class);
            showTaskIntent.setAction(Intent.ACTION_MAIN);
            showTaskIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            showTaskIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);

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
            Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER, 0, 0);
            toast.show();
        }

        private AlarmManager getAlarmManager() {
            return (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        }

    }
}