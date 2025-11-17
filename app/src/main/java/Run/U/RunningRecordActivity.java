package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
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
import com.google.firebase.firestore.GeoPoint;
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
        firestore = GoogleSignInUtils.getFirestore();
        firebaseAuth = GoogleSignInUtils.getAuth();
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
        FirebaseUser currentUser = GoogleSignInUtils.requireCurrentUser(this);
        if (currentUser == null) {
            return;
        }

        String userId = currentUser.getUid();

        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .orderBy("startTime", Query.Direction.DESCENDING)
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
                        GoogleSignInUtils.showToast(this, "기록을 불러오는데 실패했습니다.");
                    }
                });
    }

    private RunningRecord documentToRunningRecord(QueryDocumentSnapshot document) {
        try {
            RunningRecord record = new RunningRecord();
            record.setId(document.getId());
            
            // 새로운 필드 구조 우선 사용 (데이터베이스 구조에 맞춤)
            
            // type 필드 (기존 runningType과 호환)
            if (document.contains("type")) {
                String type = document.getString("type");
                record.setRunningType("free".equals(type) ? "일반 운동" : ("sketch".equals(type) ? "코스 운동" : type));
            } else if (document.contains("runningType")) {
                // 호환성을 위해 기존 필드도 지원
                record.setRunningType(document.getString("runningType"));
            }
            
            // totalDistance (미터) → totalDistanceKm (km) 변환
            if (document.contains("totalDistance")) {
                Double totalDistanceMeters = document.getDouble("totalDistance");
                if (totalDistanceMeters != null) {
                    record.setTotalDistanceKm(totalDistanceMeters / 1000.0);
                }
            } else if (document.contains("totalDistanceKm")) {
                // 호환성을 위해 기존 필드도 지원
                record.setTotalDistanceKm(document.getDouble("totalDistanceKm"));
            }
            
            // totalTime (초) → elapsedTimeMs (ms) 변환
            if (document.contains("totalTime")) {
                Long totalTimeSeconds = document.getLong("totalTime");
                if (totalTimeSeconds != null) {
                    record.setElapsedTimeMs(totalTimeSeconds * 1000);
                }
            } else if (document.contains("elapsedTimeMs")) {
                // 호환성을 위해 기존 필드도 지원
                Long elapsedTime = document.getLong("elapsedTimeMs");
                if (elapsedTime != null) {
                    record.setElapsedTimeMs(elapsedTime);
                }
            }
            
            // averagePace (숫자 초) → 문자열 변환
            if (document.contains("averagePace")) {
                Object paceObj = document.get("averagePace");
                if (paceObj instanceof Number) {
                    // 숫자 타입인 경우 문자열로 변환
                    double paceSeconds = ((Number) paceObj).doubleValue();
                    record.setAveragePace(GoogleSignInUtils.formatPaceFromSeconds(paceSeconds));
                } else if (paceObj instanceof String) {
                    // 문자열 타입인 경우 그대로 사용 (호환성)
                    record.setAveragePace((String) paceObj);
                }
            }
            
            // startTime, endTime으로부터 time 문자열 생성
            if (document.contains("startTime") && document.contains("endTime")) {
                Object startTimeObj = document.get("startTime");
                Object endTimeObj = document.get("endTime");
                if (startTimeObj instanceof com.google.firebase.Timestamp && 
                    endTimeObj instanceof com.google.firebase.Timestamp) {
                    long startMs = ((com.google.firebase.Timestamp) startTimeObj).toDate().getTime();
                    long endMs = ((com.google.firebase.Timestamp) endTimeObj).toDate().getTime();
                    long durationMs = endMs - startMs;
                    
                    record.setTime(GoogleSignInUtils.formatElapsedTimeShort(durationMs));
                }
            } else if (document.contains("time")) {
                // 호환성을 위해 기존 필드도 지원
                record.setTime(document.getString("time"));
            }
            
            // startTime으로부터 date 문자열 생성
            if (document.contains("startTime")) {
                Object startTimeObj = document.get("startTime");
                if (startTimeObj instanceof com.google.firebase.Timestamp) {
                    java.util.Date date = ((com.google.firebase.Timestamp) startTimeObj).toDate();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy년 MM월 dd일", java.util.Locale.KOREA);
                    record.setDate(sdf.format(date));
                }
            } else if (document.contains("date")) {
                // 호환성을 위해 기존 필드도 지원
                record.setDate(document.getString("date"));
            }
            
            // totalDistanceKm으로부터 distance 문자열 생성
            if (document.contains("totalDistance")) {
                Double totalDistanceMeters = document.getDouble("totalDistance");
                if (totalDistanceMeters != null) {
                    double distanceKm = totalDistanceMeters / 1000.0;
                    record.setDistance(GoogleSignInUtils.formatDistanceKm(distanceKm));
                }
            } else if (document.contains("distance")) {
                // 호환성을 위해 기존 필드도 지원
                record.setDistance(document.getString("distance"));
            }
            
            // pathEncoded
            if (document.contains("pathEncoded")) {
                record.setPathEncoded(document.getString("pathEncoded"));
            }
            
            // startMarker, endMarker로부터 routePoints 생성 (호환성을 위해)
            if (document.contains("startMarker") && document.contains("endMarker")) {
                GeoPoint startMarker = document.getGeoPoint("startMarker");
                GeoPoint endMarker = document.getGeoPoint("endMarker");
                if (startMarker != null && endMarker != null) {
                    List<GeoPoint> routePoints = new ArrayList<>();
                    routePoints.add(startMarker);
                    routePoints.add(endMarker);
                    record.setRoutePoints(routePoints);
                }
            } else if (document.contains("routePoints")) {
                // 호환성을 위해 기존 필드도 지원
                @SuppressWarnings("unchecked")
                List<com.google.firebase.firestore.GeoPoint> geoPoints = 
                    (List<com.google.firebase.firestore.GeoPoint>) document.get("routePoints");
                if (geoPoints != null) {
                    record.setRoutePoints(geoPoints);
                }
            }
            
            // userId
            if (document.contains("userId")) {
                record.setUserId(document.getString("userId"));
            }
            
            // courseId
            if (document.contains("courseId")) {
                record.setCourseId(document.getString("courseId"));
            }
            
            // createdAt
            if (document.contains("createdAt")) {
                Object createdAt = document.get("createdAt");
                if (createdAt instanceof com.google.firebase.Timestamp) {
                    record.setCreatedAt(((com.google.firebase.Timestamp) createdAt).toDate().getTime());
                }
            } else if (document.contains("startTime")) {
                // createdAt이 없으면 startTime 사용
                Object startTimeObj = document.get("startTime");
                if (startTimeObj instanceof com.google.firebase.Timestamp) {
                    record.setCreatedAt(((com.google.firebase.Timestamp) startTimeObj).toDate().getTime());
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
        GoogleSignInUtils.setupRecyclerView(recordRecyclerView, recordAdapter, this);
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

        totalDistanceText.setText(GoogleSignInUtils.formatDistanceKm(totalDistance));
        totalTimeText.setText(GoogleSignInUtils.formatElapsedTimeWithLabel(totalTimeMs));
    }
}


