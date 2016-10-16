package com.ridecam;

import android.content.Context;

import com.ridecam.fs.FSUtils;

public class Knobs {

    public static final boolean FORCE_NATIVE_CAMERA = false;

    public static final int PREVIEW_FPS = 24;

    public static final int PREVIEW_WIDTH = 1280;

    public static final int PREVIEW_HEIGHT = 720;

    public static final int REC_BITRATE = 690000; // 690 kbit/sec

    public static final int REC_FPS = 24;

    public static final int REC_WIDTH = 1280;

    public static final int REC_HEIGHT = 720;

    public static final int MAX_REC_LENGTH_MS = 3600 * 1000 * 12; // 12 hours

    public static final int GPS_MIN_TIME_CHANGE_MS = 30 * 1000; // 30 seconds

    public static final int GPS_MIN_DISTANCE_CHANGE_M = 20; // 20 meters

    public static int getMaximumRecordingFileSizeBytes(Context context) {
        double factor = 0.9; // 90%
        return (int)Math.floor(factor * FSUtils.freeBytesAvailable(FSUtils.getVideoDirectory(context).getPath()));
    }

}
