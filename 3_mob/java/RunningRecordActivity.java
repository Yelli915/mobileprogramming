package com.example.mob_3_record;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import java.util.Arrays;
import java.util.List;

public class RunningRecordActivity extends AppCompatActivity implements OnMapReadyCallback {

    private Button backButton;
    private Button dateFilterButton;
    private Button routeFilterButton;
    private Button statisticsButton;
    private RecyclerView recordRecyclerView;
    private TextView totalDistanceText;
    private TextView totalTimeText;
    private Button rateButton;
    private Button shareButton;
    private Button viewMoreButton;

    private RunningRecordAdapter recordAdapter;
    private GoogleMap googleMap;

    private List<RunningRecord> sampleRecords = Arrays.asList(
            new RunningRecord(
                    "2023년 10월 1일",
                    "10km",
                    "조깅",
                    "50분",
                    "5:00/km"
            ),
            new RunningRecord(
                    "2023년 10월 2일",
                    "5km",
                    "트레일",
                    "28분",
                    "5:36/km"
            )
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_record);

        initViews();
        setupRecyclerView();
        setupMap();
        setupClickListeners();
        updateStatistics();
    }

    private void initViews() {
        backButton = findViewById(R.id.btn_back);
        dateFilterButton = findViewById(R.id.btn_date_filter);
        routeFilterButton = findViewById(R.id.btn_route_filter);
        statisticsButton = findViewById(R.id.btn_statistics);
        recordRecyclerView = findViewById(R.id.rv_running_records);
        totalDistanceText = findViewById(R.id.tv_total_distance);
        totalTimeText = findViewById(R.id.tv_total_time);
        rateButton = findViewById(R.id.btn_rate);
        shareButton = findViewById(R.id.btn_share);
        viewMoreButton = findViewById(R.id.btn_view_more);
    }

    private void setupRecyclerView() {
        recordAdapter = new RunningRecordAdapter(sampleRecords, record -> {
            // 선택된 기록의 지도 업데이트
            updateMapForRecord(record);
        });
        recordRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recordRecyclerView.setAdapter(recordAdapter);
    }

    private void setupMap() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        SupportMapFragment mapFragment = (SupportMapFragment) fragmentManager.findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;

        // 기본 위치 설정 (서울)
        LatLng seoul = new LatLng(37.5665, 126.9780);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(seoul, 15f));

        // 첫 번째 기록의 경로 표시
        if (!sampleRecords.isEmpty()) {
            updateMapForRecord(sampleRecords.get(0));
        }
    }

    private void updateMapForRecord(RunningRecord record) {
        if (googleMap != null) {
            googleMap.clear();

            // 샘플 경로 데이터 (실제로는 record에서 경로 정보를 가져와야 함)
            List<LatLng> routePoints = Arrays.asList(
                    new LatLng(37.5665, 126.9780),
                    new LatLng(37.5675, 126.9790),
                    new LatLng(37.5685, 126.9800),
                    new LatLng(37.5695, 126.9810)
            );

            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(routePoints)
                    .width(10f)
                    .color(ContextCompat.getColor(this, R.color.primary_color));

            googleMap.addPolyline(polylineOptions);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(0), 15f));
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        dateFilterButton.setOnClickListener(v -> {
            // 날짜별 필터 로직
            dateFilterButton.setSelected(true);
            routeFilterButton.setSelected(false);
            statisticsButton.setSelected(false);
        });

        routeFilterButton.setOnClickListener(v -> {
            // 경로별 필터 로직
            dateFilterButton.setSelected(false);
            routeFilterButton.setSelected(true);
            statisticsButton.setSelected(false);
        });

        statisticsButton.setOnClickListener(v -> {
            // 통계 화면으로 이동
            dateFilterButton.setSelected(false);
            routeFilterButton.setSelected(false);
            statisticsButton.setSelected(true);
        });

        rateButton.setOnClickListener(v -> {
            // 별점 평가하기 로직
        });

        shareButton.setOnClickListener(v -> {
            // 공유하기 로직
        });

        viewMoreButton.setOnClickListener(v -> {
            // 더 많은 기록 보기 로직
        });
    }

    private void updateStatistics() {
        // 전체 통계 계산
        double totalDistance = 0.0;
        for (RunningRecord record : sampleRecords) {
            try {
                String distanceStr = record.getDistance().replace("km", "").trim();
                totalDistance += Double.parseDouble(distanceStr);
            } catch (NumberFormatException e) {
                // 파싱 실패 시 0으로 처리
            }
        }

        int totalMinutes = 0;
        for (RunningRecord record : sampleRecords) {
            try {
                String timeStr = record.getTime().replace("분", "").trim();
                totalMinutes += Integer.parseInt(timeStr);
            } catch (NumberFormatException e) {
                // 파싱 실패 시 0으로 처리
            }
        }
        int totalHours = totalMinutes / 60;

        totalDistanceText.setText((int) totalDistance + "km");
        totalTimeText.setText(totalHours + "시간");
    }
}

