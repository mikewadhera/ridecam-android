package com.ridecam;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.LayoutInflaterCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.VideoView;

import com.mikepenz.iconics.context.IconicsLayoutInflater;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";

    public static final String TRIP_ID_EXTRA = "com.ridecam.PlayerActivity.TRIP_ID_EXTRA";

    VideoView mVideoView;
    Button mBackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        LayoutInflaterCompat.setFactory(getLayoutInflater(), new IconicsLayoutInflater(getDelegate()));
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_player);

        Intent intent = getIntent();
        String videoUrl = intent.getStringExtra(TRIP_ID_EXTRA);

        mVideoView = (VideoView) findViewById(R.id.video_view);

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
    }

}
