package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class CourseListActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private MaterialButton btnCategoryEasy;
    private MaterialButton btnCategoryMedium;
    private MaterialButton btnCategoryHard;
    private RecyclerView recyclerViewCourses;
    private TextView tvEmptyMessage;
    
    private CourseAdapter courseAdapter;
    private FirebaseFirestore firestore;
    private List<Course> allCourses = new ArrayList<>();
    private List<Course> filteredCourses = new ArrayList<>();
    private String selectedCategory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_list);

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
        toolbar = findViewById(R.id.toolbar);
        btnCategoryEasy = findViewById(R.id.btnCategoryEasy);
        btnCategoryMedium = findViewById(R.id.btnCategoryMedium);
        btnCategoryHard = findViewById(R.id.btnCategoryHard);
        recyclerViewCourses = findViewById(R.id.recyclerViewCourses);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadCoursesFromFirestore() {
        firestore.collection("courses")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        allCourses.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Course course = documentToCourse(document);
                            if (course != null) {
                                allCourses.add(course);
                            }
                        }
                        Log.d("CourseListActivity", "전체 코스 로드 완료: " + allCourses.size() + "개");
                    } else {
                        Log.w("CourseListActivity", "코스 로드 실패", task.getException());
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
            Log.e("CourseListActivity", "코스 변환 실패", e);
            return null;
        }
    }

    private void filterCoursesByCategory(String difficulty) {
        selectedCategory = difficulty;
        filteredCourses.clear();
        
        for (Course course : allCourses) {
            if (difficulty.equals(course.getDifficulty())) {
                filteredCourses.add(course);
            }
        }
        
        updateCategoryButtons();
        setupRecyclerView();
        updateEmptyMessage();
        
        Log.d("CourseListActivity", "카테고리 필터링: " + difficulty + " (" + filteredCourses.size() + "개)");
    }

    private void updateCategoryButtons() {
        boolean easySelected = "easy".equals(selectedCategory);
        boolean mediumSelected = "medium".equals(selectedCategory);
        boolean hardSelected = "hard".equals(selectedCategory);
        
        updateButtonStyle(btnCategoryEasy, easySelected, "#4CAF50");
        updateButtonStyle(btnCategoryMedium, mediumSelected, "#FF9800");
        updateButtonStyle(btnCategoryHard, hardSelected, "#F44336");
    }
    
    private void updateButtonStyle(MaterialButton button, boolean selected, String color) {
        button.setSelected(selected);
        if (selected) {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color)));
            button.setTextColor(android.graphics.Color.WHITE);
            button.setStrokeWidth(0);
        } else {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            button.setTextColor(android.graphics.Color.parseColor(color));
            button.setStrokeWidth(2);
        }
    }

    private void setupRecyclerView() {
        courseAdapter = new CourseAdapter(filteredCourses, course -> {
            Intent intent = new Intent(CourseListActivity.this, RunningStartActivity.class);
            intent.putExtra("course_id", course.getId());
            intent.putExtra("course_name", course.getName());
            intent.putExtra("course_distance", course.getDistance());
            intent.putExtra("course_difficulty", course.getDifficulty());
            intent.putExtra("course_path", course.getPathEncoded());
            startActivity(intent);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewCourses.setLayoutManager(layoutManager);
        recyclerViewCourses.setAdapter(courseAdapter);
    }

    private void updateEmptyMessage() {
        if (filteredCourses.isEmpty()) {
            tvEmptyMessage.setVisibility(View.VISIBLE);
            recyclerViewCourses.setVisibility(View.GONE);
        } else {
            tvEmptyMessage.setVisibility(View.GONE);
            recyclerViewCourses.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners() {
        btnCategoryEasy.setOnClickListener(v -> filterCoursesByCategory("easy"));
        btnCategoryMedium.setOnClickListener(v -> filterCoursesByCategory("medium"));
        btnCategoryHard.setOnClickListener(v -> filterCoursesByCategory("hard"));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

