package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RunningRecordActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ImageButton backButton;
    private RecyclerView recordRecyclerView;
    private Button viewMoreButton;
    private TextView titleTextView;
    private TextView tvRecentRecordYear;
    private TextView tvRecentRecordMonthDay;
    private TextView tvRecentRecordWeekday;
    private androidx.cardview.widget.CardView recentRecordDateCard;
    private androidx.cardview.widget.CardView recentRecordInfoCard;
    private TextView tvRecentRecordDistance;
    private TextView tvRecentRecordTime;
    private TextView tvRecentRecordPace;
    private TextView tvRecentRecordType;
    private TextView tvRecentRecordCourseName;
    private java.util.Map<String, String> courseNameCache = new java.util.HashMap<>();

    private RunningRecordAdapter recordAdapter;
    private GoogleMap googleMap;
    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private List<RunningRecord> records = new ArrayList<>();
    private ListenerRegistration runsListener;
    private RunningRecord selectedRecord = null; // 현재 선택된 기록 추적
    private boolean showTodayOnly = false; // 오늘의 기록만 표시할지 여부

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_record);

        initViews();
        initFirestore();
        setupMap();
        setupClickListeners();
        
        // Intent에서 설정 확인 (더 이상 사용하지 않음 - 모든 기록 표시)
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("show_today_only")) {
            showTodayOnly = intent.getBooleanExtra("show_today_only", false);
            if (showTodayOnly && titleTextView != null) {
                // 제목을 "오늘의 기록 요약"으로 변경
                titleTextView.setText("오늘의 기록 요약");
            }
        } else {
            // show_today_only가 없으면 모든 기록 표시 (기본값)
            showTodayOnly = false;
        }
        
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

        // 기존 리스너 제거
        if (runsListener != null) {
            runsListener.remove();
        }

        // 오늘의 기록만 필터링할지 결정
        com.google.firebase.firestore.Query query = firestore.collection("users")
                .document(userId)
                .collection("runs")
                .orderBy("startTime", Query.Direction.DESCENDING);
        
        // 오늘의 기록만 표시하는 경우 필터 추가
        if (showTodayOnly) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date todayStart = calendar.getTime();
            com.google.firebase.Timestamp todayStartTimestamp = new com.google.firebase.Timestamp(todayStart);
            
            query = query.whereGreaterThanOrEqualTo("startTime", todayStartTimestamp);
        }

        // 실시간 리스너 등록
        runsListener = query.addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("RunningRecordActivity", "기록 리스너 오류", e);
                        return;
                    }

                    if (snapshot != null) {
                        // 초기 로드인지 확인 (getDocumentChanges가 비어있으면 초기 로드)
                        if (snapshot.getDocumentChanges().isEmpty()) {
                            // 초기 로드: 전체 리스트 다시 구성
                            records.clear();
                            for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                                if (document instanceof QueryDocumentSnapshot) {
                                    RunningRecord record = documentToRunningRecord((QueryDocumentSnapshot) document);
                                    if (record != null) {
                                        records.add(record);
                                    }
                                }
                            }
                            records.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                            
                            // 빈 상태 처리
                            if (records.isEmpty()) {
                                selectedRecord = null;
                                if (googleMap != null) {
                                    googleMap.clear();
                                }
                                // 카드 숨기기
                                if (recentRecordDateCard != null) {
                                    recentRecordDateCard.setVisibility(View.GONE);
                                }
                            } else {
                                // 첫 번째 기록 선택 (가장 최근 기록)
                                RunningRecord mostRecentRecord = records.get(0);
                                if (googleMap != null) {
                                    selectedRecord = mostRecentRecord;
                                    updateMapForRecord(selectedRecord);
                                }
                                // 가장 최근 기록의 날짜 정보 업데이트
                                updateRecentRecordDateCard(mostRecentRecord);
                            }
                            
                            if (recordAdapter != null) {
                                recordAdapter.notifyDataSetChanged();
                            }
                        } else {
                            // 변경사항 처리
                            for (DocumentChange dc : snapshot.getDocumentChanges()) {
                            QueryDocumentSnapshot document = dc.getDocument();
                            RunningRecord record = documentToRunningRecord(document);
                            
                            if (record == null) {
                                continue;
                            }

                            switch (dc.getType()) {
                                case ADDED:
                                    // 기록 추가
                                    int insertPosition = findInsertPosition(record);
                                    records.add(insertPosition, record);
                                    Log.d("RunningRecordActivity", "기록 추가됨: " + record.getId());
                                    
                                    // 새로 추가된 기록이 가장 최근 기록인 경우 (맨 앞에 추가된 경우)
                                    if (insertPosition == 0) {
                                        selectedRecord = record;
                                        if (googleMap != null) {
                                            updateMapForRecord(record);
                                        }
                                        // 가장 최근 기록의 UI 업데이트
                                        updateRecentRecordDateCard(record);
                                    }
                                    
                                    if (recordAdapter != null) {
                                        recordAdapter.notifyItemInserted(insertPosition);
                                    }
                                    break;
                                case MODIFIED:
                                    // 기록 수정
                                    int modifyIndex = -1;
                                    for (int i = 0; i < records.size(); i++) {
                                        if (records.get(i).getId().equals(record.getId())) {
                                            modifyIndex = i;
                                            records.set(i, record);
                                            Log.d("RunningRecordActivity", "기록 수정됨: " + record.getId());
                                            
                                            // 선택된 기록이 수정된 경우 지도 업데이트
                                            if (selectedRecord != null && selectedRecord.getId().equals(record.getId())) {
                                                selectedRecord = record;
                                                updateMapForRecord(record);
                                                // 가장 최근 기록인 경우 날짜 카드도 업데이트
                                                if (records.size() > 0 && records.get(0).getId().equals(record.getId())) {
                                                    updateRecentRecordDateCard(record);
                                                }
                                            }
                                            break;
                                        }
                                    }
                                    
                                    // 정렬이 필요할 수 있으므로 재정렬
                                    records.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                                    
                                    // 새로운 위치 찾기
                                    int newPosition = -1;
                                    for (int i = 0; i < records.size(); i++) {
                                        if (records.get(i).getId().equals(record.getId())) {
                                            newPosition = i;
                                            break;
                                        }
                                    }
                                    
                                    // 수정된 기록이 가장 최근 기록이 된 경우 UI 업데이트
                                    if (newPosition == 0) {
                                        selectedRecord = record;
                                        if (googleMap != null) {
                                            updateMapForRecord(record);
                                        }
                                        // 가장 최근 기록의 UI 업데이트
                                        updateRecentRecordDateCard(record);
                                    }
                                    
                                    if (recordAdapter != null && modifyIndex >= 0) {
                                        if (modifyIndex != newPosition) {
                                            // 위치가 변경된 경우
                                            recordAdapter.notifyItemMoved(modifyIndex, newPosition);
                                        }
                                        recordAdapter.notifyItemChanged(newPosition);
                                    }
                                    break;
                                case REMOVED:
                                    // 기록 삭제
                                    int removeIndex = -1;
                                    for (int i = 0; i < records.size(); i++) {
                                        if (records.get(i).getId().equals(record.getId())) {
                                            removeIndex = i;
                                            break;
                                        }
                                    }
                                    
                                    if (removeIndex >= 0) {
                                        records.remove(removeIndex);
                                        Log.d("RunningRecordActivity", "기록 삭제됨: " + record.getId());
                                        
                                        // 삭제된 기록이 가장 최근 기록이었던 경우 (removeIndex == 0)
                                        if (removeIndex == 0) {
                                            // 다음 기록으로 업데이트
                                            if (!records.isEmpty()) {
                                                RunningRecord newMostRecent = records.get(0);
                                                selectedRecord = newMostRecent;
                                                if (googleMap != null) {
                                                    updateMapForRecord(newMostRecent);
                                                }
                                                // 가장 최근 기록의 UI 업데이트
                                                updateRecentRecordDateCard(newMostRecent);
                                            } else {
                                                // 기록이 모두 삭제된 경우
                                                selectedRecord = null;
                                                if (googleMap != null) {
                                                    googleMap.clear();
                                                }
                                                // 카드 숨기기
                                                if (recentRecordDateCard != null) {
                                                    recentRecordDateCard.setVisibility(View.GONE);
                                                }
                                            }
                                        } else if (selectedRecord != null && selectedRecord.getId().equals(record.getId())) {
                                            // 선택된 기록이 삭제된 경우 (가장 최근 기록이 아닌 경우)
                                            selectedRecord = null;
                                            if (googleMap != null) {
                                                googleMap.clear();
                                            }
                                            // 첫 번째 기록 선택
                                            if (!records.isEmpty()) {
                                                selectedRecord = records.get(0);
                                                updateMapForRecord(selectedRecord);
                                                updateRecentRecordDateCard(selectedRecord);
                                            }
                                        }
                                        
                                        if (recordAdapter != null) {
                                            recordAdapter.notifyItemRemoved(removeIndex);
                                        }
                                    }
                                    break;
                            }
                        }

                        }
                        
                        // 빈 상태 처리
                        if (records.isEmpty()) {
                            selectedRecord = null;
                            if (googleMap != null) {
                                googleMap.clear();
                            }
                                                // 카드 숨기기
                                                if (recentRecordDateCard != null) {
                                                    recentRecordDateCard.setVisibility(View.GONE);
                                                }
                            if (recordAdapter != null) {
                                recordAdapter.notifyDataSetChanged();
                            }
                        } else {
                            // 선택된 기록이 없으면 첫 번째 기록 선택
                            if (selectedRecord == null && googleMap != null) {
                                selectedRecord = records.get(0);
                                updateMapForRecord(selectedRecord);
                                // 가장 최근 기록의 UI 업데이트
                                updateRecentRecordDateCard(selectedRecord);
                            } else if (selectedRecord != null && records.size() > 0) {
                                // 가장 최근 기록이 변경되었는지 확인
                                RunningRecord currentMostRecent = records.get(0);
                                if (!currentMostRecent.getId().equals(selectedRecord.getId())) {
                                    // 가장 최근 기록이 변경된 경우
                                    selectedRecord = currentMostRecent;
                                    if (googleMap != null) {
                                        updateMapForRecord(selectedRecord);
                                    }
                                    // 가장 최근 기록의 UI 업데이트
                                    updateRecentRecordDateCard(selectedRecord);
                                }
                            }
                        }
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
        titleTextView = findViewById(R.id.tv_title);
        recordRecyclerView = findViewById(R.id.rv_running_records);
        viewMoreButton = findViewById(R.id.btn_view_more);
        tvRecentRecordYear = findViewById(R.id.tv_recent_record_year);
        tvRecentRecordMonthDay = findViewById(R.id.tv_recent_record_month_day);
        tvRecentRecordWeekday = findViewById(R.id.tv_recent_record_weekday);
        recentRecordDateCard = findViewById(R.id.recent_record_card);
        recentRecordInfoCard = findViewById(R.id.recent_record_card);
        tvRecentRecordDistance = findViewById(R.id.tv_recent_record_distance);
        tvRecentRecordTime = findViewById(R.id.tv_recent_record_time);
        tvRecentRecordPace = findViewById(R.id.tv_recent_record_pace);
        tvRecentRecordType = findViewById(R.id.tv_recent_record_type);
        tvRecentRecordCourseName = findViewById(R.id.tv_recent_record_course_name);
    }

    private void setupRecyclerView() {
        if (recordAdapter == null) {
            recordAdapter = new RunningRecordAdapter(records, record -> {
                // 선택된 기록의 지도 업데이트
                selectedRecord = record;
                updateMapForRecord(record);
                // 가장 최근 기록인 경우 날짜 카드 업데이트
                if (records.size() > 0 && records.get(0).getId().equals(record.getId())) {
                    updateRecentRecordDateCard(record);
                }
            });
            recordAdapter.setOnItemLongClickListener(record -> {
                // 길게 누르면 삭제/수정 다이얼로그 표시
                showRunRecordOptionsDialog(record);
            });
            GoogleSignInUtils.setupRecyclerView(recordRecyclerView, recordAdapter, this);
        } else {
            // 어댑터가 이미 있으면 데이터만 업데이트
            recordAdapter.notifyDataSetChanged();
        }
    }

    private int findInsertPosition(RunningRecord newRecord) {
        // startTime 기준으로 내림차순 정렬된 위치 찾기
        for (int i = 0; i < records.size(); i++) {
            if (newRecord.getCreatedAt() > records.get(i).getCreatedAt()) {
                return i;
            }
        }
        return records.size();
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

        // 지도 UI 설정 - 드래그 및 줌 활성화
        map.getUiSettings().setScrollGesturesEnabled(true);
        map.getUiSettings().setZoomGesturesEnabled(true);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setRotateGesturesEnabled(true);
        map.getUiSettings().setTiltGesturesEnabled(true);

        // 기본 위치 설정 (서울)
        LatLng seoul = new LatLng(37.5665, 126.9780);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(seoul, 15f));

        // 첫 번째 기록의 경로 표시
        if (!records.isEmpty()) {
            RunningRecord firstRecord = records.get(0);
            updateMapForRecord(firstRecord);
            updateRecentRecordDateCard(firstRecord);
        }
    }

    private void updateRecentRecordDateCard(RunningRecord record) {
        if (record == null || tvRecentRecordYear == null || tvRecentRecordMonthDay == null || tvRecentRecordWeekday == null) {
            return;
        }
        
        // 기록이 없으면 카드 숨기기
        if (recentRecordDateCard != null && record.getCreatedAt() == 0) {
            recentRecordDateCard.setVisibility(View.GONE);
            return;
        }
        
        // 카드 표시
        if (recentRecordDateCard != null) {
            recentRecordDateCard.setVisibility(View.VISIBLE);
        }
        
        // 날짜 정보 파싱
        long timestamp = record.getCreatedAt();
        if (timestamp > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);
            
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1; // Calendar.MONTH는 0부터 시작
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            
            // 요일 문자열
            String[] weekdays = {"", "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};
            String weekdayStr = weekdays[dayOfWeek];
            
            // TextView 업데이트
            tvRecentRecordYear.setText(year + "년");
            tvRecentRecordMonthDay.setText(" " + month + "월 " + day + "일");
            tvRecentRecordWeekday.setText(" (" + weekdayStr + ")");
        }
        
        // 기록 상세 정보 업데이트
        updateRecentRecordInfo(record);
    }
    
    private void updateRecentRecordInfo(RunningRecord record) {
        if (record == null) {
            return;
        }
        
        // 거리
        if (tvRecentRecordDistance != null) {
            tvRecentRecordDistance.setText(record.getDistanceFormatted());
        }
        
        // 시간
        if (tvRecentRecordTime != null) {
            String timeStr = record.getTimeFormatted();
            // "시간: XX:XX" 형식에서 "시간: " 제거
            if (timeStr != null && timeStr.startsWith("시간: ")) {
                timeStr = timeStr.substring(4);
            }
            tvRecentRecordTime.setText(timeStr);
        }
        
        // 페이스
        if (tvRecentRecordPace != null) {
            String paceStr = record.getPaceFormatted();
            // "평균 페이스: X:XX/km" 형식에서 "평균 페이스: " 제거
            if (paceStr != null && paceStr.startsWith("평균 페이스: ")) {
                paceStr = paceStr.substring(7);
            }
            tvRecentRecordPace.setText(paceStr);
        }
        
        // 타입
        if (tvRecentRecordType != null) {
            tvRecentRecordType.setText(record.getRunningType() != null ? record.getRunningType() : "일반 운동");
        }
        
        // 코스 이름 (스케치 러닝인 경우)
        if (tvRecentRecordCourseName != null) {
            String courseId = record.getCourseId();
            if (courseId != null && !courseId.isEmpty()) {
                loadCourseNameAndUpdateView(courseId);
            } else {
                tvRecentRecordCourseName.setVisibility(View.GONE);
            }
        }
    }
    
    private void loadCourseNameAndUpdateView(String courseId) {
        // 캐시에서 먼저 확인
        if (courseNameCache.containsKey(courseId)) {
            String courseName = courseNameCache.get(courseId);
            if (tvRecentRecordCourseName != null) {
                tvRecentRecordCourseName.setText("📍 " + courseName);
                tvRecentRecordCourseName.setVisibility(View.VISIBLE);
            }
            return;
        }

        // Firestore에서 코스 이름 가져오기
        firestore.collection("courses")
                .document(courseId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String courseName = documentSnapshot.getString("name");
                        if (courseName != null && !courseName.isEmpty()) {
                            // 캐시에 저장
                            courseNameCache.put(courseId, courseName);
                            // UI 업데이트
                            if (tvRecentRecordCourseName != null) {
                                tvRecentRecordCourseName.setText("📍 " + courseName);
                                tvRecentRecordCourseName.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("RunningRecordActivity", "코스 이름 로드 실패: " + courseId, e);
                });
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
            LatLng startPoint = routePoints.get(0);
            String startLocationInfo = formatLocation(startPoint.latitude, startPoint.longitude);
            googleMap.addMarker(new MarkerOptions()
                    .position(startPoint)
                    .title("시작")
                    .snippet(startLocationInfo));

            // 종료 지점 마커
            if (routePoints.size() > 1) {
                LatLng endPoint = routePoints.get(routePoints.size() - 1);
                String endLocationInfo = formatLocation(endPoint.latitude, endPoint.longitude);
                googleMap.addMarker(new MarkerOptions()
                        .position(endPoint)
                        .title("종료")
                        .snippet(endLocationInfo));
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

        viewMoreButton.setOnClickListener(v -> {
            // 모든 기록 보기 Activity로 이동
            Intent intent = new Intent(RunningRecordActivity.this, AllRecordsActivity.class);
            startActivity(intent);
        });
    }


    private String formatLocation(double latitude, double longitude) {
        String latDirection = latitude >= 0 ? "북위" : "남위";
        String lngDirection = longitude >= 0 ? "동경" : "서경";
        
        return String.format(Locale.getDefault(), "%s %.6f, %s %.6f", 
                latDirection, Math.abs(latitude), 
                lngDirection, Math.abs(longitude));
    }

    private void showRunRecordOptionsDialog(RunningRecord record) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            return;
        }

        String userId = currentUser.getUid();
        String documentId = record.getId();

        new AlertDialog.Builder(this)
                .setTitle("기록 관리")
                .setMessage(String.format("거리: %s\n시간: %s\n페이스: %s", 
                        record.getDistanceFormatted(), 
                        record.getTimeFormatted(), 
                        record.getPaceFormatted()))
                .setItems(new String[]{"수정", "삭제"}, (dialog, which) -> {
                    if (which == 0) {
                        // 수정
                        showEditRunRecordDialog(documentId, userId, record);
                    } else if (which == 1) {
                        // 삭제
                        showDeleteConfirmDialog(documentId, userId);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showEditRunRecordDialog(String documentId, String userId, RunningRecord record) {
        // 간단한 수정 다이얼로그 (거리와 시간만 수정 가능)
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("기록 수정");

        // 커스텀 레이아웃 생성
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // 거리 입력
        android.widget.TextView distanceLabel = new android.widget.TextView(this);
        distanceLabel.setText("거리 (km):");
        distanceLabel.setTextSize(14);
        layout.addView(distanceLabel);

        android.widget.EditText distanceEdit = new android.widget.EditText(this);
        distanceEdit.setText(String.format("%.2f", record.getTotalDistanceKm()));
        distanceEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(distanceEdit);

        // 시간 입력 (분:초)
        android.widget.TextView timeLabel = new android.widget.TextView(this);
        timeLabel.setText("시간 (분:초):");
        timeLabel.setTextSize(14);
        timeLabel.setPadding(0, 20, 0, 0);
        layout.addView(timeLabel);

        android.widget.EditText timeEdit = new android.widget.EditText(this);
        long totalSeconds = record.getElapsedTimeMs() / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        timeEdit.setText(String.format("%d:%02d", minutes, seconds));
        layout.addView(timeEdit);

        builder.setView(layout);

        builder.setPositiveButton("저장", (dialog, which) -> {
            try {
                // 거리 파싱
                String distanceStr = distanceEdit.getText().toString().trim();
                double distanceKm = Double.parseDouble(distanceStr);
                double distanceMeters = distanceKm * 1000.0;

                // 시간 파싱 (분:초 형식)
                String timeStr = timeEdit.getText().toString().trim();
                String[] timeParts = timeStr.split(":");
                long totalSecondsNew = 0;
                if (timeParts.length == 2) {
                    long minutesNew = Long.parseLong(timeParts[0]);
                    long secondsNew = Long.parseLong(timeParts[1]);
                    totalSecondsNew = minutesNew * 60 + secondsNew;
                } else {
                    // 분만 입력한 경우
                    totalSecondsNew = Long.parseLong(timeStr) * 60;
                }

                // 평균 페이스 계산
                double averagePaceSeconds = 0;
                if (distanceKm > 0) {
                    averagePaceSeconds = totalSecondsNew / distanceKm;
                }

                // Firestore 업데이트
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("totalDistance", distanceMeters);
                updates.put("totalTime", totalSecondsNew);
                updates.put("averagePace", averagePaceSeconds);

                firestore.collection("users")
                        .document(userId)
                        .collection("runs")
                        .document(documentId)
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            GoogleSignInUtils.showToast(this, "기록이 수정되었습니다.");
                            Log.d("RunningRecordActivity", "기록 수정 성공: " + documentId);
                            // 실시간 리스너가 자동으로 UI 업데이트
                        })
                        .addOnFailureListener(e -> {
                            GoogleSignInUtils.showToast(this, "기록 수정에 실패했습니다: " + e.getMessage());
                            Log.e("RunningRecordActivity", "기록 수정 실패", e);
                        });
            } catch (NumberFormatException e) {
                GoogleSignInUtils.showToast(this, "올바른 형식으로 입력해주세요.");
            }
        });

        builder.setNegativeButton("취소", null);
        builder.show();
    }

    private void showDeleteConfirmDialog(String documentId, String userId) {
        new AlertDialog.Builder(this)
                .setTitle("기록 삭제")
                .setMessage("이 기록을 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    firestore.collection("users")
                            .document(userId)
                            .collection("runs")
                            .document(documentId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                GoogleSignInUtils.showToast(this, "기록이 삭제되었습니다.");
                                Log.d("RunningRecordActivity", "기록 삭제 성공: " + documentId);
                                // 실시간 리스너가 자동으로 UI 업데이트
                            })
                            .addOnFailureListener(e -> {
                                GoogleSignInUtils.showToast(this, "기록 삭제에 실패했습니다: " + e.getMessage());
                                Log.e("RunningRecordActivity", "기록 삭제 실패", e);
                            });
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 리스너 제거
        if (runsListener != null) {
            runsListener.remove();
            runsListener = null;
        }
    }
}


