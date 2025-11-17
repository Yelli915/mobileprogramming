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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        firebaseAuth = GoogleSignInUtils.getAuth();

        welcomeText = findViewById(R.id.welcome_text);
        settingsButton = findViewById(R.id.settings_button);
        startRunButton = findViewById(R.id.start_run_button);

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        startRunButton.setOnClickListener(v -> showRunOptions());

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