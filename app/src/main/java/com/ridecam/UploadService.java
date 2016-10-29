package com.ridecam;

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
import com.ridecam.net.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**

 Thanks to the FirebaseStorage client UploadService is a relatively simple service

 State:

    1. Filesystem
        We treat FS as LIFO queue of new uploads since we remove files after uploads complete

    2. FirebaseStorage
        getActiveUploadTasks() holds all in-flight uploads

    3. FirebaseDB
        Need to check Trip complete (so we don't upload video file mid-recording)
        We also store the download URL in the Trip model when upload finishes

    4. SharedPreferences
        We store session ID here to resume uploads across restarts

 **/

public class UploadService extends Service {

    public static final String TAG = UploadService.class.getSimpleName();

    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_PAUSE = "ACTION_PAUSE";

    private StorageReference mStorageRef;
    private SharedPreferences mSharedPrefs;

    public UploadService() {
        mStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl(Knobs.VIDEO_UPLOADS_BUCKET).child("videos");
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

        showUploadProgressNotification();

        dequeueUploadQueue();
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

        if (ACTION_PAUSE.equals(intent.getAction())) {
            pauseUploads();
        }

        return START_STICKY;
    }

    // Calls onDequeueUploadQueue() with the latest completed recording in videos directory not uploaded/uploading
    public void dequeueUploadQueue() {

        final List<String> filePaths = FSUtils.getVideoFileAbsolutePathsAscendingByName(this);

        if (filePaths.size() == 0) {
            Log.d(TAG, "No videos files");
            return;
        }

        String startTripId = FSUtils.getBasename(filePaths.get(0));
        String endTripId = FSUtils.getBasename(filePaths.get(filePaths.size()-1));

        Log.d(TAG, "Loading trips: " + startTripId + " (start) " + endTripId + " (end)");

        DB.SimpleTripRangeQuery tripsLoader = new DB.SimpleTripRangeQuery(AuthUtils.getUserId(UploadService.this), startTripId, endTripId);
        tripsLoader.runAsync(new DB.SimpleTripRangeQuery.ResultListener() {
            @Override
            public void onResult(List<Trip> trips) {
                ArrayList<Trip> tripsQueue = new ArrayList<>();
                for (Trip trip : trips) {
                    if (trip != null && trip.isValid()) {
                        if (!trip.isRecording()) {
                            if (!hasAlreadyDequed(trip)) {
                                Log.d(TAG, "Adding trip to upload queue: " + trip.getId());
                                tripsQueue.add(trip);
                            } else {
                                Log.d(TAG, "Skipping already uploaded/uploading trip: " + trip.getId());
                            }
                        } else {
                            Log.d(TAG, "Skipping currently recording trip: " + trip.getId());
                        }
                    } else {
                        Log.e(TAG, "Skipping invalid trip: " + trip.getId());
                        // TODO add logging
                    }
                }
                if (tripsQueue.size() > 0) {
                    Trip dequeuedTrip = tripsQueue.get(tripsQueue.size()-1); // LIFO
                    try {
                        Log.d(TAG, "Dequeing next trip for upload: " + dequeuedTrip.getId());
                        onDequeueUploadQueue(dequeuedTrip);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // TODO add logging
                    }
                } else {
                    Log.d(TAG, "No more trips in queue, shutting down");
                    stopForeground(true);
                    stopSelf();
                }
            }
        });

    }

    public void onDequeueUploadQueue(final Trip trip) {
        Log.d(TAG, "Starting upload for trip: " + trip.getId());
        final UploadTask uploadTask;

        final File file = trip.getLocalFile(this);
        StorageReference remoteFileRef = mStorageRef.child(file.getName());
        Uri localFileRef = Uri.fromFile(file);

        String resumedSessionId = getStoredUploadSessionId(trip);
        if (resumedSessionId != null) {
            Log.d(TAG, "Previous session detected, resuming upload");
            Uri resumedSessionUri = Uri.parse(resumedSessionId);
            uploadTask = remoteFileRef.putFile(localFileRef, new StorageMetadata.Builder().build(), resumedSessionUri);
        } else {
            Log.d(TAG, "No previous session detected");
            uploadTask = remoteFileRef.putFile(localFileRef);
        }

        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
            // Listen for session ID
            Uri sessionUri = taskSnapshot.getUploadSessionUri();
            if (sessionUri != null && getStoredUploadSessionId(trip) == null) {
                Log.d(TAG, "Storing session ID in case app restarts or upload pauses from loss of wifi");
                String sessionId = sessionUri.toString();
                storeUploadSessionId(trip, sessionId);
                Log.d(TAG, "Session ID stored for trip: " + trip.getId());
            }
            }
        });

        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
            Log.d(TAG, "onSuccess");
            Log.d(TAG, "Upload complete for trip: " + trip.getId());

            clearUploadSessionId(trip);

            String downloadUrl = taskSnapshot.getDownloadUrl().toString();

            DB.SaveTripVideoDownloadURL saveTripVideoDownloadURL =
                    new DB.SaveTripVideoDownloadURL(AuthUtils.getUserId(UploadService.this), trip.getId(), downloadUrl);
            saveTripVideoDownloadURL.run();

            if (!trip.isStarred()) {
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

            dequeueUploadQueue();
            }
        });

        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
            // TODO add logging
            Log.e(TAG, "onFailure");
            Log.d(TAG, "!!! Upload failed for trip: " + trip.getId());
            e.printStackTrace();

            // Forget session ID in case this was from resuming a timed out session
            clearUploadSessionId(trip);

            dequeueUploadQueue();
            }
        });
    }

    public boolean hasAlreadyDequed(Trip trip) {
        return isUploadingTrip(trip.getId()) || trip.hasUploaded();
    }

    public boolean isUploadingTrip(String id) {
        List<UploadTask> tripUploads = mStorageRef.child(id).getActiveUploadTasks();
        if (tripUploads.size() > 0) {
            return true;
        } else {
            return false;
        }
    }

    public void pauseUploads() {
        // Best effort pause
        // HACK: Since cancel() and pause() are broken in Firebase
        // we kill the current process if we are not currently recording
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
                        Log.d(TAG, "Recording in progress. Not pausing uploads");
                    }
                }
            });
            startService(checkInProgressIntent);
        }
    }

    public String getStoredUploadSessionId(Trip trip) {
        return mSharedPrefs.getString(uploadSessionIdKey(trip), null);
    }

    public void storeUploadSessionId(Trip trip, String sessionId) {
        mSharedPrefs.edit().putString(uploadSessionIdKey(trip), sessionId).commit();
    }

    public void clearUploadSessionId(Trip trip) {
        mSharedPrefs.edit().remove(uploadSessionIdKey(trip)).commit();
    }

    public String uploadSessionIdKey(Trip trip) {
        return trip.getId() + "-upload-session-uri";
    }

    private void showUploadProgressNotification() {
        android.support.v4.app.NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(Copy.UPLOAD_RUNNING_NOTIFICATION)
                .setSmallIcon(R.drawable.cast_ic_notification_small_icon)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setAutoCancel(false);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
