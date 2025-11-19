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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.Granularity;

public class RunningStartActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 2000L;

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;

    // Views
    private TextView timerTextView;
    private TextView distanceTextView;
    private TextView averagePaceTextView;
    private TextView instantPaceTextView;
    private Button startRunningButton;  // 수정: 이름 변경
    private Button pauseButton;
    private Button resumeButton;  // 추가: 재개 버튼
    private Button endRunButton;

    // Running state
    private boolean isRunning = false;
    private boolean isPaused = false;
    private long startTime = 0;
    private long pausedTime = 0;
    private long totalPausedTime = 0;

    private final TimerHandler timerHandler = new TimerHandler(this);

    private static class TimerHandler extends Handler {
        private final WeakReference<RunningStartActivity> activityRef;

        TimerHandler(RunningStartActivity activity) {
            super(Looper.getMainLooper());
            this.activityRef = new WeakReference<>(activity);
        }

        RunningStartActivity getActivity() {
            return activityRef.get();
        }
    }

    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private double totalDistance = 0.0;
    private Location lastLocation = null;

    private List<LatLng> routePoints = new ArrayList<>();
    private PolylineOptions routePolyline;

    private double averagePace = 0.0;
    private double instantPace = 0.0;

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private String courseId;

    private static class TimerRunnable implements Runnable {
        private final WeakReference<RunningStartActivity> activityRef;

        TimerRunnable(RunningStartActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            RunningStartActivity activity = activityRef.get();
            if (activity != null && activity.isRunning && !activity.isPaused) {
                long elapsedTime = System.currentTimeMillis() - activity.startTime - activity.totalPausedTime;
                activity.updateTimerDisplay(elapsedTime);
                activity.updatePace(elapsedTime);
                activity.timerHandler.postDelayed(this, 1000);
            }
        }
    }

    private TimerRunnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_start);

        // Initialize views
        timerTextView = findViewById(R.id.timerTextView);
        distanceTextView = findViewById(R.id.distanceTextView);
        averagePaceTextView = findViewById(R.id.averagePaceTextView);
        instantPaceTextView = findViewById(R.id.instantPaceTextView);
        startRunningButton = findViewById(R.id.startRunningButton);  // 수정
        pauseButton = findViewById(R.id.pauseButton);
        resumeButton = findViewById(R.id.resumeButton);  // 추가
        endRunButton = findViewById(R.id.endRunButton);

        if (timerTextView == null || distanceTextView == null || averagePaceTextView == null
                || instantPaceTextView == null || pauseButton == null || endRunButton == null
                || startRunningButton == null || resumeButton == null) {
            Log.e("RunningStartActivity", "필수 뷰를 찾을 수 없습니다.");
            GoogleSignInUtils.showToast(this, "화면 초기화 오류가 발생했습니다.");
            finish();
            return;
        }

        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (isRunning) {
                    showExitConfirmationDialog();
                } else {
                    finish();
                }
            });
        }

        handleCourseIntent();

        firestore = GoogleSignInUtils.getFirestore();
        firebaseAuth = GoogleSignInUtils.getAuth();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L
        )
                .setMinUpdateIntervalMillis(1000L)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setWaitForAccurateLocation(true)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                Location location = locationResult.getLastLocation();
                if (location != null && isRunning && !isPaused) {
                    updateDistance(location);
                    lastLocation = location;
                }
            }

            @Override
            public void onLocationAvailability(@NonNull LocationAvailability availability) {
                if (!availability.isLocationAvailable()) {
                    Log.w("RunningStartActivity", "GPS 신호가 약합니다.");
                    GoogleSignInUtils.showToast(
                            RunningStartActivity.this,
                            "GPS 신호가 약합니다. 야외로 이동해주세요."
                    );
                }
            }
        };

        FragmentManager fragmentManager = getSupportFragmentManager();
        SupportMapFragment mapFragment = (SupportMapFragment) fragmentManager.findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.w("RunningStartActivity", "MapFragment를 찾을 수 없습니다.");
        }

        // 버튼 클릭 리스너 수정
        startRunningButton.setOnClickListener(v -> startRunning());
        pauseButton.setOnClickListener(v -> pauseRunning());
        resumeButton.setOnClickListener(v -> resumeRunning());
        endRunButton.setOnClickListener(v -> endRun());

        if (checkLocationPermission()) {
            startLocationUpdates();
        } else {
            requestLocationPermission();
        }
    }

    // UI 상태 업데이트 메서드 추가
    private void updateButtonsForRunningState() {
        startRunningButton.setVisibility(View.GONE);
        pauseButton.setVisibility(View.VISIBLE);
        resumeButton.setVisibility(View.GONE);
        endRunButton.setVisibility(View.VISIBLE);
    }

    private void updateButtonsForPausedState() {
        startRunningButton.setVisibility(View.GONE);
        pauseButton.setVisibility(View.GONE);
        resumeButton.setVisibility(View.VISIBLE);
        endRunButton.setVisibility(View.VISIBLE);
    }

    private void updateButtonsForInitialState() {
        startRunningButton.setVisibility(View.VISIBLE);
        pauseButton.setVisibility(View.GONE);
        resumeButton.setVisibility(View.GONE);
        endRunButton.setVisibility(View.GONE);
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        if (checkLocationPermission()) {
            try {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
                getCurrentLocation();
            } catch (SecurityException e) {
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
                        e.printStackTrace();
                    }
                }
                getCurrentLocation();
                startLocationUpdates();
            } else {
                GoogleSignInUtils.showToast(this, "위치 권한이 필요합니다.");
                finish();
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
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                        lastLocation = location;
                    }
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (!checkLocationPermission()) {
            Log.w("RunningStartActivity", "위치 권한이 없습니다.");
            return;
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            Log.d("RunningStartActivity", "위치 업데이트 시작됨");
        } catch (SecurityException e) {
            Log.e("RunningStartActivity", "위치 업데이트 요청 실패", e);
            e.printStackTrace();
        }
    }

    private void updateDistance(Location newLocation) {
        if (newLocation.getAccuracy() > 50) {
            Log.w("RunningStartActivity", "위치 정확도가 낮습니다: " + newLocation.getAccuracy() + "m");
            return;
        }

        LatLng currentLatLng = new LatLng(newLocation.getLatitude(), newLocation.getLongitude());

        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(newLocation);

            if (distance < 5) {
                return;
            }

            totalDistance += distance / 1000.0;
            distanceTextView.setText(GoogleSignInUtils.formatDistanceKm(totalDistance));
        }

        if (routePoints.isEmpty() ||
                lastLocation == null ||
                lastLocation.distanceTo(newLocation) >= 10) {
            routePoints.add(currentLatLng);
        }

        if (map != null) {
            map.clear();

            if (routePoints.size() > 1) {
                routePolyline = new PolylineOptions()
                        .addAll(routePoints)
                        .width(10f)
                        .color(android.graphics.Color.parseColor("#FF6B35"));
                map.addPolyline(routePolyline);
            }

            if (!routePoints.isEmpty()) {
                map.addMarker(new MarkerOptions()
                        .position(routePoints.get(0))
                        .title("시작"));
            }

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
                routePoints.clear();

                if (distanceTextView != null) {
                    distanceTextView.setText("0.00 km");
                }
                if (averagePaceTextView != null) {
                    averagePaceTextView.setText("--:--/km");
                }
                if (instantPaceTextView != null) {
                    instantPaceTextView.setText("--:--/km");
                }

                startTimer();
                getCurrentLocation();
                startLocationUpdates();

                // UI 업데이트: 러닝 중 상태
                updateButtonsForRunningState();

            } catch (Exception e) {
                Log.e("RunningStartActivity", "운동 시작 중 오류 발생", e);
                GoogleSignInUtils.showToast(this, "운동 시작 중 오류가 발생했습니다.");
                isRunning = false;
            }
        }
    }

    // 일시정지 메서드 추가
    private void pauseRunning() {
        if (isRunning && !isPaused) {
            isPaused = true;
            pausedTime = System.currentTimeMillis();
            stopTimer();

            // UI 업데이트: 일시정지 상태
            updateButtonsForPausedState();
        }
    }

    // 재개 메서드 추가
    private void resumeRunning() {
        if (isRunning && isPaused) {
            isPaused = false;
            totalPausedTime += (System.currentTimeMillis() - pausedTime);
            startTimer();

            // UI 업데이트: 러닝 중 상태
            updateButtonsForRunningState();
        }
    }

    private void endRun() {
        if (!isRunning) {
            return;
        }

        if (totalDistance < 0.1) {
            new AlertDialog.Builder(this)
                    .setTitle("기록 저장 불가")
                    .setMessage("최소 100m 이상 이동해야 기록이 저장됩니다.")
                    .setPositiveButton("확인", (dialog, which) -> {
                        isRunning = false;
                        isPaused = false;
                        stopTimer();
                        stopLocationUpdates();
                        updateButtonsForInitialState();
                    })
                    .show();
            return;
        }

        isRunning = false;
        isPaused = false;
        stopTimer();
        stopLocationUpdates();

        long elapsedTime = System.currentTimeMillis() - startTime - totalPausedTime;
        String distance = distanceTextView.getText().toString();
        String time = timerTextView.getText().toString();
        String pace = averagePaceTextView.getText().toString();

        saveRunToFirestore(elapsedTime, distance, time, pace);

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
        String pathEncoded = PolylineUtils.encode(routePoints);

        Date startTimeDate = new Date(startTime);
        Date endTimeDate = new Date(System.currentTimeMillis());

        GeoPoint startMarker = null;
        GeoPoint endMarker = null;
        if (!routePoints.isEmpty()) {
            LatLng startPoint = routePoints.get(0);
            LatLng endPoint = routePoints.get(routePoints.size() - 1);
            startMarker = new GeoPoint(startPoint.latitude, startPoint.longitude);
            endMarker = new GeoPoint(endPoint.latitude, endPoint.longitude);
        }

        double totalDistanceMeters = totalDistance * 1000.0;
        long totalTimeSeconds = elapsedTimeMs / 1000;
        double averagePaceSeconds = GoogleSignInUtils.parsePaceToSeconds(pace);

        Map<String, Object> runData = new HashMap<>();
        runData.put("type", courseId != null ? "sketch" : "free");
        runData.put("startTime", new Timestamp(startTimeDate));
        runData.put("endTime", new Timestamp(endTimeDate));
        runData.put("totalDistance", totalDistanceMeters);
        runData.put("totalTime", totalTimeSeconds);
        runData.put("averagePace", averagePaceSeconds);
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
        runData.put("createdAt", FieldValue.serverTimestamp());

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
        if (timerRunnable == null) {
            timerRunnable = new TimerRunnable(this);
        }
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void updateTimerDisplay(long elapsedTime) {
        if (timerTextView != null) {
            timerTextView.setText(GoogleSignInUtils.formatElapsedTime(elapsedTime));
        }
    }

    private void updatePace(long elapsedTime) {
        if (totalDistance > 0) {
            double elapsedHours = elapsedTime / 3600000.0;
            averagePace = totalDistance / elapsedHours;

            double averagePaceMinPerKm = 60.0 / averagePace;
            double averagePaceSeconds = averagePaceMinPerKm * 60.0;
            averagePaceTextView.setText(GoogleSignInUtils.formatPaceFromSeconds(averagePaceSeconds));

            if (lastLocation != null) {
                instantPace = averagePace;
                double instantPaceMinPerKm = 60.0 / instantPace;
                double instantPaceSeconds = instantPaceMinPerKm * 60.0;
                instantPaceTextView.setText(GoogleSignInUtils.formatPaceFromSeconds(instantPaceSeconds));
            }
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("RunningStartActivity", "위치 업데이트 중지됨");
                    })
                    .addOnFailureListener(e -> {
                        Log.e("RunningStartActivity", "위치 업데이트 중지 실패", e);
                    });
        }
    }

    private void handleCourseIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("course_id")) {
            courseId = intent.getStringExtra("course_id");
        } else if (intent != null && intent.hasExtra("course_name")) {
            String courseName = intent.getStringExtra("course_name");
            double courseDistance = intent.getDoubleExtra("course_distance", 0.0);
            String courseDifficulty = intent.getStringExtra("course_difficulty");
        }
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("운동 종료")
                .setMessage("운동을 종료하시겠습니까? 기록이 저장되지 않습니다.")
                .setPositiveButton("종료", (dialog, which) -> {
                    stopTimer();
                    stopLocationUpdates();
                    finish();
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        stopLocationUpdates();

        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
    }
}
