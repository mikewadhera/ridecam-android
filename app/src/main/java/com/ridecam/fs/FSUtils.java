package com.ridecam.fs;

import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public class FSUtils {

    private static final String TAG = "FSUtils";

    public static File getVideoDirectory() {
        File mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return mediaStorageDir;
    }

    public static long freeBytesAvailable(String path) {
        StatFs stat = new StatFs(path);
        long bytesAvailable;
        if(Build.VERSION.SDK_INT >= 18){
            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        }
        else{
            bytesAvailable = stat.getBlockSize() * stat.getAvailableBlocks();
        }
        return bytesAvailable;
    }

}
