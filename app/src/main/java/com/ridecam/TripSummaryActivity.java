package com.ridecam;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;

public class TripSummaryActivity extends AppCompatActivity {

    private static final String TAG = "TripSummaryActivity";

    public static final String TRIP_ID_EXTRA = "com.ridecam.TripSummaryActivity.TRIP_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setTitle(Copy.TRIP_SUMMARY_TITLE);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();


    }

}
