package Run.U;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import java.util.HashMap;
import java.util.Map;

public class AdminCourseActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;

    private EditText courseNameEditText;
    private EditText courseDescriptionEditText;
    private EditText courseDistanceEditText;
    private EditText courseDifficultyEditText;
    private EditText courseEstimatedTimeEditText;
    private EditText coursePathEncodedEditText;
    private MaterialButton saveCourseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_course);

        firestore = GoogleSignInUtils.getFirestore();
        firebaseAuth = GoogleSignInUtils.getAuth();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        courseNameEditText = findViewById(R.id.course_name_edit);
        courseDescriptionEditText = findViewById(R.id.course_description_edit);
        courseDistanceEditText = findViewById(R.id.course_distance_edit);
        courseDifficultyEditText = findViewById(R.id.course_difficulty_edit);
        courseEstimatedTimeEditText = findViewById(R.id.course_estimated_time_edit);
        coursePathEncodedEditText = findViewById(R.id.course_path_encoded_edit);
        saveCourseButton = findViewById(R.id.save_course_button);

        if (saveCourseButton != null) {
            saveCourseButton.setOnClickListener(v -> saveCourse());
        }

        checkAdminPermission();
    }

    private void checkAdminPermission() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            finish();
            return;
        }

        GoogleSignInUtils.checkAdminRole(currentUser, isAdmin -> {
            if (!isAdmin) {
                GoogleSignInUtils.showToast(this, "관리자 권한이 필요합니다.");
                finish();
            }
        });
    }

    private void saveCourse() {
        if (courseNameEditText == null || courseDescriptionEditText == null ||
            courseDistanceEditText == null || courseDifficultyEditText == null ||
            courseEstimatedTimeEditText == null || coursePathEncodedEditText == null) {
            return;
        }

        String name = courseNameEditText.getText().toString().trim();
        String description = courseDescriptionEditText.getText().toString().trim();
        String distanceStr = courseDistanceEditText.getText().toString().trim();
        String difficulty = courseDifficultyEditText.getText().toString().trim();
        String estimatedTimeStr = courseEstimatedTimeEditText.getText().toString().trim();
        String pathEncoded = coursePathEncodedEditText.getText().toString().trim();

        if (name.isEmpty()) {
            GoogleSignInUtils.showToast(this, "코스 이름을 입력해주세요.");
            return;
        }

        if (description.isEmpty()) {
            GoogleSignInUtils.showToast(this, "코스 설명을 입력해주세요.");
            return;
        }

        if (distanceStr.isEmpty()) {
            GoogleSignInUtils.showToast(this, "거리를 입력해주세요.");
            return;
        }

        if (difficulty.isEmpty()) {
            GoogleSignInUtils.showToast(this, "난이도를 입력해주세요. (easy, medium, hard)");
            return;
        }

        if (estimatedTimeStr.isEmpty()) {
            GoogleSignInUtils.showToast(this, "예상 시간을 입력해주세요. (분 단위)");
            return;
        }

        if (pathEncoded.isEmpty()) {
            GoogleSignInUtils.showToast(this, "경로 데이터를 입력해주세요.");
            return;
        }

        try {
            double distance = Double.parseDouble(distanceStr);
            int estimatedTime = Integer.parseInt(estimatedTimeStr);

            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser == null) {
                GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
                return;
            }

            Map<String, Object> courseData = new HashMap<>();
            courseData.put("name", name);
            courseData.put("description", description);
            courseData.put("totalDistance", distance * 1000.0);
            courseData.put("difficulty", difficulty);
            courseData.put("estimatedTime", estimatedTime * 60);
            courseData.put("pathEncoded", pathEncoded);
            courseData.put("adminCreatorId", currentUser.getUid());
            courseData.put("createdAt", FieldValue.serverTimestamp());

            firestore.collection("courses")
                    .add(courseData)
                    .addOnSuccessListener(documentReference -> {
                        Log.d("AdminCourseActivity", "코스 저장 성공: " + documentReference.getId());
                        GoogleSignInUtils.showToast(this, "코스가 등록되었습니다.");
                        
                        new AlertDialog.Builder(this)
                                .setTitle("등록 완료")
                                .setMessage("코스가 성공적으로 등록되었습니다.")
                                .setPositiveButton("확인", (dialog, which) -> finish())
                                .show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AdminCourseActivity", "코스 저장 실패", e);
                        GoogleSignInUtils.showToast(this, "코스 등록에 실패했습니다: " + e.getMessage());
                    });

        } catch (NumberFormatException e) {
            GoogleSignInUtils.showToast(this, "숫자 형식이 올바르지 않습니다.");
        }
    }
}

