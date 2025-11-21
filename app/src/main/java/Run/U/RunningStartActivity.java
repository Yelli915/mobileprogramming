package Run.U;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
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
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RunningStartActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 2000L;
    private static final float GUIDE_POINT_THRESHOLD = 20.0f;  // 도착 감지: 20m
    private static final int PRE_ANNOUNCE_SECONDS = 10;  // 10초 전 사전 안내

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    // Views
    private TextView timerTextView;
    private TextView distanceTextView;
    private TextView averagePaceTextView;
    private TextView instantPaceTextView;
    private Button startRunningButton;
    private Button pauseButton;
    private Button resumeButton;
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

    private double totalDistance = 0.0;
    private Location lastLocation = null;
    private Handler locationUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable locationUpdateRunnable = null;
    private float currentZoomLevel = 15f; // 현재 줌 레벨 저장 (15배율 고정)

    private List<LatLng> routePoints = new ArrayList<>();
    private Polyline routePolyline = null;
    
    // Map markers (재사용을 위한 멤버 변수)
    private Marker startLocationMarker = null;

    private double averagePace = 0.0;
    private double instantPace = 0.0;
    private double currentPaceKmh = 0.0;  // 현재 페이스 (km/h)

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private String courseId;

    // TTS 관련 변수
    private TextToSpeech textToSpeech;
    private boolean isTTSInitialized = false;

    // 가이드 포인트 관련 변수
    private List<GuidePoint> guidePoints = new ArrayList<>();
    private Set<String> announcedGuidePoints = new HashSet<>();
    private Set<String> preAnnouncedGuidePoints = new HashSet<>();  // 사전 안내 추적

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
        startRunningButton = findViewById(R.id.startRunningButton);
        pauseButton = findViewById(R.id.pauseButton);
        resumeButton = findViewById(R.id.resumeButton);
        endRunButton = findViewById(R.id.endRunButton);

        if (timerTextView == null || distanceTextView == null || averagePaceTextView == null
                || instantPaceTextView == null || pauseButton == null || endRunButton == null
                || startRunningButton == null || resumeButton == null) {
            Log.e("RunningStartActivity", "필수 뷰를 찾을 수 없습니다.");
            GoogleSignInUtils.showToast(this, "화면 초기화 오류가 발생했습니다.");
            finish();
            return;
        }


        handleCourseIntent();

        firestore = GoogleSignInUtils.getFirestore();
        firebaseAuth = GoogleSignInUtils.getAuth();

        // TTS 초기화
        initializeTextToSpeech();

        // 스케치 런인 경우 가이드 포인트 로드
        if (courseId != null) {
            loadGuidePoints();
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize LocationRequest - 실시간 GPS 우선
        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L  // 1초마다 업데이트
        )
                .setMinUpdateIntervalMillis(500L)  // 최소 0.5초 간격
                .setGranularity(Granularity.GRANULARITY_FINE)  // 정밀 위치
                .setWaitForAccurateLocation(true)  // 정확한 위치 대기
                .setMaxUpdateDelayMillis(2000L)  // 최대 2초 지연
                .setMinUpdateDistanceMeters(0f)  // 거리 제한 없음
                .build();

        // Initialize LocationCallback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    // 위치 유효성 검증 (실시간 업데이트에서는 러닝 중 기준 사용)
                    boolean isValid = isRunning ? isValidLocationForRunning(location) : isValidLocation(location);
                    
                    if (isValid) {
                        // 위치 정확도에 따른 경고 메시지 표시
                        checkLocationAccuracy(location);

                        // 러닝 중일 때는 거리 업데이트
                        if (isRunning && !isPaused) {
                            updateDistance(location);
                        } else {
                            // 러닝 시작 전에도 지도에 현재 위치 실시간 반영
                            updateMapLocation(location);
                        }

                        lastLocation = location;
                        long locationAge = (System.currentTimeMillis() - location.getTime()) / 1000;
                        Log.d("RunningStartActivity", "✅ 실시간 GPS 위치 수신: " + formatLocation(location.getLatitude(), location.getLongitude()) + 
                            " (정확도: " + String.format("%.1f", location.getAccuracy()) + "m, 나이: " + locationAge + "초)");
                    } else {
                        // 유효하지 않은 위치는 무시하고 다음 업데이트 대기
                        // 상세한 무시 이유를 로그에 남김
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();
                        long locationAge = System.currentTimeMillis() - location.getTime();
                        Log.d("RunningStartActivity", "유효하지 않은 위치 무시됨 - 좌표: " + formatLocation(lat, lng) + ", 정확도: " + location.getAccuracy() + "m, 나이: " + (locationAge / 1000) + "초 전 - 다음 GPS 업데이트 대기 중");
                    }
                } else {
                    // 위치가 null인 경우 - GPS 신호가 약하거나 위치를 받지 못함
                    Log.d("RunningStartActivity", "위치 정보를 받지 못했습니다. GPS 신호 수신 중... (다음 업데이트 대기)");
                }
            }

            @Override
            public void onLocationAvailability(@NonNull LocationAvailability availability) {
                if (!availability.isLocationAvailable()) {
                    Log.w("RunningStartActivity", "위치 서비스를 사용할 수 없습니다. GPS 신호를 확인해주세요.");
                    // 위치 서비스를 사용할 수 없을 때는 로그만 남기고, 실시간 업데이트는 계속 시도
                    // 일부 기기에서는 GPS 신호를 받기까지 시간이 걸릴 수 있음
                } else {
                    Log.d("RunningStartActivity", "위치 서비스 사용 가능 - GPS 신호 수신 중");
                }
            }
        };

        // Initialize map
        Log.d("RunningStartActivity", "지도 초기화 시작");
        FragmentManager fragmentManager = getSupportFragmentManager();
        SupportMapFragment mapFragment = (SupportMapFragment) fragmentManager.findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            Log.d("RunningStartActivity", "MapFragment 찾음 - getMapAsync() 호출");
            mapFragment.getMapAsync(this);
        } else {
            Log.e("RunningStartActivity", "MapFragment를 찾을 수 없습니다. 지도가 표시되지 않습니다.");
        }

        // 버튼 클릭 리스너
        startRunningButton.setOnClickListener(v -> startRunning());
        pauseButton.setOnClickListener(v -> pauseRunning());
        resumeButton.setOnClickListener(v -> resumeRunning());
        endRunButton.setOnClickListener(v -> endRun());

        // Request location permissions
        Log.d("RunningStartActivity", "위치 권한 체크 시작");
        if (checkLocationPermission()) {
            Log.d("RunningStartActivity", "위치 권한 있음 - 위치 업데이트 요청");
            requestLocationUpdates();
        } else {
            Log.w("RunningStartActivity", "위치 권한 없음 - 권한 요청");
            requestLocationPermission();
        }
    }

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
                    map.addMarker(new MarkerOptions()
                            .position(guideLatLng)
                            .title("안내 지점 " + guidePoint.getOrder())
                            .snippet(guidePoint.getMessage()));
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

    @Override
    @SuppressLint("MissingPermission")
    public void onMapReady(GoogleMap googleMap) {
        Log.d("RunningStartActivity", "onMapReady() 호출됨 - 지도 초기화 시작");
        map = googleMap;

        // 지도 설정 - 15배율로 고정
        currentZoomLevel = 15f;
        
        // Enable my location button if permission granted
        if (checkLocationPermission()) {
            try {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
                Log.d("RunningStartActivity", "내 위치 버튼 활성화 완료");
                
                // 1순위: 화면 진입 시 최초 위치 즉시 표시 (현재 위치 버튼 누르지 않아도)
                Log.d("RunningStartActivity", "1순위: 최초 위치 즉시 표시 시작");
                getCurrentLocationRealtime();
                
                // 실시간 GPS 업데이트 시작 (지속적, 버튼 클릭 전에도 위치 수신 대기)
                requestLocationUpdates();
            } catch (SecurityException e) {
                Log.e("RunningStartActivity", "내 위치 활성화 실패", e);
                e.printStackTrace();
            }
        } else {
            Log.w("RunningStartActivity", "위치 권한 없음 - 권한 요청 필요");
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
                        Log.d("RunningStartActivity", "위치 권한 승인 - 최초 위치 가져오기 시작");
                        // 권한 승인 후 최초 위치 즉시 표시
                        getCurrentLocationRealtime();
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }
                requestLocationUpdates();
            } else {
                GoogleSignInUtils.showToast(this, "위치 권한이 필요합니다.");
                finish();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocationRealtime() {
        if (!checkLocationPermission()) {
            Log.w("RunningStartActivity", "위치 권한이 없습니다. 권한을 요청합니다.");
            requestLocationPermission();
            return;
        }
        
        if (map == null) {
            Log.w("RunningStartActivity", "지도가 아직 준비되지 않았습니다.");
            return;
        }
        
        Log.d("RunningStartActivity", "1순위: 최초 위치 빠른 표시 시작");
        
        // 1단계: 캐시된 위치를 먼저 빠르게 가져와서 즉시 표시 (개선된 속도)
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(cachedLocation -> {
                if (cachedLocation != null && map != null) {
                    long locationAge = System.currentTimeMillis() - cachedLocation.getTime();
                    
                    // 캐시된 위치가 1분 이내이고 유효하면 즉시 표시
                    if (locationAge < 60000 && isValidLocation(cachedLocation)) {
                        LatLng currentLatLng = new LatLng(cachedLocation.getLatitude(), cachedLocation.getLongitude());
                        currentZoomLevel = 15f;
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, currentZoomLevel));
                        lastLocation = cachedLocation;
                        
                        Log.d("RunningStartActivity", "✅ 캐시 위치로 즉시 표시 완료 (15배율): " + 
                            formatLocation(cachedLocation.getLatitude(), cachedLocation.getLongitude()) + 
                            " (정확도: " + cachedLocation.getAccuracy() + "m, 나이: " + (locationAge / 1000) + "초)");
                    }
                }
                
                // 2단계: 동시에 정확한 GPS 위치 요청 (캐시 표시와 병렬, 빠른 업데이트)
                requestAccurateLocation();
            }).addOnFailureListener(e -> {
                Log.w("RunningStartActivity", "캐시 위치 가져오기 실패, 정확한 위치 요청으로 진행", e);
                requestAccurateLocation();
            });
        } catch (SecurityException e) {
            Log.e("RunningStartActivity", "위치 권한 오류", e);
            requestAccurateLocation();
        }
    }
    
    @SuppressLint("MissingPermission")
    private void requestAccurateLocation() {
        if (!checkLocationPermission() || map == null) {
            return;
        }
        
        try {
            // 빠른 응답을 위해 BALANCED_POWER_ACCURACY로 먼저 시도
            com.google.android.gms.tasks.CancellationTokenSource cancellationTokenSource = 
                new com.google.android.gms.tasks.CancellationTokenSource();
            
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.getToken()
            ).addOnSuccessListener(location -> {
                if (location != null && map != null) {
                    long locationAge = System.currentTimeMillis() - location.getTime();
                    
                    if (isValidLocation(location) || (locationAge < 10000 && location.getAccuracy() < 100)) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        currentZoomLevel = 15f;
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, currentZoomLevel));
                        lastLocation = location;
                        
                        Log.d("RunningStartActivity", "✅ 빠른 위치 업데이트 완료 (15배율): " + 
                            formatLocation(location.getLatitude(), location.getLongitude()) + 
                            " (정확도: " + location.getAccuracy() + "m)");
                    } else {
                        // 정확도가 낮으면 HIGH_ACCURACY로 재시도
                        requestHighAccuracyLocation();
                    }
                }
            }).addOnFailureListener(e -> {
                // BALANCED 실패 시 HIGH_ACCURACY로 재시도
                Log.d("RunningStartActivity", "BALANCED 위치 실패, HIGH_ACCURACY로 재시도", e);
                requestHighAccuracyLocation();
            });
        } catch (SecurityException e) {
            Log.e("RunningStartActivity", "위치 권한 오류", e);
            requestHighAccuracyLocation();
        }
    }
    
    @SuppressLint("MissingPermission")
    private void requestHighAccuracyLocation() {
        if (!checkLocationPermission() || map == null) {
            return;
        }
        
        try {
            com.google.android.gms.tasks.CancellationTokenSource cancellationTokenSource = 
                new com.google.android.gms.tasks.CancellationTokenSource();
            
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.getToken()
            ).addOnSuccessListener(location -> {
                if (location != null && map != null) {
                    if (isValidLocation(location)) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        currentZoomLevel = 15f;
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, currentZoomLevel));
                        lastLocation = location;
                        
                        Log.d("RunningStartActivity", "✅ 고정밀 위치 업데이트 완료 (15배율): " + 
                            formatLocation(location.getLatitude(), location.getLongitude()) + 
                            " (정확도: " + location.getAccuracy() + "m)");
                    }
                }
            }).addOnFailureListener(e -> {
                Log.e("RunningStartActivity", "고정밀 위치 수신 실패", e);
                // 실패해도 locationCallback에서 업데이트될 예정
            });
        } catch (SecurityException e) {
            Log.e("RunningStartActivity", "위치 권한 오류", e);
        }
    }
    
    
    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        getCurrentLocationRealtime();
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        if (!checkLocationPermission()) {
            Log.w("RunningStartActivity", "위치 권한이 없습니다.");
            return;
        }

        try {
            // 기존 업데이트가 있다면 제거 후 재시작 (중복 방지)
            stopLocationUpdates();
            
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            Log.d("RunningStartActivity", "✅ 실시간 GPS 위치 업데이트 시작");
            Log.d("RunningStartActivity", "   - 업데이트 간격: 1초");
            Log.d("RunningStartActivity", "   - 최소 간격: 0.5초");
            Log.d("RunningStartActivity", "   - 정밀도: 최고 정밀도 (FINE)");
            Log.d("RunningStartActivity", "   - 정확한 위치 대기: 활성화");
            Log.d("RunningStartActivity", "   - 지도 상태: " + (map != null ? "초기화됨" : "null"));
        } catch (SecurityException e) {
            Log.e("RunningStartActivity", "❌ 위치 업데이트 요청 실패", e);
            e.printStackTrace();
        }
    }
    
    // 위치 업데이트 중지
    private void stopLocationUpdates() {
        if (locationCallback != null && fusedLocationClient != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                Log.d("RunningStartActivity", "기존 위치 업데이트 중지");
            } catch (Exception e) {
                Log.w("RunningStartActivity", "위치 업데이트 중지 실패", e);
            }
        }
    }

    private boolean isValidLocation(Location location) {
        if (location == null) {
            return false;
        }

        // 위치가 너무 오래된 경우 (10초 이상) - 캐시 방지 강화
        long locationAge = System.currentTimeMillis() - location.getTime();
        if (locationAge > 10 * 1000) {
            Log.w("RunningStartActivity", "위치가 너무 오래되었습니다: " + (locationAge / 1000) + "초 전 - 캐시된 위치일 가능성");
            return false;
        }

        // 위치 정확도가 너무 낮은 경우 (50m 이상)
        if (location.getAccuracy() > 50) {
            Log.w("RunningStartActivity", "위치 정확도가 낮습니다: " + location.getAccuracy() + "m");
            return false;
        }

        // 좌표 유효성 검증
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        
        // 좌표가 0,0인 경우
        if (lat == 0.0 && lng == 0.0) {
            Log.w("RunningStartActivity", "좌표가 (0,0)입니다 - 위치가 유효하지 않습니다");
            return false;
        }
        
        // 한국 범위 밖인 경우 (위도 33-43, 경도 124-132)
        if (lat < 33.0 || lat > 43.0 || lng < 124.0 || lng > 132.0) {
            Log.w("RunningStartActivity", "한국 범위 밖의 좌표입니다: " + formatLocation(lat, lng) + " - 캐시된 위치일 가능성");
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

        // 좌표가 0,0인 경우
        double lat = location.getLatitude();
        double lng = location.getLongitude();
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
        String locationInfo = formatLocation(location.getLatitude(), location.getLongitude());

        // 실시간으로 현재 위치를 따라가도록 카메라 업데이트 (15배율 고정, 마커 없이)
        currentZoomLevel = 15f; // 항상 15배율 유지
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, currentZoomLevel));
        
        Log.d("RunningStartActivity", "지도 위치 업데이트 (15배율): " + locationInfo + " (정확도: " + location.getAccuracy() + "m)");
    }

    private String formatLocation(double latitude, double longitude) {
        String latDirection = latitude >= 0 ? "북위" : "남위";
        String lngDirection = longitude >= 0 ? "동경" : "서경";
        
        return String.format(Locale.getDefault(), "%s %.6f, %s %.6f", 
                latDirection, Math.abs(latitude), 
                lngDirection, Math.abs(longitude));
    }

    private void updateDistance(Location newLocation) {
        // 위치 유효성 검증 (러닝 중에는 더 관대한 기준 사용)
        if (!isValidLocationForRunning(newLocation)) {
            Log.w("RunningStartActivity", "유효하지 않은 위치 - 거리 업데이트 건너뜀");
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

        // 지도 업데이트: 경로 Polyline 그리기 (증분 업데이트)
        if (map != null) {
            // Polyline 증분 업데이트 (전체 재생성 대신)
            if (routePoints.size() > 1) {
                if (routePolyline != null) {
                    // 기존 Polyline에 새 좌표 추가
                    List<LatLng> points = routePolyline.getPoints();
                    points.add(currentLatLng);
                    routePolyline.setPoints(points);
                } else {
                    // 첫 Polyline 생성
                    routePolyline = map.addPolyline(new PolylineOptions()
                            .addAll(routePoints)
                            .width(10f)
                            .color(android.graphics.Color.parseColor("#FF6B35")));
                }
            }
            
            // 시작 지점 마커 (한 번만 생성)
            if (startLocationMarker == null && !routePoints.isEmpty()) {
                LatLng startPoint = routePoints.get(0);
                String startLocationInfo = formatLocation(startPoint.latitude, startPoint.longitude);
                startLocationMarker = map.addMarker(new MarkerOptions()
                        .position(startPoint)
                        .title("시작")
                        .snippet(startLocationInfo));
            }

            // 실시간으로 현재 위치를 따라가도록 카메라 업데이트 (15배율 고정, 마커 없이)
            currentZoomLevel = 15f; // 항상 15배율 유지
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, currentZoomLevel));
        }
    }

    private void startRunning() {
        if (!isRunning) {
            try {
                // 1순위: 실시간 위치를 지도에 15배율로 표시
                Log.d("RunningStartActivity", "일반 러닝 버튼 클릭 - 실시간 위치 표시 시작 (15배율)");
                currentZoomLevel = 15f; // 15배율로 고정
                
                // 가장 먼저 현재 위치를 가져와서 지도에 표시
                if (map != null && checkLocationPermission()) {
                    getCurrentLocationRealtime();
                } else if (!checkLocationPermission()) {
                    requestLocationPermission();
                    return;
                } else {
                    Log.w("RunningStartActivity", "지도가 아직 준비되지 않았습니다. 대기 중...");
                    // 지도가 준비되지 않았으면 위치 업데이트만 시작
                    requestLocationUpdates();
                }
                
                isRunning = true;
                isPaused = false;
                startTime = System.currentTimeMillis();
                totalPausedTime = 0;
                totalDistance = 0.0;
                lastLocation = null;
                routePoints.clear(); // 경로 초기화
                
                // 기존 마커와 Polyline 제거
                if (startLocationMarker != null) {
                    startLocationMarker.remove();
                    startLocationMarker = null;
                }
                if (routePolyline != null) {
                    routePolyline.remove();
                    routePolyline = null;
                }

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
                requestLocationUpdates();

                // 시작 음성 안내
                if (courseId != null && !guidePoints.isEmpty()) {
                    speak("스케치 런을 시작합니다. 안내에 따라 이동해주세요.");
                } else {
                    speak("러닝을 시작합니다.");
                }

                updateButtonsForRunningState();

            } catch (Exception e) {
                Log.e("RunningStartActivity", "운동 시작 중 오류 발생", e);
                GoogleSignInUtils.showToast(this, "운동 시작 중 오류가 발생했습니다.");
                isRunning = false;
            }
        }
    }

    private void pauseRunning() {
        if (isRunning && !isPaused) {
            isPaused = true;
            pausedTime = System.currentTimeMillis();
            stopTimer();
            speak("일시정지");
            updateButtonsForPausedState();
        }
    }

    private void resumeRunning() {
        if (isRunning && isPaused) {
            isPaused = false;
            totalPausedTime += (System.currentTimeMillis() - pausedTime);
            startTimer();
            speak("다시 시작합니다");
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

            // 현재 페이스를 km/h로 저장
            currentPaceKmh = averagePace;

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
    protected void onResume() {
        super.onResume();
        // 화면이 다시 활성화될 때마다 최초 위치 표시
        if (map != null && checkLocationPermission() && !isRunning) {
            Log.d("RunningStartActivity", "onResume - 최초 위치 가져오기");
            getCurrentLocationRealtime();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        stopLocationUpdates();
        
        // 마커와 Polyline 정리
        if (startLocationMarker != null) {
            startLocationMarker.remove();
            startLocationMarker = null;
        }
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
    }
}
