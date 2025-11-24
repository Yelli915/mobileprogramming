package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentChange;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private ListenerRegistration coursesListener;

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
        // 기존 리스너 제거
        if (coursesListener != null) {
            coursesListener.remove();
        }

        // 실시간 리스너 등록
        coursesListener = firestore.collection("courses")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("CourseListActivity", "코스 리스너 오류", e);
                        return;
                    }

                    if (snapshot != null) {
                        // 초기 로드인지 확인 (getDocumentChanges가 비어있으면 초기 로드)
                        if (snapshot.getDocumentChanges().isEmpty()) {
                            // 초기 로드: 전체 리스트 다시 구성
                            allCourses.clear();
                            for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                                if (document instanceof QueryDocumentSnapshot) {
                                    Course course = documentToCourse((QueryDocumentSnapshot) document);
                                    if (course != null) {
                                        allCourses.add(course);
                                    }
                                }
                            }
                            
                            // 현재 선택된 카테고리로 필터링 업데이트
                            if (selectedCategory != null) {
                                filterCoursesByCategory(selectedCategory);
                            } else {
                                // 카테고리가 선택되지 않았으면 전체 표시
                                filteredCourses.clear();
                                filteredCourses.addAll(allCourses);
                                setupRecyclerView();
                                updateEmptyMessage();
                            }
                        } else {
                            // 변경사항 처리
                            for (DocumentChange dc : snapshot.getDocumentChanges()) {
                                QueryDocumentSnapshot document = dc.getDocument();
                                Course course = documentToCourse(document);
                                
                                if (course == null) {
                                    continue;
                                }

                                switch (dc.getType()) {
                                    case ADDED:
                                        // 코스 추가
                                        allCourses.add(course);
                                        Log.d("CourseListActivity", "코스 추가됨: " + course.getName());
                                        break;
                                    case MODIFIED:
                                        // 코스 수정
                                        for (int i = 0; i < allCourses.size(); i++) {
                                            if (allCourses.get(i).getId().equals(course.getId())) {
                                                allCourses.set(i, course);
                                                Log.d("CourseListActivity", "코스 수정됨: " + course.getName());
                                                break;
                                            }
                                        }
                                        break;
                                    case REMOVED:
                                        // 코스 삭제
                                        allCourses.removeIf(c -> c.getId().equals(course.getId()));
                                        Log.d("CourseListActivity", "코스 삭제됨: " + course.getName());
                                        break;
                                }
                            }

                            // 현재 선택된 카테고리로 필터링 업데이트
                            if (selectedCategory != null) {
                                filterCoursesByCategory(selectedCategory);
                            } else {
                                // 카테고리가 선택되지 않았으면 전체 표시
                                filteredCourses.clear();
                                filteredCourses.addAll(allCourses);
                                setupRecyclerView();
                                updateEmptyMessage();
                            }
                        }
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
        
        // 이름 가나다 순으로 정렬
        Collections.sort(filteredCourses, new Comparator<Course>() {
            private Collator collator = Collator.getInstance(Locale.KOREAN);
            
            @Override
            public int compare(Course c1, Course c2) {
                String name1 = c1.getName() != null ? c1.getName() : "";
                String name2 = c2.getName() != null ? c2.getName() : "";
                return collator.compare(name1, name2);
            }
        });
        
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
            // 코스 클릭 시 안내창 표시
            showCourseDetailDialog(course);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewCourses.setLayoutManager(layoutManager);
        recyclerViewCourses.setAdapter(courseAdapter);
    }

    private void showCourseDetailDialog(Course course) {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_course_detail, null);
        
        TextView tvCourseName = dialogView.findViewById(R.id.tvCourseName);
        TextView tvDetailDistance = dialogView.findViewById(R.id.tvDetailDistance);
        TextView tvDetailTime = dialogView.findViewById(R.id.tvDetailTime);
        TextView tvDetailDifficulty = dialogView.findViewById(R.id.tvDetailDifficulty);
        TextView tvCourseDescription = dialogView.findViewById(R.id.tvCourseDescription);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirm);
        
        String courseName = course.getName() != null ? course.getName() : "코스";
        String description = course.getDescription();
        
        if (description == null || description.trim().isEmpty()) {
            description = "코스에 대한 설명이 없습니다.";
        }
        
        tvCourseName.setText(courseName);
        tvDetailDistance.setText(course.getDistanceFormatted());
        tvDetailTime.setText(course.getEstimatedTimeFormatted());
        tvDetailDifficulty.setText(course.getDifficultyKorean());
        tvCourseDescription.setText(description);
        
        // 확인 버튼을 "스케치런 시작"으로 변경
        btnConfirm.setText("스케치런 시작");
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            startSketchRun(course);
        });
        
        dialog.show();
    }

    private void startSketchRun(Course course) {
        Intent intent = new Intent(CourseListActivity.this, RunningStartActivity.class);
        intent.putExtra("course_id", course.getId());
        intent.putExtra("course_name", course.getName());
        intent.putExtra("course_distance", course.getDistance());
        intent.putExtra("course_difficulty", course.getDifficulty());
        intent.putExtra("course_path", course.getPathEncoded());
        startActivity(intent);
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
    protected void onDestroy() {
        super.onDestroy();
        // 리스너 제거
        if (coursesListener != null) {
            coursesListener.remove();
            coursesListener = null;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

