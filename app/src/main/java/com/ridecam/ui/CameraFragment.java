package com.ridecam.ui;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;

import com.github.vignesh_iopex.confirmdialog.Confirm;
import com.github.vignesh_iopex.confirmdialog.Dialog;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.ridecam.Copy;
import com.ridecam.R;
import com.ridecam.TripActivity;
import com.ridecam.TripService;
import com.ridecam.WeekViewActivity;
import com.ridecam.av.CameraEngine;

public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";

    public static final String RERENDER_EVENT = "rerenderEvent";

    public static SurfaceTexture sCachedSurfaceTexture;
    public static TextureView.SurfaceTextureListener sTextureViewListener;
    public static int sTextureViewWidth;
    public static int sTextureViewHeight;

    private View mRootView;
    private BroadcastReceiver mReRenderReceiver;
    private FirebaseAnalytics mAnalytics;

    public CameraFragment() {
    }

    public static CameraFragment factory() {
        CameraFragment cameraFragment = new CameraFragment();
        return cameraFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mAnalytics = FirebaseAnalytics.getInstance(getActivity());

        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // Starting up or resuming after destroyed (power button pushed or killed by OS)

        // We don't start the service here as we may not have camera permissions yet
        // Not to mention there is nothing interesting the service
        // would be interested about at this point
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_trip, container, false);

        return mRootView;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        // Construct a local broadcast receiver that listens for re-render events from the service
        mReRenderReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                render();
            }
        };

        // Register for re-render events from service
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mReRenderReceiver, new IntentFilter(RERENDER_EVENT));
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        // Starting up or resuming after onPause()

        if (!hasPermissions(getActivity())) {

            pauseToRequestPermissionsDialog(getActivity());

        } else {

            addListeners();

            if (sCachedSurfaceTexture == null) {

                TextureView textureView = (TextureView) mRootView.findViewById(R.id.camera_preview);

                sTextureViewListener = new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int surfaceWidth, int surfaceHeight) {
                        sCachedSurfaceTexture = surfaceTexture;
                        sTextureViewWidth = surfaceWidth;
                        sTextureViewHeight = surfaceHeight;
                        adjustLayoutForCameraEngine();
                        Intent intent = new Intent(getActivity(), TripService.class);
                        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONRESUME);
                        getActivity().startService(intent);
                        render();
                        resumeAutoStartStop();
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                        // return "false" so that TextureView does not release cached SurfaceTexture
                        return (surfaceTexture != sCachedSurfaceTexture);
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {}

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
                };
                textureView.setSurfaceTextureListener(sTextureViewListener);
                textureView.setVisibility(View.VISIBLE);

            } else {

                TextureView textureView = (TextureView) mRootView.findViewById(R.id.camera_preview);
                textureView.setVisibility(View.VISIBLE);

                textureView.setSurfaceTextureListener(sTextureViewListener);

                if (textureView.getSurfaceTexture() != sCachedSurfaceTexture) {
                    try {
                        textureView.setSurfaceTexture(sCachedSurfaceTexture);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // TODO add logging
                    }

                }

                adjustLayoutForCameraEngine();
                Intent intent = new Intent(getActivity(), TripService.class);
                intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONRESUME);
                getActivity().startService(intent);
                render();
                resumeAutoStartStop();

            }
        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();

        // Stopping from pushing home button, starting another activity or dialog

        Intent intent = new Intent(getActivity(), TripService.class);
        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ACTIVITY_ONSTOP);
        getActivity().startService(intent);

        // Unregister for re-render events from service
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mReRenderReceiver);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    public void addListeners() {
        Button recordButton = (Button)mRootView.findViewById(R.id.record_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAnalytics.logEvent("MANUAL_RECORD_TOGGLE", null);
                toggleRecording(false);
            }
        });

        Button listButton = (Button)mRootView.findViewById(R.id.list_button);
        listButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TripActivity tripActivity = (TripActivity)getActivity();

                Intent intent = new Intent(getActivity(), WeekViewActivity.class);
                startActivity(intent);
            }
        });
    }

    public void toggleRecording(final boolean confirmStop) {
        if (hasPermissions(getActivity())) {
            Intent checkInProgressIntent = new Intent(getActivity(), TripService.class);
            checkInProgressIntent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
            checkInProgressIntent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    boolean isInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                    if (isInProgress && confirmStop) {
                        Confirm.using(getActivity()).ask(Copy.RIDE_STOP_CONFIRM).onPositive("YES", new Dialog.OnClickListener() {
                            @Override public void onClick(final Dialog dialog, int which) {
                                final Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        toggleRecording(false);
                                    }
                                }, 300); // HACK: wait for dialog to dismiss
                            }}).onNegative("NO",  null).build().show();
                    } else {
                        Intent intent = new Intent(getActivity(), TripService.class);
                        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_TOGGLE_TRIP);
                        getActivity().startService(intent);
                    }
                }
            });
            getActivity().startService(checkInProgressIntent);
        } else {
            // TODO add logging
        }
    }

    public void render() {
        final RecordButton buttonView = (RecordButton)mRootView.findViewById(R.id.record_button);
        final View previewView = mRootView.findViewById(R.id.record_frame);
        final View recordMessageView = mRootView.findViewById(R.id.record_message);

        Intent intent = new Intent(getActivity(), TripService.class);
        intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
        intent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                buttonView.setVisibility(View.VISIBLE);
                boolean isTripInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                if (isTripInProgress) {
                    previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame_on));
                    buttonView.setBackground(getResources().getDrawable(R.drawable.start_button_on));
                    buttonView.setText(Copy.RIDE_END);
                    Drawable icon = new IconicsDrawable(getActivity()).icon(GoogleMaterial.Icon.gmd_videocam_off).color(Color.WHITE).sizeDp(24);
                    buttonView.setIcon(icon);
                    recordMessageView.setVisibility(View.VISIBLE);
                } else {
                    previewView.setBackgroundDrawable(getResources().getDrawable(R.drawable.record_frame));
                    buttonView.setBackground(getResources().getDrawable(R.drawable.start_button));
                    buttonView.setText(Copy.RIDE_START);
                    Drawable icon = new IconicsDrawable(getActivity()).icon(GoogleMaterial.Icon.gmd_videocam).color(Color.WHITE).sizeDp(24);
                    buttonView.setIcon(icon);
                    recordMessageView.setVisibility(View.GONE);
                }
            }
        });
        getActivity().startService(intent);
    }

    public void resumeAutoStartStop() {
        Intent autoIntent = getActivity().getIntent();

        if (autoIntent != null) {
            if (autoIntent.getBooleanExtra(TripActivity.IS_FROM_AUTOSTART_EXTRA, false)) {
                mAnalytics.logEvent("AUTO_RECORD_START", null);
                Intent intent = new Intent(getActivity(), TripService.class);
                intent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_ON_AUTOSTART);
                getActivity().startService(intent);
                getActivity().finish();
                return;
            } else if (autoIntent.getBooleanExtra(TripActivity.IS_FROM_AUTOSTOP_EXTRA, false)) {
                mAnalytics.logEvent("AUTO_RECORD_STOP", null);
                // Confirm before automatically stopping
                toggleRecording(false);
            }
            getActivity().setIntent(null);
        }
    }

    private void adjustLayoutForCameraEngine() {
        if (CameraEngine.usingSamsungDualCamera()) {
            // Manually rotate preview texture
            int width = sTextureViewWidth;
            int height = sTextureViewHeight;
            Matrix matrix = new Matrix();
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
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
            TextureView textureView = (TextureView) mRootView.findViewById(R.id.camera_preview);
            textureView.setTransform(matrix);
        }
    }

    public boolean hasPermissions(Context context) {
        boolean hasCam =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasGps =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        return hasCam && hasGps;
    }

    public void pauseToRequestPermissionsDialog(Activity activity) {
        // Show the permissions dialog -- we'll be sent to onPause()

        ActivityCompat.requestPermissions(activity,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                1);
    }

}