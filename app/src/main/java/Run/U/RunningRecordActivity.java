package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class RunningRecordActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ImageButton backButton;
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
    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private List<RunningRecord> records = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_record);

        initViews();
        initFirestore();
        setupMap();
        setupClickListeners();
        loadRecordsFromFirestore();
        
        // Intent에서 새로운 운동 기록 데이터 받기
        handleNewRecord();
    }

    private void initFirestore() {
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    private void handleNewRecord() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("distance")) {
            // Firestore에 저장은 RunningStartActivity에서 이미 완료됨
            // 여기서는 기록 목록을 다시 로드
            loadRecordsFromFirestore();
        }
    }

    private void loadRecordsFromFirestore() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();

        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        records.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            RunningRecord record = documentToRunningRecord(document);
                            if (record != null) {
                                records.add(record);
                            }
                        }
                        setupRecyclerView();
                        updateStatistics();
                        
                        // 첫 번째 기록의 경로 표시
                        if (!records.isEmpty() && googleMap != null) {
                            updateMapForRecord(records.get(0));
                        }
                    } else {
                        Log.w("RunningRecordActivity", "기록 로드 실패", task.getException());
                        Toast.makeText(this, "기록을 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private RunningRecord documentToRunningRecord(QueryDocumentSnapshot document) {
        try {
            RunningRecord record = new RunningRecord();
            record.setId(document.getId());
            
            if (document.contains("date")) {
                record.setDate(document.getString("date"));
            }
            if (document.contains("distance")) {
                record.setDistance(document.getString("distance"));
            }
            if (document.contains("runningType")) {
                record.setRunningType(document.getString("runningType"));
            }
            if (document.contains("time")) {
                record.setTime(document.getString("time"));
            }
            if (document.contains("averagePace")) {
                record.setAveragePace(document.getString("averagePace"));
            }
            if (document.contains("totalDistanceKm")) {
                record.setTotalDistanceKm(document.getDouble("totalDistanceKm"));
            }
            if (document.contains("elapsedTimeMs")) {
                Long elapsedTime = document.getLong("elapsedTimeMs");
                if (elapsedTime != null) {
                    record.setElapsedTimeMs(elapsedTime);
                }
            }
            if (document.contains("pathEncoded")) {
                record.setPathEncoded(document.getString("pathEncoded"));
            }
            if (document.contains("routePoints")) {
                @SuppressWarnings("unchecked")
                List<com.google.firebase.firestore.GeoPoint> geoPoints = 
                    (List<com.google.firebase.firestore.GeoPoint>) document.get("routePoints");
                if (geoPoints != null) {
                    record.setRoutePoints(geoPoints);
                }
            }
            if (document.contains("userId")) {
                record.setUserId(document.getString("userId"));
            }
            if (document.contains("courseId")) {
                record.setCourseId(document.getString("courseId"));
            }
            if (document.contains("createdAt")) {
                Object createdAt = document.get("createdAt");
                if (createdAt instanceof com.google.firebase.Timestamp) {
                    record.setCreatedAt(((com.google.firebase.Timestamp) createdAt).toDate().getTime());
                }
            }
            
            return record;
        } catch (Exception e) {
            Log.e("RunningRecordActivity", "기록 변환 실패", e);
            return null;
        }
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
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
        recordAdapter = new RunningRecordAdapter(records, record -> {
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
        if (!records.isEmpty()) {
            updateMapForRecord(records.get(0));
        }
    }

    private void updateMapForRecord(RunningRecord record) {
        if (googleMap == null || record == null) {
            return;
        }

        googleMap.clear();

        List<LatLng> routePoints = new ArrayList<>();

        // pathEncoded가 있으면 디코딩
        if (record.getPathEncoded() != null && !record.getPathEncoded().isEmpty()) {
            routePoints = PolylineUtils.decode(record.getPathEncoded());
        } 
        // routePoints가 있으면 사용
        else if (record.getRoutePoints() != null && !record.getRoutePoints().isEmpty()) {
            routePoints = record.getRoutePointsLatLng();
        }

        if (!routePoints.isEmpty()) {
            // 경로 Polyline 그리기
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(routePoints)
                    .width(10f)
                    .color(ContextCompat.getColor(this, R.color.primary_color));

            googleMap.addPolyline(polylineOptions);

            // 시작 지점 마커
            googleMap.addMarker(new MarkerOptions()
                    .position(routePoints.get(0))
                    .title("시작"));

            // 종료 지점 마커
            if (routePoints.size() > 1) {
                googleMap.addMarker(new MarkerOptions()
                        .position(routePoints.get(routePoints.size() - 1))
                        .title("종료"));
            }

            // 경로가 모두 보이도록 카메라 조정
            if (routePoints.size() > 1) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (LatLng point : routePoints) {
                    builder.include(point);
                }
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            } else {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(0), 15f));
            }
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(RunningRecordActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

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
        long totalTimeMs = 0;

        for (RunningRecord record : records) {
            // totalDistanceKm 사용
            totalDistance += record.getTotalDistanceKm();
            totalTimeMs += record.getElapsedTimeMs();
        }

        long totalHours = totalTimeMs / (1000 * 60 * 60);
        long totalMinutes = (totalTimeMs % (1000 * 60 * 60)) / (1000 * 60);

        totalDistanceText.setText(String.format("%.1fkm", totalDistance));
        if (totalHours > 0) {
            totalTimeText.setText(String.format("%d시간 %d분", totalHours, totalMinutes));
        } else {
            totalTimeText.setText(String.format("%d분", totalMinutes));
        }
    }
}

