package com.ridecam.fs;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.comparator.NameFileComparator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FSUtils {

    private static final String TAG = "FSUtils";

    public static File getVideoDirectory(Context context) {
        String subFolder = "videos";
        File folder = new File(context.getFilesDir(), subFolder);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                // TODO add logging
            }
        }
        return folder;
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

    public static List<String> getVideoFileAbsolutePathsAscendingByName(Context context) {
        File[] files = FSUtils.getVideoDirectory(context).listFiles();
        Arrays.sort(files, NameFileComparator.NAME_COMPARATOR);
        ArrayList<String> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file.getAbsolutePath());
        }
        return paths;
    }

    public static String getBasename(String path) {
        return FilenameUtils.getBaseName(path);
    }

    public static void deleteFile(File file) {
        file.delete();
    }

    public static File getVideoFile(Context context, String tripId) {
        return new File(getVideoDirectory(context), tripId + ".mp4");
    }

}
