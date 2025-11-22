package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentChange;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SketchRunActivity extends AppCompatActivity implements OnMapReadyCallback {

    private RecyclerView recyclerViewCourses;
    private CourseAdapter courseAdapter;
    private MaterialToolbar toolbar;
    private MaterialButton btnCourseDetail;
    private FloatingActionButton btnFindLocation;
    private MaterialButton btnStartRun;
    private TextView tvCourseTotalDistance;
    private TextView tvCourseEstimatedTime;
    private TextView tvDifficulty;
    private TextView btnMoreCourses;
    private FrameLayout mapContainer;

    private Course selectedCourse = null;
    private FirebaseFirestore firestore;
    private List<Course> allCourses = new ArrayList<>();
    private List<Course> filteredCourses = new ArrayList<>();
    private String selectedCategory = "전체";
    
    private GoogleMap map;
    private SupportMapFragment mapFragment;
    private Polyline coursePolyline;
    private ListenerRegistration coursesListener;

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
        btnStartRun = findViewById(R.id.btnStartRun);

        // 코스 정보 TextView 초기화
        tvCourseTotalDistance = findViewById(R.id.tvCourseTotalDistance);
        tvCourseEstimatedTime = findViewById(R.id.tvCourseEstimatedTime);
        tvDifficulty = findViewById(R.id.tvDifficulty);
        btnMoreCourses = findViewById(R.id.btnMoreCourses);
        mapContainer = findViewById(R.id.mapContainer);
        
        setupMap();
    }
    
    private void setupMap() {
        if (mapContainer == null) {
            Log.w("SketchRunActivity", "mapContainer를 찾을 수 없습니다.");
            return;
        }
        
        FragmentManager fragmentManager = getSupportFragmentManager();
        mapFragment = SupportMapFragment.newInstance();
        fragmentManager.beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commit();
        
        mapFragment.getMapAsync(this);
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
        // 기존 리스너 제거
        if (coursesListener != null) {
            coursesListener.remove();
        }

        // 실시간 리스너 등록
        coursesListener = firestore.collection("courses")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("SketchRunActivity", "코스 리스너 오류", e);
                        return;
                    }

                    if (snapshot != null) {
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
                                    Log.d("SketchRunActivity", "코스 추가됨: " + course.getName());
                                    break;
                                case MODIFIED:
                                    // 코스 수정
                                    for (int i = 0; i < allCourses.size(); i++) {
                                        if (allCourses.get(i).getId().equals(course.getId())) {
                                            allCourses.set(i, course);
                                            Log.d("SketchRunActivity", "코스 수정됨: " + course.getName());
                                            
                                            // 현재 선택된 코스가 수정된 경우 업데이트
                                            if (selectedCourse != null && selectedCourse.getId().equals(course.getId())) {
                                                selectedCourse = course;
                                                updateCourseInfo(course);
                                                if (map != null) {
                                                    displayCoursePathOnMap(course);
                                                }
                                            }
                                            break;
                                        }
                                    }
                                    break;
                                case REMOVED:
                                    // 코스 삭제
                                    allCourses.removeIf(c -> c.getId().equals(course.getId()));
                                    Log.d("SketchRunActivity", "코스 삭제됨: " + course.getName());
                                    
                                    // 현재 선택된 코스가 삭제된 경우
                                    if (selectedCourse != null && selectedCourse.getId().equals(course.getId())) {
                                        selectedCourse = null;
                                        tvCourseTotalDistance.setText("--");
                                        tvCourseEstimatedTime.setText("--");
                                        tvDifficulty.setText("--");
                                        if (map != null) {
                                            map.clear();
                                        }
                                    }
                                    break;
                            }
                        }

                        // 추천 코스 업데이트
                        updateRecommendedCourses();
                    }
                });
    }

    private void updateRecommendedCourses() {
        // 추천 코스: 랜덤 3개 선택
        List<Course> recommendedCourses = getRandomCourses(3);
        filteredCourses.clear();
        filteredCourses.addAll(recommendedCourses);
        setupRecyclerView();

        if (!filteredCourses.isEmpty()) {
            // 현재 선택된 코스가 필터링된 목록에 없으면 첫 번째 코스 선택
            if (selectedCourse == null || !filteredCourses.contains(selectedCourse)) {
                selectedCourse = filteredCourses.get(0);
                updateCourseInfo(selectedCourse);
                if (map != null) {
                    displayCoursePathOnMap(selectedCourse);
                }
            }
        } else {
            selectedCourse = null;
            tvCourseTotalDistance.setText("--");
            tvCourseEstimatedTime.setText("--");
            tvDifficulty.setText("--");
            if (map != null) {
                map.clear();
            }
        }
    }
    
    private List<Course> getRandomCourses(int count) {
        if (allCourses.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }
        
        List<Course> shuffled = new ArrayList<>(allCourses);
        Collections.shuffle(shuffled, new Random());
        
        int size = Math.min(count, shuffled.size());
        return shuffled.subList(0, size);
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
        courseAdapter = new CourseAdapter(filteredCourses, course -> {
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
    }

    private void updateCourseInfo(Course course) {
        tvCourseTotalDistance.setText(course.getDistanceFormatted());
        tvCourseEstimatedTime.setText(course.getEstimatedTimeFormatted());
        tvDifficulty.setText(course.getDifficultyKorean());
        
        displayCoursePathOnMap(course);
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setScrollGesturesEnabled(true);
        map.getUiSettings().setZoomGesturesEnabled(true);
        map.getUiSettings().setRotateGesturesEnabled(true);
        map.getUiSettings().setTiltGesturesEnabled(true);
        
        if (selectedCourse != null) {
            displayCoursePathOnMap(selectedCourse);
        }
    }
    
    private void displayCoursePathOnMap(Course course) {
        if (map == null || course == null) {
            return;
        }
        
        String pathEncoded = course.getPathEncoded();
        if (pathEncoded == null || pathEncoded.isEmpty()) {
            Log.w("SketchRunActivity", "코스 경로 데이터가 없습니다: " + course.getName());
            return;
        }
        
        try {
            List<LatLng> coursePoints = PolylineUtils.decode(pathEncoded);
            
            if (coursePoints == null || coursePoints.isEmpty()) {
                Log.w("SketchRunActivity", "코스 경로 디코딩 실패 또는 빈 경로: " + course.getName());
                return;
            }
            
            map.clear();
            
            PolylineOptions coursePolylineOptions = new PolylineOptions()
                    .addAll(coursePoints)
                    .width(12f)
                    .color(android.graphics.Color.parseColor("#FF6B35"))
                    .geodesic(true);
            
            coursePolyline = map.addPolyline(coursePolylineOptions);
            
            if (coursePoints.size() > 1) {
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                for (LatLng point : coursePoints) {
                    boundsBuilder.include(point);
                }
                LatLngBounds bounds = boundsBuilder.build();
                
                int padding = 50;
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                
                Log.d("SketchRunActivity", "코스 경로 지도에 표시 완료: " + course.getName() + " (" + coursePoints.size() + "개 좌표)");
            }
        } catch (Exception e) {
            Log.e("SketchRunActivity", "코스 경로 표시 중 오류", e);
        }
    }

    private void setupClickListeners() {
        // 더보기 버튼 - 카테고리별 코스 목록 화면으로 이동
        if (btnMoreCourses != null) {
            btnMoreCourses.setOnClickListener(v -> {
                Intent intent = new Intent(SketchRunActivity.this, CourseListActivity.class);
                startActivity(intent);
            });
        }
        
        // 코스 상세 보기 버튼
        btnCourseDetail.setOnClickListener(v -> {
            if (selectedCourse != null) {
                showCourseDescription(selectedCourse);
            } else {
                showCourseNotSelectedMessage();
            }
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
    
    private void showCourseDescription(Course course) {
        LayoutInflater inflater = LayoutInflater.from(this);
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
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        btnConfirm.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
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