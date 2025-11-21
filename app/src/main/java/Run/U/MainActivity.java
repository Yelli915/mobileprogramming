package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private TextView welcomeText;
    private ImageButton settingsButton;
    private MaterialButton startNormalRunButton;
    private MaterialButton startCourseRunButton;

    // 통계 TextViews
    private TextView totalDistanceText;
    private TextView totalTimeText;
    private TextView runCountText;

    // 최근 기록
    private LinearLayout recentRunsList;
    private TextView noRunsText;
    private TextView viewAllButton;

    // 통계 TextViews
    private TextView totalDistanceText;
    private TextView totalTimeText;
    private TextView runCountText;

    // 최근 기록
    private LinearLayout recentRunsList;
    private TextView noRunsText;
    private TextView viewAllButton;

    // 관리자 카드 및 버튼
    private androidx.cardview.widget.CardView adminCard;
    private MaterialButton adminCourseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        firebaseAuth = GoogleSignInUtils.getAuth();
        firestore = GoogleSignInUtils.getFirestore();

        // View 초기화
        welcomeText = findViewById(R.id.welcome_text);
        settingsButton = findViewById(R.id.settings_button);
        startNormalRunButton = findViewById(R.id.start_normal_run_button);
        startCourseRunButton = findViewById(R.id.start_course_run_button);

        totalDistanceText = findViewById(R.id.total_distance_text);
        totalTimeText = findViewById(R.id.total_time_text);
        runCountText = findViewById(R.id.run_count_text);

        // 통계 및 기록 뷰 초기화 (레이아웃에 있는 경우)
        totalDistanceText = findViewById(R.id.total_distance_text);
        totalTimeText = findViewById(R.id.total_time_text);
        runCountText = findViewById(R.id.run_count_text);
        recentRunsList = findViewById(R.id.recent_runs_list);
        noRunsText = findViewById(R.id.no_runs_text);
        viewAllButton = findViewById(R.id.view_all_button);

        // 관리자 뷰 초기화
        adminCard = findViewById(R.id.admin_card);
        adminCourseButton = findViewById(R.id.admin_course_button);

        // 버튼 클릭 리스너
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        startNormalRunButton.setOnClickListener(v -> startNormalRun());
        startCourseRunButton.setOnClickListener(v -> startCourseSelection());

        viewAllButton.setOnClickListener(v -> {
            // 전체 기록 보기 Activity로 이동
            Intent intent = new Intent(MainActivity.this, RunningRecordActivity.class);
            startActivity(intent);
        });

        // 관리자 코스 등록 버튼
        if (adminCourseButton != null) {
            adminCourseButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AdminCourseActivity.class);
                startActivity(intent);
            });
        }

        // 전체 기록 보기 버튼
        if (viewAllButton != null) {
            viewAllButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, RunningRecordActivity.class);
                startActivity(intent);
            });
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateWelcomeMessage();
        loadWeeklyStats();
        loadRecentRuns();
        checkAdminRole();
    }

    private void updateWelcomeMessage() {
        if (welcomeText == null) {
            return;
        }
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = GoogleSignInUtils.getUserDisplayName(currentUser);
            if (displayName != null && !displayName.isEmpty()) {
                if (displayName.contains("@")) {
                    displayName = displayName.split("@")[0];
                }
                try {
                    String welcomeMessage = getString(R.string.welcome_message, displayName);
                    welcomeText.setText(welcomeMessage);
                } catch (Exception e) {
                    welcomeText.setText(displayName + "님, 안녕하세요!");
                }
            }
        }
    }

    private void checkAdminRole() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            if (adminCard != null) {
                adminCard.setVisibility(View.GONE);
            }
            return;
        }

        GoogleSignInUtils.checkAdminRole(currentUser, isAdmin -> {
            if (isAdmin && adminCard != null) {
                adminCard.setVisibility(View.VISIBLE);
            } else if (adminCard != null) {
                adminCard.setVisibility(View.GONE);
            }
        });
    }

    private void loadWeeklyStats() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || totalDistanceText == null || totalTimeText == null || runCountText == null) {
            return;
        }

        // 이번 주 시작일 계산 (월요일)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date weekStart = calendar.getTime();

        String userId = currentUser.getUid();

        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .whereGreaterThanOrEqualTo("startTime", weekStart)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalDistance = 0.0;
                    long totalTime = 0;
                    int runCount = queryDocumentSnapshots.size();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double distance = doc.getDouble("totalDistance");
                        Long time = doc.getLong("totalTime");

                        if (distance != null) {
                            totalDistance += distance; // 미터 단위
                        }
                        if (time != null) {
                            totalTime += time; // 초 단위
                        }
                    }

                    // UI 업데이트
                    if (totalDistanceText != null) {
                        totalDistanceText.setText(String.format("%.1f km", totalDistance / 1000.0));
                    }
                    if (totalTimeText != null) {
                        long hours = totalTime / 3600;
                        long minutes = (totalTime % 3600) / 60;
                        if (hours > 0) {
                            totalTimeText.setText(String.format("%d:%02d", hours, minutes));
                        } else {
                            totalTimeText.setText(String.format("%d분", minutes));
                        }
                    }
                    if (runCountText != null) {
                        runCountText.setText(String.format("%d회", runCount));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "주간 통계 로드 실패", e);
                });
    }

    private void loadRecentRuns() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || recentRunsList == null || noRunsText == null) {
            return;
        }

        String userId = currentUser.getUid();

        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        if (noRunsText != null) {
                            noRunsText.setVisibility(View.VISIBLE);
                        }
                        if (recentRunsList != null) {
                            recentRunsList.setVisibility(View.GONE);
                        }
                    } else {
                        if (noRunsText != null) {
                            noRunsText.setVisibility(View.GONE);
                        }
                        if (recentRunsList != null) {
                            recentRunsList.setVisibility(View.VISIBLE);
                            // 기존 아이템 제거
                            recentRunsList.removeAllViews();
                            // 최근 기록 추가
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                addRecentRunItem(doc);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "최근 기록 로드 실패", e);
                });
    }

    private void addRecentRunItem(QueryDocumentSnapshot doc) {
        if (recentRunsList == null) {
            return;
        }

        // 간단한 기록 아이템 생성
        TextView itemView = new TextView(this);
        Double distance = doc.getDouble("totalDistance");
        Long time = doc.getLong("totalTime");

        String distanceStr = distance != null ?
                String.format("%.2f km", distance / 1000.0) : "0.00 km";

        String timeStr = "";
        if (time != null) {
            long minutes = time / 60;
            long seconds = time % 60;
            timeStr = String.format("%d:%02d", minutes, seconds);
        }

        itemView.setText(String.format("📍 %s • ⏱ %s", distanceStr, timeStr));
        itemView.setTextSize(14);
        itemView.setTextColor(getResources().getColor(R.color.accent_white, null));
        itemView.setPadding(0, 16, 0, 16);
        recentRunsList.addView(itemView);
    }

    private void showRunOptions() {
        new AlertDialog.Builder(this)
                .setTitle("운동 시작")
                .setItems(new String[]{"일반 운동 시작", "코스 선택하기"}, (dialog, which) -> {
                    if (which == 0) {
                        startNormalRun();
                    } else {
                        noRunsText.setVisibility(View.GONE);
                        recentRunsList.setVisibility(View.VISIBLE);

                        // 기존 아이템 제거 (no_runs_text 제외)
                        recentRunsList.removeAllViews();

                        // 최근 기록 추가
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            addRecentRunItem(doc);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "최근 기록 로드 실패", e);
                });
    }

    private void addRecentRunItem(QueryDocumentSnapshot doc) {
        // 간단한 기록 아이템 생성
        TextView itemView = new TextView(this);

        Double distance = doc.getDouble("totalDistance");
        Long time = doc.getLong("totalTime");

        String distanceStr = distance != null ?
                String.format("%.2f km", distance / 1000.0) : "0.00 km";
        String timeStr = "";

        if (time != null) {
            long minutes = time / 60;
            long seconds = time % 60;
            timeStr = String.format("%d:%02d", minutes, seconds);
        }

        itemView.setText(String.format("📍 %s • ⏱ %s", distanceStr, timeStr));
        itemView.setTextSize(14);
        itemView.setTextColor(getResources().getColor(R.color.accent_white, null));
        itemView.setPadding(0, 16, 0, 16);

        recentRunsList.addView(itemView);
    }

    private void startNormalRun() {
        Intent intent = new Intent(MainActivity.this, RunningStartActivity.class);
        startActivity(intent);
    }

    private void startCourseSelection() {
        Intent intent = new Intent(MainActivity.this, SketchRunActivity.class);
        startActivity(intent);
    }
}
