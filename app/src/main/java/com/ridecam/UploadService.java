package com.ridecam;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.ridecam.auth.AuthUtils;
import com.ridecam.db.DB;
import com.ridecam.fs.FSUtils;
import com.ridecam.model.Trip;
import com.ridecam.wifi.Utils;

import java.io.File;
import java.util.List;

/**

 Thanks to the FirebaseStorage client UploadService is a relatively simple service

 State:

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

    5. mNumTasks
        Counter for in-flight active tasks. When it drops to 0 we stop service

 **/

public class UploadService extends Service {

    public static final String TAG = UploadService.class.getSimpleName();

    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_PAUSE_ALL_TASKS = "ACTION_PAUSE_ALL_TASKS";

    interface FileListener {
        void onDequeue(String tripId, File file, boolean isStarred);
    }

    private StorageReference mStorageRef;
    private FirebaseAuth mAuth;
    private SharedPreferences mSharedPrefs;
    private int mNumTasks = 0;

    public UploadService() {
        mStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl(Knobs.VIDEO_UPLOADS_BUCKET).child("videos");
        mAuth = FirebaseAuth.getInstance();
    }

    public void taskStarted() {
        changeNumberOfTasks(1);
    }

    public void taskCompletedOrFailedOrPaused() {
        changeNumberOfTasks(-1);
    }

    private synchronized void changeNumberOfTasks(int delta) {
        Log.d(TAG, "changeNumberOfTasks:" + mNumTasks + ":" + delta);
        mNumTasks += delta;

        // If there are no tasks left, stop the service
        if (mNumTasks <= 0) {
            Log.d(TAG, "stopping");
            stopForeground(true);
            stopSelf();
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        if (!Utils.isConnectedToWifi(this)) {
            Log.d(TAG, "Not connected to Wifi... shutting down");
            stopSelf();
            return;
        }

        mSharedPrefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);

        dequeue(new FileListener() {
            @Override
            public void onDequeue(final String tripId, final File file, final boolean isStarred) {

                final String sessionIdKey = tripId + "-upload-session-uri";
                StorageReference remoteFileRef = mStorageRef.child(file.getName());
                Uri localFileRef = Uri.fromFile(file);

                Log.d(TAG, "Starting upload task for trip: " + tripId);

                final UploadTask uploadTask;
                String resumedSessionId = mSharedPrefs.getString(sessionIdKey, null);
                if (resumedSessionId != null) {
                    Log.d(TAG, "Previous session detected, resuming upload");
                    Uri resumedSessionUri = Uri.parse(resumedSessionId);
                    uploadTask = remoteFileRef.putFile(localFileRef, new StorageMetadata.Builder().build(), resumedSessionUri);
                } else {
                    Log.d(TAG, "No previous session detected");
                    uploadTask = remoteFileRef.putFile(localFileRef);
                }

                taskStarted();

                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // TODO add logging
                        Log.e(TAG, "onFailure");
                        Log.d(TAG, "!!! Upload failed for trip: " + tripId);
                        e.printStackTrace();

                        // Forget session ID in case this was from resuming a timed out session
                        mSharedPrefs.edit().remove(sessionIdKey).commit();

                        taskCompletedOrFailedOrPaused();
                    }
                });

                uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                        // Listen for session ID
                        Uri sessionUri = taskSnapshot.getUploadSessionUri();
                        if (sessionUri != null && mSharedPrefs.getString(sessionIdKey, null) == null) {
                            Log.d(TAG, "Storing session ID in case app restarts or all tasks become paused from loss of wifi");
                            String sessionId = sessionUri.toString();
                            mSharedPrefs.edit().putString(sessionIdKey, sessionId).commit();
                            Log.d(TAG, "Session ID stored for trip: " + tripId);
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
                                new DB.SaveTripVideoDownloadURL(AuthUtils.getUserId(UploadService.this), tripId, downloadUrl);
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

                        taskCompletedOrFailedOrPaused();
                    }
                });

            }
        });

        showUploadProgressNotification();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand:" + intent + ":" + startId);

        if (intent == null) {
            Log.e(TAG, "Cannot start service with null intent");
            return START_NOT_STICKY;
        }

        if (ACTION_PAUSE_ALL_TASKS.equals(intent.getAction())) {

            // Best effort pause
            if (mStorageRef.getActiveUploadTasks().size() > 0) {
                final Intent checkInProgressIntent = new Intent(this, TripService.class);
                checkInProgressIntent.putExtra(TripService.START_SERVICE_COMMAND, TripService.COMMAND_IS_TRIP_IN_PROGRESS);
                checkInProgressIntent.putExtra(TripService.RESULT_RECEIVER, new ResultReceiver(new Handler()) {
                    @Override
                    protected void onReceiveResult(int code, Bundle data) {
                        boolean isInProgress = data.getBoolean(TripService.RESULT_IS_TRIP_IN_PROGRESS);
                        if (!isInProgress) {
                            android.os.Process.killProcess(android.os.Process.myPid());
                        } else {
                            // TODO add logging
                            Log.d(TAG, "Recording in progress. Not killing uploads");
                        }
                    }
                });
                startService(checkInProgressIntent);
            }

        }

        return START_STICKY;
    }

    // Calls fileListener for every file in videos directory in oldest-first order
    // that is neither recording, already uploaded or actively resumed upload (not paused)
    public void dequeue(final FileListener fileListener) {

        final File[] files = FSUtils.getVideoFilesOldestFirst(this);

        for (final File file : files) {

            final String tripId = FSUtils.getBasename(file);

            if (!hasUploadAlreadyResumed(tripId)) {
                DB.LoadSimpleTrip tripLoader = new DB.LoadSimpleTrip(AuthUtils.getUserId(UploadService.this), tripId);

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
            return tripUploads.get(0).isInProgress();
        } else {
            return false;
        }
    }

    private void showUploadProgressNotification() {
        android.support.v4.app.NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Uploading...")
                .setSmallIcon(R.drawable.cast_ic_notification_small_icon)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setAutoCancel(false);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
