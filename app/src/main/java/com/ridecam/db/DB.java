package com.ridecam.db;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ridecam.model.Trip;

import java.util.HashMap;
import java.util.Map;

public abstract class DB {

    protected DatabaseReference mTripsRef;
    protected DatabaseReference mLocationsRef;

    public DB() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        mTripsRef = rootRef.child("trips");
        mLocationsRef = rootRef.child("locations");
    }

    public static class Save extends DB {

        private Trip mTrip;

        public Save(Trip trip) {
            super();
            mTrip = trip;
        }

        public void run() {
            Map<String, Object> tripMap = new HashMap<>();
            tripMap.put("t_start", mTrip.getStartTimestamp());
            tripMap.put("t_end", mTrip.getEndTimestamp());

            mTripsRef.child(mTrip.getId()).setValue(tripMap);

            DatabaseReference locationRef = mLocationsRef.child(mTrip.getId());
            Map<String, Object> coordinateMap;
            for (Trip.Coordinate c : mTrip.getCoordinates()) {
                coordinateMap = new HashMap<>();
                coordinateMap.put("x", c.latitude);
                coordinateMap.put("y", c.longitude);
                coordinateMap.put("z", c.timestamp);
                coordinateMap.put("w", c.bearing);
                coordinateMap.put("a", c.title);
                locationRef.push().setValue(coordinateMap);
            }
        }

    }

    public static class IsRecordingComplete extends DB {

        public interface ResultListener {
            public void onResult(boolean isRecordingComplete);
        }

        private String mId;

        public IsRecordingComplete(String id) {
            super();
            mId = id;
        }

        public void runAsync(final ResultListener resultListener) {
            mTripsRef.child(mId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    boolean recordingComplete;
                    if (dataSnapshot == null) {
                        recordingComplete = false;
                    } else {
                        recordingComplete = dataSnapshot.hasChild("t_end");
                    }
                    resultListener.onResult(recordingComplete);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {}
            });
        }

    }

    public static class SaveTripVideoDownloadURL extends DB {

        private String mId;
        private String mDownloadUrl;

        public SaveTripVideoDownloadURL(String id, String downloadUrl) {
            super();
            mId = id;
            mDownloadUrl = downloadUrl;
        }

        public void run() {
            mTripsRef.child(mId).child("h264_video_url").setValue(mDownloadUrl);
        }

    }

}
