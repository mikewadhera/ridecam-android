package com.ridecam;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class PauseUploadsService extends Service {

    private final static String TAG = PauseUploadsService.class.getSimpleName();

    private StorageReference mStorageRef;

    public PauseUploadsService() {
        mStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl(Knobs.VIDEO_UPLOADS_BUCKET).child("videos");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        Log.d(TAG, "Pausing all active upload tasks");

        for (UploadTask uploadTask : mStorageRef.getActiveUploadTasks()) {
            if (uploadTask.isInProgress()) {
                Log.d(TAG, "Pausing task");
                uploadTask.pause();
            }
        }

        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
