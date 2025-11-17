package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.List;

public class SketchRunActivity extends AppCompatActivity {

    private RecyclerView recyclerViewCourses;
    private CourseAdapter courseAdapter;
    private ImageButton backButton;
    private Button btnCourseDetail;
    private Button btnFindLocation;
    private Button btnStartRun;
    private TextView tvCourseTotalDistance;
    private TextView tvCourseEstimatedTime;
    private TextView tvDifficulty;

    private Course selectedCourse = null;
    private FirebaseFirestore firestore;
    private List<Course> courses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sketch_run);

        initViews();
        initFirestore();
        loadCoursesFromFirestore();
        setupClickListeners();
    }

    private void initFirestore() {
        firestore = GoogleSignInUtils.getFirestore();
    }

    private void initViews() {
        recyclerViewCourses = findViewById(R.id.recyclerViewCourses);
        backButton = findViewById(R.id.backButton);
        btnCourseDetail = findViewById(R.id.btnCourseDetail);
        btnFindLocation = findViewById(R.id.btnFindLocation);
        btnStartRun = findViewById(R.id.btnStartRun);
        tvCourseTotalDistance = findViewById(R.id.tvCourseTotalDistance);
        tvCourseEstimatedTime = findViewById(R.id.tvCourseEstimatedTime);
        tvDifficulty = findViewById(R.id.tvDifficulty);
    }

    private void loadCoursesFromFirestore() {
        firestore.collection("courses")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        courses.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Course course = documentToCourse(document);
                            if (course != null) {
                                courses.add(course);
                            }
                        }
                        setupRecyclerView();
                        
                        if (!courses.isEmpty()) {
                            selectedCourse = courses.get(0);
                            updateCourseInfo(courses.get(0));
                        } else {
                            GoogleSignInUtils.showToast(this, "등록된 코스가 없습니다.");
                        }
                    } else {
                        Log.w("SketchRunActivity", "코스 로드 실패", task.getException());
                        GoogleSignInUtils.showToast(this, "코스를 불러오는데 실패했습니다.");
                    }
                });
    }

    private Course documentToCourse(QueryDocumentSnapshot document) {
        try {
            Course course = new Course();
            course.setId(document.getId());
            
            if (document.contains("name")) {
                course.setName(document.getString("name"));
            }
            if (document.contains("description")) {
                course.setDescription(document.getString("description"));
            }
            if (document.contains("totalDistance")) {
                course.setTotalDistance(document.getDouble("totalDistance"));
            }
            if (document.contains("difficulty")) {
                course.setDifficulty(document.getString("difficulty"));
            }
            if (document.contains("estimatedTime")) {
                Long estimatedTime = document.getLong("estimatedTime");
                if (estimatedTime != null) {
                    course.setEstimatedTimeSeconds(estimatedTime.intValue());
                }
            }
            if (document.contains("pathEncoded")) {
                course.setPathEncoded(document.getString("pathEncoded"));
            }
            if (document.contains("startMarker")) {
                course.setStartMarker(document.getGeoPoint("startMarker"));
            }
            if (document.contains("endMarker")) {
                course.setEndMarker(document.getGeoPoint("endMarker"));
            }
            if (document.contains("adminCreatorId")) {
                course.setAdminCreatorId(document.getString("adminCreatorId"));
            }
            if (document.contains("createdAt")) {
                Object createdAt = document.get("createdAt");
                if (createdAt instanceof com.google.firebase.Timestamp) {
                    course.setCreatedAt(((com.google.firebase.Timestamp) createdAt).toDate().getTime());
                }
            }
            
            return course;
        } catch (Exception e) {
            Log.e("SketchRunActivity", "코스 변환 실패", e);
            return null;
        }
    }

    private void setupRecyclerView() {
        courseAdapter = new CourseAdapter(courses, course -> {
            selectedCourse = course;
            updateCourseInfo(course);
        });

        GoogleSignInUtils.setupRecyclerView(recyclerViewCourses, courseAdapter, this);
    }

    private void updateCourseInfo(Course course) {
        tvCourseTotalDistance.setText(course.getDistanceFormatted());
        tvCourseEstimatedTime.setText(course.getEstimatedTimeFormatted());
        tvDifficulty.setText(course.getDifficultyKorean());
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        btnCourseDetail.setOnClickListener(v -> {
            if (selectedCourse != null) {
                GoogleSignInUtils.showToast(this, selectedCourse.getName() + " 상세 정보");
                // 코스 상세 화면으로 이동하는 로직 추가
            } else {
                showCourseNotSelectedMessage();
            }
        });

        btnFindLocation.setOnClickListener(v -> {
            Intent intent = new Intent(SketchRunActivity.this, RunningStartActivity.class);
            startActivity(intent);
        });

        btnStartRun.setOnClickListener(v -> {
            if (selectedCourse != null) {
                Intent intent = new Intent(SketchRunActivity.this, RunningStartActivity.class);
                intent.putExtra("course_id", selectedCourse.getId());
                intent.putExtra("course_name", selectedCourse.getName());
                intent.putExtra("course_distance", selectedCourse.getDistance());
                intent.putExtra("course_difficulty", selectedCourse.getDifficulty());
                startActivity(intent);
            } else {
                showCourseNotSelectedMessage();
            }
        });
    }

    private void showCourseNotSelectedMessage() {
        GoogleSignInUtils.showToast(this, "코스를 선택해주세요");
    }
}

