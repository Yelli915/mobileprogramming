package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class SketchRunActivity extends AppCompatActivity {

    private RecyclerView recyclerViewCourses;
    private CourseAdapter courseAdapter;
    private MaterialToolbar toolbar;
    private MaterialButton btnCourseDetail;
    private FloatingActionButton btnFindLocation;
    private MaterialButton btnStartRun;
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
        setupToolbar();
        loadCoursesFromFirestore();
        setupClickListeners();
    }

    private void initFirestore() {
        firestore = GoogleSignInUtils.getFirestore();
    }

    private void initViews() {
        // MaterialToolbar 초기화
        toolbar = findViewById(R.id.toolbar);

        // RecyclerView 초기화
        recyclerViewCourses = findViewById(R.id.recyclerViewCourses);

        // Material3 버튼들 초기화
        btnCourseDetail = findViewById(R.id.btnCourseDetail);
        btnFindLocation = findViewById(R.id.btnFindLocation);
        btnStartRun = findViewById(R.id.btnStartRun);

        // 코스 정보 TextView 초기화
        tvCourseTotalDistance = findViewById(R.id.tvCourseTotalDistance);
        tvCourseEstimatedTime = findViewById(R.id.tvCourseEstimatedTime);
        tvDifficulty = findViewById(R.id.tvDifficulty);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // 뒤로가기 버튼 클릭 리스너
        toolbar.setNavigationOnClickListener(v -> finish());
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

        // 수평 스크롤 RecyclerView 설정
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
        );
        recyclerViewCourses.setLayoutManager(layoutManager);
        recyclerViewCourses.setAdapter(courseAdapter);

        // 스크롤 성능 최적화
        recyclerViewCourses.setHasFixedSize(true);
    }

    private void updateCourseInfo(Course course) {
        tvCourseTotalDistance.setText(course.getDistanceFormatted());
        tvCourseEstimatedTime.setText(course.getEstimatedTimeFormatted());
        tvDifficulty.setText(course.getDifficultyKorean());
    }

    private void setupClickListeners() {
        // 코스 상세 보기 버튼
        btnCourseDetail.setOnClickListener(v -> {
            if (selectedCourse != null) {
                GoogleSignInUtils.showToast(this, selectedCourse.getName() + " 상세 정보");
                // TODO: 코스 상세 화면으로 이동
                // Intent intent = new Intent(this, CourseDetailActivity.class);
                // intent.putExtra("course_id", selectedCourse.getId());
                // startActivity(intent);
            } else {
                showCourseNotSelectedMessage();
            }
        });

        // 내 위치 찾기 버튼 (지도에서 현재 위치로 이동)
        btnFindLocation.setOnClickListener(v -> {
            GoogleSignInUtils.showToast(this, "내 위치로 이동합니다");
            // TODO: 지도에서 현재 위치로 이동하는 로직 구현
            // mapView.moveToCurrentLocation();
        });

        // 스케치 런 시작 버튼
        btnStartRun.setOnClickListener(v -> {
            if (selectedCourse != null) {
                Intent intent = new Intent(SketchRunActivity.this, RunningStartActivity.class);
                intent.putExtra("course_id", selectedCourse.getId());
                intent.putExtra("course_name", selectedCourse.getName());
                intent.putExtra("course_distance", selectedCourse.getDistance());
                intent.putExtra("course_difficulty", selectedCourse.getDifficulty());
                intent.putExtra("course_path", selectedCourse.getPathEncoded());
                startActivity(intent);
            } else {
                showCourseNotSelectedMessage();
            }
        });
    }

    private void showCourseNotSelectedMessage() {
        GoogleSignInUtils.showToast(this, "코스를 선택해주세요");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
