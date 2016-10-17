package com.ridecam.model;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ridecam.geo.Utils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Trip {

    public static class Coordinate {
        public double latitude;
        public double longitude;
        public long timestamp;
        public float bearing;
        public String title;
    }

    private String id;
    private long startTimestamp;
    private long endTimestamp;
    private List<Coordinate> coordinates;
    private String videoUrl;

    public static String allocateId() {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        return db.child("trips").push().getKey();
    }

    public Trip(String id) {
        this.id = id;
        this.coordinates = new LinkedList<Coordinate>();
    }

    public String getId() {
        return id;
    }

    public void addCoordinate(Coordinate coordinate) {
        this.coordinates.add(coordinate);
    }

    public List<Coordinate> getCoordinates() { return this.coordinates; }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public long getDistanceInMiles() {
        double total = 0;
        Coordinate lastCoordinate = null;
        for (Coordinate c : getCoordinates()) {
            double distance = 0;
            if (lastCoordinate != null) {
                distance = Utils.distance(c.latitude, c.longitude, lastCoordinate.latitude, lastCoordinate.longitude);
            }
            total += distance;
            lastCoordinate = c;
        }
        return Math.round(total);
    }

    public abstract static class DBCommand {

        protected DatabaseReference mTripsRef;
        protected DatabaseReference mLocationsRef;

        public DBCommand() {
            DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
            mTripsRef = rootRef.child("trips");
            mLocationsRef = rootRef.child("locations");
        }

    }

    public static class SaveCommand extends DBCommand {

        private Trip mTrip;

        public SaveCommand(Trip trip) {
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
            for (Coordinate c : mTrip.getCoordinates()) {
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

    public static class IsRecordingCompleteCommand extends DBCommand {

        public interface ResultListener {
            public void onResult(boolean isRecordingComplete);
        }

        private String mId;

        public IsRecordingCompleteCommand(String id) {
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

    public static class SaveTripVideoDownloadURL extends DBCommand {

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
