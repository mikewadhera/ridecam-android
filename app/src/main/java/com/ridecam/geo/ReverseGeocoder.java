package com.ridecam.geo;

import android.app.IntentService;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import java.util.List;
import java.util.Locale;

public class ReverseGeocoder extends IntentService {

    private static final String TAG = "ReverseGeocoder";

    public static final int SUCCESS_RESULT = 0;
    public static final String PACKAGE_NAME = "com.ridecam.geo";
    public static final String RECEIVER = PACKAGE_NAME + ".RECEIVER";
    public static final String RESULT_DATA_KEY = PACKAGE_NAME + ".RESULT_DATA_KEY";
    public static final String LOCATION_DATA_EXTRA = PACKAGE_NAME + ".LOCATION_DATA_EXTRA";

    public ReverseGeocoder() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        ResultReceiver resultReceiver = intent.getParcelableExtra(RECEIVER);

        if (resultReceiver == null) {
            Log.e(TAG, "No receiver received. There is nowhere to send the results.");
            return;
        }

        Location location = intent.getParcelableExtra(LOCATION_DATA_EXTRA);

        if (location == null) {
            Log.e(TAG, "No location received. There is nothing to geocode.");
            return;
        }

        try {
            String address = getReverseGeocodedLocation(location.getLatitude(), location.getLongitude(), 1);
            Bundle bundle = new Bundle();
            bundle.putString(RESULT_DATA_KEY, address);
            resultReceiver.send(SUCCESS_RESULT, bundle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected String getReverseGeocodedLocation(final double latitude, final double longitude, final int desiredNumDetails) {
        String location = null;
        int numDetails = 0;
        try {
            Address addr = lookupAddress(latitude, longitude);

            if (addr != null) {
                location = addr.getThoroughfare();
                if (location != null && !("null".equals(location))) {
                    numDetails++;
                } else {
                    location = addr.getFeatureName();
                    if (location != null && !("null".equals(location))) {
                        numDetails++;
                    }
                }

                if (numDetails == desiredNumDetails) {
                    return location;
                }

                String locality = addr.getLocality();
                if (locality != null && !("null".equals(locality))) {
                    if (location != null && location.length() > 0) {
                        location += ", " + locality;
                    } else {
                        location = locality;
                    }
                    numDetails++;
                }

                if (numDetails == desiredNumDetails) {
                    return location;
                }

                String adminArea = addr.getAdminArea();
                if (adminArea != null && !("null".equals(adminArea))) {
                    if (location != null && location.length() > 0) {
                        location += ", " + adminArea;
                    } else {
                        location = adminArea;
                    }
                    numDetails++;
                }

                if (numDetails == desiredNumDetails) {
                    return location;
                }

                String countryCode = addr.getCountryCode();
                if (countryCode != null && !("null".equals(countryCode))) {
                    if (location != null && location.length() > 0) {
                        location += ", " + countryCode;
                    } else {
                        location = addr.getCountryName();
                    }
                }
            }

            return location;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Address lookupAddress(final double latitude, final double longitude) {
        try {
            Address address = null;
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (!addresses.isEmpty()) {
                address = addresses.get(0);
            }
            return address;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
