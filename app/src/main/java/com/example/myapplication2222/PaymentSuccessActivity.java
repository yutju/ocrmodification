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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;

public class PaymentSuccessActivity extends AppCompatActivity {

    private static final int DELAY_MILLIS = 2000; // 2 seconds delay
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_IS_ADULT = "is_adult";

    private FirebaseFirestore firestore;
    private CollectionReference cartCollectionRef;
    private CollectionReference inventoryCollectionRef;
    private boolean isProcessing = true; // Track if processing is ongoing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        firestore = FirebaseFirestore.getInstance();
        cartCollectionRef = firestore.collection("kartrider");
        inventoryCollectionRef = firestore.collection("inventory");

        // Start the update inventory and clear cart process
        updateInventoryAndClearCart();
        // Reset adult status
        resetAdultStatus();
    }

    private void updateInventoryAndClearCart() {
        cartCollectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Task<Void>> updateTasks = new ArrayList<>();
                WriteBatch batch = firestore.batch();

                Log.d("PaymentSuccess", "Retrieved cart data successfully.");

                for (DocumentSnapshot document : task.getResult().getDocuments()) {
                    Kartrider cartProduct = document.toObject(Kartrider.class);
                    if (cartProduct != null && cartProduct.getId() != null) {
                        String productId = cartProduct.getId();
                        int quantityInCart = cartProduct.getQuantity();

                        Log.d("PaymentSuccess", "Processing product ID: " + productId + " with quantity: " + quantityInCart);

                        DocumentReference inventoryDocRef = inventoryCollectionRef.document(productId);
                        Task<DocumentSnapshot> inventoryTask = inventoryDocRef.get();
                        updateTasks.add(inventoryTask.continueWithTask(inventorySnapshotTask -> {
                            if (inventorySnapshotTask.isSuccessful()) {
                                DocumentSnapshot inventoryDoc = inventorySnapshotTask.getResult();
                                if (inventoryDoc.exists()) {
                                    Long currentStockLong = inventoryDoc.getLong("stock");
                                    if (currentStockLong != null) {
                                        int currentStock = currentStockLong.intValue();
                                        if (currentStock >= quantityInCart) {
                                            long updatedStock = currentStock - quantityInCart;
                                            batch.update(inventoryDocRef, "stock", updatedStock);
                                            Log.d("PaymentSuccess", "Stock updated for product ID: " + productId);
                                        } else {
                                            Log.w("PaymentSuccess", "Insufficient stock for product ID: " + productId);
                                        }
                                    } else {
                                        Log.w("PaymentSuccess", "Current stock is null for product ID: " + productId);
                                    }
                                } else {
                                    Log.w("PaymentSuccess", "Inventory document does not exist for product ID: " + productId);
                                }
                                // Add cart item deletion to batch
                                batch.delete(cartCollectionRef.document(document.getId()));
                                Log.d("PaymentSuccess", "Cart item scheduled for deletion with document ID: " + document.getId());
                                return Tasks.forResult(null); // Continue the task chain
                            } else {
                                Log.e("PaymentSuccess", "Failed to get inventory document for product ID: " + productId, inventorySnapshotTask.getException());
                                return Tasks.forException(inventorySnapshotTask.getException()); // Fail the chain
                            }
                        }));
                    } else {
                        Log.w("PaymentSuccess", "Cart product is null or has an invalid ID.");
                    }
                }

                // Wait for all inventory update tasks to complete
                Tasks.whenAllComplete(updateTasks).addOnCompleteListener(allTasks -> {
                    if (allTasks.isSuccessful()) {
                        // Commit the batch write after all tasks are complete
                        batch.commit().addOnCompleteListener(batchCommitTask -> {
                            if (batchCommitTask.isSuccessful()) {
                                Log.d("PaymentSuccess", "Batch commit successful.");
                                isProcessing = false; // Processing is complete
                                // Delay before starting MainActivity
                                new Handler().postDelayed(() -> {
                                    Intent intent = new Intent(PaymentSuccessActivity.this, MainActivity.class);
                                    startActivity(intent);
                                    finish();
                                }, DELAY_MILLIS);
                            } else {
                                Log.e("PaymentSuccess", "Failed to commit batch", batchCommitTask.getException());
                                isProcessing = false; // Processing is complete even on failure
                            }
                        });
                    } else {
                        Log.e("PaymentSuccess", "Failed to update inventory and clear cart", allTasks.getException());
                        isProcessing = false; // Processing is complete even on failure
                    }
                });
            } else {
                Log.e("PaymentSuccess", "Failed to retrieve cart data", task.getException());
                isProcessing = false; // Processing is complete even on failure
            }
        });
    }

    private void resetAdultStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_ADULT, false); // Reset adult status
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isProcessing) {
            // Additional logic to handle activity pausing during processing
            Log.d("PaymentSuccess", "Activity paused during processing.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isProcessing) {
            // Additional logic to handle activity stopping during processing
            Log.d("PaymentSuccess", "Activity stopped during processing.");
        }
    }
}
