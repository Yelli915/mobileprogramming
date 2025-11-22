package Run.U;

import android.content.Context;
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
    private static final String COLOR_ACCENT = "#FFFF4C29";

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private FirebaseFirestore firebaseFirestore;
    private ViewPager2 viewPager;
    private LinearLayout pageIndicator;
    private TextView termsNoticeText;
    private boolean isSigningIn = false;
    private android.widget.Button googleSignInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = GoogleSignInUtils.getAuth();
        firebaseFirestore = GoogleSignInUtils.getFirestore();

        googleSignInClient = GoogleSignInUtils.getGoogleSignInClient(this);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleSignInResult
        );

        googleSignInButton = findViewById(R.id.google_sign_in_button);
        if (googleSignInButton != null) {
            googleSignInButton.setOnClickListener(v -> {
                // 중복 클릭 방지
                if (isSigningIn) {
                    Log.d(TAG, "로그인 진행 중입니다.");
                    return;
                }
                
                // 즉시 UI 업데이트 (버튼 비활성화)
                setSigningInState(true);
                
                // 네트워크 확인을 비동기로 처리하거나 빠르게 체크
                if (GoogleSignInUtils.isNetworkAvailable(LoginActivity.this)) {
                    signIn();
                } else {
                    setSigningInState(false);
                    GoogleSignInUtils.showToast(this, "네트워크 연결을 확인해주세요.");
                }
            });
        } else {
            Log.e(TAG, "Google 로그인 버튼을 찾을 수 없습니다.");
        }

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

        ClickableSpan termsClickableSpan = createClickableSpan(this::openTermsOfService);
        ClickableSpan privacyClickableSpan = createClickableSpan(this::openPrivacyPolicy);

        if (termsStart >= 0) {
            setClickableSpan(spannableString, termsClickableSpan, termsStart, termsEnd);
        }

        if (privacyStart >= 0) {
            setClickableSpan(spannableString, privacyClickableSpan, privacyStart, privacyEnd);
        }

        termsNoticeText.setText(spannableString);
        termsNoticeText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private ClickableSpan createClickableSpan(Runnable onClickAction) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                onClickAction.run();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor(COLOR_ACCENT));
                ds.setUnderlineText(true);
            }
        };
    }

    private void setClickableSpan(SpannableString spannableString, ClickableSpan clickableSpan, int start, int end) {
        spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor(COLOR_ACCENT)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void openUrl(String url, String errorMessage) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (Exception e) {
            GoogleSignInUtils.showToast(this, errorMessage);
        }
    }

    private void openTermsOfService() {
        openUrl("https://www.example.com/terms", "이용약관 페이지를 열 수 없습니다.");
    }

    private void openPrivacyPolicy() {
        openUrl("https://www.example.com/privacy", "개인정보처리방침 페이지를 열 수 없습니다.");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // firebaseAuth가 null인 경우 다시 초기화
        if (firebaseAuth == null) {
            firebaseAuth = GoogleSignInUtils.getAuth();
        }
        
        // GoogleSignInUtils를 통해 현재 사용자 확인 (일관성 유지)
        FirebaseUser currentUser = GoogleSignInUtils.getCurrentUser();
        if (currentUser != null) {
            // 이미 로그인되어 있으면 MainActivity로 이동
            navigateToMainActivity(false);
            return;
        }
    }

    private void setSigningInState(boolean signingIn) {
        isSigningIn = signingIn;
        if (googleSignInButton != null) {
            googleSignInButton.setEnabled(!signingIn);
        }
    }

    private void signIn() {
        if (googleSignInClient == null) {
            Log.e(TAG, "GoogleSignInClient가 초기화되지 않았습니다.");
            GoogleSignInUtils.showToast(this, "로그인 설정 오류가 발생했습니다.");
            setSigningInState(false);
            return;
        }

        try {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            if (signInIntent != null) {
                googleSignInLauncher.launch(signInIntent);
            } else {
                Log.e(TAG, "Google 로그인 Intent를 생성할 수 없습니다.");
                setSigningInState(false);
                GoogleSignInUtils.showToast(this, "로그인 설정 오류가 발생했습니다.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Google 로그인 시작 중 오류 발생", e);
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, "로그인 시작 중 오류가 발생했습니다.");
        }
    }

    private void handleSignInResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            Log.d(TAG, "로그인 결과 코드: " + result.getResultCode());
            setSigningInState(false);
            // 사용자가 로그인을 취소한 경우는 조용히 처리
            if (result.getResultCode() != RESULT_CANCELED) {
                GoogleSignInUtils.showToast(this, R.string.sign_in_failed);
            }
            return;
        }

        Intent data = result.getData();
        if (data == null) {
            Log.e(TAG, "로그인 결과 데이터가 null입니다.");
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, R.string.sign_in_failed);
            return;
        }

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        if (task == null) {
            Log.e(TAG, "GoogleSignInAccount Task가 null입니다.");
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, R.string.sign_in_failed);
            return;
        }

        // GoogleSignIn.getSignedInAccountFromIntent()는 보통 즉시 완료되므로
        // addOnCompleteListener로 통일하여 처리
        task.addOnCompleteListener(this::handleSignInTask);
    }

    private void handleSignInTask(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                firebaseAuthWithGoogle(account.getIdToken());
            } else {
                Log.w(TAG, "GoogleSignInAccount 또는 ID Token이 null입니다.");
                setSigningInState(false);
                GoogleSignInUtils.showToast(this, R.string.sign_in_failed);
            }
        } catch (ApiException exception) {
            int statusCode = exception.getStatusCode();
            String errorMessage = "Google 로그인 실패: " + statusCode;
            Log.w(TAG, errorMessage, exception);
            setSigningInState(false);
            
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
            GoogleSignInUtils.showToast(this, userMessage);
        } catch (Exception e) {
            Log.e(TAG, "로그인 처리 중 예상치 못한 오류 발생", e);
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, "로그인 처리 중 오류가 발생했습니다.");
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null || idToken.isEmpty()) {
            Log.e(TAG, "ID Token이 null이거나 비어있습니다.");
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, R.string.sign_in_failed);
            return;
        }

        if (firebaseAuth == null) {
            Log.e(TAG, "FirebaseAuth가 null입니다.");
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, "인증 서비스 초기화 오류가 발생했습니다.");
            return;
        }

        try {
            // GoogleAuthProvider.getCredential의 두 번째 파라미터는 accessToken으로,
            // Google Sign-In에서는 idToken만으로 충분하므로 null 사용
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
            if (credential == null) {
                Log.e(TAG, "AuthCredential을 생성할 수 없습니다.");
                setSigningInState(false);
                GoogleSignInUtils.showToast(this, R.string.sign_in_failed);
                return;
            }

            firebaseAuth.signInWithCredential(credential)
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                AuthResult result = task.getResult();
                                FirebaseUser user = result != null ? result.getUser() : null;
                                
                                if (user == null) {
                                    user = GoogleSignInUtils.getCurrentUser();
                                }
                                
                                if (user != null) {
                                    Log.d(TAG, "Firebase 인증 성공: " + user.getEmail());
                                    updateUserInFirestore(user);
                                } else {
                                    Log.w(TAG, "Firebase 인증은 성공했으나 사용자 정보가 null입니다.");
                                    setSigningInState(false);
                                    GoogleSignInUtils.showToast(LoginActivity.this, "사용자 정보를 가져올 수 없습니다.");
                                }
                            } else {
                                Log.w(TAG, "Firebase 인증 실패", task.getException());
                                setSigningInState(false);
                                Exception exception = task.getException();
                                String errorMessage = getString(R.string.firebase_auth_failed);
                                if (exception != null) {
                                    String exceptionMessage = exception.getMessage();
                                    if (exceptionMessage != null) {
                                        if (exceptionMessage.toLowerCase().contains("network") || 
                                            exceptionMessage.toLowerCase().contains("connect")) {
                                            errorMessage = "네트워크 연결을 확인해주세요.";
                                        } else if (exceptionMessage.toLowerCase().contains("invalid") ||
                                                   exceptionMessage.toLowerCase().contains("credential")) {
                                            errorMessage = "인증 정보가 유효하지 않습니다.";
                                        } else if (exceptionMessage.toLowerCase().contains("quota")) {
                                            errorMessage = "서버 부하로 인해 잠시 후 다시 시도해주세요.";
                                        }
                                    }
                                }
                                GoogleSignInUtils.showToast(LoginActivity.this, errorMessage);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Firebase 인증 시작 중 오류 발생", e);
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, "인증 처리 중 오류가 발생했습니다.");
        }
    }

    private void updateUserInFirestore(FirebaseUser user) {
        if (user == null) {
            Log.e(TAG, "FirebaseUser가 null입니다.");
            GoogleSignInUtils.showToast(this, R.string.firebase_auth_failed);
            return;
        }

        if (firebaseFirestore == null) {
            Log.e(TAG, "Firestore가 초기화되지 않았습니다.");
            navigateToMainActivity(true); // Firestore 저장 실패해도 로그인은 진행
            return;
        }

        String uid = user.getUid();
        if (uid == null || uid.isEmpty()) {
            Log.e(TAG, "사용자 UID가 null이거나 비어있습니다.");
            navigateToMainActivity(true); // UID 없어도 로그인은 진행
            return;
        }

        navigateToMainActivity(true);

        // Firestore 업데이트는 백그라운드에서 비동기로 처리
        DocumentReference userRef = firebaseFirestore.collection("users").document(uid);
        userRef.get().addOnCompleteListener(task -> {
            // Activity가 종료되었는지 확인
            if (isFinishing() || isDestroyed()) {
                Log.d(TAG, "Activity가 종료되어 Firestore 업데이트를 건너뜁니다.");
                return;
            }
            
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Log.d(TAG, "기존 사용자입니다: " + user.getEmail());
                    updateExistingUserInBackground(userRef, user);
                } else {
                    Log.d(TAG, "신규 사용자입니다: " + user.getEmail());
                    createNewUserInBackground(userRef, user);
                }
            } else {
                Log.w(TAG, "Firestore 문서 조회 실패", task.getException());
            }
        });
    }

    private Map<String, Object> extractUserData(FirebaseUser user) {
        Map<String, Object> userData = new HashMap<>();
        String email = user.getEmail();
        if (email != null) {
            userData.put("email", email);
        }
        String displayName = user.getDisplayName();
        if (displayName != null) {
            userData.put("displayName", displayName);
        }
        String photoURL = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
        if (photoURL != null && !photoURL.isEmpty()) {
            userData.put("photoURL", photoURL);
        }
        return userData;
    }

    private void updateExistingUserInBackground(DocumentReference userRef, FirebaseUser user) {
        // Activity가 종료되었는지 확인
        if (isFinishing() || isDestroyed()) {
            Log.d(TAG, "Activity가 종료되어 사용자 업데이트를 건너뜁니다.");
            return;
        }
        
        Map<String, Object> updates = extractUserData(user);
        // email은 업데이트에서 제외
        updates.remove("email");

        if (!updates.isEmpty()) {
            userRef.update(updates)
                    .addOnSuccessListener(unused -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Log.d(TAG, "사용자 정보 업데이트 성공");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Log.w(TAG, "사용자 정보 업데이트 실패", e);
                        }
                    });
        } else {
            Log.d(TAG, "업데이트할 내용이 없습니다.");
        }
    }

    private void createNewUserInBackground(DocumentReference userRef, FirebaseUser user) {
        // Activity가 종료되었는지 확인
        if (isFinishing() || isDestroyed()) {
            Log.d(TAG, "Activity가 종료되어 신규 사용자 생성을 건너뜁니다.");
            return;
        }
        
        Map<String, Object> newUser = extractUserData(user);
        newUser.put("role", "user");
        newUser.put("createdAt", FieldValue.serverTimestamp());

        userRef.set(newUser)
                .addOnSuccessListener(unused -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Log.d(TAG, "신규 사용자 정보 Firestore에 저장 성공");
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Log.w(TAG, "신규 사용자 정보 저장 실패", e);
                    }
                });
    }

    private void navigateToMainActivity(boolean showSuccessMessage) {
        setSigningInState(false); // 로그인 상태 초기화
        
        // Activity가 이미 종료 중이면 화면 전환 불가
        if (isFinishing()) {
            Log.w(TAG, "Activity가 이미 종료 중입니다. 화면 전환 불가.");
            return;
        }
        
        Intent intent = createMainActivityIntent();
        
        if (showSuccessMessage) {
            // 성공 메시지 표시 (Toast는 짧게 표시)
            // GoogleSignInUtils를 통해 현재 사용자 가져오기 (일관성 유지)
            FirebaseUser currentUser = GoogleSignInUtils.getCurrentUser();
            String displayName = GoogleSignInUtils.getUserDisplayName(currentUser);
            if (displayName != null && !displayName.isEmpty()) {
                String successMessage = getString(R.string.sign_in_success, displayName);
                GoogleSignInUtils.showToast(this, successMessage);
            }
            // 성공 메시지와 함께 즉시 이동 (딜레이 제거)
        }
        
        // 화면 전환 실행
        try {
            startActivity(intent);
            finish();
            Log.d(TAG, "MainActivity로 이동 성공");
        } catch (Exception e) {
            Log.e(TAG, "MainActivity로 이동 실패", e);
            setSigningInState(false);
            GoogleSignInUtils.showToast(this, "화면 전환 중 오류가 발생했습니다.");
        }
    }

    private Intent createMainActivityIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 로그인 상태 초기화
        isSigningIn = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Activity가 백그라운드로 가도 로그인 프로세스는 계속 진행될 수 있도록
        // 상태는 유지하되, UI 업데이트는 주의해야 함
    }
}
