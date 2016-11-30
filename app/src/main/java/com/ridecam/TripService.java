package com.ridecam;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
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
import com.ridecam.ui.CircleAngleAnimation;
import com.ridecam.ui.CircleView;
import com.ridecam.ui.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import wei.mark.standout.StandOutWindow;
import wei.mark.standout.constants.StandOutFlags;
import wei.mark.standout.ui.Window;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.ridecam.R.id.circle;

public class TripService extends StandOutWindow implements CameraEngine.ErrorListener, RecorderEngine.ErrorListener, GPSEngine.LocationListener {

    private static final String TAG = "TripService";

    private static final int NOTIFICATION_ID = 1;

    private static final int WINDOW_ID_INPROGRESS_INDICATOR = 1;
    private static final int WINDOW_ID_CONTROL_BAR = 2;

    public static final String START_SERVICE_COMMAND = "startServiceCommands";
    public static final int COMMAND_NONE = -1;
    public static final int COMMAND_ACTIVITY_ONRESUME = 0;
    public static final int COMMAND_ACTIVITY_ONSTOP = 1;
    public static final int COMMAND_TOGGLE_TRIP = 2;
    public static final int COMMAND_IS_TRIP_IN_PROGRESS = 3;
    public static final int COMMAND_ALARM_LOW_STORAGE = 4;
    public static final int COMMAND_ON_AUTOSTART = 5;
    public static final int COMMAND_ON_AUTOSTOP = 6;

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
    private View mControlBarView;
    private Handler mControlBarAutoDismiss;

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

        StandOutWindow.closeAll(this, this.getClass());
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

        StandOutWindow.closeAll(this, this.getClass());

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
    public void createAndAttachView(int id, FrameLayout frame) {
        Log.d(TAG, "createAndAttachView");

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        if (id == WINDOW_ID_INPROGRESS_INDICATOR) {
            inflater.inflate(R.layout.service_inprogress_indicator, frame, true);
        } else if (id == WINDOW_ID_CONTROL_BAR) {
            mControlBarView = inflater.inflate(R.layout.service_control_bar, frame, true);
        } else {
            throw new IllegalArgumentException("Unknown window ID: " + id);
        }
    }

    @Override
    public StandOutLayoutParams getParams(int id, Window window) {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        int width = display.getWidth();
        int height;
        int yPos;
        if (id == WINDOW_ID_INPROGRESS_INDICATOR) {
            height = Utils.toPixels(Knobs.IN_PROGRESS_INDICATOR_HEIGHT_DP, getResources().getDisplayMetrics());
            yPos = 0;
        } else if (id == WINDOW_ID_CONTROL_BAR) {
            height = Utils.toPixels(Knobs.CONTROL_BAR_HEIGHT_DP, getResources().getDisplayMetrics());
            yPos = Utils.toPixels(Knobs.IN_PROGRESS_INDICATOR_HEIGHT_DP, getResources().getDisplayMetrics());
        } else {
            throw new IllegalArgumentException("Unknown window ID: " + id);
        }
        return new StandOutLayoutParams(id, width, height, 0, yPos);
    }

    @Override
    public int getFlags(int id) {
        return super.getFlags(id) | StandOutFlags.FLAG_WINDOW_FOCUSABLE_DISABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    @Override
    public Animation getShowAnimation(int id) {
        if (id == WINDOW_ID_CONTROL_BAR) {
            return AnimationUtils.loadAnimation(this, R.anim.slide_down);
        } else {
            return super.getShowAnimation(id);
        }
    }

    @Override
    public Animation getCloseAnimation(int id) {
        if (id == WINDOW_ID_CONTROL_BAR) {
            return AnimationUtils.loadAnimation(this, R.anim.slide_up);
        } else {
            return null;
        }
    }

    public boolean isTripInProgress() {
        if (mRecorder == null) {
            return false;
        } else {
            return mRecorder.isRecording();
        }
    }

    public void showInProgressIndicator() {
        show(WINDOW_ID_INPROGRESS_INDICATOR);
    }

    public void hideInProgressIndicator() {
        if (getWindow(WINDOW_ID_INPROGRESS_INDICATOR) != null) {
            close(WINDOW_ID_INPROGRESS_INDICATOR);
        }
    }

    public void revealControlBar(String message) {
        if (mControlBarAutoDismiss != null) {
            mControlBarAutoDismiss.removeCallbacksAndMessages(null);
        }
        show(WINDOW_ID_CONTROL_BAR);
        renderControlBar(message);
        mControlBarAutoDismiss = new Handler();
        mControlBarAutoDismiss.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissControlBar();
            }
        }, Knobs.CONTROL_BAR_AUTODISMISS_MS);
    }

    public void dismissControlBar() {
        if (getWindow(WINDOW_ID_CONTROL_BAR) != null) {
            close(WINDOW_ID_CONTROL_BAR);
        }
    }

    public void renderControlBar(String message) {
        if (mControlBarView != null) {
            View view = mControlBarView.findViewById(R.id.control_bar);
            TextView textView = (TextView) view.findViewById(R.id.control_bar_text);
            textView.setText(message);

            Button buttonView = (Button) mControlBarView.findViewById(R.id.control_bar_button);
            buttonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (isTripInProgress()) {
                        stopTrip();
                        revealControlBar("RIDECAM: OFF");
                    } else {
                        startTrip(false);
                        revealControlBar("RIDECAM: ON");
                    }
                }
            });

            Button closeButtonView = (Button) mControlBarView.findViewById(R.id.control_bar_close_button);
            closeButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    CircleView circleView = (CircleView) mControlBarView.findViewById(circle);
                    if (circleView.getAnimation() != null) {
                        circleView.setAnimation(null);
                        circleView.setAngle(0);
                    }
                    dismissControlBar();
                }
            });

            if (isTripInProgress()) {
                buttonView.setBackground(getResources().getDrawable(R.drawable.start_button_on_control_bar));
                buttonView.setText(Copy.RIDE_END);
            } else {
                buttonView.setBackground(getResources().getDrawable(R.drawable.start_button));
                buttonView.setText(Copy.RIDE_START);
            }

            CircleView circleView = (CircleView) mControlBarView.findViewById(circle);
            CircleAngleAnimation animation = new CircleAngleAnimation(circleView, 360);
            animation.setDuration(Knobs.CONTROL_BAR_AUTODISMISS_MS);
            circleView.startAnimation(animation);
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

    public void toggleTrip() {
        if (mCameraEngine != null) {
            if (!isTripInProgress()) {
                startTrip(false);
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

            case COMMAND_ON_AUTOSTART:
                handleAutoStart();
                break;

            case COMMAND_ON_AUTOSTOP:
                handleAutoStop();
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
                showInProgressIndicator();
                SimpleDateFormat sdf = new SimpleDateFormat("'TURNED ON AT' h:mm a");
                showForegroundNotification(sdf.format(new Date()), viaAutoStart);
                long sayDelay = 0;
                if (viaAutoStart) {
                    sayDelay = 1000;
                }
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        say(Copy.RIDE_START_SAY);
                    }
                }, sayDelay);
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

    public void stopTrip() {
        if (mRecorder != null) {
            mRecorder.stopRecording();
            if (!mRecorder.isRecording()) {
                hideInProgressIndicator();
                say(Copy.RIDE_END_SAY);
                mRecorder = null;
                stopLowStorageAlarm();
                if (mTrip != null) {
                    mTrip.setEndTimestamp(System.currentTimeMillis());
                    DB.Save saveCommand = new DB.Save(AuthUtils.getUserId(this), mTrip);
                    saveCommand.run();
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
        stopTrip();
        flash(Copy.RIDE_INTERRUPTED);
        foregroundTripActivity();
    }

    public void onLowStorageError() {
        mAnalytics.logEvent("ERROR_LOW_STORAGE", null);
        stopTrip();
        flash(Copy.RIDE_LOW_STORAGE);
        foregroundTripActivity();
    }

    private void handleAutoStart() {
        if (!isTripInProgress()) {
            startTrip(true);
            revealControlBar("RIDECAM: ON");
        }
    }

    private void handleAutoStop() {
        if (isTripInProgress()) {
            revealControlBar("RIDECAM: ON");
        }
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
        mLastCoordinate = null;
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

    private void showForegroundNotification(String contentText, boolean playSound) {
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

        if (playSound) {
            builder.setSound(Uri.parse("android.resource://"
                    + getPackageName() + "/" + R.raw.music_vibelong_doorbell));
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
