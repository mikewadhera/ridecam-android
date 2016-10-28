package com.ridecam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.ridecam.wifi.Utils;

public class WifiStateChangedReceiver extends BroadcastReceiver {

    private final static String TAG = WifiStateChangedReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "onReceive");

        if (Utils.isConnectedToWifi(context)) {
            context.startService(new Intent(context, UploadService.class));
        } else {
            Intent stopIntent = new Intent(context, UploadService.class);
            stopIntent.setAction(UploadService.ACTION_PAUSE_ALL_TASKS);
            context.startService(stopIntent);
        }
    }

}
