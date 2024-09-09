package com.example.myapplication2222;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OcrActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;

    private TextView resultTextView;
    private ImageView imageView;
    private ProgressBar progressBar;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private File photoFile;
    private ProcessCameraProvider cameraProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        // UI 컴포넌트 초기화
        Button captureButton = findViewById(R.id.captureButton);
        Button recaptureButton = findViewById(R.id.recaptureButton);
        resultTextView = findViewById(R.id.resultTextView);
        imageView = findViewById(R.id.imageView);
        progressBar = findViewById(R.id.progressBar);
        previewView = findViewById(R.id.previewView);

        // 카메라 실행자를 초기화
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 화면 크기에 맞춰 UI 컴포넌트 크기 조정
        adjustLayoutForScreenSize();

        // 카메라 권한 확인
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }

        // 버튼 리스너 설정
        captureButton.setOnClickListener(v -> takePhoto());
        recaptureButton.setOnClickListener(v -> startCamera());
    }

    // 화면 크기에 맞게 레이아웃 조정
    private void adjustLayoutForScreenSize() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);
        int screenWidth = size.x;
        int screenHeight = size.y;

        // 16:9 비율로 높이 계산
        int viewWidth = screenWidth / 2;
        int viewHeight = (viewWidth * 9) / 16;

        // PreviewView와 ImageView의 너비와 높이를 16:9 비율로 설정
        ViewGroup.LayoutParams previewLayoutParams = previewView.getLayoutParams();
        previewLayoutParams.width = viewWidth;
        previewLayoutParams.height = viewHeight;
        previewView.setLayoutParams(previewLayoutParams);

        ViewGroup.LayoutParams imageViewLayoutParams = imageView.getLayoutParams();
        imageViewLayoutParams.width = viewWidth;
        imageViewLayoutParams.height = viewHeight;
        imageView.setLayoutParams(imageViewLayoutParams);
    }

    // 필요한 모든 권한이 부여되었는지 확인
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // 카메라 시작 및 생명 주기에 바인드
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("OcrActivity", "카메라 초기화 실패", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 프리뷰와 이미지 캡처를 카메라 생명주기에 바인딩
    private void bindPreview(ProcessCameraProvider cameraProvider) {
        previewView.post(() -> {
            int previewWidth = previewView.getMeasuredWidth();
            int previewHeight = previewView.getMeasuredHeight();

            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(previewWidth, previewHeight))
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // 디바이스의 디스플레이 회전에 맞게 타겟 회전을 설정
            imageCapture = new ImageCapture.Builder()
                    .setTargetResolution(new Size(previewWidth, previewHeight))
                    .setTargetRotation(previewView.getDisplay().getRotation()) // 회전을 디바이스에 맞춤
                    .build();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
        });
    }

    // 사진을 캡처하고 저장
    private void takePhoto() {
        if (imageCapture == null) return;

        photoFile = new File(getExternalFilesDir(null), "photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(() -> {
                    Toast.makeText(OcrActivity.this, "사진이 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    updateImageView(Uri.fromFile(photoFile));
                    cameraProvider.unbindAll();
                });
                Log.d("OcrActivity", "Photo saved at: " + photoFile.getAbsolutePath());
                processImage(Uri.fromFile(photoFile));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(OcrActivity.this, "사진 저장 실패", Toast.LENGTH_SHORT).show());
                Log.e("OcrActivity", "Photo capture failed", exception);
            }
        });
    }

    // 캡처된 사진을 ImageView에 업데이트
    private void updateImageView(Uri photoUri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));
            imageView.setImageBitmap(bitmap); // 이미지 회전 없이 설정
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(OcrActivity.this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
        }
    }

    // 캡처된 이미지를 처리하고 OCR 수행
    private void processImage(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String recognizedText = text.getText();
                        Log.d("OcrActivity", "Recognized text: " + recognizedText);

                        // 생년월일 및 면허증 패턴 정의
                        String dobPattern = "\\b(19\\d{2}|20\\d{2})[./-]?(0[1-9]|1[0-2])[./-]?(0[1-9]|[12][0-9]|3[01])\\b" +
                                "|\\b(\\d{4})[년\\s-](0[1-9]|1[0-2])[월\\s-](0[1-9]|[12][0-9]|3[01])[일\\s-]?\\b" +
                                "|\\b(\\d{6})\\b";  // 필요한 경우 더 많은 패턴 추가

                        String licenseNumberPattern = "\\b\\d{2}-\\d{2}-\\d{6}-\\d{2}\\b";

                        if (containsLicenseNumber(recognizedText, licenseNumberPattern)) {
                            // 자동차운전면허증일 경우 생년월일만 추출
                            String dob = findDateOfBirth(recognizedText, new String[]{dobPattern});
                            if (dob != null) {
                                boolean isAdult = !isMinor(dob);
                                runOnUiThread(() -> resultTextView.setText(isAdult ? "성인입니다." : "미성년자입니다."));
                                sendResult(isAdult);
                            } else {
                                runOnUiThread(() -> resultTextView.setText("생년월일을 찾을 수 없습니다."));
                                sendResult(false);
                            }
                        } else {
                            // 면허증이 아닌 경우 생년월일 추출
                            String dob = findDateOfBirth(recognizedText, new String[]{dobPattern});
                            if (dob != null) {
                                boolean isAdult = !isMinor(dob);
                                runOnUiThread(() -> resultTextView.setText(isAdult ? "성인입니다." : "미성년자입니다."));
                                sendResult(isAdult);
                            } else {
                                runOnUiThread(() -> resultTextView.setText("생년월일을 찾을 수 없습니다."));
                                sendResult(false);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("OcrActivity", "텍스트 인식 실패", e);
                        runOnUiThread(() -> resultTextView.setText("텍스트 인식 실패"));
                        sendResult(false);
                    });

        } catch (IOException e) {
            Log.e("OcrActivity", "이미지 파일을 읽을 수 없습니다.", e);
        }
    }

    // 텍스트에서 생년월일 찾기
    private String findDateOfBirth(String text, String[] patterns) {
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            if (m.find()) {
                String matchedDate = m.group();
                if (pattern.contains("년") || pattern.contains("월") || pattern.contains("일")) {
                    return matchedDate.replaceAll("[^0-9]", "");
                }
                return matchedDate;
            }
        }
        return null;
    }

    // 텍스트에서 면허증 번호가 포함되어 있는지 확인
    private boolean containsLicenseNumber(String text, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);
        return m.find();
    }

    // 생년월일을 기반으로 성인 여부 확인
    private boolean isMinor(String dob) {
        try {
            SimpleDateFormat sdf;
            if (dob.length() == 6) {
                sdf = new SimpleDateFormat("yyMMdd", Locale.getDefault());
            } else {
                sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            }
            Date dateOfBirth = sdf.parse(dob);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, -19);
            return dateOfBirth.after(cal.getTime());
        } catch (ParseException e) {
            Log.e("OcrActivity", "생년월일 파싱 오류", e);
            return false;
        }
    }

    // 결과를 메인 액티비티에 전달
    private void sendResult(boolean isAdult) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("IS_ADULT", isAdult);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    // 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // 액티비티가 파괴될 때 카메라 리소스 해제
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
