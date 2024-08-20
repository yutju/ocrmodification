package com.example.myapplication2222;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class OrderSummaryActivity extends AppCompatActivity implements KartriderAdapter.OnProductClickListener {

    private static final int REQUEST_CODE_OCR = 1; // OcrActivity 요청 코드

    private RecyclerView recyclerView;
    private KartriderAdapter productAdapter;
    private TextView totalQuantityTextView, totalPriceTextView;
    private FirebaseFirestore firestore;
    private CollectionReference cartCollectionRef;
    private CollectionReference inventoryCollectionRef;
    private boolean isAdult = false; // 성인 인증 상태를 저장하는 변수
    private boolean isDialogShowing = false; // 다이얼로그 표시 상태

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
        isAdult = intent.getBooleanExtra("IS_ADULT", false); // 성인 인증 상태 추가

        // ProductAdapter 초기화
        productAdapter = new KartriderAdapter(productList != null ? productList : new ArrayList<>(), this, this, true); // true 플래그 추가
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
        inventoryCollectionRef = firestore.collection("inventory"); // Inventory 컬렉션 참조

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
        if (isAdult) {
            // 성인 인증이 완료되었으므로 결제 처리
            processPayment();
        } else {
            if (!isDialogShowing) {
                // 성인 인증이 되지 않았으므로 OcrActivity로 이동하기 전에 다이얼로그 표시
                showAgeRestrictionDialog();
            }
        }
    }

    private void processPayment() {
        if (isAdult) {
            // 장바구니의 모든 상품을 가져와서 미성년자 구매 불가 품목 확인
            cartCollectionRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    QuerySnapshot querySnapshot = task.getResult();
                    if (querySnapshot != null) {
                        ArrayList<Kartrider> cartProducts = new ArrayList<>();
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            Kartrider cartProduct = document.toObject(Kartrider.class);
                            if (cartProduct != null) {
                                cartProduct.setId(document.getId()); // ensure the ID is set
                                cartProducts.add(cartProduct);
                            }
                        }

                        // 미성년자 구매 불가 상품 확인
                        checkAgeRestrictedProducts(cartProducts);
                    }
                } else {
                    Toast.makeText(OrderSummaryActivity.this, "장바구니 데이터 로드 실패", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // 성인 인증이 되지 않았을 때는 결제 처리하지 않음
            showAgeRestrictionDialog();
        }
    }

    private void checkAgeRestrictedProducts(List<Kartrider> cartProducts) {
        List<Task<Void>> checkTasks = new ArrayList<>();
        boolean[] containsRestricted = {false}; // 배열로 변경하여 람다에서 수정 가능하게 함

        for (Kartrider cartProduct : cartProducts) {
            if (cartProduct != null && cartProduct.getId() != null) {
                Task<Void> checkTask = inventoryCollectionRef.document(cartProduct.getId()).get().continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot inventoryDoc = task.getResult();
                        if (inventoryDoc.exists() && "No".equals(inventoryDoc.getString("allow"))) {
                            containsRestricted[0] = true;
                        }
                    }
                    return null;
                });
                checkTasks.add(checkTask);
            }
        }

        Tasks.whenAllComplete(checkTasks).addOnCompleteListener(allTasks -> {
            if (containsRestricted[0]) {
                showAgeRestrictionDialog();
            } else {
                // 성인 인증이 완료되었고, 미성년자 구매 불가 상품이 없으므로 결제 완료 화면으로 이동
                navigateToPaymentSuccess();
            }
        });
    }

    private void showAgeRestrictionDialog() {
        isDialogShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_age_verification, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();

        Button confirmButton = dialogView.findViewById(R.id.confirm_button);
        confirmButton.setOnClickListener(v -> {
            // OcrActivity를 시작하여 신분증 스캔 및 성인 인증을 수행합니다.
            Intent intent = new Intent(OrderSummaryActivity.this, OcrActivity.class);
            startActivityForResult(intent, REQUEST_CODE_OCR);
            dialog.dismiss(); // 다이얼로그를 닫습니다.
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OCR) {
            if (resultCode == RESULT_OK) {
                isAdult = data.getBooleanExtra("IS_ADULT", false);
                if (isAdult) {
                    // 성인 인증이 완료되면 결제 처리
                    processPayment();
                } else {
                    Toast.makeText(this, "성인 인증에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }
            // 다이얼로그가 이미 닫혔으므로 상태를 초기화합니다.
            isDialogShowing = false;
        }
    }

    private void navigateToPaymentSuccess() {
        // 결제 성공 화면으로 이동합니다.
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(OrderSummaryActivity.this, PaymentSuccessActivity.class);
            startActivity(intent);
            finish();
        }, 1000); // 1초 지연
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
