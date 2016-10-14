package com.ridecam.model;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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

    public static class SaveCommand {

        private DatabaseReference mDatabase;
        private Trip mTrip;

        public SaveCommand(Trip trip) {
            mDatabase = FirebaseDatabase.getInstance().getReference();
            mTrip = trip;
        }

        public void run() {
            Map<String, Object> tripMap = new HashMap<>();
            tripMap.put("t_start", mTrip.getStartTimestamp());
            tripMap.put("t_end", mTrip.getEndTimestamp());

            mDatabase.child("/trips/" + mTrip.getId()).setValue(tripMap);

            DatabaseReference locationRef = mDatabase.child("/locations/" + mTrip.getId());
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
}
