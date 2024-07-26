package com.example.myapplication2222;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import java.util.List;

public class CartActivity extends AppCompatActivity implements KartriderAdapter.OnProductClickListener {

    private RecyclerView recyclerView;
    private KartriderAdapter productAdapter;
    private List<Kartrider> productList;
    private FirebaseFirestore db;
    private Context context;
    private TextView totalPriceTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        context = this;

        // RecyclerView 초기화
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        productList = new ArrayList<>();

        // isOrderSummary 플래그는 false로 설정 (CartActivity에서는 결제 페이지가 아닌 장바구니 페이지이므로)
        productAdapter = new KartriderAdapter(productList, this, this, false);
        recyclerView.setAdapter(productAdapter);

        // 총 결제액 TextView 초기화
        totalPriceTextView = findViewById(R.id.total_amount);

        // FirebaseFirestore 객체 초기화
        db = FirebaseFirestore.getInstance();

        // Firestore에서 상품 데이터 가져오기
        fetchProducts();

        // Firestore 실시간 업데이트 설정
        setupFirestoreListener();

        // '결제' 버튼 설정
        Button payButton = findViewById(R.id.pay_button);
        payButton.setOnClickListener(v -> {
            Intent intent = new Intent(CartActivity.this, OrderSummaryActivity.class);
            intent.putParcelableArrayListExtra("PRODUCT_LIST", new ArrayList<>(productList));
            intent.putExtra("TOTAL_PRICE", calculateTotalPrice());
            intent.putExtra("TOTAL_QUANTITY", calculateTotalQuantity());
            startActivity(intent);
        });
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
        int totalPrice = calculateTotalPrice();
        totalPriceTextView.setText("총 결제금액: " + totalPrice + "원");
    }

    private int calculateTotalPrice() {
        int totalPrice = 0;
        for (Kartrider product : productList) {
            totalPrice += product.getPrice() * product.getQuantity();
        }
        return totalPrice;
    }

    private int calculateTotalQuantity() {
        int totalQuantity = 0;
        for (Kartrider product : productList) {
            totalQuantity += product.getQuantity();
        }
        return totalQuantity;
    }

    @Override
    public void onProductDeleteClick(int position) {
        Kartrider product = productList.get(position);
        if (product != null && product.getId() != null && !product.getId().isEmpty()) {
            db.collection("kartrider").document(product.getId()).delete()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("CartActivity", "DocumentSnapshot successfully deleted!");
                        } else {
                            Log.w("CartActivity", "Error deleting document", task.getException());
                        }
                    });
        } else {
            Log.e("CartActivity", "Product or Product ID is null or empty. Cannot delete from Firestore.");
        }
    }

    @Override
    public void onProductQuantityChanged() {
        updateTotalPrice();
    }
}
