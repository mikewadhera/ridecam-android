package com.ridecam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

public class PowerDisconnectReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerDisconnectReceiver";

    public void onReceive(final Context context , Intent intent) {
        Log.d(TAG, "onReceive");
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            // Check if we are recording, we don't want to foreground the app if not
            // NOTE: The only place we start trip service not necessarily before trip activity
            // *May* cause edge case where service was started but front-end never initialized
            final Intent checkInProgressIntent = new Intent(context, TripService.class);
            checkInProgressIntent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
            checkInProgressIntent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    boolean isInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                    if (isInProgress) {
                        Intent activity = new Intent(context, TripActivity.class);
                        activity.putExtra(TripActivity.IS_FROM_AUTOSTOP_EXTRA, true);
                        activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(activity);
                    }
                }
            });
            context.startService(checkInProgressIntent);
        }
    }

}
