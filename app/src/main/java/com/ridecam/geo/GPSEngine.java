package com.ridecam.geo;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import com.ridecam.Knobs;

public class GPSEngine implements android.location.LocationListener {

    public interface LocationListener {
        void onLocationUpdate(long timestamp, double latitude, double longitude, float bearing);
    }

    private Context mContext;
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;

    public GPSEngine(Context context) {
        mContext = context;
    }

    public void setLocationListener(LocationListener locationListener) {
        mLocationListener = locationListener;
    }

    public void startLocationUpdates() {
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Knobs.GPS_MIN_TIME_CHANGE_MS, Knobs.GPS_MIN_DISTANCE_CHANGE_M, this);
        } catch (SecurityException e) {
            // TODO add logging
        }
    }

    public void stopLocationUpdates() {
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(this);
            } catch (SecurityException e) {
                // TODO add logging
            }
            mLocationManager = null;
        }
    }

    @Override
    public void onLocationChanged(final Location location) {
        mLocationListener.onLocationUpdate(location.getTime(), location.getLatitude(), location.getLongitude(), location.getBearing());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

}
