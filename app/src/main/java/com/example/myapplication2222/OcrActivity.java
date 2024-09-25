package com.example.myapplication2222;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.View;
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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MultipartBody;

public class OcrActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;
    private TextView resultTextView;
    private TextView faceResultTextView; // 얼굴 결과 TextView 추가
    private ImageView imageView;
    private ImageView faceImageView;
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

        // UI 구성 요소 초기화
        Button captureButton = findViewById(R.id.captureButton);
        Button recaptureButton = findViewById(R.id.recaptureButton);
        resultTextView = findViewById(R.id.resultTextView);
        faceResultTextView = findViewById(R.id.faceResultTextView); // 얼굴 결과 TextView 초기화
        imageView = findViewById(R.id.imageView);
        faceImageView = findViewById(R.id.faceImageView);
        progressBar = findViewById(R.id.progressBar);
        previewView = findViewById(R.id.previewView);

        // 카메라 실행기 초기화
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 화면 크기에 맞게 레이아웃 조정
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

        // PreviewView 및 ImageView의 너비와 높이 설정
        ViewGroup.LayoutParams previewLayoutParams = previewView.getLayoutParams();
        previewLayoutParams.width = viewWidth;
        previewLayoutParams.height = viewHeight;
        previewView.setLayoutParams(previewLayoutParams);

        ViewGroup.LayoutParams imageViewLayoutParams = imageView.getLayoutParams();
        imageViewLayoutParams.width = viewWidth;
        imageViewLayoutParams.height = viewHeight;
        imageView.setLayoutParams(imageViewLayoutParams);

        // 얼굴 인식 결과 이미지뷰 설정
        ViewGroup.LayoutParams faceImageViewLayoutParams = faceImageView.getLayoutParams();
        faceImageViewLayoutParams.width = viewWidth;
        faceImageViewLayoutParams.height = viewHeight;
        faceImageView.setLayoutParams(faceImageViewLayoutParams);
    }

    // 모든 권한이 부여되었는지 확인
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // 카메라 시작 및 생명주기에 바인딩
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

    // 카메라 생명주기에 미리보기 및 이미지 캡처 바인딩
    private void bindPreview(ProcessCameraProvider cameraProvider) {
        previewView.post(() -> {
            int previewWidth = previewView.getMeasuredWidth();
            int previewHeight = previewView.getMeasuredHeight();

            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(previewWidth, previewHeight))
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageCapture = new ImageCapture.Builder()
                    .setTargetResolution(new Size(previewWidth, previewHeight))
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
        });
    }

    // 사진 캡처 및 저장
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
                Log.d("OcrActivity", "사진 저장 위치: " + photoFile.getAbsolutePath());
                processImage(Uri.fromFile(photoFile));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(OcrActivity.this, "사진 캡처 실패", Toast.LENGTH_SHORT).show());
                Log.e("OcrActivity", "사진 캡처 실패", exception);
            }
        });
    }

    // 캡처한 사진으로 ImageView 업데이트
    private void updateImageView(Uri photoUri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));
            imageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(OcrActivity.this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
        }
    }

    // 캡처한 이미지를 처리하고 OCR 수행
    private void processImage(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String recognizedText = text.getText();
                        Log.d("OcrActivity", "인식된 텍스트: " + recognizedText);

                        // 생년월일 추출
                        String dob = findDateOfBirth(recognizedText);
                        if (dob != null) {
                            boolean isAdult = !isMinor(dob);
                            runOnUiThread(() -> {
                                resultTextView.setText(isAdult ? "성인입니다." : "미성년자입니다.");
                                faceResultTextView.setVisibility(View.VISIBLE); // 얼굴 결과 TextView 보이기
                            });
                            sendResult(isAdult);
                            // 얼굴 이미지 처리
                            processFaceImage(); // 얼굴 이미지 비교 처리
                        } else {
                            runOnUiThread(() -> {
                                resultTextView.setText("생년월일을 찾을 수 없습니다.");
                                faceResultTextView.setVisibility(View.GONE); // 얼굴 결과 TextView 숨기기
                            });
                            sendResult(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("OcrActivity", "텍스트 인식 실패", e);
                        runOnUiThread(() -> resultTextView.setText("텍스트 인식 실패"));
                    });
        } catch (IOException e) {
            Log.e("OcrActivity", "이미지 처리 실패", e);
            runOnUiThread(() -> resultTextView.setText("이미지 처리 실패"));
        }
    }
    // 생년월일 찾기
    private String findDateOfBirth(String text) {
        // 한국의 생년월일 형식(YYYY.MM.DD 또는 YYYY-MM-DD) 정규 표현식
        Pattern pattern = Pattern.compile("(\\d{4}[.\\-]\\d{1,2}[.\\-]\\d{1,2})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // 성인 여부 확인
    private boolean isMinor(String dob) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date birthDate = sdf.parse(dob);
            if (birthDate != null) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.YEAR, -19); // 19세 이상 체크
                return birthDate.after(cal.getTime());
            }
        } catch (ParseException e) {
            Log.e("OcrActivity", "생년월일 파싱 실패", e);
        }
        return true; // 파싱 실패 시 미성년자로 간주
    }

    // 결과 전송 처리
    private void sendResult(boolean isAdult) {
        // 결과를 서버에 전송하거나 처리하는 로직 추가
        Log.d("OcrActivity", "성인 여부: " + (isAdult ? "성인" : "미성년자"));
    }

    // 얼굴 캡처 및 비교를 위한 메서드
    private void processFaceImage() {
        Uri faceImageUri = Uri.fromFile(photoFile); // 얼굴 이미지를 URI로 가져옴
        uploadImage(faceImageUri, new ImageUploadCallback() {
            @Override
            public void onUploadSuccess(String faceImageUrl) {
                // 신분증 이미지도 업로드
                uploadImage(Uri.fromFile(photoFile), new ImageUploadCallback() {
                    @Override
                    public void onUploadSuccess(String idImageUrl) {
                        compareFaces(idImageUrl, faceImageUrl);
                    }

                    @Override
                    public void onUploadFailure() {
                        // 신분증 이미지 업로드 실패 처리
                    }
                });
            }

            @Override
            public void onUploadFailure() {
                // 얼굴 이미지 업로드 실패 처리
            }
        });
    }

    // 이미지 업로드 메서드
    private void uploadImage(Uri imageUri, ImageUploadCallback callback) {
        String apiKey = "YOUR_API_KEY"; // API 키
        String apiSecret = "YOUR_API_SECRET"; // API 비밀

        OkHttpClient client = new OkHttpClient();
        File imageFile = new File(imageUri.getPath());

        // 멀티파트 요청 본문 생성
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_key", apiKey)
                .addFormDataPart("api_secret", apiSecret)
                .addFormDataPart("image_file", imageFile.getName(),
                        RequestBody.create(MediaType.parse("image/jpeg"), imageFile))
                .build();

        // 요청 생성
        Request request = new Request.Builder()
                .url("https://api-us.faceplusplus.com/facepp/v3/upload")
                .post(requestBody)
                .build();

        // 요청 실행
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onUploadFailure();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onUploadFailure();
                    return;
                }
                String responseBody = response.body().string();
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                String imageUrl = jsonResponse.get("url").getAsString(); // URL 가져오기
                callback.onUploadSuccess(imageUrl);
            }
        });
    }

    // 얼굴 비교 메서드
    private void compareFaces(String idImageUrl, String faceImageUrl) {
        String apiKey = "YOUR_API_KEY"; // API 키
        String apiSecret = "YOUR_API_SECRET"; // API 비밀

        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_key", apiKey)
                .addFormDataPart("api_secret", apiSecret)
                .addFormDataPart("image_url1", idImageUrl)
                .addFormDataPart("image_url2", faceImageUrl)
                .build();

        Request request = new Request.Builder()
                .url("https://api-us.faceplusplus.com/facepp/v3/compare")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("OcrActivity", "얼굴 비교 실패", e);
                runOnUiThread(() -> {
                    faceResultTextView.setText("얼굴 비교 실패");
                    faceResultTextView.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("OcrActivity", "얼굴 비교 실패: " + response.message());
                    runOnUiThread(() -> {
                        faceResultTextView.setText("얼굴 비교 실패");
                        faceResultTextView.setVisibility(View.VISIBLE);
                    });
                    return;
                }
                String responseBody = response.body().string();
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                boolean isMatch = jsonResponse.get("is_match").getAsBoolean();

                runOnUiThread(() -> {
                    if (isMatch) {
                        faceImageView.setImageResource(R.drawable.face_match); // 매칭 이미지로 변경
                        faceResultTextView.setText("얼굴이 일치합니다.");
                    } else {
                        faceImageView.setImageResource(R.drawable.face_no_match); // 불일치 이미지로 변경
                        faceResultTextView.setText("얼굴이 일치하지 않습니다.");
                    }
                    faceResultTextView.setVisibility(View.VISIBLE); // 결과 TextView 보이기
                });
            }
        });
    }

    // 인터페이스 정의
    interface ImageUploadCallback {
        void onUploadSuccess(String imageUrl);
        void onUploadFailure();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
