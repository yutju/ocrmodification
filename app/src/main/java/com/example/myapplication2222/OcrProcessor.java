package com.example.myapplication2222;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrProcessor {

    private static final String TESSDATA = "tessdata/";
    private TessBaseAPI tessBaseAPI;
    private String dataPath;

    public OcrProcessor(Context context, String dataPath) {
        this.dataPath = dataPath;
        initTesseract(context);
    }

    private void initTesseract(Context context) {
        tessBaseAPI = new TessBaseAPI();

        // Ensure tessdata directory exists
        File dir = new File(dataPath + TESSDATA);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Copy trained data file from assets
        copyTrainedData(context, dataPath + TESSDATA + "kor.traineddata");

        // Initialize Tesseract API with the language code
        tessBaseAPI.init(dataPath, "kor");
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
    }

    private void copyTrainedData(Context context, String destinationPath) {
        try {
            InputStream in = context.getAssets().open("tessdata/kor.traineddata");
            OutputStream out = new FileOutputStream(destinationPath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String extractText(Bitmap bitmap) {
        tessBaseAPI.setImage(bitmap);
        return tessBaseAPI.getUTF8Text();
    }

    public void release() {
        if (tessBaseAPI != null) {
            tessBaseAPI.end();
        }
    }

    public IdentityInfo extractIdentityInfo(Bitmap bitmap) {
        String ocrResult = extractText(bitmap);

        // Extract fields using regex patterns
        String name = extractField(ocrResult, "이름: (.+)");
        String birthDate = extractField(ocrResult, "생년월일: (\\d{4}-\\d{2}-\\d{2})");
        String idNumber = extractField(ocrResult, "주민등록번호: (\\d{6}-\\d{7})");

        return new IdentityInfo(name, birthDate, idNumber);
    }

    private String extractField(String text, String pattern) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(text);
        if (m.find()) {
            return m.group(1);
        } else {
            return null;
        }
    }

    public Bitmap preprocessImage(Bitmap bitmap) {
        // Convert to grayscale
        Bitmap grayBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int pixel = bitmap.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                grayBitmap.setPixel(x, y, Color.rgb(gray, gray, gray));
            }
        }

        // Apply Gaussian Blur
        Bitmap blurredBitmap = applyGaussianBlur(grayBitmap);

        // Simple thresholding
        Bitmap preprocessedBitmap = Bitmap.createBitmap(blurredBitmap.getWidth(), blurredBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < blurredBitmap.getHeight(); y++) {
            for (int x = 0; x < blurredBitmap.getWidth(); x++) {
                int pixel = blurredBitmap.getPixel(x, y);
                int gray = Color.red(pixel);
                if (gray > 128) {
                    preprocessedBitmap.setPixel(x, y, Color.WHITE);
                } else {
                    preprocessedBitmap.setPixel(x, y, Color.BLACK);
                }
            }
        }

        return preprocessedBitmap;
    }

    private Bitmap applyGaussianBlur(Bitmap bitmap) {
        // Placeholder for Gaussian Blur implementation
        // Consider using a library like RenderScript or OpenCV for real blur implementation
        return bitmap;
    }

    public Bitmap detectAndCropIdCard(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Edge detection logic (simple threshold for demonstration purposes)
        int left = width, top = height, right = 0, bottom = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                if (gray < 128) { // Threshold
                    if (x < left) left = x;
                    if (y < top) top = y;
                    if (x > right) right = x;
                    if (y > bottom) bottom = y;
                }
            }
        }

        // Crop the bitmap
        Rect rect = new Rect(left, top, right, bottom);
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height());
    }
}
