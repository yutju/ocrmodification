package com.example.myapplication2222;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.ExecutionException;

public class OcrActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String TAG = "OcrActivity";

    private OcrProcessor ocrProcessor;
    private ImageView imageView;
    private TextView resultTextView;
    private ProgressBar progressBar;
    private Button captureButton;
    private PreviewView previewView;
    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        imageView = findViewById(R.id.imageView);
        resultTextView = findViewById(R.id.resultTextView);
        progressBar = findViewById(R.id.progressBar);
        captureButton = findViewById(R.id.captureButton);
        previewView = findViewById(R.id.previewView);

        String dataPath = getFilesDir() + "/tesseract/";
        ocrProcessor = new OcrProcessor(this, dataPath);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }

        captureButton.setOnClickListener(v -> {
            if (imageCapture != null) {
                captureImage();
            }
        });
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        File photoFile = new File(getExternalFilesDir(null), "photo.jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Log.d(TAG, "Image capture succeeded: " + photoFile.getAbsolutePath());
                processImage(photoFile);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Image capture failed", exception);
            }
        });
    }

    private void processImage(File file) {
        try {
            Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(file));
            if (imageBitmap != null) {
                runOnUiThread(() -> {
                    imageView.setImageBitmap(imageBitmap);
                    imageView.setVisibility(View.VISIBLE);
                });

                progressBar.setVisibility(View.VISIBLE);
                resultTextView.setText("");
                imageView.setVisibility(View.VISIBLE);

                new Thread(() -> {
                    try {
                        Bitmap preprocessedBitmap = ocrProcessor.preprocessImage(imageBitmap);
                        String extractedText = ocrProcessor.extractText(preprocessedBitmap);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (extractedText != null && !extractedText.isEmpty()) {
                                resultTextView.setText(extractedText);
                                IdentityInfo identityInfo = ocrProcessor.extractIdentityInfo(preprocessedBitmap);
                                displayIdentityInfo(identityInfo);
                            } else {
                                resultTextView.setText("텍스트를 인식하지 못했습니다.");
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            resultTextView.setText("OCR 처리 중 오류가 발생했습니다.");
                            Toast.makeText(OcrActivity.this, "OCR 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                        });
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void displayIdentityInfo(IdentityInfo identityInfo) {
        if (identityInfo != null) {
            String info = "이름: " + (identityInfo.getName() != null ? identityInfo.getName() : "정보 없음") +
                    "\n생년월일: " + (identityInfo.getBirthDate() != null ? identityInfo.getBirthDate() : "정보 없음") +
                    "\n주민등록번호: " + (identityInfo.getIdNumber() != null ? identityInfo.getIdNumber() : "정보 없음");

            resultTextView.append("\n" + info);
            checkAge(identityInfo.getBirthDate());
        }
    }

    private void checkAge(String birthDate) {
        if (birthDate == null) {
            resultTextView.append("\n연도 정보를 찾을 수 없습니다.");
            return;
        }

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        if (birthDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            int birthYear = Integer.parseInt(birthDate.split("-")[0]);
            int age = currentYear - birthYear;

            if (age >= 18) {
                resultTextView.append("\n성인입니다.");
            } else {
                resultTextView.append("\n미성년자입니다.");
            }
        } else {
            resultTextView.append("\n유효한 생년월일 형식이 아닙니다.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ocrProcessor.release(); // Release resources when activity is destroyed
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                Toast.makeText(this, "카메라 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "카메라 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
