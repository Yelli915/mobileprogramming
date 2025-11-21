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

<<<<<<< Updated upstream
        // Initialize map
=======
        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L
        )
                .setMinUpdateIntervalMillis(1000L)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setWaitForAccurateLocation(false)
                .setMaxUpdateDelayMillis(5000L)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    // 위치 정확도에 따른 경고 메시지 표시
                    checkLocationAccuracy(location);

                    // 러닝 중일 때는 거리 업데이트 및 가이드 포인트 체크
                    if (isRunning && !isPaused) {
                        updateDistance(location);

                        // 스케치 런인 경우 가이드 포인트 체크
                        if (courseId != null) {
                            checkGuidePoints(location);
                        }
                    } else {
                        // 러닝 시작 전에도 지도에 현재 위치 실시간 반영
                        updateMapLocation(location);
                    }

                    lastLocation = location;
                } else {
                    // 위치가 null인 경우 - GPS 신호가 약하거나 위치를 받지 못함
                    Log.w("RunningStartActivity", "위치 정보를 받지 못했습니다. GPS 신호를 확인해주세요.");
                }
            }

            @Override
            public void onLocationAvailability(@NonNull LocationAvailability availability) {
                if (!availability.isLocationAvailable()) {
                    Log.w("RunningStartActivity", "위치 서비스를 사용할 수 없습니다.");
                    // LocationAvailability은 위치가 완전히 사용 불가능할 때만 false
                    // 실제 위치 정확도는 onLocationResult에서 확인
                }
            }
        };

>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
=======
    // TTS 초기화
    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.KOREAN);

                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "한국어가 지원되지 않습니다.");
                        GoogleSignInUtils.showToast(
                                RunningStartActivity.this,
                                "음성 안내를 사용할 수 없습니다."
                        );
                        isTTSInitialized = false;
                    } else {
                        textToSpeech.setPitch(1.0f);
                        textToSpeech.setSpeechRate(1.0f);

                        isTTSInitialized = true;
                        Log.d("TTS", "TTS 초기화 성공");

                        speak("음성 안내가 준비되었습니다.");
                    }
                } else {
                    Log.e("TTS", "TTS 초기화 실패");
                    isTTSInitialized = false;
                }
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d("TTS", "음성 시작: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d("TTS", "음성 완료: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Log.e("TTS", "음성 오류: " + utteranceId);
            }
        });
    }

    // 가이드 포인트 로드
    private void loadGuidePoints() {
        if (courseId == null) {
            return;
        }

        firestore.collection("courses")
                .document(courseId)
                .collection("guidePoints")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    guidePoints.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        GuidePoint guidePoint = new GuidePoint();
                        guidePoint.setId(document.getId());

                        if (document.contains("location")) {
                            guidePoint.setLocation(document.getGeoPoint("location"));
                        }
                        if (document.contains("message")) {
                            guidePoint.setMessage(document.getString("message"));
                        }
                        if (document.contains("order")) {
                            Long order = document.getLong("order");
                            if (order != null) {
                                guidePoint.setOrder(order.intValue());
                            }
                        }

                        guidePoints.add(guidePoint);
                    }

                    // order 순으로 정렬
                    Collections.sort(guidePoints, new Comparator<GuidePoint>() {
                        @Override
                        public int compare(GuidePoint gp1, GuidePoint gp2) {
                            return Integer.compare(gp1.getOrder(), gp2.getOrder());
                        }
                    });

                    Log.d("GuidePoints", guidePoints.size() + "개의 가이드 포인트 로드됨");

                    if (!guidePoints.isEmpty()) {
                        GoogleSignInUtils.showToast(
                                this,
                                guidePoints.size() + "개의 안내 지점이 설정되었습니다"
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("GuidePoints", "가이드 포인트 로드 실패", e);
                });
    }

    // TTS 음성 출력
    private void speak(String text) {
        if (!isTTSInitialized || textToSpeech == null) {
            Log.w("TTS", "TTS가 초기화되지 않았습니다.");
            return;
        }

        if (text == null || text.isEmpty()) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            String utteranceId = String.valueOf(System.currentTimeMillis());
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                    String.valueOf(System.currentTimeMillis()));
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, params);
        }

        Log.d("TTS", "음성 출력: " + text);
    }

    // 페이스 기반 거리 계산
    private float calculateDistanceForSeconds(int seconds) {
        if (currentPaceKmh <= 0) {
            // 페이스가 없으면 평균 러닝 속도 가정 (9km/h)
            currentPaceKmh = 9.0;
        }

        // km/h를 m/s로 변환
        double metersPerSecond = (currentPaceKmh * 1000.0) / 3600.0;

        // 주어진 시간(초) 동안 이동할 거리(미터)
        float distance = (float) (metersPerSecond * seconds);

        Log.d("PaceCalculation",
                "현재 페이스: " + currentPaceKmh + " km/h, " +
                        seconds + "초 후 거리: " + distance + "m");

        return distance;
    }

    // 가이드 포인트 체크 (사전 안내 포함)
    private void checkGuidePoints(Location currentLocation) {
        if (guidePoints.isEmpty()) {
            return;
        }

        // 10초 후 도달할 거리 계산
        float preAnnounceDistance = calculateDistanceForSeconds(PRE_ANNOUNCE_SECONDS);

        for (GuidePoint guidePoint : guidePoints) {
            GeoPoint geoPoint = guidePoint.getLocation();
            if (geoPoint == null) {
                continue;
            }

            // 현재 위치에서 가이드 포인트까지 거리 계산
            float[] results = new float[1];
            Location.distanceBetween(
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    geoPoint.getLatitude(),
                    geoPoint.getLongitude(),
                    results
            );

            float distance = results[0];
            String pointId = guidePoint.getId();

            Log.d("GuidePoint",
                    "가이드 포인트 " + guidePoint.getOrder() +
                            "까지 거리: " + distance + "m (사전안내 임계값: " + preAnnounceDistance + "m)");

            // 10초 전 사전 안내
            if (distance <= preAnnounceDistance &&
                    distance > GUIDE_POINT_THRESHOLD &&
                    !preAnnouncedGuidePoints.contains(pointId) &&
                    !announcedGuidePoints.contains(pointId)) {

                preAnnounceGuidePoint(guidePoint, distance);
                preAnnouncedGuidePoints.add(pointId);
            }

            // 도착 안내 (20m 이내)
            if (distance <= GUIDE_POINT_THRESHOLD &&
                    !announcedGuidePoints.contains(pointId)) {

                announceGuidePoint(guidePoint);
                announcedGuidePoints.add(pointId);

                // 지도에 마커 표시
                if (map != null) {
                    LatLng guideLatLng = new LatLng(
                            geoPoint.getLatitude(),
                            geoPoint.getLongitude()
                    );
                    String guideLocationInfo = formatLocation(geoPoint.getLatitude(), geoPoint.getLongitude());
                    String snippet = guidePoint.getMessage() != null 
                            ? guidePoint.getMessage() + "\n" + guideLocationInfo
                            : guideLocationInfo;
                    map.addMarker(new MarkerOptions()
                            .position(guideLatLng)
                            .title("안내 지점 " + guidePoint.getOrder())
                            .snippet(snippet));
                }
            }
        }
    }

    // 사전 안내
    private void preAnnounceGuidePoint(GuidePoint guidePoint, float distance) {
        String message = guidePoint.getMessage();

        // 거리를 초로 변환
        int secondsToArrival = (int) (distance / (currentPaceKmh * 1000.0 / 3600.0));

        // 사전 안내 메시지 생성
        String preAnnounceMessage = "약 " + secondsToArrival + "초 후, " + message;

        Log.d("PreAnnounce", "사전 안내: " + preAnnounceMessage);

        // TTS 음성 출력
        speak(preAnnounceMessage);
    }

    // 가이드 포인트 안내
    private void announceGuidePoint(GuidePoint guidePoint) {
        String message = guidePoint.getMessage();

        if (message == null || message.isEmpty()) {
            message = "안내 지점에 도착했습니다";
        }

        Log.d("GuidePoint", "안내: " + message);

        GoogleSignInUtils.showToast(this, message);
        speak(message);
    }

    // UI 상태 업데이트 메서드
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

>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
<<<<<<< Updated upstream
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        map.addMarker(new MarkerOptions().position(currentLatLng).title("현재 위치"));
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                        lastLocation = location;
=======
=======
>>>>>>> Stashed changes
                        // 위치 유효성 검증
                        if (isValidLocation(location)) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                            lastLocation = location;
                            Log.d("RunningStartActivity", "현재 위치: " + formatLocation(location.getLatitude(), location.getLongitude()) + " (정확도: " + location.getAccuracy() + "m)");
                        } else {
                            Log.w("RunningStartActivity", "위치가 유효하지 않거나 오래되었습니다. 실시간 위치 업데이트를 기다립니다...");
                            // getLastLocation()이 null이거나 오래된 경우, 실시간 업데이트를 기다림
                            // locationCallback에서 처리됨
                        }
                    } else {
                        Log.w("RunningStartActivity", "getLastLocation()이 null을 반환했습니다. 실시간 위치 업데이트를 기다립니다...");
                        // 위치가 없으면 실시간 업데이트를 기다림
<<<<<<< Updated upstream
>>>>>>> Stashed changes
=======
>>>>>>> Stashed changes
                    }
                }
            });
        } catch (SecurityException e) {
<<<<<<< Updated upstream
<<<<<<< Updated upstream
            // Permission was revoked between check and use
=======
            Log.e("RunningStartActivity", "위치 권한 오류", e);
>>>>>>> Stashed changes
=======
            Log.e("RunningStartActivity", "위치 권한 오류", e);
>>>>>>> Stashed changes
            e.printStackTrace();
        }
    }

<<<<<<< Updated upstream
<<<<<<< Updated upstream
    private void requestLocationUpdates() {
=======
=======
>>>>>>> Stashed changes
    private boolean isValidLocation(Location location) {
        if (location == null) {
            return false;
        }

        // 위치가 너무 오래된 경우 (5분 이상)
        long locationAge = System.currentTimeMillis() - location.getTime();
        if (locationAge > 5 * 60 * 1000) {
            Log.w("RunningStartActivity", "위치가 너무 오래되었습니다: " + (locationAge / 1000) + "초 전");
            return false;
        }

        // 위치 정확도가 너무 낮은 경우 (100m 이상)
        if (location.getAccuracy() > 100) {
            Log.w("RunningStartActivity", "위치 정확도가 낮습니다: " + location.getAccuracy() + "m");
            return false;
        }

        // 구글 본사 좌표 체크 (37.4219983, -122.084) - 일반적으로 잘못된 위치
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        if (Math.abs(lat - 37.4219983) < 0.001 && Math.abs(lng - (-122.084)) < 0.001) {
            Log.w("RunningStartActivity", "구글 본사 좌표 감지 - 위치가 유효하지 않습니다");
            return false;
        }

        // 좌표가 0,0인 경우 (유효하지 않은 위치)
        if (lat == 0.0 && lng == 0.0) {
            Log.w("RunningStartActivity", "좌표가 (0,0)입니다 - 위치가 유효하지 않습니다");
            return false;
        }

        return true;
    }

    private boolean isValidLocationForRunning(Location location) {
        if (location == null) {
            return false;
        }

        // 위치가 너무 오래된 경우 (10분 이상) - 러닝 중에는 더 긴 시간 허용
        long locationAge = System.currentTimeMillis() - location.getTime();
        if (locationAge > 10 * 60 * 1000) {
            Log.w("RunningStartActivity", "위치가 너무 오래되었습니다: " + (locationAge / 1000) + "초 전");
            return false;
        }

        // 러닝 중에는 정확도 기준을 완화 (200m까지 허용)
        if (location.getAccuracy() > 200) {
            Log.w("RunningStartActivity", "위치 정확도가 너무 낮습니다: " + location.getAccuracy() + "m");
            return false;
        }

        // 구글 본사 좌표 체크
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        if (Math.abs(lat - 37.4219983) < 0.001 && Math.abs(lng - (-122.084)) < 0.001) {
            Log.w("RunningStartActivity", "구글 본사 좌표 감지 - 위치가 유효하지 않습니다");
            return false;
        }

        // 좌표가 0,0인 경우
        if (lat == 0.0 && lng == 0.0) {
            Log.w("RunningStartActivity", "좌표가 (0,0)입니다 - 위치가 유효하지 않습니다");
            return false;
        }

        return true;
    }

    private void checkLocationAccuracy(Location location) {
        if (location == null) {
            return;
        }

        float accuracy = location.getAccuracy();
        long locationAge = System.currentTimeMillis() - location.getTime();

        // 위치가 너무 오래된 경우
        if (locationAge > 30000) { // 30초 이상
            Log.w("RunningStartActivity", "위치 정보가 오래되었습니다: " + (locationAge / 1000) + "초 전");
            if (locationAge > 60000) { // 1분 이상
                GoogleSignInUtils.showToast(this, "GPS 신호가 약합니다. 야외로 이동해주세요.");
            }
            return;
        }

        // 위치 정확도에 따른 단계적 경고
        if (accuracy > 50) {
            if (accuracy > 100) {
                // 정확도가 매우 낮은 경우 (100m 이상)
                Log.w("RunningStartActivity", "위치 정확도가 매우 낮습니다: " + accuracy + "m");
                if (!isRunning) {
                    // 러닝 중이 아닐 때만 토스트 표시 (러닝 중에는 너무 자주 표시되지 않도록)
                    GoogleSignInUtils.showToast(this, "GPS 신호가 약합니다. 정확한 위치를 위해 야외로 이동해주세요.");
                }
            } else {
                // 정확도가 낮은 경우 (50-100m)
                Log.w("RunningStartActivity", "위치 정확도가 낮습니다: " + accuracy + "m");
            }
        } else {
            // 정확도가 양호한 경우
            Log.d("RunningStartActivity", "위치 정확도 양호: " + accuracy + "m");
        }
    }

<<<<<<< Updated upstream
=======
    @SuppressLint("MissingPermission")
    private void updateMapLocation(Location location) {
        if (location == null || map == null) {
            return;
        }

        // 위치 유효성 검증
        if (!isValidLocation(location)) {
            Log.w("RunningStartActivity", "유효하지 않은 위치 - 지도 업데이트 건너뜀");
            return;
        }

        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (map != null) {
            map.clear();

            String locationInfo = formatLocation(location.getLatitude(), location.getLongitude());
            map.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("현재 위치")
                    .snippet(locationInfo));

            map.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng));
            
            Log.d("RunningStartActivity", "지도 위치 업데이트: " + locationInfo + " (정확도: " + location.getAccuracy() + "m)");
        }
    }

    private String formatLocation(double latitude, double longitude) {
        String latDirection = latitude >= 0 ? "북위" : "남위";
        String lngDirection = longitude >= 0 ? "동경" : "서경";
        
        return String.format(Locale.getDefault(), "%s %.6f, %s %.6f", 
                latDirection, Math.abs(latitude), 
                lngDirection, Math.abs(longitude));
    }

>>>>>>> Stashed changes
    @SuppressLint("MissingPermission")
    private void updateMapLocation(Location location) {
        if (location == null || map == null) {
            return;
        }

<<<<<<< Updated upstream
        // 위치 유효성 검증
        if (!isValidLocation(location)) {
            Log.w("RunningStartActivity", "유효하지 않은 위치 - 지도 업데이트 건너뜀");
=======
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
        // 위치 유효성 검증
        if (!isValidLocation(newLocation)) {
            Log.w("RunningStartActivity", "유효하지 않은 위치 - 거리 업데이트 건너뜀");
>>>>>>> Stashed changes
            return;
        }

        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (map != null) {
            map.clear();

            String locationInfo = formatLocation(location.getLatitude(), location.getLongitude());
            map.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("현재 위치")
                    .snippet(locationInfo));

            map.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng));
            
            Log.d("RunningStartActivity", "지도 위치 업데이트: " + locationInfo + " (정확도: " + location.getAccuracy() + "m)");
        }
    }

    private String formatLocation(double latitude, double longitude) {
        String latDirection = latitude >= 0 ? "북위" : "남위";
        String lngDirection = longitude >= 0 ? "동경" : "서경";
        
        return String.format(Locale.getDefault(), "%s %.6f, %s %.6f", 
                latDirection, Math.abs(latitude), 
                lngDirection, Math.abs(longitude));
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======
        // 위치 유효성 검증
        if (!isValidLocation(newLocation)) {
            Log.w("RunningStartActivity", "유효하지 않은 위치 - 거리 업데이트 건너뜀");
            return;
        }

>>>>>>> Stashed changes
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
                LatLng startPoint = routePoints.get(0);
                String startLocationInfo = formatLocation(startPoint.latitude, startPoint.longitude);
                map.addMarker(new MarkerOptions()
                        .position(startPoint)
                        .title("시작")
                        .snippet(startLocationInfo));
            }
<<<<<<< Updated upstream
<<<<<<< Updated upstream
            
            // 현재 위치 마커
            map.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("현재 위치"));
            
=======
=======

            String currentLocationInfo = formatLocation(newLocation.getLatitude(), newLocation.getLongitude());
            map.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("현재 위치")
                    .snippet(currentLocationInfo));
>>>>>>> Stashed changes

            String currentLocationInfo = formatLocation(newLocation.getLatitude(), newLocation.getLongitude());
            map.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("현재 위치")
                    .snippet(currentLocationInfo));

>>>>>>> Stashed changes
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

