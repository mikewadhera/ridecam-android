package com.ridecam;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.ridecam.av.AVUtils;
import com.ridecam.av.CameraEngine;

public class TripActivity extends AppCompatActivity {

    private static final String TAG = "TripActivity";

    public static final String RERENDER_EVENT = "rerenderEvent";

    public static SurfaceTexture sCachedSurfaceTexture;
    public static TextureView.SurfaceTextureListener sTextureViewListener;
    public static int sTextureViewWidth;
    public static int sTextureViewHeight;

    private BroadcastReceiver mReRenderReceiver;
    private boolean mToggleLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // Starting up or resuming after destroyed (power button pushed or killed by OS)

        // We don't start the service here as we may not have camera permissions yet
        // Not to mention there is nothing interesting the service
        // would be interested about at this point

        // Construct a local broadcast receiver that listens for re-render events from the service
        mReRenderReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                render();
            }
        };

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        loadLayout();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        // Register for re-render events from service
        LocalBroadcastManager.getInstance(this).registerReceiver(mReRenderReceiver, new IntentFilter(RERENDER_EVENT));
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        // Starting up or resuming after onPause()

        if (!hasPermissions()) {

            pauseToRequestPermissionsDialog();

        } else {

            if (sCachedSurfaceTexture == null) {

                // Important: we reload layout to make sure onSurfaceTextureAvailable()
                // is called back when resuming from permissions dialog
                loadLayout();

                TextureView textureView = (TextureView) findViewById(R.id.camera_preview);

                sTextureViewListener = new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int surfaceWidth, int surfaceHeight) {
                        sCachedSurfaceTexture = surfaceTexture;
                        sTextureViewWidth = surfaceWidth;
                        sTextureViewHeight = surfaceHeight;
                        manuallyRotatePreviewIfNeeded();
                        Intent intent = new Intent(TripActivity.this, TripService.class);
                        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONRESUME);
                        startService(intent);
                        render();
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                        // return "false" so that TextureView does not release cached SurfaceTexture
                        return (surfaceTexture != TripActivity.sCachedSurfaceTexture);
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {}

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
                };
                textureView.setSurfaceTextureListener(sTextureViewListener);

            } else {

                TextureView textureView = (TextureView) findViewById(R.id.camera_preview);

                textureView.setSurfaceTextureListener(sTextureViewListener);

                if (textureView.getSurfaceTexture() != sCachedSurfaceTexture) {
                    try {
                        textureView.setSurfaceTexture(sCachedSurfaceTexture);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // TODO add logging
                    }

                }

                manuallyRotatePreviewIfNeeded();
                Intent intent = new Intent(this, TripService.class);
                intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONRESUME);
                startService(intent);
                render();

            }
        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();

        // Stopping from pushing home button, starting another activity or dialog

        Intent intent = new Intent(this, TripService.class);
        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONSTOP);
        startService(intent);

        // Unregister for re-render events from service
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReRenderReceiver);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    public void loadLayout() {
        setContentView(R.layout.fragment_camera);

        Button recordButton = (Button)findViewById(R.id.record_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecording();
            }
        });
    }

    public void toggleRecording() {
        if (hasPermissions()) {
            if (mToggleLock) return;
            mToggleLock = true;
            Intent intent = new Intent(TripActivity.this, TripService.class);
            intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_TOGGLE_TRIP);
            intent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    mToggleLock = false;
                }
            });
            startService(intent);
        } else {
            // TODO add logging
        }
    }

    public void render() {
        if (!hasPermissions()) {
            return;
        }

        final Button buttonView = (Button)findViewById(R.id.record_button);
        final View previewView = findViewById(R.id.record_frame);

        Intent intent = new Intent(this, TripService.class);
        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
        intent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                boolean isTripInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                if (isTripInProgress) {
                    previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame_on));
                    buttonView.setText("FINISH");
                } else {
                    previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame));
                    buttonView.setText("START");
                }
            }
        });
        startService(intent);

    }

    private void manuallyRotatePreviewIfNeeded() {
        if (CameraEngine.usingSamsungDualCamera()) {
            int width = sTextureViewWidth;
            int height = sTextureViewHeight;
            Matrix matrix = new Matrix();
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            RectF textureRectF = new RectF(0, 0, width, height);
            RectF previewRectF = new RectF(0, 0, height, width);
            float centerX = textureRectF.centerX();
            float centerY = textureRectF.centerY();
            //if(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(centerX - previewRectF.centerX(),
                    centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)width / width,
                    (float)height / height);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90, centerX, centerY);
            //}
            TextureView textureView = (TextureView) findViewById(R.id.camera_preview);
            textureView.setTransform(matrix);
        }
    }

    public boolean hasPermissions() {
        boolean hasCam =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasGps =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        return hasCam && hasGps;
    }

    public void pauseToRequestPermissionsDialog() {
        // Show the permissions dialog -- we'll be sent to onPause()

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                1);
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


}