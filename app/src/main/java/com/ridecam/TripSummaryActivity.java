package com.ridecam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.LayoutInflaterCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.mikepenz.iconics.context.IconicsLayoutInflater;
import com.ridecam.auth.AuthUtils;
import com.ridecam.auth.Installation;
import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;
import com.ridecam.model.Trip;

import java.io.File;
import java.io.IOException;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class TripSummaryActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "TripSummaryActivity";

    public static final String TRIP_ID_EXTRA = "com.ridecam.TripSummaryActivity.TRIP_ID";

    private String mTripId;
    private SurfaceView mSurfaceView;
    private MediaPlayer mMediaPlayer;
    private boolean mHasStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_summary);

        Intent intent = getIntent();
        mTripId = intent.getStringExtra(TRIP_ID_EXTRA);

        mMediaPlayer = new MediaPlayer();
        mSurfaceView = (SurfaceView)findViewById(R.id.summary_preview);
        mSurfaceView.getHolder().addCallback(this);

        Button doneButton = (Button)findViewById(R.id.done_button);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onDone();
            }
        });

        DB.LoadSimpleTrip loadSimpleTrip = new DB.LoadSimpleTrip(AuthUtils.getUserId(this), mTripId);
        loadSimpleTrip.runAsync(new DB.LoadSimpleTrip.ResultListener() {
            @Override
            public void onResult(Trip trip) {
                EditText editView = (EditText)findViewById(R.id.trip_name);
                editView.setText(trip.getName());
                editView.setSelection(trip.getName().length());

                TextView milesView = (TextView)findViewById(R.id.miles_circle);
                milesView.setText(String.valueOf(trip.getMiles()));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_summary_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

    }

    public void onDone() {
        EditText editView = (EditText)findViewById(R.id.trip_name);
        DB.UpdateTripName updateTripName = new DB.UpdateTripName(AuthUtils.getUserId(this), mTripId, editView.getText().toString());
        updateTripName.run();
        Intent intent = new Intent(this, TripActivity.class);
        intent.putExtra(TripActivity.FOCUS_TRIPS_EXTRA, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        try {
            if (!mHasStarted) {
                mHasStarted = true;
                File videoFile = FSUtils.getVideoFile(this, mTripId);
                mMediaPlayer.setDataSource(this, Uri.fromFile(videoFile));
                mMediaPlayer.prepare();
            }

            mMediaPlayer.setDisplay(holder);
            mMediaPlayer.setLooping(true);
            mMediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mMediaPlayer.stop();
    }


}
