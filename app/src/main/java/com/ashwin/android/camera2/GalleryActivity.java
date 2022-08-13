package com.ashwin.android.camera2;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryActivity";

    private ImageView imageView;
    private Button prevButton, nextButton;
    private Button deleteButton;

    private File folder;
    private File file;

    private ListIterator<File> it;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        folder = FileUtils.getImagesFolder(this);

        imageView = findViewById(R.id.image_view);

        deleteButton = findViewById(R.id.delete_button);
        deleteButton.setOnClickListener(v -> {
            delete();
        });

        prevButton = findViewById(R.id.prev_button);
        prevButton.setOnClickListener(v -> {
            prev();
        });

        nextButton = findViewById(R.id.next_button);
        nextButton.setOnClickListener(v -> {
            next();
        });

        loadFiles();
    }

    private void loadFiles() {
        LinkedList<File> filesList = new LinkedList<>();

        File[] files = folder.listFiles();

        if (files == null || files.length == 0) {
            show();
            return;
        }

        // Sort by file name
        Arrays.sort(files, (f1, f2) -> {
            return f1.getName().compareTo(f2.getName());
        });

        for (File f : files) {
            filesList.add(f);
        }

        it = filesList.listIterator();

        next();
    }

    private void prev() {
        if (it.hasPrevious()) {
            file = it.previous();
            show();
        }
    }

    private void next() {
        if (it.hasNext()) {
            file = it.next();
            show();
        }
    }

    private void show() {
        updateButtons();

        if (file == null) {
            String msg = "No image files found!";
            Log.e(MainActivity.TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            imageView.setImageResource(R.mipmap.ic_launcher);
            return;
        }

        // Decode image file
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        imageView.setImageBitmap(bitmap);
    }

    private void delete() {
        file.delete();
        file = null;

        it.remove();

        if (it.hasNext()) {
            next();
        } else if (it.hasPrevious()) {
            prev();
        } else {
            deleteButton.setEnabled(false);
            show();
        }
    }

    private void updateButtons() {
        if (it == null) {
            deleteButton.setEnabled(false);
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            return;
        }
        nextButton.setEnabled(it.hasNext());
        prevButton.setEnabled(it.hasPrevious());
        //Log.d(TAG, "hasPrev = " + it.hasPrevious() + ", prevIdx = " + it.previousIndex() + ", hasNExt = " + it.hasNext() + ", nextIdx = " + it.nextIndex());
    }
}
