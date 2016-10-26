package com.ridecam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiStateChangedReceiver extends BroadcastReceiver {

    private final static String TAG = WifiStateChangedReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "onReceive");
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnectedToWifi = activeNetwork != null &&
                                        activeNetwork.isConnectedOrConnecting() &&
                                            activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        if (isConnectedToWifi) {
            context.stopService(new Intent(context, PauseUploadsService.class));
            context.startService(new Intent(context, ResumeUploadsService.class));
        } else {
            context.stopService(new Intent(context, ResumeUploadsService.class));
            context.startService(new Intent(context, PauseUploadsService.class));
        }
    }

}
