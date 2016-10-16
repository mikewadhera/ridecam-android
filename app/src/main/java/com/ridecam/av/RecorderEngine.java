package com.ridecam.av;

import android.os.Environment;
import android.util.Log;

import com.ridecam.Knobs;
import com.ridecam.av.device.OSRecorder;
import com.ridecam.av.device.RecorderDevice;
import com.ridecam.av.device.vendor.SamsungRecorder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class RecorderEngine {

    public interface ErrorListener {
        void onRecorderError();
    }

    private static final String TAG = "RecorderEngine";

    private CameraEngine mCameraEngine;
    private RecorderDevice mRecorder;
    private boolean mRecordingLock;
    private ErrorListener mErrorListener;

    public RecorderEngine(CameraEngine cameraEngine) {
        mCameraEngine = cameraEngine;
    }

    public boolean isRecording() {
        return mRecordingLock;
    }

    public void startRecording() {
        if (mRecordingLock) {
            fatal("Recording Failed: Already in progress");
            return;
        }

        mRecordingLock = true;

        try {
            if (CameraEngine.usingSamsungCamera()) {
                mRecorder = new SamsungRecorder();
            } else {
                mRecorder = new OSRecorder();
            }

            mRecorder.setOnInfoListener(new RecorderDevice.OnInfoListener() {
                @Override
                public void onInfo(RecorderDevice mediaRecorder, int what, int extra) {
                    onRecorderInfo(what, extra);
                }
            });
            mRecorder.setOnErrorListener(new RecorderDevice.OnErrorListener() {
                @Override
                public void onError(RecorderDevice recorder, int errorType, int errorCode) {
                onRecorderError(errorType, errorCode);
                }
            });

            if (mCameraEngine == null) {
                fatal("Recording Failed: No Camera");
                mRecordingLock = false;
                return;
            }

            Log.d(TAG, "R: Unlocking Camera");
            mCameraEngine.unlockCamera();

            Log.d(TAG, "R: Setting Camera");
            mRecorder.setCamera(mCameraEngine.getDevice());

            Log.d(TAG, "R: Setting Camera orientation hint");
            mRecorder.setOrientationHint(90);

            Log.d(TAG, "R: Setting sources");
            mRecorder.setVideoSource(OSRecorder.VideoSource.CAMERA);

            Log.d(TAG, "R: Setting profile");
            mRecorder.setOutputFormat(OSRecorder.OutputFormat.MPEG_4);
            mRecorder.setVideoSize(Knobs.REC_WIDTH, Knobs.REC_HEIGHT);
            mRecorder.setVideoFrameRate(Knobs.REC_FPS);
            mRecorder.setVideoEncodingBitRate(Knobs.REC_BITRATE);
            mRecorder.setVideoEncoder(OSRecorder.VideoEncoder.H264);

            Log.d(TAG, "R: Setting max outputs");
            mRecorder.setMaxFileSize(Knobs.getMaximumRecordingFileSizeBytes());
            mRecorder.setMaxDuration(Knobs.MAX_REC_LENGTH_MS);

            Log.d(TAG, "R: Setting output file");
            mRecorder.setOutputFile(getOutputMediaFilePath(MEDIA_TYPE_VIDEO));

            Log.d(TAG, "R: Prepare");
            try {
                mRecorder.prepare();
            } catch (IllegalStateException e) {
                Log.d(TAG, "IllegalStateException when preparing MediaRecorder: " + e.getMessage());
                fatal("Recording Failed: Internal Error");
                mRecordingLock = false;
                return;
            } catch (IOException e) {
                Log.d(TAG, "IOException when preparing MediaRecorder: " + e.getMessage());
                fatal("Recording Failed: Internal Error");
                mRecordingLock = false;
                return;
            }

            Log.d(TAG, "R: Start");
            mRecorder.start();

            Log.d(TAG, "R: Registering Recording Surface");
            mRecorder.registerRecordingSurface(mCameraEngine.getDevice());

            Log.d(TAG, "R: Started");
        } catch (Exception e) {
            e.printStackTrace();
            mRecordingLock = false;
        }
    }

    public void stopRecording() {
        if (!mRecordingLock) {
            fatal("Stop Failed: Not Recording");
            return;
        }

        try {
            Log.d(TAG, "R: Stopping");
            mRecorder.stop();

            cleanup();

        } catch (Exception e) {
            e.printStackTrace();
            // TODO add logging
        } finally {
            mRecordingLock = false;
        }

        Log.d(TAG, "R: Stopped");
    }

    public void cleanup() {
        if (mRecorder != null) {
            Log.d(TAG, "R: Unregistering Recording Surface");
            mRecorder.unregisterRecordingSurface(mCameraEngine.getDevice());

            Log.d(TAG, "R: Reseting recorder");
            mRecorder.reset();

            Log.d(TAG, "R: Releasing recorder");
            mRecorder.release();

            Log.d(TAG, "R: Locking camera");
            mCameraEngine.lockCamera();

        } else {
            // TODO add logging
        }
    }

    // Called back when recorder hits max file size or max duration
    public void onRecorderInfo(int what, int extra) {
        Log.e(TAG, "!!!!!!! onRecorderInfo what: " + what + " extra: " + extra);
        // TODO add logging
        try {
            cleanup();
        }  catch (Exception e) {
            e.printStackTrace();
            // TODO add logging
        } finally {
            mRecordingLock = false;
            if (mErrorListener != null) {
                mErrorListener.onRecorderError();
            }
        }
    }

    // Called back when recorder has internal error
    public void onRecorderError(int errorType, int errorCode) {
        Log.e(TAG, "!!!!!!! onRecorderError errorType: " + errorType + " errorCode: " + errorCode);
        // TODO add logging
        try {
            cleanup();
        }  catch (Exception e) {
            e.printStackTrace();
            // TODO add logging
        } finally {
            mRecordingLock = false;
            if (mErrorListener != null) {
                mErrorListener.onRecorderError();
            }
        }
    }

    public void fatal(String text) {
        // TODO add logging
    }

    public void setErrorListener(ErrorListener errorListener) {
        mErrorListener = errorListener;
    }

    public static final int MEDIA_TYPE_VIDEO = 2;

    private static String getOutputMediaFilePath(int type){
        return getOutputMediaFile(type).getAbsolutePath();
    }

    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), TAG);
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

}
