package com.example.myapplication2222;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter inventoryAdapter;
    private List<InventoryItem> inventoryList;
    private FirebaseFirestore db;
    private Context context;
    private ListenerRegistration registration;

    private EditText searchEditText;
    private Button searchButton;
    private View rootView;
    private String currentSearchText = "";

    private Handler handler;
    private Runnable searchRunnable;
    private static final long SEARCH_DELAY = 500; // 검색 지연 시간

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        context = this;

        // 최상위 레이아웃 초기화
        rootView = findViewById(R.id.relativeLayout);

        // RecyclerView 초기화
        recyclerView = findViewById(R.id.inventoryRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        inventoryList = new ArrayList<>();
        inventoryAdapter = new InventoryAdapter(inventoryList);
        recyclerView.setAdapter(inventoryAdapter);

        // FirebaseFirestore 객체 초기화
        db = FirebaseFirestore.getInstance();

        // UI 요소 초기화
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);

        // 초기 상태에서 RecyclerView 숨기기
        recyclerView.setVisibility(View.GONE);

        // Handler 및 Runnable 초기화
        handler = new Handler();

        // 실시간 데이터 업데이트 설정
        setupRealTimeUpdates();

        // 검색 버튼 클릭 리스너 설정
        searchButton.setOnClickListener(v -> {
            String searchText = searchEditText.getText().toString().trim();
            if (!searchText.isEmpty()) {
                currentSearchText = searchText;  // 현재 검색어를 저장합니다.
                searchInventoryItems(searchText);
                hideKeyboard();  // 검색 버튼 클릭 시 키보드를 숨깁니다.
            } else {
                Toast.makeText(context, "검색어를 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // 텍스트 입력 변화 감지 및 검색
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    handler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> {
                    String searchText = s.toString().trim();
                    if (!searchText.isEmpty()) {
                        currentSearchText = searchText;  // 현재 검색어를 저장합니다.
                        searchInventoryItems(searchText);
                    } else {
                        // 검색어가 없으면 빈 리스트를 보여줍니다.
                        inventoryAdapter.updateInventoryList(new ArrayList<>());
                        recyclerView.setVisibility(View.GONE);
                    }
                };
                handler.postDelayed(searchRunnable, SEARCH_DELAY); // 지연 시간
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 전체 화면 클릭 시 키보드 숨기기
        rootView.setOnClickListener(v -> hideKeyboard());

        // 검색 입력 필드 클릭 시 키보드 나타나기
        searchEditText.setOnClickListener(v -> showKeyboard());
    }

    private void setupRealTimeUpdates() {
        registration = db.collection("inventory")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("InventoryActivity", "Listen failed.", e);
                        return;
                    }

                    if (snapshot != null && !snapshot.isEmpty()) {
                        List<InventoryItem> newInventoryList = new ArrayList<>();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            InventoryItem item = document.toObject(InventoryItem.class);
                            if (item != null) {
                                newInventoryList.add(item);
                            }
                        }

                        // 현재 검색어에 맞는 데이터만 필터링
                        List<InventoryItem> filteredList = new ArrayList<>();
                        if (!currentSearchText.isEmpty()) {
                            for (InventoryItem item : newInventoryList) {
                                String itemName = item.getName();
                                if (itemName != null) {
                                    // 검색어의 모든 문자 중 하나라도 포함된 경우 필터링
                                    if (itemName.toLowerCase().contains(currentSearchText.toLowerCase())) {
                                        filteredList.add(item);
                                    }
                                }
                            }
                        }
                        inventoryAdapter.updateInventoryList(filteredList);

                        // 검색어가 있을 때만 RecyclerView를 보이도록 설정
                        recyclerView.setVisibility(filteredList.isEmpty() ? View.GONE : View.VISIBLE);
                    } else {
                        Log.d("InventoryActivity", "Current data: null");
                    }
                });
    }

    private void searchInventoryItems(String searchText) {
        db.collection("inventory")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<InventoryItem> newInventoryList = new ArrayList<>();
                        for (DocumentSnapshot document : task.getResult().getDocuments()) {
                            InventoryItem item = document.toObject(InventoryItem.class);
                            if (item != null) {
                                newInventoryList.add(item);
                            }
                        }
                        // 현재 검색어에 맞는 데이터만 필터링
                        List<InventoryItem> filteredList = new ArrayList<>();
                        if (!searchText.isEmpty()) {
                            for (InventoryItem item : newInventoryList) {
                                String itemName = item.getName();
                                if (itemName != null) {
                                    // 검색어의 모든 문자 중 하나라도 포함된 경우 필터링
                                    if (itemName.toLowerCase().contains(searchText.toLowerCase())) {
                                        filteredList.add(item);
                                    }
                                }
                            }
                        }
                        inventoryAdapter.updateInventoryList(filteredList);
                        // RecyclerView를 보이도록 설정합니다.
                        recyclerView.setVisibility(filteredList.isEmpty() ? View.GONE : View.VISIBLE);
                    } else {
                        Log.e("InventoryActivity", "Error getting documents: ", task.getException());
                        Toast.makeText(this, "데이터를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        searchEditText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) {
            registration.remove(); // 리스너를 제거하여 메모리 누수를 방지합니다.
        }
        if (handler != null && searchRunnable != null) {
            handler.removeCallbacks(searchRunnable); // 핸들러 및 Runnable 정리
        }
    }
}
