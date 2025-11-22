package Run.U;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GoogleSignInUtils {

    private static final String TAG = "GoogleSignInUtils";
    
    private static FirebaseFirestore firestore;
    private static FirebaseAuth firebaseAuth;
    private static GoogleSignInOptions cachedSignInOptions;
    private static String cachedWebClientId;

    public static GoogleSignInOptions getGoogleSignInOptions(Context context) {
        // Web Client ID는 변경되지 않으므로 캐싱 가능
        String webClientId = context.getString(R.string.default_web_client_id);
        
        // 캐시된 옵션이 있고 Web Client ID가 같으면 재사용
        if (cachedSignInOptions != null && webClientId.equals(cachedWebClientId)) {
            return cachedSignInOptions;
        }
        
        // 새로운 옵션 생성 및 캐싱
        cachedSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        cachedWebClientId = webClientId;
        
        return cachedSignInOptions;
    }

    public static GoogleSignInClient getGoogleSignInClient(Context context) {
        // Google SDK가 내부적으로 캐싱을 하지만, Options는 우리가 캐싱
        return GoogleSignIn.getClient(context, getGoogleSignInOptions(context));
    }

    public static FirebaseFirestore getFirestore() {
        if (firestore == null) {
            firestore = FirebaseFirestore.getInstance();
        }
        return firestore;
    }

    public static FirebaseAuth getAuth() {
        if (firebaseAuth == null) {
            firebaseAuth = FirebaseAuth.getInstance();
        }
        return firebaseAuth;
    }

    public static FirebaseUser getCurrentUser() {
        return getAuth().getCurrentUser();
    }

    public static FirebaseUser requireCurrentUser(Context context) {
        FirebaseUser user = getCurrentUser();
        if (user == null && context != null) {
            Log.w(TAG, "사용자가 로그인되어 있지 않습니다.");
            showToast(context, "로그인이 필요합니다.");
        }
        return user;
    }

    public static String getUserDisplayName(FirebaseUser user) {
        if (user == null) {
            return null;
        }
        
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            // email이 null일 수 있으므로 체크
            String email = user.getEmail();
            displayName = (email != null && !email.isEmpty()) ? email : null;
        }
        return displayName;
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }
        
        try {
            Context appContext = context.getApplicationContext();
            ConnectivityManager connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            
            // Android 10 (API 29) 이상에서는 NetworkCapabilities 사용
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network == null) {
                    return false;
                }
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities == null) {
                    return false;
                }
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                       capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            } else {
                // Android 9 이하에서는 기존 API 사용 (deprecated이지만 하위 호환성)
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.w(TAG, "네트워크 확인 중 오류 발생", e);
        }
        return false;
    }

    public static void setupRecyclerView(RecyclerView recyclerView, RecyclerView.Adapter adapter, Context context) {
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
    }

    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void showToast(Context context, int resId) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
    }

    public static LatLng toLatLng(GeoPoint geoPoint) {
        if (geoPoint == null) {
            return null;
        }
        return new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
    }

    public static List<LatLng> toLatLngList(List<GeoPoint> geoPoints) {
        List<LatLng> result = new ArrayList<>();
        if (geoPoints != null) {
            for (GeoPoint point : geoPoints) {
                result.add(toLatLng(point));
            }
        }
        return result;
    }

    public static String formatElapsedTime(long elapsedTimeMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTimeMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMs) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatElapsedTimeShort(long elapsedTimeMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTimeMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMs) % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public static String formatElapsedTimeWithLabel(long elapsedTimeMs) {
        long totalHours = elapsedTimeMs / (1000 * 60 * 60);
        long totalMinutes = (elapsedTimeMs % (1000 * 60 * 60)) / (1000 * 60);
        if (totalHours > 0) {
            return String.format("%d시간 %d분", totalHours, totalMinutes);
        } else {
            return String.format("%d분", totalMinutes);
        }
    }

    public static String formatDistanceKm(double distanceKm) {
        return String.format("%.1fkm", distanceKm);
    }

    public static String formatPaceFromSeconds(double paceSeconds) {
        int minutes = (int) (paceSeconds / 60);
        int seconds = (int) (paceSeconds % 60);
        return String.format("%02d:%02d/km", minutes, seconds);
    }

    public static double parsePaceToSeconds(String paceString) {
        if (paceString == null || paceString.isEmpty() || paceString.equals("--:--/km")) {
            return 0.0;
        }
        try {
            String timePart = paceString.replace("/km", "").trim();
            String[] parts = timePart.split(":");
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60.0 + seconds;
            }
        } catch (Exception e) {
        }
        return 0.0;
    }

    public static void checkAdminRole(FirebaseUser user, AdminRoleCallback callback) {
        if (user == null || callback == null) {
            callback.onResult(false);
            return;
        }

        String uid = user.getUid();
        if (uid == null || uid.isEmpty()) {
            callback.onResult(false);
            return;
        }

        getFirestore().collection("users")
                .document(uid)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            String role = document.getString("role");
                            if (role != null) {
                                role = role.trim().toLowerCase();
                            }
                            boolean isAdmin = "admin".equals(role);
                            Log.d(TAG, "사용자 권한 확인 - UID: " + uid + ", Role: " + role + ", IsAdmin: " + isAdmin);
                            callback.onResult(isAdmin);
                        } else {
                            Log.d(TAG, "사용자 문서가 존재하지 않음 - UID: " + uid);
                            callback.onResult(false);
                        }
                    } else {
                        Log.w(TAG, "관리자 권한 확인 실패 - UID: " + uid, task.getException());
                        callback.onResult(false);
                    }
                });
    }

    public static void signOut(Context context, Runnable onComplete) {
        if (context == null) {
            Log.w(TAG, "Context가 null입니다. 로그아웃을 수행할 수 없습니다.");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // Firebase 로그아웃 (getAuth()로 재확인하여 안전하게 처리)
        FirebaseAuth auth = getAuth();
        if (auth != null) {
            auth.signOut();
        }

        // Google 로그아웃
        GoogleSignInClient signInClient = getGoogleSignInClient(context);
        if (signInClient != null) {
            signInClient.signOut().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "로그아웃 성공");
                } else {
                    Log.w(TAG, "Google 로그아웃 실패", task.getException());
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        } else {
            Log.w(TAG, "GoogleSignInClient가 null입니다.");
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public interface AdminRoleCallback {
        void onResult(boolean isAdmin);
    }
}

