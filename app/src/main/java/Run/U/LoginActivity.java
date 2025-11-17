package Run.U;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "GoogleSignIn";

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private FirebaseFirestore firebaseFirestore;
    private ViewPager2 viewPager;
    private LinearLayout pageIndicator;
    private TextView termsNoticeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        googleSignInClient = GoogleSignInUtils.getGoogleSignInClient(this);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleSignInResult
        );

        android.widget.Button googleSignInButton = findViewById(R.id.google_sign_in_button);
        googleSignInButton.setOnClickListener(v -> signIn());

        setupOnboardingCarousel();
        setupTermsNotice();
    }

    private void setupOnboardingCarousel() {
        viewPager = findViewById(R.id.onboarding_viewpager);
        pageIndicator = findViewById(R.id.page_indicator);

        OnboardingAdapter adapter = new OnboardingAdapter();
        viewPager.setAdapter(adapter);

        int slideCount = adapter.getItemCount();
        for (int i = 0; i < slideCount; i++) {
            View dot = new View(this);
            int size = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(size / 2, 0, size / 2, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.page_indicator_dot);
            dot.setAlpha(0.3f);
            pageIndicator.addView(dot);
        }

        updatePageIndicator(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updatePageIndicator(position);
            }
        });
    }

    private void updatePageIndicator(int position) {
        for (int i = 0; i < pageIndicator.getChildCount(); i++) {
            View dot = pageIndicator.getChildAt(i);
            if (i == position) {
                dot.setAlpha(1.0f);
            } else {
                dot.setAlpha(0.3f);
            }
        }
    }

    private void setupTermsNotice() {
        termsNoticeText = findViewById(R.id.terms_notice_text);
        String fullText = "로그인 시 " + getString(R.string.terms_of_service) + " 및 " + getString(R.string.privacy_policy) + "에 동의하게 됩니다.";
        SpannableString spannableString = new SpannableString(fullText);

        String termsText = getString(R.string.terms_of_service);
        String privacyText = getString(R.string.privacy_policy);

        int termsStart = fullText.indexOf(termsText);
        int termsEnd = termsStart + termsText.length();
        int privacyStart = fullText.indexOf(privacyText);
        int privacyEnd = privacyStart + privacyText.length();

        ClickableSpan termsClickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                openTermsOfService();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#FFFF4C29"));
                ds.setUnderlineText(true);
            }
        };

        ClickableSpan privacyClickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                openPrivacyPolicy();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#FFFF4C29"));
                ds.setUnderlineText(true);
            }
        };

        if (termsStart >= 0) {
            spannableString.setSpan(termsClickableSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFF4C29")), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (privacyStart >= 0) {
            spannableString.setSpan(privacyClickableSpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFF4C29")), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        termsNoticeText.setText(spannableString);
        termsNoticeText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void openTermsOfService() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/terms"));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "이용약관 페이지를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPrivacyPolicy() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/privacy"));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "개인정보처리방침 페이지를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            // 이미 로그인되어 있으면 MainActivity로 이동
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }
    }

    private void signIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void handleSignInResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent data = result.getData();
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                firebaseAuthWithGoogle(account.getIdToken());
            } else {
                Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException exception) {
            int statusCode = exception.getStatusCode();
            String errorMessage = "Google 로그인 실패: " + statusCode;
            Log.w(TAG, errorMessage, exception);
            
            String userMessage;
            switch (statusCode) {
                case 10: // DEVELOPER_ERROR
                    userMessage = "구글 로그인 설정 오류가 발생했습니다. 개발자에게 문의하세요.";
                    break;
                case 12500: // SIGN_IN_CANCELLED
                    userMessage = "로그인이 취소되었습니다.";
                    break;
                case 7: // NETWORK_ERROR
                    userMessage = "네트워크 연결을 확인해주세요.";
                    break;
                case 8: // INTERNAL_ERROR
                    userMessage = "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
                    break;
                default:
                    userMessage = getString(R.string.sign_in_failed) + " (오류 코드: " + statusCode + ")";
                    break;
            }
            Toast.makeText(this, userMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null) {
            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            Log.d(TAG, "Firebase 인증 성공");
                            updateUserInFirestore(user);
                        } else {
                            Log.w(TAG, "Firebase 인증 실패", task.getException());
                            Exception exception = task.getException();
                            String errorMessage = getString(R.string.firebase_auth_failed);
                            if (exception != null) {
                                String exceptionMessage = exception.getMessage();
                                if (exceptionMessage != null && exceptionMessage.contains("network")) {
                                    errorMessage = "네트워크 연결을 확인해주세요.";
                                } else if (exceptionMessage != null && exceptionMessage.contains("invalid")) {
                                    errorMessage = "인증 정보가 유효하지 않습니다.";
                                }
                            }
                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updateUserInFirestore(FirebaseUser user) {
        if (user == null) {
            Toast.makeText(this, R.string.firebase_auth_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        DocumentReference userRef = firebaseFirestore.collection("users").document(uid);

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Log.d(TAG, "기존 사용자입니다: " + user.getEmail());

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("displayName", user.getDisplayName());
                    updates.put("photoURL", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");

                    userRef.update(updates)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "사용자 정보 업데이트 성공");
                                goToMainActivity();
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "사용자 정보 업데이트 실패", e);
                                goToMainActivity();
                            });
                } else {
                    Log.d(TAG, "신규 사용자입니다: " + user.getEmail());

                    Map<String, Object> newUser = new HashMap<>();
                    newUser.put("email", user.getEmail());
                    newUser.put("displayName", user.getDisplayName());
                    newUser.put("photoURL", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
                    newUser.put("role", "user");
                    newUser.put("createdAt", FieldValue.serverTimestamp());

                    userRef.set(newUser)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "신규 사용자 정보 Firestore에 저장 성공");
                                goToMainActivity();
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "신규 사용자 정보 저장 실패", e);
                                goToMainActivity();
                            });
                }
            } else {
                Log.w(TAG, "Firestore 문서 조회 실패", task.getException());
                goToMainActivity();
            }
        });
    }

    private void goToMainActivity() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = currentUser.getEmail();
            }
            if (displayName != null && !displayName.isEmpty()) {
                String successMessage = getString(R.string.sign_in_success, displayName);
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            }
        }
        
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }, 2000);
    }
}
