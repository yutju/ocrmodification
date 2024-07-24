package com.example.myapplication2222;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class OrderSummaryActivity extends AppCompatActivity implements KartriderAdapter.OnProductClickListener {

    private RecyclerView recyclerView;
    private KartriderAdapter productAdapter;
    private TextView totalQuantityTextView, totalPriceTextView;
    private FirebaseFirestore firestore;
    private CollectionReference cartCollectionRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_summary);

        // RecyclerView 초기화
        recyclerView = findViewById(R.id.recycler_view_order_summary);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Intent에서 데이터 받기
        Intent intent = getIntent();
        ArrayList<Kartrider> productList = intent.getParcelableArrayListExtra("PRODUCT_LIST");
        int totalPrice = intent.getIntExtra("TOTAL_PRICE", 0);
        int totalQuantity = intent.getIntExtra("TOTAL_QUANTITY", 0);

        // ProductAdapter 초기화
        productAdapter = new KartriderAdapter(productList != null ? productList : new ArrayList<>(), this, this);
        recyclerView.setAdapter(productAdapter);

        // 총 수량 및 총 금액 TextView 초기화
        totalQuantityTextView = findViewById(R.id.total_quantity);
        totalPriceTextView = findViewById(R.id.total_amount_summary);

        if (productList != null) {
            updateSummary(totalPrice, totalQuantity);
        }

        // Firebase Firestore 초기화
        firestore = FirebaseFirestore.getInstance();
        cartCollectionRef = firestore.collection("kartrider");

        // 결제하기 버튼 설정
        Button payButton = findViewById(R.id.pay_button_summary);
        payButton.setOnClickListener(v -> handlePayment());
    }

    private void updateSummary(int totalPrice, int totalQuantity) {
        totalQuantityTextView.setText(getColoredText("총 수량: ", totalQuantity + "개"));
        totalPriceTextView.setText(getColoredText("총 결제금액: ", totalPrice + "원"));
    }

    private Spannable getColoredText(String prefix, String value) {
        Spannable spannable = new SpannableString(prefix + value);

        // prefix 부분을 검정색으로 설정
        int prefixEnd = prefix.length();
        spannable.setSpan(new ForegroundColorSpan(Color.BLACK), 0, prefixEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // 숫자 부분의 색상을 빨간색으로 설정
        int numberStart = prefixEnd;
        int numberEnd = numberStart;
        int unitStart = prefixEnd;
        int unitEnd = spannable.length();

        // 숫자와 단위 구분
        String[] parts = value.split("(?<=\\d)(?=\\D)");
        if (parts.length == 2) {
            numberEnd = numberStart + parts[0].length();
            unitStart = numberEnd;
            unitEnd = unitStart + parts[1].length();
        }

        // 숫자 부분의 색상 변경
        if (numberEnd > numberStart) {
            spannable.setSpan(new ForegroundColorSpan(Color.RED), numberStart, numberEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 단위 부분의 색상 변경
        if (unitEnd > unitStart) {
            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), unitStart, unitEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }

    private void handlePayment() {
        // kartrider 컬렉션의 모든 문서 삭제
        cartCollectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();
                if (querySnapshot != null) {
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        cartCollectionRef.document(document.getId()).delete();
                    }

                    // 모든 문서 삭제 후, PaymentSuccessActivity로 이동
                    Intent intent = new Intent(OrderSummaryActivity.this, PaymentSuccessActivity.class);
                    startActivity(intent);
                    finish(); // 현재 Activity 종료
                }
            } else {
                // 실패 처리
                Toast.makeText(OrderSummaryActivity.this, "장바구니 초기화 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onProductDeleteClick(int position) {
        // 상품 삭제 처리 로직 추가
    }

    @Override
    public void onProductQuantityChanged() {
        // 수량 변경 처리 로직 추가
    }
}



