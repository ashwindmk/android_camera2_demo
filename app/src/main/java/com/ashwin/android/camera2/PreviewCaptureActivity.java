package com.ashwin.android.camera2;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Toast;

import com.ashwin.android.camera2.databinding.ActivityPreviewCaptureBinding;

import java.io.File;
import java.util.function.Consumer;

public class PreviewCaptureActivity extends AppCompatActivity {
    private ActivityPreviewCaptureBinding binding;

    private CameraController cameraController;

    private final Consumer<byte[]> bytesConsumer = bytes -> {
        File folder = FileUtils.getImagesFolder(PreviewCaptureActivity.this);
        String fileName = "img_" + System.currentTimeMillis() + ".jpg";
        File file = FileUtils.saveImage(folder, fileName, bytes);
        if (file != null) {
            Toast.makeText(PreviewCaptureActivity.this, "Image saved successfully", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(PreviewCaptureActivity.this, "Error saving image", Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPreviewCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraController = new CameraController(this);

        binding.captureButton.setOnClickListener(v -> {
            cameraController.capture(bytesConsumer, true);
        });

        binding.viewButton.setOnClickListener(v -> {
            openGallery();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPreview();
    }

    private void startPreview() {
        if (binding.textureView.isAvailable()) {
            cameraController.startPreview(binding.textureView.getSurfaceTexture(), bytesConsumer);
        } else {
            binding.textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    cameraController.startPreview(binding.textureView.getSurfaceTexture(), bytesConsumer);
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                }
            });
        }
    }

    private void openGallery() {
        startActivity(new Intent(this, GalleryActivity.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraController.close();
    }
}
