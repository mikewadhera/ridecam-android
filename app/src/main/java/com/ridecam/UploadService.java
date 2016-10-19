package com.ridecam;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.ridecam.auth.AuthUtils;
import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;

import java.io.File;

/**
 *

 Thanks to the FirebaseStorage client UploadService is a relatively simple service

 Key pieces of state:

    1. Filesystem
        We treat FS as 'inbox' of new uploads since we remove files after uploads complete

    2. FirebaseStorage
        getActiveUploadTasks() holds all in-flight uploads
        We can use getName() on a upload to see if a particular Trip ID uploading
        Since this is a global singleton we can call from an Activity to show upload state
        by attaching a scoped progress listener with task.addOnSuccessListener(activity)
        New uploads are enqueued with putFile()

    3. FirebaseDB
        Need to check Trip complete so we don't upload video file while recording
        We also store the download URL in the Trip when upload completes

    4. SharedPreferences
        We store session ID here to resume uploads across process restarts

 **/

public class UploadService extends Service {

    public static final String TAG = "UploadService";

    public static final String STORE_URL = "gs://ridecam-b2023.appspot.com";

    interface FileListener {
        public void onDequeue(File file);
    }

    private StorageReference mStorageRef;

    public UploadService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        mStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl(STORE_URL).child("videos");

        dequeue(new FileListener() {
            @Override
            public void onDequeue(final File file) {

                StorageReference remoteFileRef = mStorageRef.child(file.getName());
                Uri localFileRef = Uri.fromFile(file);
                Log.e(TAG, "Starting upload");
                UploadTask uploadTask = remoteFileRef.putFile(localFileRef);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // TODO add logging
                        Log.e(TAG, "!!! UPLOAD FAILED");
                        Log.e(TAG, exception.getMessage());
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.e(TAG, "onSuccess");
                        String tripId = FSUtils.getBasename(file);
                        String downloadUrl = taskSnapshot.getDownloadUrl().toString();

                        DB.SaveTripVideoDownloadURL saveTripVideoDownloadURL = new DB.SaveTripVideoDownloadURL(AuthUtils.getUserId(UploadService.this), tripId, downloadUrl);
                        saveTripVideoDownloadURL.run();

                        Log.e(TAG, "deleteing File");
                        try {
                            FSUtils.deleteFile(file);
                            Log.e(TAG, "file deleted");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

            }
        });

    }

    // Calls fileListener for every completed video file not uploaded in oldest-first order
    public void dequeue(final FileListener fileListener) {
        File[] files = FSUtils.getVideoFilesOldestFirst(this);
        for (final File file : files) {
            String id = FSUtils.getBasename(file);
            if (isNotUploading(id)) {
                DB.IsRecordingComplete isRecordingCompleteCommand = new DB.IsRecordingComplete(AuthUtils.getUserId(this), id);
                isRecordingCompleteCommand.runAsync(new DB.IsRecordingComplete.ResultListener() {
                    @Override
                    public void onResult(boolean isRecordingComplete) {
                        if (isRecordingComplete) {
                            fileListener.onDequeue(file);
                        }
                    }
                });

            }
        }
    }

    public boolean isNotUploading(String id) {
        return mStorageRef.child(id).getActiveUploadTasks().size() == 0;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
