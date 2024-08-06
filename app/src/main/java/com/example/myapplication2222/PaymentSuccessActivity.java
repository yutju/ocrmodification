package com.example.myapplication2222;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class PaymentSuccessActivity extends AppCompatActivity {

    private static final int DELAY_MILLIS = 2000; // 2초 지연 시간

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        // 일정 시간 후에 초기 화면으로 돌아가는 코드
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(PaymentSuccessActivity.this, MainActivity.class); // 초기 화면으로 이동
            startActivity(intent);
            finish(); // 현재 Activity 종료
        }, DELAY_MILLIS);
    }
}

