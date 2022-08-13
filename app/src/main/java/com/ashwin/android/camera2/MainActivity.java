package com.ashwin.android.camera2;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            String msg = "Camera permission granted";
            //Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            Log.d(TAG, msg);
        } else {
            String msg = "Camera permission declined";
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button previewCaptureButton = findViewById(R.id.preview_capture_button);
        previewCaptureButton.setOnClickListener(v -> {
            startActivity(new Intent(this, PreviewCaptureActivity.class));
        });

        Button directCaptureButton = findViewById(R.id.direct_capture_button);
        directCaptureButton.setOnClickListener(v -> {
            startActivity(new Intent(this, DirectCaptureActivity.class));
        });

        Button galleryButton = findViewById(R.id.gallery_button);
        galleryButton.setOnClickListener(v -> {
            startActivity(new Intent(this, GalleryActivity.class));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasCameraPermission()) {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private boolean hasCameraPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}
