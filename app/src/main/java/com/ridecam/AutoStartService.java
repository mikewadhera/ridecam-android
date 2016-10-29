package com.ridecam;

import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

public class AutoStartService extends Service implements GoogleApiClient.ConnectionCallbacks, ResultCallback<Status>, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "AutoStartService";

    public static final String ACTIVITY_DETECTION_EVENT = "AutoStartService.ACTIVITY_DETECTION_EVENT";

    protected BroadcastReceiver mActivityDetectionReceiver;
    protected GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        // Construct a local broadcast receiver that listens for activity detections
        mActivityDetectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onDriveDetection();
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(mActivityDetectionReceiver,
                new IntentFilter(ACTIVITY_DETECTION_EVENT));

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(ActivityRecognition.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                    mGoogleApiClient,
                    getActivityDetectionPendingIntent()
            ).setResultCallback(this);

            mGoogleApiClient.disconnect();
        }

        if (mActivityDetectionReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mActivityDetectionReceiver);
        }
    }

    public void onDriveDetection() {
        Log.d(TAG, "onDriveDetection");
        Intent intent = new Intent(this, TripActivity.class);
        intent.putExtra(TripActivity.IS_FROM_AUTOSTART_EXTRA, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Intent we send for each activity detection
    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivityUpdateIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Callback from requestActivityUpdates()
    @Override
    public void onResult(Status status) {
        Log.d(TAG, "onResult");
    }

    // Callbacks from Google Client
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Connected to GoogleApiClient");
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient,
                0,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    public static class ActivityUpdateIntentService extends IntentService {

        private static final String TAG = "ActivityUpdateIntentService";

        public ActivityUpdateIntentService() {
            super(TAG);
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(TAG, "onHandleIntent");
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

            Log.d(TAG, result.getMostProbableActivity().toString());

            if (result.getMostProbableActivity().getType() == DetectedActivity.IN_VEHICLE &&
                    result.getMostProbableActivity().getConfidence() >= Knobs.AUTOSTART_IN_VEHICLE_MIN_CONFIDENCE) {
                Intent localIntent = new Intent(ACTIVITY_DETECTION_EVENT);
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }
        }
    }
}
