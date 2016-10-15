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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ridecam.av.CameraEngine;
import com.ridecam.av.device.CameraDevice;
import com.ridecam.av.device.OSCamera;
import com.ridecam.av.device.OSRecorder;
import com.ridecam.av.device.RecorderDevice;
import com.ridecam.av.AVUtils;
import com.ridecam.av.device.vendor.SamsungCamera;
import com.ridecam.av.device.vendor.SamsungRecorder;
import com.ridecam.geo.ReverseGeocoder;
import com.ridecam.model.Trip;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static com.ridecam.Knobs.MAX_REC_LENGTH_MS;

public class TripActivity extends AppCompatActivity {

    private static final String TAG = "TripActivity";

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

    public boolean toggleRecording() {
        if (hasPermissions()) {
            Intent intent = new Intent(TripActivity.this, CameraService.class);
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
        Button buttonView = (Button)findViewById(R.id.record_button);
        View previewView = findViewById(R.id.record_frame);
        TextView capacityView = (TextView)findViewById(R.id.record_capacity);

        int capacityHours = AVUtils.estimateVideoDurationHours(Knobs.REC_BITRATE, Knobs.getMaximumRecordingFileSizeBytes());
        capacityView.setText(capacityHours + "HRS");
        if (CameraService.isRecording()) {
            previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame_on));
            buttonView.setText("FINISH");
        } else {
            previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame));
            buttonView.setText("START");
        }

    }

    private void manuallyRotatePreviewIfNeeded(int width, int height) {
        if (CameraEngine.usingSamsungCameraEngine()) {
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


    public static class CameraService extends Service implements LocationListener {

        private static final String TAG = "CameraService";

        private static final int NOTIFICATION_ID = 1;

        private static final String START_SERVICE_COMMAND = "startServiceCommands";
        private static final int COMMAND_NONE = -1;
        private static final int COMMAND_ACTIVITY_ONRESUME = 0;
        private static final int COMMAND_ACTIVITY_ONSTOP = 1;
        private static final int COMMAND_ACTIVITY_RECORD = 2;

        public static final String RESULT_RECEIVER = "resultReceiver";

        private static boolean sRecordingLock;
        private CameraEngine mCameraEngine;
        private RecorderDevice mRecorder;
        private LocationManager mLocationManager;
        private Trip.Coordinate mLastCoordinate;
        private Trip mTrip;

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
                startLocationUpdates();
            }
        }

        public void handleOnStop() {
            mLastCoordinate = null;
            if (!sRecordingLock) {
                releaseCamera();
                stopLocationUpdates();
            }
        }

        public static boolean usingSamsungCameraEngine() {
            return (SamsungCamera.isAvailable() && !Knobs.FORCE_NATIVE_CAMERA);
        }

        public void acquireCamera() {
            if (mCameraEngine == null) {
                mCameraEngine = new CameraEngine(this, sCachedSurfaceTexture);
                mCameraEngine.acquireCamera();
            }
        }

        public void releaseCamera() {
            if (mCameraEngine != null) {
                try {
                   mCameraEngine.releaseCamera();
                } finally {
                    mCameraEngine = null;
                }
            }
        }

        public void toggleRecording() {
            if (mCameraEngine != null) {
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

                if (usingSamsungCameraEngine()) {
                    mRecorder = new SamsungRecorder();
                } else {
                    mRecorder = new OSRecorder();
                }

                String tripId = Trip.allocateId();
                mTrip = new Trip(tripId);
                if (mLastCoordinate != null) {
                    mTrip.addCoordinate(mLastCoordinate);
                }

                mRecorder.setOnErrorListener(new RecorderDevice.OnErrorListener() {
                    @Override
                    public void onError(RecorderDevice recorder, int errorType, int errorCode) {
                        onRecorderError(errorType, errorCode);
                    }
                });

                Log.d(TAG, "R: Obtaining Camera");
                CameraDevice camera = mCameraEngine.getDevice();

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
                mRecorder.setVideoSource(OSRecorder.VideoSource.CAMERA);

                Log.d(TAG, "R: Setting profile");
                mRecorder.setOutputFormat(OSRecorder.OutputFormat.MPEG_4);
                mRecorder.setVideoSize(Knobs.REC_WIDTH, Knobs.REC_HEIGHT);
                mRecorder.setVideoFrameRate(Knobs.REC_FPS);
                mRecorder.setVideoEncodingBitRate(Knobs.REC_BITRATE);
                mRecorder.setVideoEncoder(OSRecorder.VideoEncoder.H264);

                Log.d(TAG, "R: Setting max outputs");
                mRecorder.setMaxFileSize(Knobs.getMaximumRecordingFileSizeBytes());
                mRecorder.setMaxDuration(MAX_REC_LENGTH_MS);

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
                mRecorder.registerRecordingSurface(camera);

                mTrip.setStartTimestamp(System.currentTimeMillis());

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

                Log.d(TAG, "R: Unregistering Recording Surface");
                mRecorder.unregisterRecordingSurface(mCameraEngine.getDevice());

                Log.d(TAG, "R: Reseting recorder");
                mRecorder.reset();

                Log.d(TAG, "R: Releasing recorder");
                mRecorder.release();

                Log.d(TAG, "R: Locking camera");
                mCameraEngine.lockCamera();

                mTrip.setEndTimestamp(System.currentTimeMillis());

                Trip.SaveCommand saveCommand = new Trip.SaveCommand(mTrip);
                saveCommand.run();

                showForegroundNotification("Not Recording");

                startSummaryActivity(mTrip.getId());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                sRecordingLock = false;
                mTrip = null;
            }

            flash("Recording Stopped");

            Log.d(TAG, "R: Stopped");
        }

        public void startSummaryActivity(String tripId) {
            Intent intent = new Intent(getBaseContext(), TripSummaryActivity.class);
            intent.putExtra(TripSummaryActivity.TRIP_ID_EXTRA, tripId);
            startActivity(intent);
        }

        public void startLocationUpdates() {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Knobs.GPS_MIN_TIME_CHANGE_MS, Knobs.GPS_MIN_DISTANCE_CHANGE_M, this);
        }

        public void stopLocationUpdates() {
            if (mLocationManager != null) {
                mLocationManager.removeUpdates(this);
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
            Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER, 0, 0);
            toast.show();
        }

        public WindowManager getWindowManager() {
            return (WindowManager) getSystemService(Context.WINDOW_SERVICE);
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