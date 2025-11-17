package Run.U;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.Timestamp;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RunningStartActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 2000L; // 2 seconds

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;

    // Views
    private TextView timerTextView;
    private TextView distanceTextView;
    private TextView averagePaceTextView;
    private TextView instantPaceTextView;
    private Button pauseButton;
    private Button endRunButton;
    private Button startTimerButton;

    // Running state
    private boolean isRunning = false;
    private boolean isPaused = false;
    private long startTime = 0;
    private long pausedTime = 0;
    private long totalPausedTime = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable = null;

    // Distance tracking
    private double totalDistance = 0.0;
    private Location lastLocation = null;
    private Handler locationUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable locationUpdateRunnable = null;

    // Route tracking
    private List<LatLng> routePoints = new ArrayList<>();
    private PolylineOptions routePolyline;

    // Pace calculation
    private double averagePace = 0.0;
    private double instantPace = 0.0;

    // Firestore
    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private String courseId; // 코스 기반 러닝인 경우

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_start);

        // Initialize views
        timerTextView = findViewById(R.id.timerTextView);
        distanceTextView = findViewById(R.id.distanceTextView);
        averagePaceTextView = findViewById(R.id.averagePaceTextView);
        instantPaceTextView = findViewById(R.id.instantPaceTextView);
        pauseButton = findViewById(R.id.pauseButton);
        endRunButton = findViewById(R.id.endRunButton);
        startTimerButton = findViewById(R.id.startTimerButton);

        // Check if views are properly initialized
        if (timerTextView == null || distanceTextView == null || averagePaceTextView == null 
                || instantPaceTextView == null || pauseButton == null || endRunButton == null 
                || startTimerButton == null) {
            Log.e("RunningStartActivity", "필수 뷰를 찾을 수 없습니다.");
            GoogleSignInUtils.showToast(this, "화면 초기화 오류가 발생했습니다.");
            finish();
            return;
        }

        // Set up back button
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (isRunning) {
                    // 운동 중이면 종료 확인 다이얼로그 표시
                    showExitConfirmationDialog();
                } else {
                    finish();
                }
            });
        }

        // Intent에서 코스 정보 받기
        handleCourseIntent();

        // Initialize Firestore
        firestore = GoogleSignInUtils.getFirestore();
        firebaseAuth = GoogleSignInUtils.getAuth();

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize map
        FragmentManager fragmentManager = getSupportFragmentManager();
        SupportMapFragment mapFragment = (SupportMapFragment) fragmentManager.findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.w("RunningStartActivity", "MapFragment를 찾을 수 없습니다.");
        }

        // Button click listeners
        pauseButton.setOnClickListener(v -> togglePause());

        endRunButton.setOnClickListener(v -> endRun());

        startTimerButton.setOnClickListener(v -> startRunning());

        // Request location permissions
        if (checkLocationPermission()) {
            requestLocationUpdates();
        } else {
            requestLocationPermission();
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        // Enable my location button if permission granted
        if (checkLocationPermission()) {
            try {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
                getCurrentLocation();
            } catch (SecurityException e) {
                // Permission was revoked between check and use
                e.printStackTrace();
            }
        }
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (checkLocationPermission() && map != null) {
                    try {
                        map.setMyLocationEnabled(true);
                        map.getUiSettings().setMyLocationButtonEnabled(true);
                    } catch (SecurityException e) {
                        // Permission was revoked between check and use
                        e.printStackTrace();
                    }
                }
                getCurrentLocation();
                requestLocationUpdates();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        if (!checkLocationPermission()) {
            return;
        }
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null && map != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        map.addMarker(new MarkerOptions().position(currentLatLng).title("현재 위치"));
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                        lastLocation = location;
                    }
                }
            });
        } catch (SecurityException e) {
            // Permission was revoked between check and use
            e.printStackTrace();
        }
    }

    private void requestLocationUpdates() {
        if (!checkLocationPermission()) {
            return;
        }

        locationUpdateRunnable = new Runnable() {
            @Override
            @SuppressLint("MissingPermission")
            public void run() {
                if (isRunning && !isPaused && checkLocationPermission()) {
                    try {
                        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                if (location != null) {
                                    updateDistance(location);
                                    lastLocation = location;
                                }
                            }
                        });
                    } catch (SecurityException e) {
                        // Permission was revoked between check and use
                        e.printStackTrace();
                    }
                }
                locationUpdateHandler.postDelayed(this, LOCATION_UPDATE_INTERVAL);
            }
        };
        locationUpdateHandler.post(locationUpdateRunnable);
    }

    private void updateDistance(Location newLocation) {
        LatLng currentLatLng = new LatLng(newLocation.getLatitude(), newLocation.getLongitude());
        
        if (lastLocation != null) {
            double distance = lastLocation.distanceTo(newLocation) / 1000.0; // Convert to km
            totalDistance += distance;
            distanceTextView.setText(GoogleSignInUtils.formatDistanceKm(totalDistance));
        }

        // 경로 좌표 추가
        routePoints.add(currentLatLng);

        // 지도 업데이트: 경로 Polyline 그리기
        if (map != null) {
            map.clear();
            
            // 경로 Polyline 그리기
            if (routePoints.size() > 1) {
                routePolyline = new PolylineOptions()
                        .addAll(routePoints)
                        .width(10f)
                        .color(android.graphics.Color.parseColor("#FF6B35"));
                map.addPolyline(routePolyline);
            }
            
            // 시작 지점 마커
            if (!routePoints.isEmpty()) {
                map.addMarker(new MarkerOptions()
                        .position(routePoints.get(0))
                        .title("시작"));
            }
            
            // 현재 위치 마커
            map.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("현재 위치"));
            
            map.moveCamera(CameraUpdateFactory.newLatLng(currentLatLng));
        }
    }

    private void startRunning() {
        if (!isRunning) {
            try {
                isRunning = true;
                isPaused = false;
                startTime = System.currentTimeMillis();
                totalPausedTime = 0;
                totalDistance = 0.0;
                lastLocation = null;
                routePoints.clear(); // 경로 초기화

                // Reset displays
                if (distanceTextView != null) {
                    distanceTextView.setText("0.00 km");
                }
                if (averagePaceTextView != null) {
                    averagePaceTextView.setText("--:--/km");
                }
                if (instantPaceTextView != null) {
                    instantPaceTextView.setText("--:--/km");
                }

                // Start timer
                startTimer();

                // Start location updates
                getCurrentLocation();

                // Update button states
                if (startTimerButton != null) {
                    startTimerButton.setEnabled(false);
                }
                if (pauseButton != null) {
                    pauseButton.setEnabled(true);
                }
            } catch (Exception e) {
                Log.e("RunningStartActivity", "운동 시작 중 오류 발생", e);
                GoogleSignInUtils.showToast(this, "운동 시작 중 오류가 발생했습니다.");
                isRunning = false;
            }
        }
    }

    private void togglePause() {
        if (isRunning) {
            if (isPaused) {
                // Resume
                isPaused = false;
                totalPausedTime += (System.currentTimeMillis() - pausedTime);
                pauseButton.setText("일시 정지");
                startTimer();
            } else {
                // Pause
                isPaused = true;
                pausedTime = System.currentTimeMillis();
                pauseButton.setText("재개");
                stopTimer();
            }
        }
    }

    private void endRun() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        isPaused = false;
        stopTimer();
        stopLocationUpdates();

        // 운동 기록 데이터 준비
        long elapsedTime = System.currentTimeMillis() - startTime - totalPausedTime;
        String distance = distanceTextView.getText().toString();
        String time = timerTextView.getText().toString();
        String pace = averagePaceTextView.getText().toString();

        // Firestore에 저장
        saveRunToFirestore(elapsedTime, distance, time, pace);

        // RunningRecordActivity로 이동
        Intent intent = new Intent(RunningStartActivity.this, RunningRecordActivity.class);
        intent.putExtra("distance", distance);
        intent.putExtra("time", time);
        intent.putExtra("pace", pace);
        intent.putExtra("total_distance_km", totalDistance);
        intent.putExtra("elapsed_time_ms", elapsedTime);
        startActivity(intent);
        finish();
    }

    private void saveRunToFirestore(long elapsedTimeMs, String distance, String time, String pace) {
        FirebaseUser currentUser = GoogleSignInUtils.requireCurrentUser(this);
        if (currentUser == null) {
            Log.w("RunningStartActivity", "사용자가 로그인되어 있지 않습니다.");
            return;
        }

        String userId = currentUser.getUid();

        // Encoded Polyline 생성
        String pathEncoded = PolylineUtils.encode(routePoints);

        // 시작/종료 시간 계산
        Date startTimeDate = new Date(startTime);
        Date endTimeDate = new Date(System.currentTimeMillis());

        // 시작/종료 마커 추출
        GeoPoint startMarker = null;
        GeoPoint endMarker = null;
        if (!routePoints.isEmpty()) {
            LatLng startPoint = routePoints.get(0);
            LatLng endPoint = routePoints.get(routePoints.size() - 1);
            startMarker = new GeoPoint(startPoint.latitude, startPoint.longitude);
            endMarker = new GeoPoint(endPoint.latitude, endPoint.longitude);
        }

        // totalDistance: km → 미터 단위 변환
        double totalDistanceMeters = totalDistance * 1000.0;

        // totalTime: 밀리초 → 초 단위 변환
        long totalTimeSeconds = elapsedTimeMs / 1000;

        // averagePace: 문자열 "MM:SS/km" → 초 단위 숫자로 변환
        double averagePaceSeconds = GoogleSignInUtils.parsePaceToSeconds(pace);

        // Firestore 문서 데이터 (데이터베이스 구조에 맞게 수정)
        Map<String, Object> runData = new HashMap<>();
        runData.put("type", courseId != null ? "sketch" : "free");
        runData.put("startTime", new Timestamp(startTimeDate));
        runData.put("endTime", new Timestamp(endTimeDate));
        runData.put("totalDistance", totalDistanceMeters); // 미터 단위
        runData.put("totalTime", totalTimeSeconds); // 초 단위
        runData.put("averagePace", averagePaceSeconds); // km당 초 (숫자)
        runData.put("pathEncoded", pathEncoded);
        if (startMarker != null) {
            runData.put("startMarker", startMarker);
        }
        if (endMarker != null) {
            runData.put("endMarker", endMarker);
        }
        if (courseId != null) {
            runData.put("courseId", courseId);
        }
        
        // 호환성을 위해 추가 필드 (선택사항)
        runData.put("createdAt", FieldValue.serverTimestamp());

        // users/{uid}/runs 컬렉션에 저장
        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .add(runData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("RunningStartActivity", "러닝 기록 저장 성공: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.w("RunningStartActivity", "러닝 기록 저장 실패", e);
                    GoogleSignInUtils.showToast(this, "기록 저장에 실패했습니다.");
                });
    }


    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    long elapsedTime = System.currentTimeMillis() - startTime - totalPausedTime;
                    updateTimerDisplay(elapsedTime);
                    updatePace(elapsedTime);
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
        }
    }

    private void updateTimerDisplay(long elapsedTime) {
        timerTextView.setText(GoogleSignInUtils.formatElapsedTime(elapsedTime));
    }

    private void updatePace(long elapsedTime) {
        if (totalDistance > 0) {
            double elapsedHours = elapsedTime / 3600000.0;
            averagePace = totalDistance / elapsedHours; // km/h

            // Convert to min/km format
            double averagePaceMinPerKm = 60.0 / averagePace;
            double averagePaceSeconds = averagePaceMinPerKm * 60.0;
            averagePaceTextView.setText(GoogleSignInUtils.formatPaceFromSeconds(averagePaceSeconds));

            // Calculate instant pace (using last location update)
            if (lastLocation != null) {
                // This is a simplified instant pace calculation
                // In a real app, you'd track speed from GPS
                instantPace = averagePace;
                double instantPaceMinPerKm = 60.0 / instantPace;
                double instantPaceSeconds = instantPaceMinPerKm * 60.0;
                instantPaceTextView.setText(GoogleSignInUtils.formatPaceFromSeconds(instantPaceSeconds));
            }
        }
    }

    private void stopLocationUpdates() {
        if (locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
    }

    private void handleCourseIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("course_id")) {
            courseId = intent.getStringExtra("course_id");
        } else if (intent != null && intent.hasExtra("course_name")) {
            // 기존 호환성을 위해 course_name도 처리
            String courseName = intent.getStringExtra("course_name");
            double courseDistance = intent.getDoubleExtra("course_distance", 0.0);
            String courseDifficulty = intent.getStringExtra("course_difficulty");
        }
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("운동 종료")
                .setMessage("운동을 종료하시겠습니까? 기록이 저장되지 않습니다.")
                .setPositiveButton("종료", (dialog, which) -> finish())
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        stopLocationUpdates();
    }
}

