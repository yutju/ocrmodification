package com.example.myapplication2222;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class PaymentSuccessActivity extends AppCompatActivity {

    private static final int DELAY_MILLIS = 2000; // 2초 지연 시간
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_IS_ADULT = "is_adult";

    private FirebaseFirestore firestore;
    private CollectionReference cartCollectionRef;
    private CollectionReference inventoryCollectionRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        firestore = FirebaseFirestore.getInstance();
        cartCollectionRef = firestore.collection("kartrider");
        inventoryCollectionRef = firestore.collection("inventory");

        // 장바구니 비우기 및 재고 업데이트
        clearCart();
        updateInventory();

        // 성인 인증 상태 초기화
        resetAdultStatus();

        // 일정 시간 후에 MainActivity로 돌아가는 코드
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(PaymentSuccessActivity.this, MainActivity.class); // MainActivity로 이동
            startActivity(intent);
            finish(); // 현재 Activity 종료
        }, DELAY_MILLIS);
    }

    private void clearCart() {
        cartCollectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (DocumentSnapshot document : task.getResult().getDocuments()) {
                    cartCollectionRef.document(document.getId()).delete()
                            .addOnSuccessListener(aVoid -> Log.d("PaymentSuccess", "Cart item deleted"))
                            .addOnFailureListener(e -> Log.e("PaymentSuccess", "Failed to delete cart item", e));
                }
            } else {
                Log.e("PaymentSuccess", "Failed to clear cart", task.getException());
            }
        });
    }

    private void updateInventory() {
        cartCollectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Task<Void>> updateTasks = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult().getDocuments()) {
                    Kartrider cartProduct = document.toObject(Kartrider.class);
                    if (cartProduct != null && cartProduct.getId() != null) {
                        Task<Void> updateTask = inventoryCollectionRef.document(cartProduct.getId()).get().continueWithTask(inventoryTask -> {
                            if (inventoryTask.isSuccessful() && inventoryTask.getResult() != null) {
                                DocumentSnapshot inventoryDoc = inventoryTask.getResult();
                                if (inventoryDoc.exists()) {
                                    Long currentStockLong = inventoryDoc.getLong("stock"); // Firestore에서 가져온 재고 수량
                                    int quantityInCart = cartProduct.getQuantity(); // 장바구니에서 가져온 수량 (int로 가져온다고 가정)

                                    // Null 체크 및 변환
                                    if (currentStockLong != null) {
                                        int currentStock = currentStockLong.intValue(); // Long을 int로 변환

                                        // 현재 재고와 장바구니 수량을 비교하여 재고 업데이트
                                        if (currentStock >= quantityInCart) {
                                            return inventoryDoc.getReference().update("stock", currentStockLong - quantityInCart);
                                        } else {
                                            // 재고 부족 상황 처리
                                            Log.w("PaymentSuccess", "Stock not sufficient for: " + cartProduct.getName());
                                            return Tasks.forException(new Exception("Stock not sufficient"));
                                        }
                                    } else {
                                        // 재고가 null인 경우 처리
                                        Log.w("PaymentSuccess", "Current stock is null.");
                                        return Tasks.forException(new Exception("Invalid stock"));
                                    }
                                }
                            }
                            return null;
                        });
                        updateTasks.add(updateTask);
                    }
                }

                Tasks.whenAllComplete(updateTasks).addOnCompleteListener(updateAllTasks -> {
                    if (updateAllTasks.isSuccessful()) {
                        Log.d("PaymentSuccess", "Inventory updated successfully");
                    } else {
                        Log.e("PaymentSuccess", "Failed to update inventory", updateAllTasks.getException());
                    }
                });
            } else {
                Log.e("PaymentSuccess", "Failed to retrieve cart data", task.getException());
            }
        });
    }

    private void resetAdultStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_ADULT, false); // 인증 상태를 초기화
        editor.apply();
    }
}
