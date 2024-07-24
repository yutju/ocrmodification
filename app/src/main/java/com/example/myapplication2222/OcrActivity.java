package com.example.myapplication2222;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrActivity extends AppCompatActivity {
    // 전역변수 선언
    TessBaseAPI tess;
    String dataPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        // 데이터 경로
        dataPath = getFilesDir() + "/tesseract/";

        // 한글&영어 데이터 체크
        checkFile(new File(dataPath + "/tessdata/"), "kor");

        // 문자 인식을 수행할 tess객체 생성
        String lang = "kor";
        tess = new TessBaseAPI();
        tess.init(dataPath, lang);

        // 문자 인식진행  , R.drawable.identification인 사진에 있는 글자를 변환하겠다
        processImage(BitmapFactory.decodeResource(getResources(), R.drawable.identification));
    }

    public void processImage(Bitmap bitmap) {
        Toast.makeText(getApplicationContext(), "이미지가 복잡할 경우 많은 시간이 소요됩니다.", Toast.LENGTH_LONG).show();
        String OCRresult = null;
        tess.setImage(bitmap);
        OCRresult = tess.getUTF8Text();

        // 생년월일 추출
        String birthDate = extractBirthDate(OCRresult);

        TextView OCRTextView = findViewById(R.id.textView);
        OCRTextView.setText(birthDate);
    }

    private String extractBirthDate(String text) {
        // 정규 표현식을 이용하여 생년월일 패턴을 찾기
        Pattern pattern = Pattern.compile("\\b(\\d{6})\\b");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String birthDate = matcher.group(1);
            String yearPrefix = birthDate.substring(0, 2);
            int year = Integer.parseInt(yearPrefix);
            if (year <= 23) { // Assuming current year is 2023 or later
                year += 2000;
            } else {
                year += 1900;
            }
            String month = birthDate.substring(2, 4);
            String day = birthDate.substring(4, 6);
            return String.format("생년월일: %d년 %s월 %s일", year, month, day);
        } else {
            return "생년월일을 찾을 수 없습니다.";
        }
    }

    // 파일 복제
    private void copyFiles(String lang) {
        try {
            // 우리가 원하는 파일이 장소에 없을때
            String filepath = dataPath + "/tessdata/" + lang + ".traineddata";

            // Asset매니저 접속
            AssetManager assetManager = getAssets();

            // reading/writing stream open
            InputStream inStream = assetManager.open("tessdata/" + lang + ".traineddata");
            OutputStream outStream = new FileOutputStream(filepath);

            // 파일경로에서 파일 복사하기
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, read);
            }
            outStream.flush();
            outStream.close();
            inStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 파일 존재확인
    private void checkFile(File dir, String lang) {
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles(lang);
        }
        if (dir.exists()) {
            String datafilePath = dataPath + "/tessdata/" + lang + ".traineddata";
            File datafile = new File(datafilePath);
            if (!datafile.exists()) {
                copyFiles(lang);
            }
        }
    }
}
