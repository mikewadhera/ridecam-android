package com.ridecam;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.ridecam.auth.AuthUtils;
import com.ridecam.av.CameraEngine;
import com.ridecam.av.RecorderEngine;
import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;
import com.ridecam.geo.GPSEngine;
import com.ridecam.model.Trip;
import com.ridecam.ui.CameraFragment;
import com.ridecam.ui.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import wei.mark.standout.StandOutWindow;
import wei.mark.standout.constants.StandOutFlags;
import wei.mark.standout.ui.Window;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class TripService extends StandOutWindow implements CameraEngine.ErrorListener, RecorderEngine.ErrorListener, GPSEngine.LocationListener {

    private static final String TAG = "TripService";

    private static final int NOTIFICATION_ID = 1;

    public static final String START_SERVICE_COMMAND = "startServiceCommands";
    public static final int COMMAND_NONE = -1;
    public static final int COMMAND_ACTIVITY_ONRESUME = 0;
    public static final int COMMAND_ACTIVITY_ONSTOP = 1;
    public static final int COMMAND_TOGGLE_TRIP = 2;
    public static final int COMMAND_IS_TRIP_IN_PROGRESS = 3;
    public static final int COMMAND_ALARM_LOW_STORAGE = 4;
    public static final int COMMAND_ON_AUTOSTART = 5;

    public static final String RESULT_IS_TRIP_IN_PROGRESS = "isTripInProgressResult";

    public static final String RESULT_RECEIVER = "resultReceiver";

    private CameraEngine mCameraEngine;
    private RecorderEngine mRecorder;
    private GPSEngine mGPSEngine;
    private Trip.Coordinate mLastCoordinate;
    private Trip mTrip;
    private PendingIntent mLowStorageAlarmIntent;
    private FirebaseAnalytics mAnalytics;
    private TextToSpeech mTTS;
    private boolean mHasTTSInit;

    @Override
    public void onCreate() {
        mAnalytics = FirebaseAnalytics.getInstance(this);

        Log.d(TAG, "onCreate");
        super.onCreate();

        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Log.d(TAG, "TextToSpeech_onInit()");
                if (status == TextToSpeech.SUCCESS) {
                    mTTS.setSpeechRate(Knobs.SPEECH_RATE);
                    int result = mTTS.setLanguage(Knobs.SPEECH_LOCALE);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "Text to Speech locale not supported");
                        return;
                    }
                    mHasTTSInit = true;
                } else {
                    Log.e(TAG, "Text to Speech failed to load");
                }
            }
        });

        hideStatusBarRecordingIndicator();

        hideForegroundNotification();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "TripService onDestroy");

        super.onDestroy();

        // By design this should not be called much / only when app is killed

        if (mTTS != null) {
            mTTS.shutdown();
            mHasTTSInit = false;
        }

        hideStatusBarRecordingIndicator();

        try {
            if (isTripInProgress()) {
                // This is VERY bad
                // We should *never* have the service
                // shutdown while a trip is in progress
                mAnalytics.logEvent("ERROR_TRIP_SHUTDOWN", null);
            }

            releaseCamera();
        } catch (Exception e) {
            e.printStackTrace();
            // TODO add logging
        }
    }

    // Standout overrides

    @Override
    public String getAppName() {
        return "Ridecam";
    }

    @Override
    public int getAppIcon() {
        return R.drawable.ic_stat_r;
    }

    @Override
    public void createAndAttachView(int id, FrameLayout frame) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.service_trip, frame, true);
    }

    @Override
    public StandOutLayoutParams getParams(int id, Window window) {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        return new StandOutLayoutParams(id, display.getWidth(), Utils.toPixels(2, getResources().getDisplayMetrics()),
                StandOutLayoutParams.TOP, StandOutLayoutParams.LEFT);
    }

    @Override
    public int getFlags(int id) {
        return super.getFlags(id) | StandOutFlags.FLAG_WINDOW_FOCUSABLE_DISABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
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
        hideStatusBarRecordingIndicator();
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
        } else {
            showStatusBarRecordingIndicator();
        }
    }

    private void showStatusBarRecordingIndicator() {
        StandOutWindow.show(this, this.getClass(), StandOutWindow.DEFAULT_ID);
    }

    private void hideStatusBarRecordingIndicator() {
        StandOutWindow.closeAll(this, this.getClass());
    }

    // Alarm callbacks

    public void handleLowStorageCheck() {
        Log.d(TAG, "Checking storage...");
        if (isTripInProgress()) {
            long freeBytes = FSUtils.freeBytesAvailable(FSUtils.getVideoDirectory(this).getAbsolutePath());
            boolean hasLowStorage = freeBytes < Knobs.LOW_STORAGE_FLOOR_BYTES;
            Log.d(TAG, "Free bytes: " + freeBytes);
            Bundle params = new Bundle();
            params.putLong("FREE_BYTES", freeBytes);
            mAnalytics.logEvent("FREE_BYTES_CHECK", params);
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
            showForegroundNotification("Not recording", false);
        } else {
            // TODO add logging
        }
    }

    public void releaseCamera() {
        if (mCameraEngine != null) {
            try {
                mCameraEngine.releaseCamera();
                hideForegroundNotification();
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

    public void toggleTrip(boolean viaAutoStop) {
        if (mCameraEngine != null) {
            if (!isTripInProgress()) {
                startTrip(false);
            } else {
                stopTrip(viaAutoStop);
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
                toggleTrip(intent.getBooleanExtra(TripActivity.IS_FROM_AUTOSTOP_EXTRA, false));
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

            case COMMAND_ON_AUTOSTART:
                handleAutoStart();
                break;

            case COMMAND_NONE:
                // Important: need to call super here to let Standout run it's state changes
                super.onStartCommand(intent, flags, startId);
                break;
        }

        return START_STICKY;
    }

    public void reRenderActivity() {
        Intent intent = new Intent(CameraFragment.RERENDER_EVENT);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void startTrip(boolean viaAutoStart) {
        if (mCameraEngine != null) {
            String tripId = Trip.allocateId();
            mRecorder = new RecorderEngine(this, mCameraEngine, tripId);
            mRecorder.setErrorListener(this);
            mRecorder.startRecording();
            if (mRecorder.isRecording()) {
                say(Copy.RIDE_START_SAY);
                long flashDelay = viaAutoStart ? Knobs.AUTOSTART_FLASH_DELAY : 0;
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        flash(Copy.RIDE_START_FLASH);
                    }
                }, flashDelay);
                SimpleDateFormat sdf = new SimpleDateFormat("'TURNED ON AT' h:mm a");
                showForegroundNotification(sdf.format(new Date()), true);
                mTrip = new Trip(tripId);
                mTrip.setStartTimestamp(System.currentTimeMillis());
                if (mLastCoordinate != null) {
                    mTrip.addCoordinate(mLastCoordinate);
                }
                startLowStorageAlarm();
            } else {
                flash(Copy.RIDE_START_FAIL);
                showForegroundNotification("Internal Error", false);
                // TODO add logging
            }
        } else {
            // TODO add logging
        }
    }

    public void stopTrip(boolean viaAutoStop) {
        if (mRecorder != null) {
            mRecorder.stopRecording();
            if (!mRecorder.isRecording()) {
                say(Copy.RIDE_END_SAY);
                flash(Copy.RIDE_END_FLASH);
                mRecorder = null;
                stopLowStorageAlarm();
                hideStatusBarRecordingIndicator();
                if (mTrip != null) {
                    mTrip.setEndTimestamp(System.currentTimeMillis());
                    DB.Save saveCommand = new DB.Save(AuthUtils.getUserId(this), mTrip);
                    saveCommand.run();
                    onTripEnd(mTrip.getId(), viaAutoStop);
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
        mAnalytics.logEvent("ERROR_CAMERA", null);
        flash(Copy.CAMERA_ERROR);
        if (isTripInProgress()) {
            foregroundTripActivity();
        }
    }

    @Override
    public void onRecorderError() {
        mAnalytics.logEvent("ERROR_RECORDING", null);
        stopTrip(false);
        flash(Copy.RIDE_INTERRUPTED);
        foregroundTripActivity();
    }

    public void onLowStorageError() {
        mAnalytics.logEvent("ERROR_LOW_STORAGE", null);
        stopTrip(false);
        flash(Copy.RIDE_LOW_STORAGE);
        foregroundTripActivity();
    }

    private void handleAutoStart() {
        if (!isTripInProgress()) {
            startTrip(true);
        }
    }

    public void onTripEnd(String tripId, boolean viaAutoStop) {
        Intent intent = new Intent(CameraFragment.ON_TRIP_END_EVENT);
        intent.putExtra(TripSummaryActivity.TRIP_ID_EXTRA, tripId);
        intent.putExtra(TripActivity.IS_FROM_AUTOSTOP_EXTRA, viaAutoStop);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
    public void onLocationUpdate(long timestamp, double latitude, double longitude, float bearing) {
        Trip.Coordinate coordinate = new Trip.Coordinate();
        coordinate.latitude = latitude;
        coordinate.longitude = longitude;
        coordinate.timestamp = timestamp;
        coordinate.bearing = bearing;
        if (mTrip != null) {
            mTrip.addCoordinate(coordinate);
        } else {
            mLastCoordinate = coordinate;
        }
    }

    private void showForegroundNotification(String contentText, boolean recordStart) {
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

        android.support.v4.app.NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_r)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setAutoCancel(false);

        if (recordStart) {
            //builder.setSound(Uri.parse("android.resource://"
              //      + getPackageName() + "/" + R.raw.pad_glow_chime));
        }

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void hideForegroundNotification() {
        stopForeground(true);
    }

    private void flash(String text) {
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.custom_toast, null);
        TextView textView = (TextView)layout.findViewById(R.id.toast_message);
        textView.setText(text);
        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    private AlarmManager getAlarmManager() {
        return (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    private void say(String text) {
        if (mTTS != null && mHasTTSInit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ttsGreater21(text);
            } else {
                ttsUnder20(text);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void ttsUnder20(String text) {
        HashMap<String, String> map = new HashMap<>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MessageId");
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void ttsGreater21(String text) {
        String utteranceId=this.hashCode() + "";
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

}
