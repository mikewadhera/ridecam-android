package com.ridecam;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class TripSummaryActivity extends Activity {

    private static final String TAG = "TripSummaryActivity";

    public static final String TRIP_ID_EXTRA = "com.ridecam.TripSummaryActivity.TRIP_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();


    }

}
