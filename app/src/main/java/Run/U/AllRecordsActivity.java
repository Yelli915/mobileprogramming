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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;
import android.app.DatePickerDialog;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class AllRecordsActivity extends AppCompatActivity {

    private ImageButton backButton;
    private com.google.android.material.button.MaterialButton dateFilterButton;
    private com.google.android.material.button.MaterialButton routeFilterButton;
    private com.google.android.material.button.MaterialButton statisticsButton;
    private RecyclerView recordRecyclerView;
    private TextView totalDistanceText;
    private TextView totalTimeText;
    private TextView titleTextView;
    private TextView emptyMessageText;

    private RunningRecordAdapter recordAdapter;
    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private List<RunningRecord> allRecords = new ArrayList<>();
    private List<RunningRecord> filteredRecords = new ArrayList<>();
    private ListenerRegistration runsListener;
    
    private String currentFilter = "all"; // "all", "date", "difficulty", "statistics"
    private String selectedDifficulty = null; // "easy", "medium", "hard"
    private Date selectedStartDate = null;
    private Date selectedEndDate = null;
    private java.util.Map<String, String> courseIdToDifficulty = new java.util.HashMap<>(); // courseId -> difficulty 매핑
    private java.util.Map<String, String> courseNameCache = new java.util.HashMap<>(); // courseId -> courseName 매핑

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_record);

        initViews();
        initFirestore();
        setupClickListeners();
        // 초기 빈 어댑터 설정 (레이아웃 그리기 전에 어댑터 연결)
        if (recordRecyclerView != null) {
            recordAdapter = new RunningRecordAdapter(new ArrayList<>(), record -> {});
            recordAdapter.setOnItemLongClickListener(record -> {
                // 길게 누르면 삭제/수정 다이얼로그 표시
                showRunRecordOptionsDialog(record);
            });
            recordAdapter.setCourseNameCache(courseNameCache);
            recordRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            recordRecyclerView.setAdapter(recordAdapter);
            recordRecyclerView.setNestedScrollingEnabled(false);
        }
        loadAllRecordsFromFirestore();
        loadCourseDifficulties(null); // 코스 난이도 정보 미리 로드
    }

    private void initFirestore() {
        firestore = GoogleSignInUtils.getFirestore();
        firebaseAuth = GoogleSignInUtils.getAuth();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        titleTextView = findViewById(R.id.tv_title);
        recordRecyclerView = findViewById(R.id.rv_running_records);
        
        // 필터 버튼들 표시 (AllRecordsActivity에서만 사용)
        View filterButtonsContainer = findViewById(R.id.filter_buttons_container);
        if (filterButtonsContainer != null) {
            filterButtonsContainer.setVisibility(View.VISIBLE);
            dateFilterButton = findViewById(R.id.btn_date_filter);
            routeFilterButton = findViewById(R.id.btn_route_filter);
            statisticsButton = findViewById(R.id.btn_statistics);
            
            // 버튼들이 항상 보이도록 설정
            if (dateFilterButton != null) {
                dateFilterButton.setVisibility(View.VISIBLE);
                dateFilterButton.setEnabled(true);
            }
            if (routeFilterButton != null) {
                routeFilterButton.setVisibility(View.VISIBLE);
                routeFilterButton.setEnabled(true);
            }
            if (statisticsButton != null) {
                statisticsButton.setVisibility(View.VISIBLE);
                statisticsButton.setEnabled(true);
            }
            
            // 초기 버튼 상태 설정
            updateFilterButtons();
        }
        
        // 전체 통계 표시 (AllRecordsActivity에서만 사용)
        View totalStatisticsContainer = findViewById(R.id.total_statistics_container);
        if (totalStatisticsContainer != null) {
            totalStatisticsContainer.setVisibility(View.VISIBLE);
            totalDistanceText = findViewById(R.id.tv_total_distance);
            totalTimeText = findViewById(R.id.tv_total_time);
        }
        
        // 제목 변경
        if (titleTextView != null) {
            titleTextView.setText("전체 기록");
        }
        
        // 지도 영역 숨기기 (전체 기록 화면에서는 지도 불필요)
        View mapContainer = findViewById(R.id.map_fragment);
        if (mapContainer != null) {
            View parent = (View) mapContainer.getParent();
            if (parent != null) {
                parent.setVisibility(View.GONE);
            }
        }
        
        // "가장 최근 기록" 통합 카드 숨기기
        View recentRecordCard = findViewById(R.id.recent_record_card);
        if (recentRecordCard != null) {
            recentRecordCard.setVisibility(View.GONE);
        }
        
        // "더 많은 기록 보기" 버튼 숨기기
        View viewMoreButton = findViewById(R.id.btn_view_more);
        if (viewMoreButton != null) {
            viewMoreButton.setVisibility(View.GONE);
        }
        
        // "오늘의 기록 요약" 섹션 숨기기
        // ScrollView > LinearLayout 구조에서 찾기
        View scrollView = findViewById(android.R.id.content);
        if (scrollView != null) {
            hideTodaySummarySection(scrollView);
        }
    }
    
    private void hideTodaySummarySection(View view) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                
                // ScrollView 내부의 LinearLayout 찾기
                if (child instanceof android.widget.ScrollView) {
                    android.widget.ScrollView scrollView = (android.widget.ScrollView) child;
                    if (scrollView.getChildCount() > 0) {
                        View linearLayout = scrollView.getChildAt(0);
                        if (linearLayout instanceof android.widget.LinearLayout) {
                            hideTodaySummaryInLinearLayout((android.widget.LinearLayout) linearLayout);
                        }
                    }
                } else if (child instanceof android.widget.LinearLayout) {
                    hideTodaySummaryInLinearLayout((android.widget.LinearLayout) child);
                } else {
                    hideTodaySummarySection(child);
                }
            }
        }
    }
    
    private void hideTodaySummaryInLinearLayout(android.widget.LinearLayout linearLayout) {
        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            View child = linearLayout.getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                if ("오늘의 기록 요약".equals(textView.getText().toString())) {
                    // TextView 숨기기
                    textView.setVisibility(View.GONE);
                    // 다음 GridLayout도 숨기기
                    if (i + 1 < linearLayout.getChildCount()) {
                        View nextView = linearLayout.getChildAt(i + 1);
                        if (nextView instanceof android.widget.GridLayout) {
                            nextView.setVisibility(View.GONE);
                        }
                    }
                    return;
                }
            }
        }
    }

    private void loadAllRecordsFromFirestore() {
        FirebaseUser currentUser = GoogleSignInUtils.requireCurrentUser(this);
        if (currentUser == null) {
            return;
        }

        String userId = currentUser.getUid();

        // 기존 리스너 제거
        if (runsListener != null) {
            runsListener.remove();
        }

        // 모든 기록 로드 (필터 없음)
        runsListener = firestore.collection("users")
                .document(userId)
                .collection("runs")
                .orderBy("startTime", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("AllRecordsActivity", "기록 리스너 오류", e);
                        return;
                    }

                    if (snapshot != null) {
                        Log.d("AllRecordsActivity", "Firestore 스냅샷 수신 - 문서 수: " + snapshot.size() + ", 변경사항 수: " + snapshot.getDocumentChanges().size());
                        
                        // 초기 로드인지 확인
                        if (snapshot.getDocumentChanges().isEmpty()) {
                            // 초기 로드: 전체 리스트 다시 구성
                            allRecords.clear();
                            for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                                if (document instanceof QueryDocumentSnapshot) {
                                    RunningRecord record = documentToRunningRecord((QueryDocumentSnapshot) document);
                                    if (record != null) {
                                        allRecords.add(record);
                                        Log.d("AllRecordsActivity", "기록 변환 성공 - ID: " + record.getId() + 
                                            ", 거리: " + record.getTotalDistanceKm() + 
                                            ", 시간: " + record.getElapsedTimeMs() + 
                                            ", 날짜: " + record.getDate());
                                    } else {
                                        Log.w("AllRecordsActivity", "기록 변환 실패 - 문서 ID: " + document.getId());
                                    }
                                }
                            }
                            allRecords.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                            
                            Log.d("AllRecordsActivity", "로드된 기록 수: " + allRecords.size());
                            if (!allRecords.isEmpty()) {
                                RunningRecord first = allRecords.get(0);
                                Log.d("AllRecordsActivity", "첫 번째 기록 - 거리: " + first.getTotalDistanceKm() + 
                                    ", 시간: " + first.getElapsedTimeMs() + 
                                    ", 날짜: " + first.getDate() + 
                                    ", 난이도: " + first.getDifficulty() +
                                    ", 페이스: " + first.getAveragePace());
                            }
                            
                            applyCurrentFilter();
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
                                        allRecords.add(record);
                                        break;
                                    case MODIFIED:
                                        for (int i = 0; i < allRecords.size(); i++) {
                                            if (allRecords.get(i).getId().equals(record.getId())) {
                                                allRecords.set(i, record);
                                                break;
                                            }
                                        }
                                        break;
                                    case REMOVED:
                                        allRecords.removeIf(r -> r.getId().equals(record.getId()));
                                        break;
                                }
                            }
                            
                            allRecords.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                            applyCurrentFilter();
                        }
                    }
                });
    }

    private RunningRecord documentToRunningRecord(QueryDocumentSnapshot document) {
        try {
            RunningRecord record = new RunningRecord();
            record.setId(document.getId());
            
            if (document.contains("type")) {
                String type = document.getString("type");
                record.setRunningType("free".equals(type) ? "일반 운동" : ("sketch".equals(type) ? "코스 운동" : type));
            } else if (document.contains("runningType")) {
                record.setRunningType(document.getString("runningType"));
            }
            
            // 거리 설정 (우선순위: totalDistance > totalDistanceKm > distance)
            double totalDistanceKm = 0.0;
            if (document.contains("totalDistance")) {
                Double totalDistanceMeters = document.getDouble("totalDistance");
                if (totalDistanceMeters != null && totalDistanceMeters > 0) {
                    totalDistanceKm = totalDistanceMeters / 1000.0;
                    record.setTotalDistanceKm(totalDistanceKm);
                }
            }
            if (totalDistanceKm == 0.0 && document.contains("totalDistanceKm")) {
                Double distanceKm = document.getDouble("totalDistanceKm");
                if (distanceKm != null && distanceKm > 0) {
                    totalDistanceKm = distanceKm;
                    record.setTotalDistanceKm(totalDistanceKm);
                }
            }
            
            // elapsedTimeMs 설정 (우선순위: totalTime > elapsedTimeMs > startTime/endTime 계산)
            long elapsedTimeMs = 0;
            if (document.contains("totalTime")) {
                Long totalTimeSeconds = document.getLong("totalTime");
                if (totalTimeSeconds != null && totalTimeSeconds > 0) {
                    elapsedTimeMs = totalTimeSeconds * 1000;
                    record.setElapsedTimeMs(elapsedTimeMs);
                }
            }
            if (elapsedTimeMs == 0 && document.contains("elapsedTimeMs")) {
                Long elapsedTime = document.getLong("elapsedTimeMs");
                if (elapsedTime != null && elapsedTime > 0) {
                    elapsedTimeMs = elapsedTime;
                    record.setElapsedTimeMs(elapsedTimeMs);
                }
            }
            
            // startTime과 endTime으로부터 시간 계산 (elapsedTimeMs가 없을 경우)
            if (elapsedTimeMs == 0 && document.contains("startTime") && document.contains("endTime")) {
                Object startTimeObj = document.get("startTime");
                Object endTimeObj = document.get("endTime");
                if (startTimeObj instanceof com.google.firebase.Timestamp && 
                    endTimeObj instanceof com.google.firebase.Timestamp) {
                    long startMs = ((com.google.firebase.Timestamp) startTimeObj).toDate().getTime();
                    long endMs = ((com.google.firebase.Timestamp) endTimeObj).toDate().getTime();
                    if (endMs > startMs) {
                        elapsedTimeMs = endMs - startMs;
                        record.setElapsedTimeMs(elapsedTimeMs);
                    }
                }
            }
            
            // 시간 문자열 설정
            if (elapsedTimeMs > 0) {
                record.setTime(GoogleSignInUtils.formatElapsedTimeShort(elapsedTimeMs));
            } else if (document.contains("time")) {
                String timeStr = document.getString("time");
                if (timeStr != null && !timeStr.trim().isEmpty()) {
                    record.setTime(timeStr);
                }
            }
            
            // averagePace 설정 (우선순위: averagePace 필드 > 계산)
            if (document.contains("averagePace")) {
                Object paceObj = document.get("averagePace");
                if (paceObj instanceof Number) {
                    double paceSeconds = ((Number) paceObj).doubleValue();
                    if (paceSeconds > 0) {
                        record.setAveragePace(GoogleSignInUtils.formatPaceFromSeconds(paceSeconds));
                    }
                } else if (paceObj instanceof String) {
                    String paceStr = (String) paceObj;
                    if (paceStr != null && !paceStr.trim().isEmpty()) {
                        record.setAveragePace(paceStr);
                    }
                }
            }
            
            // averagePace가 없으면 totalTime과 totalDistance로부터 계산
            if (record.getAveragePace() == null && elapsedTimeMs > 0 && totalDistanceKm > 0) {
                double totalTimeSeconds = elapsedTimeMs / 1000.0;
                double paceSeconds = totalTimeSeconds / totalDistanceKm;
                record.setAveragePace(GoogleSignInUtils.formatPaceFromSeconds(paceSeconds));
            }
            
            // 날짜 설정 (우선순위: startTime > date > createdAt)
            if (document.contains("startTime")) {
                Object startTimeObj = document.get("startTime");
                if (startTimeObj instanceof com.google.firebase.Timestamp) {
                    java.util.Date date = ((com.google.firebase.Timestamp) startTimeObj).toDate();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy년 MM월 dd일", java.util.Locale.KOREA);
                    record.setDate(sdf.format(date));
                }
            } else if (document.contains("date")) {
                String dateStr = document.getString("date");
                if (dateStr != null && !dateStr.trim().isEmpty()) {
                    record.setDate(dateStr);
                }
            }
            
            // 날짜가 아직 설정되지 않았으면 createdAt 사용
            if (record.getDate() == null && document.contains("createdAt")) {
                Object createdAt = document.get("createdAt");
                if (createdAt instanceof com.google.firebase.Timestamp) {
                    java.util.Date date = ((com.google.firebase.Timestamp) createdAt).toDate();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy년 MM월 dd일", java.util.Locale.KOREA);
                    record.setDate(sdf.format(date));
                }
            }
            
            // distance 문자열 설정 (포맷팅된 거리)
            if (totalDistanceKm > 0) {
                record.setDistance(GoogleSignInUtils.formatDistanceKm(totalDistanceKm));
            } else if (document.contains("distance")) {
                String distanceStr = document.getString("distance");
                if (distanceStr != null && !distanceStr.trim().isEmpty()) {
                    record.setDistance(distanceStr);
                }
            }
            
            if (document.contains("pathEncoded")) {
                record.setPathEncoded(document.getString("pathEncoded"));
            }
            
            if (document.contains("courseId")) {
                record.setCourseId(document.getString("courseId"));
            }
            
            // createdAt 설정 (우선순위: createdAt > startTime)
            if (document.contains("createdAt")) {
                Object createdAt = document.get("createdAt");
                if (createdAt instanceof com.google.firebase.Timestamp) {
                    record.setCreatedAt(((com.google.firebase.Timestamp) createdAt).toDate().getTime());
                }
            } else if (document.contains("startTime")) {
                Object startTimeObj = document.get("startTime");
                if (startTimeObj instanceof com.google.firebase.Timestamp) {
                    record.setCreatedAt(((com.google.firebase.Timestamp) startTimeObj).toDate().getTime());
                }
            }
            
            if (document.contains("name")) {
                record.setName(document.getString("name"));
            }
            
            if (document.contains("difficulty")) {
                record.setDifficulty(document.getString("difficulty"));
            }
            
            Log.d("AllRecordsActivity", "기록 변환 완료 - ID: " + record.getId() + 
                ", 거리: " + record.getTotalDistanceKm() + 
                ", 시간: " + record.getElapsedTimeMs() + 
                ", 날짜: " + record.getDate() + 
                ", 난이도: " + record.getDifficulty() +
                ", 페이스: " + record.getAveragePace());
            
            return record;
        } catch (Exception e) {
            Log.e("AllRecordsActivity", "기록 변환 실패", e);
            return null;
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        dateFilterButton.setOnClickListener(v -> {
            if (dateFilterButton == null) return;
            // 이미 선택된 버튼이면 토글하여 해제
            if ("date".equals(currentFilter)) {
                currentFilter = "all";
                selectedStartDate = null;
                selectedEndDate = null;
                updateFilterButtons();
                applyCurrentFilter();
            } else {
                currentFilter = "date";
                // 버튼이 사라지지 않도록 보장
                dateFilterButton.setVisibility(View.VISIBLE);
                dateFilterButton.setEnabled(true);
                updateFilterButtons();
                showDateFilterDialog();
            }
        });

        routeFilterButton.setOnClickListener(v -> {
            if (routeFilterButton == null) return;
            // 이미 선택된 버튼이면 토글하여 해제
            if ("difficulty".equals(currentFilter)) {
                currentFilter = "all";
                selectedDifficulty = null;
                updateFilterButtons();
                applyCurrentFilter();
            } else {
                currentFilter = "difficulty";
                // 버튼이 사라지지 않도록 보장
                routeFilterButton.setVisibility(View.VISIBLE);
                routeFilterButton.setEnabled(true);
                updateFilterButtons();
                showDifficultyFilterDialog();
            }
        });

        statisticsButton.setOnClickListener(v -> {
            if (statisticsButton == null) return;
            // 이미 선택된 버튼이면 토글하여 해제
            if ("statistics".equals(currentFilter)) {
                currentFilter = "all";
                updateFilterButtons();
                applyCurrentFilter();
            } else {
                currentFilter = "statistics";
                // 버튼이 사라지지 않도록 보장
                statisticsButton.setVisibility(View.VISIBLE);
                statisticsButton.setEnabled(true);
                updateFilterButtons();
                showStatisticsView();
            }
        });
    }

    private void updateFilterButtons() {
        if (dateFilterButton == null || routeFilterButton == null || statisticsButton == null) {
            return;
        }
        
        boolean isDateSelected = "date".equals(currentFilter);
        boolean isDifficultySelected = "difficulty".equals(currentFilter);
        boolean isStatisticsSelected = "statistics".equals(currentFilter);
        
        // 버튼들이 항상 보이도록 강제 설정
        dateFilterButton.setVisibility(View.VISIBLE);
        routeFilterButton.setVisibility(View.VISIBLE);
        statisticsButton.setVisibility(View.VISIBLE);
        
        // 버튼 활성화 상태 유지
        dateFilterButton.setEnabled(true);
        routeFilterButton.setEnabled(true);
        statisticsButton.setEnabled(true);
        
        // 날짜별 버튼 UI 업데이트
        dateFilterButton.setSelected(isDateSelected);
        if (isDateSelected) {
            // 선택된 상태: 주황색 배경, 흰색 텍스트
            dateFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B35));
            dateFilterButton.setTextColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            dateFilterButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            dateFilterButton.setStrokeWidth(0);
            dateFilterButton.setElevation(4f);
        } else {
            // 선택되지 않은 상태: 투명 배경, 검은색 텍스트, 테두리
            dateFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00FFFFFF));
            dateFilterButton.setTextColor(android.content.res.ColorStateList.valueOf(0xFF000000));
            dateFilterButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFF000000));
            dateFilterButton.setStrokeWidth(2);
            dateFilterButton.setElevation(0f);
        }
        
        // 난이도별 버튼 UI 업데이트
        routeFilterButton.setSelected(isDifficultySelected);
        if (isDifficultySelected) {
            // 선택된 상태: 주황색 배경, 흰색 텍스트
            routeFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B35));
            routeFilterButton.setTextColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            routeFilterButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            routeFilterButton.setStrokeWidth(0);
            routeFilterButton.setElevation(4f);
        } else {
            // 선택되지 않은 상태: 투명 배경, 검은색 텍스트, 테두리
            routeFilterButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00FFFFFF));
            routeFilterButton.setTextColor(android.content.res.ColorStateList.valueOf(0xFF000000));
            routeFilterButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFF000000));
            routeFilterButton.setStrokeWidth(2);
            routeFilterButton.setElevation(0f);
        }
        
        // 통계 버튼 UI 업데이트
        statisticsButton.setSelected(isStatisticsSelected);
        if (isStatisticsSelected) {
            // 선택된 상태: 주황색 배경, 흰색 텍스트
            statisticsButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B35));
            statisticsButton.setTextColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            statisticsButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            statisticsButton.setStrokeWidth(0);
            statisticsButton.setElevation(4f);
        } else {
            // 선택되지 않은 상태: 투명 배경, 검은색 텍스트, 테두리
            statisticsButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00FFFFFF));
            statisticsButton.setTextColor(android.content.res.ColorStateList.valueOf(0xFF000000));
            statisticsButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFF000000));
            statisticsButton.setStrokeWidth(2);
            statisticsButton.setElevation(0f);
        }
    }

    private void applyCurrentFilter() {
        filteredRecords.clear();
        
        Log.d("AllRecordsActivity", "applyCurrentFilter 호출 - 필터: " + currentFilter + ", 전체 기록 수: " + allRecords.size());
        
        switch (currentFilter) {
            case "date":
                // 날짜별 필터 적용
                if (selectedStartDate != null && selectedEndDate != null) {
                    for (RunningRecord record : allRecords) {
                        long recordTime = record.getCreatedAt();
                        if (recordTime >= selectedStartDate.getTime() && recordTime <= selectedEndDate.getTime()) {
                            filteredRecords.add(record);
                        }
                    }
                } else {
                    filteredRecords.addAll(allRecords);
                }
                break;
            case "difficulty":
                // 난이도별 필터 적용
                if (selectedDifficulty != null && !selectedDifficulty.isEmpty()) {
                    // 코스 정보가 아직 로드되지 않았다면 먼저 로드
                    if (courseIdToDifficulty.isEmpty()) {
                        loadCourseDifficulties(() -> applyDifficultyFilter());
                    } else {
                        applyDifficultyFilter();
                    }
                } else {
                    filteredRecords.addAll(allRecords);
                }
                break;
            case "statistics":
                // 통계 모드: 모든 기록 표시
                filteredRecords.addAll(allRecords);
                break;
            default:
                // 전체 표시
                filteredRecords.addAll(allRecords);
                break;
        }
        
        Log.d("AllRecordsActivity", "필터 적용 완료 - 필터된 기록 수: " + filteredRecords.size());
        
        // 필터가 변경되면 어댑터를 다시 생성하여 filteredRecords 반영
        setupRecyclerView();
        updateStatistics();
        
        // 버튼 상태 업데이트 (항상 보이도록 보장)
        updateFilterButtons();
    }

    private void showDateFilterDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_date_filter, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        com.google.android.material.button.MaterialButton btnStartDate = dialogView.findViewById(R.id.btn_start_date);
        com.google.android.material.button.MaterialButton btnEndDate = dialogView.findViewById(R.id.btn_end_date);
        com.google.android.material.button.MaterialButton btnApply = dialogView.findViewById(R.id.btn_apply_date);
        com.google.android.material.button.MaterialButton btnClear = dialogView.findViewById(R.id.btn_clear_date);

        // 시작 날짜 버튼 초기화
        if (selectedStartDate != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA);
            btnStartDate.setText(sdf.format(selectedStartDate));
        }

        btnStartDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (selectedStartDate != null) {
                calendar.setTime(selectedStartDate);
            }
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month, dayOfMonth, 0, 0, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    selectedStartDate = cal.getTime();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA);
                    btnStartDate.setText(sdf.format(selectedStartDate));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // 종료 날짜 버튼 초기화
        if (selectedEndDate != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA);
            btnEndDate.setText(sdf.format(selectedEndDate));
        }

        btnEndDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (selectedEndDate != null) {
                calendar.setTime(selectedEndDate);
            }
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month, dayOfMonth, 23, 59, 59);
                    cal.set(Calendar.MILLISECOND, 999);
                    selectedEndDate = cal.getTime();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA);
                    btnEndDate.setText(sdf.format(selectedEndDate));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);

        btnApply.setOnClickListener(v -> {
            updateFilterButtons();
            applyCurrentFilter();
            dialog.dismiss();
        });

        btnClear.setOnClickListener(v -> {
            selectedStartDate = null;
            selectedEndDate = null;
            btnStartDate.setText("날짜 선택");
            btnEndDate.setText("날짜 선택");
            updateFilterButtons();
            applyCurrentFilter();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDifficultyFilterDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_difficulty_filter, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        com.google.android.material.button.MaterialButton btnAll = dialogView.findViewById(R.id.btn_difficulty_all);
        com.google.android.material.button.MaterialButton btnEasy = dialogView.findViewById(R.id.btn_difficulty_easy);
        com.google.android.material.button.MaterialButton btnMedium = dialogView.findViewById(R.id.btn_difficulty_medium);
        com.google.android.material.button.MaterialButton btnHard = dialogView.findViewById(R.id.btn_difficulty_hard);

        // 현재 선택된 난이도에 따라 버튼 스타일 업데이트
        updateDifficultyButtonStyle(btnAll, selectedDifficulty == null);
        updateDifficultyButtonStyle(btnEasy, "easy".equals(selectedDifficulty));
        updateDifficultyButtonStyle(btnMedium, "medium".equals(selectedDifficulty));
        updateDifficultyButtonStyle(btnHard, "hard".equals(selectedDifficulty));

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);

        android.view.View.OnClickListener difficultyClickListener = v -> {
            com.google.android.material.button.MaterialButton clickedButton = (com.google.android.material.button.MaterialButton) v;
            
            // 모든 버튼 스타일 초기화
            updateDifficultyButtonStyle(btnAll, false);
            updateDifficultyButtonStyle(btnEasy, false);
            updateDifficultyButtonStyle(btnMedium, false);
            updateDifficultyButtonStyle(btnHard, false);
            
            // 클릭된 버튼 스타일 업데이트
            if (clickedButton == btnAll) {
                selectedDifficulty = null;
                updateDifficultyButtonStyle(btnAll, true);
            } else if (clickedButton == btnEasy) {
                selectedDifficulty = "easy";
                updateDifficultyButtonStyle(btnEasy, true);
            } else if (clickedButton == btnMedium) {
                selectedDifficulty = "medium";
                updateDifficultyButtonStyle(btnMedium, true);
            } else if (clickedButton == btnHard) {
                selectedDifficulty = "hard";
                updateDifficultyButtonStyle(btnHard, true);
            }
            
            updateFilterButtons();
            applyCurrentFilter();
            dialog.dismiss();
        };

        btnAll.setOnClickListener(difficultyClickListener);
        btnEasy.setOnClickListener(difficultyClickListener);
        btnMedium.setOnClickListener(difficultyClickListener);
        btnHard.setOnClickListener(difficultyClickListener);

        dialog.show();
    }

    private void updateDifficultyButtonStyle(com.google.android.material.button.MaterialButton button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B35));
            button.setTextColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            button.setIconTint(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            button.setStrokeWidth(0);
        } else {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00FFFFFF));
            button.setTextColor(android.content.res.ColorStateList.valueOf(0xFF000000));
            button.setStrokeWidth(2);
        }
    }
    
    private void loadCourseDifficulties(Runnable onComplete) {
        firestore.collection("courses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    courseIdToDifficulty.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String courseId = doc.getId();
                        String difficulty = doc.getString("difficulty");
                        if (difficulty != null) {
                            courseIdToDifficulty.put(courseId, difficulty);
                        }
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AllRecordsActivity", "코스 난이도 로드 실패", e);
                    GoogleSignInUtils.showToast(this, "코스 정보를 불러오는데 실패했습니다.");
                });
    }
    
    private void applyDifficultyFilter() {
        filteredRecords.clear();
        for (RunningRecord record : allRecords) {
            String recordDifficulty = record.getDifficulty();
            
            // 기록에 난이도가 설정되어 있으면 그것을 사용
            if (recordDifficulty == null || recordDifficulty.isEmpty()) {
                // 기록에 난이도가 없으면 코스의 난이도 사용
                String courseId = record.getCourseId();
                if (courseId != null && courseIdToDifficulty.containsKey(courseId)) {
                    recordDifficulty = courseIdToDifficulty.get(courseId);
                }
            }
            
            // 선택된 난이도와 일치하면 필터에 추가
            if (recordDifficulty != null && selectedDifficulty.equals(recordDifficulty)) {
                filteredRecords.add(record);
            }
        }
        
        // 어댑터가 이미 있으면 데이터만 업데이트
        if (recordAdapter != null) {
            recordAdapter.notifyDataSetChanged();
        } else {
            setupRecyclerView();
        }
        updateStatistics();
    }

    private void showStatisticsView() {
        // 통계 모드: 모든 기록의 통계 표시
        applyCurrentFilter();
        
        double totalDistance = 0.0;
        long totalTimeMs = 0;
        int totalRuns = filteredRecords.size();
        
        for (RunningRecord record : filteredRecords) {
            totalDistance += record.getTotalDistanceKm();
            totalTimeMs += record.getElapsedTimeMs();
        }
        
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_statistics, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        TextView tvTotalRuns = dialogView.findViewById(R.id.tv_total_runs);
        TextView tvTotalDistance = dialogView.findViewById(R.id.tv_total_distance);
        TextView tvTotalTime = dialogView.findViewById(R.id.tv_total_time);
        TextView tvAvgDistance = dialogView.findViewById(R.id.tv_avg_distance);
        TextView tvAvgTime = dialogView.findViewById(R.id.tv_avg_time);
        com.google.android.material.button.MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_statistics);

        // 통계 데이터 설정
        tvTotalRuns.setText(totalRuns + "회");
        tvTotalDistance.setText(GoogleSignInUtils.formatDistanceKm(totalDistance));
        tvTotalTime.setText(GoogleSignInUtils.formatElapsedTimeWithLabel(totalTimeMs));
        tvAvgDistance.setText(totalRuns > 0 ? GoogleSignInUtils.formatDistanceKm(totalDistance / totalRuns) : "0.0km");
        tvAvgTime.setText(totalRuns > 0 ? GoogleSignInUtils.formatElapsedTimeWithLabel(totalTimeMs / totalRuns) : "0분");

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);

        btnConfirm.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void setupRecyclerView() {
        if (recordRecyclerView == null) {
            return;
        }
        
        // 기존 어댑터가 있으면 데이터만 업데이트
        if (recordAdapter != null) {
            recordAdapter.updateRecords(filteredRecords);
            recordAdapter.setOnItemLongClickListener(record -> {
                // 길게 누르면 삭제/수정 다이얼로그 표시
                showRunRecordOptionsDialog(record);
            });
            recordAdapter.setCourseNameCache(courseNameCache);
        } else {
            // 어댑터가 없으면 새로 생성
            recordAdapter = new RunningRecordAdapter(filteredRecords, record -> {
                // 기록 클릭 시 상세 보기 (선택사항)
            });
            recordAdapter.setOnItemLongClickListener(record -> {
                // 길게 누르면 삭제/수정 다이얼로그 표시
                showRunRecordOptionsDialog(record);
            });
            
            // 코스 이름 캐시 설정
            recordAdapter.setCourseNameCache(courseNameCache);
            
            // RecyclerView 설정
            recordRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            recordRecyclerView.setAdapter(recordAdapter);
            recordRecyclerView.setNestedScrollingEnabled(false);
        }
        
        // 코스 이름 로드 (스케치 러닝인 경우)
        loadCourseNamesForRecords();
    }
    
    private void loadCourseNamesForRecords() {
        if (filteredRecords == null || filteredRecords.isEmpty()) {
            return;
        }
        
        // 필요한 코스 ID 수집
        java.util.Set<String> courseIdsToLoad = new java.util.HashSet<>();
        for (RunningRecord record : filteredRecords) {
            String courseId = record.getCourseId();
            if (courseId != null && !courseId.isEmpty() && !courseNameCache.containsKey(courseId)) {
                courseIdsToLoad.add(courseId);
            }
        }
        
        if (courseIdsToLoad.isEmpty()) {
            return;
        }
        
        // Firestore에서 코스 이름 로드
        for (String courseId : courseIdsToLoad) {
            firestore.collection("courses")
                    .document(courseId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            String courseName = documentSnapshot.getString("name");
                            if (courseName != null && !courseName.isEmpty()) {
                                courseNameCache.put(courseId, courseName);
                                // 어댑터 업데이트
                                if (recordAdapter != null) {
                                    recordAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w("AllRecordsActivity", "코스 이름 로드 실패: " + courseId, e);
                    });
        }
    }

    private void updateStatistics() {
        double totalDistance = 0.0;
        long totalTimeMs = 0;

        for (RunningRecord record : filteredRecords) {
            totalDistance += record.getTotalDistanceKm();
            totalTimeMs += record.getElapsedTimeMs();
        }

        if (totalDistanceText != null) {
            totalDistanceText.setText(GoogleSignInUtils.formatDistanceKm(totalDistance));
        }
        if (totalTimeText != null) {
            totalTimeText.setText(GoogleSignInUtils.formatElapsedTimeWithLabel(totalTimeMs));
        }
    }

    private void showRunRecordOptionsDialog(RunningRecord record) {
        FirebaseUser currentUser = GoogleSignInUtils.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            return;
        }

        String userId = currentUser.getUid();
        String documentId = record.getId();

        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_record_detail, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // 뷰 초기화
        android.view.View nameContainer = dialogView.findViewById(R.id.name_container);
        TextView nameText = dialogView.findViewById(R.id.tv_record_detail_name);
        TextView dateText = dialogView.findViewById(R.id.tv_record_detail_date);
        TextView typeText = dialogView.findViewById(R.id.tv_record_detail_type);
        android.view.View difficultyContainer = dialogView.findViewById(R.id.difficulty_container);
        TextView difficultyText = dialogView.findViewById(R.id.tv_record_detail_difficulty);
        android.view.View courseContainer = dialogView.findViewById(R.id.course_container);
        TextView courseText = dialogView.findViewById(R.id.tv_record_detail_course);
        TextView distanceText = dialogView.findViewById(R.id.tv_record_detail_distance);
        TextView timeText = dialogView.findViewById(R.id.tv_record_detail_time);
        TextView paceText = dialogView.findViewById(R.id.tv_record_detail_pace);
        com.google.android.material.button.MaterialButton editButton = dialogView.findViewById(R.id.btn_edit_record);
        com.google.android.material.button.MaterialButton deleteButton = dialogView.findViewById(R.id.btn_delete_record);
        android.widget.ImageButton cancelButton = dialogView.findViewById(R.id.btn_cancel_record);

        // 기록 이름 설정
        String name = record.getName();
        if (name != null && !name.trim().isEmpty()) {
            nameText.setText(name);
            nameContainer.setVisibility(android.view.View.VISIBLE);
        } else {
            nameContainer.setVisibility(android.view.View.GONE);
        }

        // 날짜 설정
        if (dateText != null) {
            dateText.setText(record.getDate());
        }

        // 러닝 타입 설정
        if (typeText != null) {
            String runningType = record.getRunningType();
            typeText.setText(runningType != null ? runningType : "일반 운동");
        }

        // 난이도 설정
        String difficulty = record.getDifficulty();
        if (difficulty != null && !difficulty.isEmpty()) {
            String difficultyDisplay = record.getDifficultyDisplayName();
            if (difficultyText != null) {
                difficultyText.setText(difficultyDisplay);
            }
            if (difficultyContainer != null) {
                difficultyContainer.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            if (difficultyContainer != null) {
                difficultyContainer.setVisibility(android.view.View.GONE);
            }
        }

        // 코스 이름 설정
        String courseId = record.getCourseId();
        if (courseId != null && !courseId.isEmpty()) {
            String courseName = courseNameCache != null ? courseNameCache.get(courseId) : null;
            if (courseName != null && !courseName.isEmpty()) {
                courseText.setText("📍 " + courseName);
                courseContainer.setVisibility(android.view.View.VISIBLE);
            } else {
                // 코스 이름이 캐시에 없으면 로드 시도
                courseContainer.setVisibility(android.view.View.GONE);
                firestore.collection("courses")
                        .document(courseId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot != null && documentSnapshot.exists()) {
                                String loadedCourseName = documentSnapshot.getString("name");
                                if (loadedCourseName != null && !loadedCourseName.isEmpty()) {
                                    courseText.setText("📍 " + loadedCourseName);
                                    courseContainer.setVisibility(android.view.View.VISIBLE);
                                    // 캐시에 저장
                                    if (courseNameCache != null) {
                                        courseNameCache.put(courseId, loadedCourseName);
                                    }
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.w("AllRecordsActivity", "코스 이름 로드 실패: " + courseId, e);
                        });
            }
        } else {
            courseContainer.setVisibility(android.view.View.GONE);
        }

        // 거리 설정
        if (distanceText != null) {
            distanceText.setText(record.getDistanceFormatted());
        }

        // 시간 설정
        if (timeText != null) {
            String timeStr = record.getTimeFormatted();
            if (timeStr != null && timeStr.startsWith("시간: ")) {
                timeStr = timeStr.substring(4);
            }
            timeText.setText(timeStr);
        }

        // 페이스 설정
        if (paceText != null) {
            String paceStr = record.getPaceFormatted();
            if (paceStr != null && paceStr.startsWith("평균 페이스: ")) {
                paceStr = paceStr.substring(7);
            }
            paceText.setText(paceStr);
        }

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);

        // 수정 버튼 클릭
        editButton.setOnClickListener(v -> {
            dialog.dismiss();
            showEditRunRecordDialog(documentId, userId, record);
        });

        // 삭제 버튼 클릭
        deleteButton.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteConfirmDialog(documentId, userId);
        });

        // 취소 버튼 클릭
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showEditRunRecordDialog(String documentId, String userId, RunningRecord record) {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_edit_record, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // 뷰 초기화
        com.google.android.material.textfield.TextInputEditText nameEdit = dialogView.findViewById(R.id.et_record_name);
        com.google.android.material.textfield.TextInputEditText distanceEdit = dialogView.findViewById(R.id.et_record_distance);
        com.google.android.material.textfield.TextInputEditText timeEdit = dialogView.findViewById(R.id.et_record_time);
        com.google.android.material.button.MaterialButton easyButton = dialogView.findViewById(R.id.btn_difficulty_easy);
        com.google.android.material.button.MaterialButton mediumButton = dialogView.findViewById(R.id.btn_difficulty_medium);
        com.google.android.material.button.MaterialButton hardButton = dialogView.findViewById(R.id.btn_difficulty_hard);
        android.widget.ImageButton cancelButton = dialogView.findViewById(R.id.btn_cancel_edit);
        com.google.android.material.button.MaterialButton cancelBottomButton = dialogView.findViewById(R.id.btn_cancel_edit_bottom);
        com.google.android.material.button.MaterialButton saveButton = dialogView.findViewById(R.id.btn_save_record);

        // 난이도 선택 변수
        final String[] selectedDifficulty = {record.getDifficulty()};

        // 기존 값 설정
        if (nameEdit != null) {
            nameEdit.setText(record.getName() != null ? record.getName() : "");
        }
        if (distanceEdit != null) {
            distanceEdit.setText(String.format("%.2f", record.getTotalDistanceKm()));
        }
        if (timeEdit != null) {
            long totalSeconds = record.getElapsedTimeMs() / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            timeEdit.setText(String.format("%d:%02d", minutes, seconds));
        }

        // 난이도 버튼 초기 상태 설정
        if (easyButton != null && mediumButton != null && hardButton != null) {
            updateDifficultyButtonStyle(easyButton, "easy".equals(selectedDifficulty[0]));
            updateDifficultyButtonStyle(mediumButton, "medium".equals(selectedDifficulty[0]));
            updateDifficultyButtonStyle(hardButton, "hard".equals(selectedDifficulty[0]));
        }

        // 난이도 버튼 클릭 리스너
        if (easyButton != null) {
            easyButton.setOnClickListener(v -> {
                selectedDifficulty[0] = "easy";
                updateDifficultyButtonStyle(easyButton, true);
                updateDifficultyButtonStyle(mediumButton, false);
                updateDifficultyButtonStyle(hardButton, false);
            });
        }
        if (mediumButton != null) {
            mediumButton.setOnClickListener(v -> {
                selectedDifficulty[0] = "medium";
                updateDifficultyButtonStyle(easyButton, false);
                updateDifficultyButtonStyle(mediumButton, true);
                updateDifficultyButtonStyle(hardButton, false);
            });
        }
        if (hardButton != null) {
            hardButton.setOnClickListener(v -> {
                selectedDifficulty[0] = "hard";
                updateDifficultyButtonStyle(easyButton, false);
                updateDifficultyButtonStyle(mediumButton, false);
                updateDifficultyButtonStyle(hardButton, true);
            });
        }

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);

        // 취소 버튼 (상단)
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        // 취소 버튼 (하단)
        if (cancelBottomButton != null) {
            cancelBottomButton.setOnClickListener(v -> dialog.dismiss());
        }

        // 저장 버튼
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                try {
                    String nameStr = nameEdit != null ? nameEdit.getText().toString().trim() : "";
                    
                    String distanceStr = distanceEdit != null ? distanceEdit.getText().toString().trim() : "";
                    double distanceKm = Double.parseDouble(distanceStr);
                    double distanceMeters = distanceKm * 1000.0;

                    String timeStr = timeEdit != null ? timeEdit.getText().toString().trim() : "";
                    String[] timeParts = timeStr.split(":");
                    long totalSecondsNew = 0;
                    if (timeParts.length == 2) {
                        long minutesNew = Long.parseLong(timeParts[0]);
                        long secondsNew = Long.parseLong(timeParts[1]);
                        totalSecondsNew = minutesNew * 60 + secondsNew;
                    } else {
                        totalSecondsNew = Long.parseLong(timeStr) * 60;
                    }

                    double averagePaceSeconds = 0;
                    if (distanceKm > 0) {
                        averagePaceSeconds = totalSecondsNew / distanceKm;
                    }

                    java.util.Map<String, Object> updates = new java.util.HashMap<>();
                    if (!nameStr.isEmpty()) {
                        updates.put("name", nameStr);
                    } else {
                        updates.put("name", null);
                    }
                    if (selectedDifficulty[0] != null && !selectedDifficulty[0].isEmpty()) {
                        updates.put("difficulty", selectedDifficulty[0]);
                    } else {
                        updates.put("difficulty", null);
                    }
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
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> {
                                GoogleSignInUtils.showToast(this, "기록 수정에 실패했습니다: " + e.getMessage());
                            });
                } catch (NumberFormatException e) {
                    GoogleSignInUtils.showToast(this, "올바른 형식으로 입력해주세요.");
                }
            });
        }

        dialog.show();
    }

    private void showDeleteConfirmDialog(String documentId, String userId) {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_delete_confirm, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // 뷰 초기화
        android.widget.ImageButton cancelButton = dialogView.findViewById(R.id.btn_cancel_delete);
        com.google.android.material.button.MaterialButton cancelBottomButton = dialogView.findViewById(R.id.btn_cancel_delete_bottom);
        com.google.android.material.button.MaterialButton confirmButton = dialogView.findViewById(R.id.btn_confirm_delete);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);

        // 취소 버튼 (상단)
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        // 취소 버튼 (하단)
        if (cancelBottomButton != null) {
            cancelBottomButton.setOnClickListener(v -> dialog.dismiss());
        }

        // 삭제 확인 버튼
        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> {
                firestore.collection("users")
                        .document(userId)
                        .collection("runs")
                        .document(documentId)
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            GoogleSignInUtils.showToast(this, "기록이 삭제되었습니다.");
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> {
                            GoogleSignInUtils.showToast(this, "기록 삭제에 실패했습니다: " + e.getMessage());
                        });
            });
        }

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (runsListener != null) {
            runsListener.remove();
            runsListener = null;
        }
    }
}

