package com.ridecam;

import android.Manifest;
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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import com.ridecam.geo.ReverseGeocoder;
import com.ridecam.model.Trip;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class TripActivity extends AppCompatActivity {

    private static final String TAG = "TripActivity";

    public static final String RERENDER_EVENT = "rerenderEvent";

    public static SurfaceTexture sCachedSurfaceTexture;
    public static TextureView.SurfaceTextureListener sTextureViewListener;

    private BroadcastReceiver mReRenderReceiver;

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
                        manuallyRotatePreviewIfNeeded(surfaceWidth, surfaceHeight);
                        Intent intent = new Intent(TripActivity.this, CameraService.class);
                        intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_ACTIVITY_ONRESUME);
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
            Intent intent = new Intent(TripActivity.this, CameraService.class);
            intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_TOGGLE_TRIP);
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

        final int capacityHours = AVUtils.estimateVideoDurationHours(Knobs.REC_BITRATE, Knobs.getMaximumRecordingFileSizeBytes());
        capacityView.setText(capacityHours + "HRS");

        Intent intent = new Intent(this, CameraService.class);
        intent.putExtra(CameraService.START_SERVICE_COMMAND, CameraService.COMMAND_IS_TRIP_IN_PROGRESS);
        intent.putExtra(CameraService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                boolean isTripInProgress = data.getBoolean(CameraService.RESULT_IS_TRIP_IN_PROGRESS);
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

    private void manuallyRotatePreviewIfNeeded(int width, int height) {
        if (CameraEngine.usingSamsungCamera()) {
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


    public static class CameraService extends Service implements CameraEngine.ErrorListener, RecorderEngine.ErrorListener, LocationListener {

        private static final String TAG = "CameraService";

        private static final int NOTIFICATION_ID = 1;

        private static final String START_SERVICE_COMMAND = "startServiceCommands";
        private static final int COMMAND_NONE = -1;
        private static final int COMMAND_ACTIVITY_ONRESUME = 0;
        private static final int COMMAND_ACTIVITY_ONSTOP = 1;
        private static final int COMMAND_TOGGLE_TRIP = 2;
        private static final int COMMAND_IS_TRIP_IN_PROGRESS = 3;

        private static final String RESULT_IS_TRIP_IN_PROGRESS = "isTripInProgressResult";

        public static final String RESULT_RECEIVER = "resultReceiver";

        private CameraEngine mCameraEngine;
        private RecorderEngine mRecorder;
        private LocationManager mLocationManager;
        private Trip.Coordinate mLastCoordinate;
        private Trip mTrip;

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
            return mRecorder != null && mRecorder.isRecording();
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
            Log.d(TAG, "CameraService onStartCommand");

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
                    break;

                case COMMAND_IS_TRIP_IN_PROGRESS:
                    boolean isTripInProgress = isTripInProgress();
                    receiver = intent.getParcelableExtra(RESULT_RECEIVER);
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(RESULT_IS_TRIP_IN_PROGRESS, isTripInProgress);
                    receiver.send(0, bundle);
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
            if (mRecorder == null) {
                if (mCameraEngine != null) {
                    mRecorder = new RecorderEngine(mCameraEngine);

                    mRecorder.setErrorListener(this);
                    mRecorder.startRecording();
                    if (mRecorder.isRecording()) {
                        flash("Recording");
                        showForegroundNotification("Recording");
                        String tripId = Trip.allocateId();
                        mTrip = new Trip(tripId);
                        mTrip.setStartTimestamp(System.currentTimeMillis());
                        if (mLastCoordinate != null) {
                            mTrip.addCoordinate(mLastCoordinate);
                        }
                    } else {
                        flash("Unable to Start Record");
                        showForegroundNotification("Recording Failed");
                        // TODO add logging
                    }
                } else {
                    // TODO add logging
                }
            } else {
                // TODO Add logging
            }
        }

        public void stopTrip() {
            if (mRecorder != null) {
                mRecorder.stopRecording();
                if (!mRecorder.isRecording()) {
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

        @Override
        public void onCameraError() {
            // TODO add logging
            flash("Camera Error");
            mTrip = null;
            reRenderActivity();
        }

        @Override
        public void onRecorderError() {
            // TODO add logging
            flash("Trip Stopped (Error)");
            mTrip = null;
            reRenderActivity();
        }

        public void startSummaryActivity(String tripId) {
            Intent intent = new Intent(getBaseContext(), TripSummaryActivity.class);
            intent.putExtra(TripSummaryActivity.TRIP_ID_EXTRA, tripId);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        public void startLocationUpdates() {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            try {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Knobs.GPS_MIN_TIME_CHANGE_MS, Knobs.GPS_MIN_DISTANCE_CHANGE_M, this);
            } catch (SecurityException e) {
                // TODO add logging
            }
        }

        public void stopLocationUpdates() {
            if (mLocationManager != null) {
                try {
                    mLocationManager.removeUpdates(this);
                } catch (SecurityException e) {
                    // TODO add logging
                }
                mLocationManager = null;
            }
        }

        // LocationListener
        @Override
        public void onLocationChanged(final Location location) {
            Intent intent = new Intent(this, ReverseGeocoder.class);
            intent.putExtra(ReverseGeocoder.LOCATION_DATA_EXTRA, location);
            intent.putExtra(ReverseGeocoder.RECEIVER, new android.os.ResultReceiver(null) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    String result = resultData.getString(ReverseGeocoder.RESULT_DATA_KEY);
                    onLocationGeocoded(location, result);
                }
            });
            startService(intent);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }

        public void onLocationGeocoded(Location location, String address) {
            Trip.Coordinate coordinate = new Trip.Coordinate();
            coordinate.latitude = location.getLatitude();
            coordinate.longitude = location.getLongitude();
            coordinate.timestamp = location.getTime();
            coordinate.bearing = location.getBearing();
            coordinate.title = address;
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

    }
}