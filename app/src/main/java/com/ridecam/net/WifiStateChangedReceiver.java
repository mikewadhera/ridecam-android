package com.ridecam.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ridecam.UploadService;

public class WifiStateChangedReceiver extends BroadcastReceiver {

    private final static String TAG = WifiStateChangedReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "onReceive");

        if (Utils.isConnectedToWifi(context)) {
            context.startService(new Intent(context, UploadService.class));
        } else {
            Intent stopIntent = new Intent(context, UploadService.class);
            stopIntent.setAction(UploadService.ACTION_PAUSE);
            context.startService(stopIntent);
        }
    }

}
