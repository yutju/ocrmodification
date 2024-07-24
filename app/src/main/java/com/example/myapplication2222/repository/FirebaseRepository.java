package com.example.myapplication2222.repository;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseRepository {

    private static FirebaseRepository instance = null;
    private FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    // 생성자를 private으로 선언하여 외부에서 객체 생성을 막음
    private FirebaseRepository() {}

    public static FirebaseRepository getInstance() {
        if (instance == null) {
            instance = new FirebaseRepository();
        }
        return instance;
    }

    public Map<String,Object> getData(String collectionName,String documentName){

        final Map<String, Object> data = new HashMap<>();
        //Ex) DocumentReference docRef = firestore.collection("product").document("상품1");
        DocumentReference docRef = firestore.collection(collectionName).document(documentName);
        docRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // 문서 데이터 추출
                        data.putAll(documentSnapshot.getData());
                        Log.d("Firestore", "문서 데이터: " + data.toString());
                    } else {
                        // 문서가 존재하지 않는 경우 처리
                        Log.d("Firestore", "문서가 존재하지 않습니다.");
                    }
                })
                .addOnFailureListener(e -> {
                    // 문서 가져오기 실패 처리
                    Log.e("Firestore", "문서 가져오기 실패: " + e.getMessage());
                });

        return data;
        }

}
