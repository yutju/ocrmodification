package com.example.myapplication2222;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartActivity extends AppCompatActivity implements KartriderAdapter.OnProductClickListener {

    private static final int REQUEST_CODE_OCR = 1; // OcrActivity 요청 코드
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_IS_ADULT = "is_adult";

    private RecyclerView recyclerView;
    private KartriderAdapter productAdapter;
    private List<Kartrider> productList;
    private FirebaseFirestore db;
    private Context context;
    private TextView totalPriceTextView;
    private Map<String, Boolean> restrictedProducts = new HashMap<>();
    private boolean isDialogShowing = false; // 다이얼로그 표시 상태

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        context = this;

        // RecyclerView 초기화
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        productList = new ArrayList<>();

        // ProductAdapter 초기화
        productAdapter = new KartriderAdapter(productList, this, this, false);
        recyclerView.setAdapter(productAdapter);

        // 총 결제액 TextView 초기화
        totalPriceTextView = findViewById(R.id.total_amount);

        // FirebaseFirestore 객체 초기화
        db = FirebaseFirestore.getInstance();

        // Firestore에서 상품 데이터와 미성년자 구매 불가 품목 데이터 가져오기
        fetchProducts();
        fetchRestrictedProducts();

        // Firestore 실시간 업데이트 설정
        setupFirestoreListener();

        // '결제' 버튼 설정
        Button payButton = findViewById(R.id.pay_button);
        payButton.setOnClickListener(v -> handlePayment());
    }

    private boolean containsRestrictedProducts() {
        for (Kartrider product : productList) {
            if (restrictedProducts.containsKey(product.getId()) && restrictedProducts.get(product.getId())) {
                return true;
            }
        }
        return false;
    }

    private void fetchProducts() {
        db.collection("kartrider")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Kartrider> newProductList = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Kartrider product = document.toObject(Kartrider.class);
                            product.setId(document.getId()); // Firestore document ID를 Kartrider 객체에 설정
                            newProductList.add(product);
                        }
                        runOnUiThread(() -> {
                            productList.clear();
                            productList.addAll(newProductList);
                            productAdapter.notifyDataSetChanged();
                            updateTotalPrice();
                        });
                    } else {
                        Log.e("CartActivity", "Error fetching data: " + task.getException());
                        runOnUiThread(() -> Toast.makeText(context, "데이터를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void fetchRestrictedProducts() {
        db.collection("inventory")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        restrictedProducts.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // `allow` 필드가 String인지 확인
                            Object allowObject = document.get("allow");
                            if (allowObject instanceof String) {
                                String allow = (String) allowObject;
                                restrictedProducts.put(document.getId(), "No".equalsIgnoreCase(allow));
                            } else {
                                Log.w("CartActivity", "Field 'allow' is not a String or is missing");
                                // 필드가 없거나 String이 아닌 경우 기본값 처리
                                restrictedProducts.put(document.getId(), false);
                            }
                        }
                    } else {
                        Log.e("CartActivity", "Error fetching restricted products: " + task.getException());
                    }
                });
    }

    private void setupFirestoreListener() {
        db.collection("kartrider")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.w("CartActivity", "Listen failed.", e);
                        return;
                    }
                    if (queryDocumentSnapshots != null) {
                        for (DocumentChange dc : queryDocumentSnapshots.getDocumentChanges()) {
                            Kartrider updatedProduct = dc.getDocument().toObject(Kartrider.class);
                            updatedProduct.setId(dc.getDocument().getId()); // Firestore document ID를 Kartrider 객체에 설정
                            switch (dc.getType()) {
                                case ADDED:
                                    addProductToList(updatedProduct);
                                    break;
                                case MODIFIED:
                                    updateProductInList(updatedProduct);
                                    break;
                                case REMOVED:
                                    removeProductFromList(updatedProduct.getId());
                                    break;
                            }
                        }
                        updateTotalPrice();
                    }
                });
    }

    private void addProductToList(Kartrider product) {
        if (product != null && product.getId() != null) {
            if (findProductIndexById(product.getId()) == -1) {
                productList.add(product);
                productAdapter.notifyItemInserted(productList.size() - 1);
            }
        } else {
            Log.e("CartActivity", "Product or Product ID is null. Cannot add to list.");
        }
    }

    private void updateProductInList(Kartrider product) {
        if (product != null && product.getId() != null) {
            int index = findProductIndexById(product.getId());
            if (index != -1) {
                productList.set(index, product);
                productAdapter.notifyItemChanged(index);
            }
        } else {
            Log.e("CartActivity", "Product or Product ID is null. Cannot update list.");
        }
    }

    private void removeProductFromList(String productId) {
        if (productId != null) {
            int index = findProductIndexById(productId);
            if (index != -1) {
                productList.remove(index);
                productAdapter.notifyItemRemoved(index);
            }
        } else {
            Log.e("CartActivity", "Product ID is null. Cannot remove from list.");
        }
    }

    private int findProductIndexById(String id) {
        if (id != null) {
            for (int i = 0; i < productList.size(); i++) {
                if (id.equals(productList.get(i).getId())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void updateTotalPrice() {
        int totalPrice = 0;
        for (Kartrider product : productList) {
            if (product != null) {
                totalPrice += product.getPrice() * product.getQuantity(); // 제품 가격에 수량을 곱하여 총 가격에 추가
            }
        }
        totalPriceTextView.setText("총 결제금액: " + totalPrice + "원");
    }

    private void handlePayment() {
        // SharedPreferences에서 성인 인증 상태 로드
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isAdult = prefs.getBoolean(KEY_IS_ADULT, false);

        if (containsRestrictedProducts()) {
            if (!isAdult) {
                // 미성년자 구매 불가 품목이 있는 경우 성인 인증을 요구
                if (!isDialogShowing) {
                    showAgeRestrictionDialog();
                }
            } else {
                // 성인 인증이 완료된 경우 결제 처리
                navigateToOrderSummary();
            }
        } else {
            // 미성년자 구매 불가 품목이 없는 경우 결제 처리
            navigateToOrderSummary();
        }
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
            Intent intent = new Intent(CartActivity.this, OcrActivity.class);
            startActivityForResult(intent, REQUEST_CODE_OCR);
            dialog.dismiss(); // 다이얼로그를 닫습니다.
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OCR) { // OcrActivity에서 돌아온 경우
            if (resultCode == RESULT_OK) { // OcrActivity에서 성공적으로 성인 인증이 완료된 경우
                boolean isAdult = data.getBooleanExtra("IS_ADULT", false); // 성인 여부를 Intent로부터 가져옴
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_IS_ADULT, isAdult); // 성인 여부를 SharedPreferences에 저장
                editor.apply();

                if (isAdult) {
                    // 성인 인증이 완료되었으면 인증 완료 메시지 표시 후 결제 처리
                    Toast.makeText(this, "성인 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                    navigateToOrderSummary(); // 주문 요약 화면으로 이동
                } else {
                    // 성인 인증에 실패했을 경우 메시지 표시
                    Toast.makeText(this, "성인 인증에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }
            // 다이얼로그가 이미 닫혔으므로 상태를 초기화합니다.
            isDialogShowing = false;
        }
    }

    private void navigateToOrderSummary() {
        Intent intent = new Intent(CartActivity.this, OrderSummaryActivity.class);
        intent.putParcelableArrayListExtra("PRODUCT_LIST", new ArrayList<>(productList));

        int totalPrice = 0;
        int totalQuantity = 0;

        for (Kartrider product : productList) {
            totalPrice += product.getPrice() * product.getQuantity(); // 각 제품의 총 가격 계산
            totalQuantity += product.getQuantity(); // 각 제품의 수량을 총 수량에 추가
        }

        intent.putExtra("TOTAL_PRICE", totalPrice);
        intent.putExtra("TOTAL_QUANTITY", totalQuantity);
        startActivity(intent);
        finish(); // 현재 Activity 종료
    }

    @Override
    public void onProductDeleteClick(int position) {
        if (position >= 0 && position < productList.size()) {
            productList.remove(position);
            productAdapter.notifyItemRemoved(position);
            updateTotalPrice();
            Toast.makeText(this, "상품이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "상품 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProductQuantityChanged() {
        // Adapter에서 수량이 변경될 때마다 호출되는 메서드
        updateTotalPrice(); // 총 결제 금액 업데이트
    }
}
