package com.ridecam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PowerDisconnectReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerDisconnectReceiver";

    public void onReceive(Context context , Intent intent) {
        Log.d(TAG, "onReceive");
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            Intent tripService = new Intent(context, TripService.class);
            tripService.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_SYSTEM_ONDISCONNECT_POWER);
            context.startService(tripService);
        }
    }

}
