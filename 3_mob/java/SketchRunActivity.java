package com.example.mob_3_sketch;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Arrays;
import java.util.List;

public class SketchRunActivity extends AppCompatActivity {

    private RecyclerView recyclerViewCourses;
    private CourseAdapter courseAdapter;
    private ImageButton btnBack;
    private Button btnCourseDetail;
    private Button btnFindLocation;
    private Button btnStartRun;
    private TextView tvTotalDistance;
    private TextView tvEstimatedTime;
    private TextView tvDifficulty;

    private Course selectedCourse = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sketch_run);

        initViews();
        setupRecyclerView();
        setupClickListeners();
    }

    private void initViews() {
        recyclerViewCourses = findViewById(R.id.recyclerViewCourses);
        btnBack = findViewById(R.id.btnBack);
        btnCourseDetail = findViewById(R.id.btnCourseDetail);
        btnFindLocation = findViewById(R.id.btnFindLocation);
        btnStartRun = findViewById(R.id.btnStartRun);
        tvTotalDistance = findViewById(R.id.tvTotalDistance);
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        tvDifficulty = findViewById(R.id.tvDifficulty);
    }

    private void setupRecyclerView() {
        List<Course> courses = Arrays.asList(
                new Course("코스 1", 5.0, "중급", 30),
                new Course("코스 2", 10.0, "초급", 60),
                new Course("코스 3", 15.0, "고급", 90)
        );

        courseAdapter = new CourseAdapter(courses, course -> {
            selectedCourse = course;
            updateCourseInfo(course);
            // 여기에 지도 업데이트 로직 추가 가능
        });

        recyclerViewCourses.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewCourses.setAdapter(courseAdapter);

        // 기본적으로 첫 번째 코스 선택
        if (!courses.isEmpty()) {
            selectedCourse = courses.get(0);
            updateCourseInfo(courses.get(0));
        }
    }

    private void updateCourseInfo(Course course) {
        tvTotalDistance.setText((int) course.getDistance() + "km");
        tvEstimatedTime.setText(course.getEstimatedTime() + "분");
        tvDifficulty.setText(course.getDifficulty());
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnCourseDetail.setOnClickListener(v -> {
            if (selectedCourse != null) {
                Toast.makeText(this, selectedCourse.getName() + " 상세 정보", Toast.LENGTH_SHORT).show();
                // 코스 상세 화면으로 이동하는 로직 추가
            } else {
                Toast.makeText(this, "코스를 선택해주세요", Toast.LENGTH_SHORT).show();
            }
        });

        btnFindLocation.setOnClickListener(v -> {
            Toast.makeText(this, "내 위치 찾기", Toast.LENGTH_SHORT).show();
            // 위치 찾기 로직 추가 (GPS, 지도 API 등)
        });

        btnStartRun.setOnClickListener(v -> {
            if (selectedCourse != null) {
                Toast.makeText(this, selectedCourse.getName() + " 시작!", Toast.LENGTH_SHORT).show();
                // 러닝 시작 화면으로 이동하는 로직 추가
            } else {
                Toast.makeText(this, "코스를 선택해주세요", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

