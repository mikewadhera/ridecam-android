package com.ridecam;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.ridecam.auth.AuthUtils;
import com.ridecam.av.CameraEngine;
import com.ridecam.av.RecorderEngine;
import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;
import com.ridecam.geo.GPSEngine;
import com.ridecam.model.Trip;
import com.ridecam.ui.CameraFragment;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class TripService extends Service implements CameraEngine.ErrorListener, RecorderEngine.ErrorListener, GPSEngine.LocationListener {

    private static final String TAG = "TripService";

    private static final int NOTIFICATION_ID = 1;

    public static final String START_SERVICE_COMMAND = "startServiceCommands";
    public static final int COMMAND_NONE = -1;
    public static final int COMMAND_ACTIVITY_ONRESUME = 0;
    public static final int COMMAND_ACTIVITY_ONSTOP = 1;
    public static final int COMMAND_TOGGLE_TRIP = 2;
    public static final int COMMAND_IS_TRIP_IN_PROGRESS = 3;
    public static final int COMMAND_ALARM_LOW_STORAGE = 4;
    public static final int COMMAND_ON_AUTOSTOP = 5;
    public static final int COMMAND_ON_AUTOSTART = 6;

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
            mCameraEngine = new CameraEngine(this, CameraFragment.sCachedSurfaceTexture);
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

            case COMMAND_ON_AUTOSTOP:
                handleAutoStop();
                reRenderActivity();
                break;

            case COMMAND_ON_AUTOSTART:
                handleAutoStart();
                reRenderActivity();
                break;

            default:
                // TODO add logging
                Log.e(TAG, "Cannot start service with illegal commands");
        }

        return START_STICKY;
    }

    public void reRenderActivity() {
        Intent intent = new Intent(CameraFragment.RERENDER_EVENT);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void startTrip() {
        if (mCameraEngine != null) {
            String tripId = Trip.allocateId();
            mRecorder = new RecorderEngine(this, mCameraEngine, tripId);
            mRecorder.setErrorListener(this);
            mRecorder.startRecording();
            if (mRecorder.isRecording()) {
                showForegroundNotification(Copy.RIDE_START);
                mTrip = new Trip(tripId);
                mTrip.setStartTimestamp(System.currentTimeMillis());
                if (mLastCoordinate != null) {
                    mTrip.addCoordinate(mLastCoordinate);
                }
                startLowStorageAlarm();
            } else {
                flash(Copy.RIDE_START_FAIL);
                showForegroundNotification(Copy.RIDE_START_FAIL);
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
                showForegroundNotification(Copy.RIDE_FINISH);
                if (mTrip != null) {
                    mTrip.setEndTimestamp(System.currentTimeMillis());
                    DB.Save saveCommand = new DB.Save(AuthUtils.getUserId(this), mTrip);
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
        getAlarmManager().setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 5 * 1000,
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
        flash(Copy.CAMERA_ERROR);
        if (isTripInProgress()) {
            foregroundTripActivity();
        }
    }

    @Override
    public void onRecorderError() {
        // TODO add logging
        stopTrip();
        flash(Copy.RIDE_INTERRUPTED);
        foregroundTripActivity();
    }

    public void onLowStorageError() {
        // TODO add logging
        stopTrip();
        flash(Copy.RIDE_LOW_STORAGE);
        foregroundTripActivity();
    }

    private void handleAutoStop() {
        if (isTripInProgress()) {
            stopTrip();
        }
    }

    private void handleAutoStart() {
        if (!isTripInProgress()) {
            startTrip();
        }
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
        if (mGPSEngine != null) {
            mGPSEngine.stopLocationUpdates();
        }
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
        toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER, 0, 0);
        toast.show();
    }

    private AlarmManager getAlarmManager() {
        return (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

}
