package com.ashwin.android.camera2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Toast;

import com.ashwin.android.camera2.databinding.ActivityDirectCaptureBinding;

import java.io.File;
import java.util.function.Consumer;

public class DirectCaptureActivity extends AppCompatActivity {
    private ActivityDirectCaptureBinding binding;

    private CameraController cameraController;

    private final Consumer<byte[]> bytesConsumer = bytes -> {
        File folder = FileUtils.getImagesFolder(DirectCaptureActivity.this);
        String fileName = "img_" + System.currentTimeMillis() + ".jpg";
        File file = FileUtils.saveImage(folder, fileName, bytes);
        if (file != null) {
            Toast.makeText(DirectCaptureActivity.this, "Image saved successfully", Toast.LENGTH_LONG).show();
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            runOnUiThread(() -> binding.imageView.setImageBitmap(bitmap));
        } else {
            Toast.makeText(DirectCaptureActivity.this, "Error saving image", Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDirectCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraController = new CameraController(this);

        binding.captureButton.setOnClickListener(v -> {
            capture();
        });

        binding.viewButton.setOnClickListener(v -> {
            openGallery();
        });

        binding.clearButton.setOnClickListener(v -> {
            binding.imageView.setImageResource(R.mipmap.ic_launcher);
        });
    }

    private void capture() {
        if (binding.textureView.isAvailable()) {
            cameraController.startPreviewAndCapture(binding.textureView.getSurfaceTexture(), bytesConsumer);
        } else {
            binding.textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    cameraController.startPreviewAndCapture(binding.textureView.getSurfaceTexture(), bytesConsumer);
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
}
