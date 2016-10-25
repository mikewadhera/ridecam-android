package com.ridecam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PowerConnectReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerConnectReceiver";

    public void onReceive(Context context , Intent intent) {
        Log.d(TAG, "onReceive");
        String action = intent.getAction();

        Intent autoStartTripActivityService = new Intent(context, AutoStartService.class);
        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            context.startService(autoStartTripActivityService);
        } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            context.stopService(autoStartTripActivityService);
        }
    }

}
