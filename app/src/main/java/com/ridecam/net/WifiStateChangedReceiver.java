package com.ridecam.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.ridecam.UploadService;

public class WifiStateChangedReceiver extends BroadcastReceiver {

    private final static String TAG = WifiStateChangedReceiver.class.getSimpleName();
    private FirebaseAnalytics mAnalytics;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "onReceive");

        mAnalytics = FirebaseAnalytics.getInstance(context);

        if (Utils.isConnectedToWifi(context)) {
            mAnalytics.logEvent("WIFI_CONNECTED", null);
            context.startService(new Intent(context, UploadService.class));
        } else {
            mAnalytics.logEvent("WIFI_DISCONNECTED", null);
            Intent stopIntent = new Intent(context, UploadService.class);
            stopIntent.setAction(UploadService.ACTION_PAUSE);
            context.startService(stopIntent);
        }
    }

}
