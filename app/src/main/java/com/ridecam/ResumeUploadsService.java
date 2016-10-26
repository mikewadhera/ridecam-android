package com.ridecam;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.ridecam.auth.AuthUtils;
import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;
import com.ridecam.model.Trip;

import java.io.File;
import java.util.List;

/**
 *

 Thanks to the FirebaseStorage client ResumeUploadsService is a relatively simple service

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

public class ResumeUploadsService extends Service {

    public static final String TAG = ResumeUploadsService.class.getSimpleName();

    interface FileListener {
        void onDequeue(String tripId, File file, boolean isStarred);
    }

    private StorageReference mStorageRef;

    public ResumeUploadsService() {
        mStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl(Knobs.VIDEO_UPLOADS_BUCKET).child("videos");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        dequeue(new FileListener() {
            @Override
            public void onDequeue(final String tripId, final File file, final boolean isStarred) {

                final String sessionIdKey = tripId + "-upload-session-uri";
                StorageReference remoteFileRef = mStorageRef.child(file.getName());
                Uri localFileRef = Uri.fromFile(file);

                Log.d(TAG, "Starting upload task for trip: " + tripId);

                final UploadTask uploadTask;
                SharedPreferences sharedPrefs = ResumeUploadsService.this.getSharedPreferences(TAG, Context.MODE_PRIVATE);
                String resumedSessionId = sharedPrefs.getString(sessionIdKey, null);
                if (resumedSessionId != null) {
                    Log.d(TAG, "Previous session detected, resuming upload");
                    Uri resumedSessionUri = Uri.parse(resumedSessionId);
                    uploadTask = remoteFileRef.putFile(localFileRef, new StorageMetadata.Builder().build(), resumedSessionUri);
                    if (uploadTask.isPaused()) {
                        Log.d(TAG, "Resuming active upload from paused state");
                        uploadTask.resume();
                    }
                } else {
                    Log.d(TAG, "No previous session detected, starting from byte zero");
                    uploadTask = remoteFileRef.putFile(localFileRef);
                }

                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // TODO add logging
                        Log.e(TAG, "onFailure");
                        Log.d(TAG, "!!! Upload failed for trip: " + tripId);
                        e.printStackTrace();

                        // Forget session ID in case this was from resuming a timed out session
                        SharedPreferences sharedPrefs = ResumeUploadsService.this.getSharedPreferences(TAG, Context.MODE_PRIVATE);
                        sharedPrefs.edit().remove(sessionIdKey).commit();
                    }
                });

                Log.d(TAG, "Storing session ID in case app restarts or all tasks become paused from loss of wifi");
                uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                        Uri sessionUri = taskSnapshot.getUploadSessionUri();
                        if (sessionUri != null) {
                            String sessionId = sessionUri.toString();
                            SharedPreferences sharedPrefs = ResumeUploadsService.this.getSharedPreferences(TAG, Context.MODE_PRIVATE);
                            sharedPrefs.edit().putString(sessionIdKey, sessionId).commit();
                            Log.d(TAG, "Session ID stored for trip: " + tripId);
                            uploadTask.removeOnProgressListener(this);
                        } else {
                            Log.e(TAG, "No session ID");
                        }
                    }
                });

                uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d(TAG, "onSuccess");
                        Log.d(TAG, "Upload complete for trip: " + tripId);

                        String downloadUrl = taskSnapshot.getDownloadUrl().toString();

                        DB.SaveTripVideoDownloadURL saveTripVideoDownloadURL =
                                new DB.SaveTripVideoDownloadURL(AuthUtils.getUserId(ResumeUploadsService.this), tripId, downloadUrl);
                        saveTripVideoDownloadURL.run();

                        if (!isStarred) {
                            Log.d(TAG, "Not starred. Deleting local file...");
                            try {
                                FSUtils.deleteFile(file);
                                Log.d(TAG, "File deleted");
                            } catch (Exception e) {
                                // TODO add logging
                                // bad place!
                                e.printStackTrace();
                            }
                        } else {
                            Log.d(TAG, "Starred trip. Not deleting local file");
                        }
                    }
                });

            }
        });

        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    // Calls fileListener for every file in videos directory in oldest-first order
    // that is neither recording, already uploaded or actively resumed upload (not paused)
    public void dequeue(final FileListener fileListener) {

        final File[] files = FSUtils.getVideoFilesOldestFirst(this);

        for (final File file : files) {

            final String tripId = FSUtils.getBasename(file);

            if (!hasUploadAlreadyResumed(tripId)) {
                DB.LoadSimpleTrip tripLoader =
                        new DB.LoadSimpleTrip(AuthUtils.getUserId(ResumeUploadsService.this), tripId);

                tripLoader.runAsync(new DB.LoadSimpleTrip.ResultListener() {
                    @Override
                    public void onResult(Trip trip) {
                        if (trip.isValid()) {
                            if (!trip.isRecording()) {
                                if (!trip.isUploaded()) {
                                    try {
                                        Log.d(TAG, "Dequeing next trip for upload: " + tripId);
                                        fileListener.onDequeue(tripId, file, trip.isStarred());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        // TODO add logging
                                    }
                                } else {
                                    Log.d(TAG, "Skipping completed trip: " + tripId);
                                }
                            } else {
                                Log.d(TAG, "Skipping currently recording trip: " + tripId);
                            }
                        } else {
                            Log.e(TAG, "Skipping invalid trip: " + tripId);
                            // TODO add logging
                        }
                    }
                });
            } else {
                Log.d(TAG, "Skipping already resumed trip: " + tripId);
            }

        }
    }

    public boolean hasUploadAlreadyResumed(String id) {
        List<UploadTask> tripUploads = mStorageRef.child(id).getActiveUploadTasks();
        if (tripUploads.size() > 0) {
            return tripUploads.get(0).isInProgress(); // e.g. not paused
        } else {
            return false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
