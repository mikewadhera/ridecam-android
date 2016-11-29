package com.ridecam.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.ridecam.AutoStartService;
import com.ridecam.TripActivity;
import com.ridecam.TripService;

public class PowerStateChangedReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerStateChangedReceiver";
    private FirebaseAnalytics mAnalytics;

    public void onReceive(final Context context , Intent intent) {
        Log.d(TAG, "onReceive");
        mAnalytics = FirebaseAnalytics.getInstance(context);
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {

            Log.d(TAG, "Intent Action: ACTION_POWER_CONNECTED");
            mAnalytics.logEvent("POWER_CONNECTED", null);

            // Are we already recording? If so don't bring up autostart service
            final Intent checkInProgressIntent = new Intent(context, TripService.class);
            checkInProgressIntent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
            checkInProgressIntent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    boolean isInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                    if (isInProgress) {
                        Log.d(TAG, "Recording in progress - not starting autostart service");
                    } else {
                        Intent autoStartTripActivityService = new Intent(context, AutoStartService.class);
                        context.startService(autoStartTripActivityService);
                    }
                }
            });
            context.startService(checkInProgressIntent);

        } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {

            Log.d(TAG, "Intent Action: ACTION_POWER_DISCONNECTED");
            mAnalytics.logEvent("POWER_DISCONNECTED", null);

            Intent autoStartTripActivityService = new Intent(context, AutoStartService.class);
            context.stopService(autoStartTripActivityService);

            mAnalytics.logEvent("AUTO_RECORD_STOP", null);
            Intent autoStopIntent = new Intent(context, TripService.class);
            autoStopIntent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ON_AUTOSTOP);
            context.startService(autoStopIntent);
        }
    }

}
