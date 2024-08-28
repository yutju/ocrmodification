package com.example.myapplication2222;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 버튼 인스턴스를 가져옵니다.
        Button mapButton = findViewById(R.id.map_button);
        Button cartButton = findViewById(R.id.cart_button);
        Button stockButton = findViewById(R.id.stock_button);


        // 지도 버튼에 클릭 리스너를 설정합니다.
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // MapActivity로 이동하는 Intent를 생성합니다.
                Intent intent = new Intent(MainActivity.this, MapActivity.class);
                startActivity(intent);  // 액티비티 전환
            }
        });
        // 장바구니 버튼에 클릭 리스너를 설정합니다.
        cartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 장바구니 화면으로 이동하는 Intent를 생성합니다.
                Intent intent = new Intent(MainActivity.this, CartActivity.class);
                startActivity(intent);  // 액티비티 전환
            }
        });

        // "재고 조회" 버튼 클릭 리스너 설정
        stockButton = findViewById(R.id.stock_button);
        stockButton.setOnClickListener(v -> {
            // InventoryActivity로 이동
            Intent intent = new Intent(MainActivity.this, InventoryActivity.class);
            startActivity(intent);
        });



    }
}