package com.ashwin.android.camera2;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static File getImagesFolder(Context context) {
        File folder = new File(context.getFilesDir(), "photos");
        if (!folder.exists()) {
            folder.mkdir();
        }
        return folder;
    }

    public static File saveImage(File folder, String fileName, byte[] bytes) {
        try {
            // Create file
            File file = new File(folder, fileName);
            if (!file.exists()) {
                file.createNewFile();
            }

            // Save file
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();

            Log.d(TAG, "Image saved successfully");
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Save image to file exception", e);
            return null;
        }
    }
}
