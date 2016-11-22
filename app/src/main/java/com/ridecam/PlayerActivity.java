package com.ridecam;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.LayoutInflaterCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.github.vignesh_iopex.confirmdialog.Confirm;
import com.github.vignesh_iopex.confirmdialog.Dialog;
import com.mikepenz.iconics.context.IconicsLayoutInflater;
import com.ridecam.auth.AuthUtils;
import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static com.ridecam.TripSummaryActivity.TRIP_ID_EXTRA;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";

    public static final String TRIP_ID_EXTRA = "com.ridecam.PlayerActivity.TRIP_ID_EXTRA";
    public static final String TRIP_VIDEO_URL_EXTRA = "com.ridecam.PlayerActivity.TRIP_VIDEO_URL_EXTRA";
    public static final String TRIP_START_EXTRA = "TRIP_START_EXTRA";

    static final long REPAINT_INTERVAL_MS = 500;

    String mTripId;
    VideoView mVideoView;
    ProgressBar mProgressBar;
    Button mBackButton;
    Button mDeleteButton;
    long mTripStart;
    Handler mRepaintHandler;
    Timer mRepaintTimer;
    SimpleDateFormat mTimestampFormat;
    TextView mTimestampView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        LayoutInflaterCompat.setFactory(getLayoutInflater(), new IconicsLayoutInflater(getDelegate()));
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_player);

        Intent intent = getIntent();
        mTripId = intent.getStringExtra(TRIP_ID_EXTRA);
        String videoUrl = intent.getStringExtra(TRIP_VIDEO_URL_EXTRA);
        mTripStart = intent.getLongExtra(TRIP_START_EXTRA, 0);

        mVideoView = (VideoView) findViewById(R.id.video_view);
        mProgressBar = (ProgressBar) findViewById(R.id.progressbar);

        try {
            MediaController mediacontroller = new MediaController(this);
            mediacontroller.setAnchorView(mVideoView);
            Uri video = Uri.parse(videoUrl);
            mVideoView.setMediaController(mediacontroller);
            mVideoView.setVideoURI(video);

        } catch (Exception e) {
            Log.e(TAG, "Video failure");
            e.printStackTrace();
        }

        mVideoView.requestFocus();

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                mBackButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                mVideoView.start();
            }
        });

        mBackButton = (Button)findViewById(R.id.back_arrow);
        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        mDeleteButton = (Button)findViewById(R.id.delete_button);
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                delete();
            }
        });

        mTimestampView = (TextView)findViewById(R.id.timestamp);
        mTimestampFormat = new SimpleDateFormat("h:mm a");
        mRepaintHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                repaint();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        mProgressBar.setVisibility(View.VISIBLE);

        mRepaintTimer = new Timer();
        mRepaintTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mRepaintHandler.sendEmptyMessage(0);
            }
        }, 0, REPAINT_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mRepaintTimer.cancel();
    }

    private void delete() {
        Confirm.using(this).ask("Delete?").onPositive("YES", new Dialog.OnClickListener() {
            @Override public void onClick(final Dialog dialog, int which) {
                DB.DeleteTrip deleteTrip = new DB.DeleteTrip(AuthUtils.getUserId(PlayerActivity.this), mTripId);
                deleteTrip.runAsync(new DB.DeleteTrip.ResultListener() {
                    @Override
                    public void onResult() {
                        File localFile = FSUtils.getVideoFile(PlayerActivity.this, mTripId);
                        FSUtils.deleteFile(localFile);
                        finish();
                    }
                });
            }}).onNegative("NO",  null).build().show();
    }

    private void repaint() {
        Date date = new Date(mTripStart + mVideoView.getCurrentPosition());
        mTimestampView.setText(mTimestampFormat.format(date));
    }

}
