package com.example.myapplication2222;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignupActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        EditText editTextEmail = findViewById(R.id.editTextUsername);
        EditText editTextPassword = findViewById(R.id.editTextPassword);
        Button signupButton = findViewById(R.id.signup_button);
        progressBar = findViewById(R.id.progressBar);

        // 회원가입 버튼 클릭 리스너 설정
        signupButton.setOnClickListener(v -> createUser(editTextEmail, editTextPassword));
    }

    private void createUser(EditText editTextEmail, EditText editTextPassword) {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        // 이메일 및 비밀번호 유효성 검증
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "유효한 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "비밀번호는 최소 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 로딩 중 상태 표시
        progressBar.setVisibility(View.VISIBLE);

        // Firebase 회원가입 처리
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE); // 로딩 상태 해제
                    if (task.isSuccessful()) {
                        // 회원가입 성공
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d("SignupActivity", "회원가입 성공: " + user.getEmail());
                        Toast.makeText(this, "회원가입 성공: " + user.getEmail(), Toast.LENGTH_SHORT).show();

                        // 메인 화면으로 이동
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // 회원가입 실패
                        String errorMessage = "회원가입 실패: " + task.getException().getMessage();
                        Log.e("SignupActivity", errorMessage);
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
