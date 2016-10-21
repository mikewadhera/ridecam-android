package com.ridecam;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.view.LayoutInflaterCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;

import com.mikepenz.iconics.context.IconicsLayoutInflater;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class TripActivity extends AppCompatActivity {

    private static final String TAG = "TripActivity";

    public static final String IS_FROM_AUTOSTART_EXTRA = "com.ridecam.TripActivity.IS_FROM_AUTOSTART_EXTRA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        LayoutInflaterCompat.setFactory(getLayoutInflater(), new IconicsLayoutInflater(getDelegate()));
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_trip);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Success. We don't have to do anything as we should be transitioned
                    // to onResume() after the user accepts the permissions dialog


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

}