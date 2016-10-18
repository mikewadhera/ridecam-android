package com.ridecam;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;
import com.ridecam.model.Trip;

import java.io.File;
import java.io.IOException;

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

        setContentView(R.layout.activity_summary);

        Intent intent = getIntent();
        mTripId = intent.getStringExtra(TRIP_ID_EXTRA);

        mMediaPlayer = new MediaPlayer();
        mSurfaceView = (SurfaceView)findViewById(R.id.summary_preview);
        mSurfaceView.getHolder().addCallback(this);

        DB.LoadSimpleTrip loadSimpleTrip = new DB.LoadSimpleTrip(mTripId);
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
            case R.id.action_done:
                EditText editView = (EditText)findViewById(R.id.trip_name);
                DB.UpdateTripName updateTripName = new DB.UpdateTripName(mTripId, editView.getText().toString());
                updateTripName.run();
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

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
