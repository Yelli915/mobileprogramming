package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleSignInResult
        );

        android.widget.Button googleSignInButton = findViewById(R.id.google_sign_in_button);
        googleSignInButton.setOnClickListener(v -> signIn());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            updateUserInFirestore(currentUser);
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
                            Toast.makeText(LoginActivity.this, R.string.firebase_auth_failed, Toast.LENGTH_SHORT).show();
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

