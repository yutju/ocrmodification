package com.example.myapplication2222;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization failed", e);
        }
    }
}
