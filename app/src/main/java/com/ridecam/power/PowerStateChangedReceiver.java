package com.ridecam.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

import com.ridecam.AutoStartService;
import com.ridecam.TripActivity;
import com.ridecam.TripService;

public class PowerStateChangedReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerStateChangedReceiver";

    public void onReceive(final Context context , Intent intent) {
        Log.d(TAG, "onReceive");
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {

            Log.d(TAG, "Intent Action: ACTION_POWER_CONNECTED");
            Intent autoStartTripActivityService = new Intent(context, AutoStartService.class);
            context.startService(autoStartTripActivityService);

        } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            Log.d(TAG, "Intent Action: ACTION_POWER_DISCONNECTED");

            Intent autoStartTripActivityService = new Intent(context, AutoStartService.class);
            context.stopService(autoStartTripActivityService);

            // Only present activity / end trip dialog if currently recording
            final Intent checkInProgressIntent = new Intent(context, TripService.class);
            checkInProgressIntent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
            checkInProgressIntent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    boolean isInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                    if (isInProgress) {
                        Log.d(TAG, "Recording in progress - foregrounding");
                        Intent activity = new Intent(context, TripActivity.class);
                        activity.putExtra(TripActivity.IS_FROM_AUTOSTOP_EXTRA, true);
                        activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(activity);
                    } else {
                        Log.d(TAG, "No recording in progress");
                    }
                }
            });
            context.startService(checkInProgressIntent);
        }
    }

}
