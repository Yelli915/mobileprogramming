package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private TextView welcomeText;
    private ImageButton settingsButton;
    private Button startRunButton;

    // 관리자 카드 및 버튼
    private androidx.cardview.widget.CardView adminCard;
    private MaterialButton adminCourseButton;

    // 관리자 카드 및 버튼
    private androidx.cardview.widget.CardView adminCard;
    private MaterialButton adminCourseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        firebaseAuth = GoogleSignInUtils.getAuth();

        welcomeText = findViewById(R.id.welcome_text);
        settingsButton = findViewById(R.id.settings_button);
        startRunButton = findViewById(R.id.start_run_button);

<<<<<<< Updated upstream
=======
        totalDistanceText = findViewById(R.id.total_distance_text);
        totalTimeText = findViewById(R.id.total_time_text);
        runCountText = findViewById(R.id.run_count_text);

        recentRunsList = findViewById(R.id.recent_runs_list);
        noRunsText = findViewById(R.id.no_runs_text);
        viewAllButton = findViewById(R.id.view_all_button);

        adminCard = findViewById(R.id.admin_card);
        adminCourseButton = findViewById(R.id.admin_course_button);

        // 버튼 클릭 리스너
>>>>>>> Stashed changes
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

<<<<<<< Updated upstream
        startRunButton.setOnClickListener(v -> showRunOptions());
=======
        startNormalRunButton.setOnClickListener(v -> startNormalRun());
        startCourseRunButton.setOnClickListener(v -> startCourseSelection());

        if (adminCourseButton != null) {
            adminCourseButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AdminCourseActivity.class);
                startActivity(intent);
            });
        }

        viewAllButton.setOnClickListener(v -> {
            // 전체 기록 보기 Activity로 이동
            Intent intent = new Intent(MainActivity.this, RunningRecordActivity.class);
            startActivity(intent);
        });
>>>>>>> Stashed changes

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
<<<<<<< Updated upstream
=======
        loadWeeklyStats();
        loadRecentRuns();
        checkAdminRole();
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
<<<<<<< Updated upstream
>>>>>>> Stashed changes
=======
>>>>>>> Stashed changes
    }

    private void updateWelcomeMessage() {
        if (welcomeText == null) {
            return;
        }
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = GoogleSignInUtils.getUserDisplayName(currentUser);
            if (displayName != null && !displayName.isEmpty()) {
                // 이메일 주소인 경우 @ 앞부분만 사용
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

    private void showRunOptions() {
        new AlertDialog.Builder(this)
                .setTitle("운동 시작")
                .setItems(new String[]{"일반 운동 시작", "코스 선택하기"}, (dialog, which) -> {
                    if (which == 0) {
                        startNormalRun();
                    } else {
                        startCourseSelection();
                    }
                })
                .show();
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