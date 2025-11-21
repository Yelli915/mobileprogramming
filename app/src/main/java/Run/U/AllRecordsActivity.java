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
                        // 초기 로드인지 확인
                        if (snapshot.getDocumentChanges().isEmpty()) {
                            // 초기 로드: 전체 리스트 다시 구성
                            allRecords.clear();
                            for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                                if (document instanceof QueryDocumentSnapshot) {
                                    RunningRecord record = documentToRunningRecord((QueryDocumentSnapshot) document);
                                    if (record != null) {
                                        allRecords.add(record);
                                    }
                                }
                            }
                            allRecords.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                            
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
            
            if (document.contains("totalDistance")) {
                Double totalDistanceMeters = document.getDouble("totalDistance");
                if (totalDistanceMeters != null) {
                    record.setTotalDistanceKm(totalDistanceMeters / 1000.0);
                }
            } else if (document.contains("totalDistanceKm")) {
                record.setTotalDistanceKm(document.getDouble("totalDistanceKm"));
            }
            
            if (document.contains("totalTime")) {
                Long totalTimeSeconds = document.getLong("totalTime");
                if (totalTimeSeconds != null) {
                    record.setElapsedTimeMs(totalTimeSeconds * 1000);
                }
            } else if (document.contains("elapsedTimeMs")) {
                Long elapsedTime = document.getLong("elapsedTimeMs");
                if (elapsedTime != null) {
                    record.setElapsedTimeMs(elapsedTime);
                }
            }
            
            if (document.contains("averagePace")) {
                Object paceObj = document.get("averagePace");
                if (paceObj instanceof Number) {
                    double paceSeconds = ((Number) paceObj).doubleValue();
                    record.setAveragePace(GoogleSignInUtils.formatPaceFromSeconds(paceSeconds));
                } else if (paceObj instanceof String) {
                    record.setAveragePace((String) paceObj);
                }
            }
            
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
                record.setTime(document.getString("time"));
            }
            
            if (document.contains("startTime")) {
                Object startTimeObj = document.get("startTime");
                if (startTimeObj instanceof com.google.firebase.Timestamp) {
                    java.util.Date date = ((com.google.firebase.Timestamp) startTimeObj).toDate();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy년 MM월 dd일", java.util.Locale.KOREA);
                    record.setDate(sdf.format(date));
                }
            } else if (document.contains("date")) {
                record.setDate(document.getString("date"));
            }
            
            if (document.contains("totalDistance")) {
                Double totalDistanceMeters = document.getDouble("totalDistance");
                if (totalDistanceMeters != null) {
                    double distanceKm = totalDistanceMeters / 1000.0;
                    record.setDistance(GoogleSignInUtils.formatDistanceKm(distanceKm));
                }
            } else if (document.contains("distance")) {
                record.setDistance(document.getString("distance"));
            }
            
            if (document.contains("pathEncoded")) {
                record.setPathEncoded(document.getString("pathEncoded"));
            }
            
            if (document.contains("courseId")) {
                record.setCourseId(document.getString("courseId"));
            }
            
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
            currentFilter = "date";
            // 버튼이 사라지지 않도록 보장
            dateFilterButton.setVisibility(View.VISIBLE);
            dateFilterButton.setEnabled(true);
            updateFilterButtons();
            showDateFilterDialog();
        });

        routeFilterButton.setOnClickListener(v -> {
            if (routeFilterButton == null) return;
            currentFilter = "difficulty";
            // 버튼이 사라지지 않도록 보장
            routeFilterButton.setVisibility(View.VISIBLE);
            routeFilterButton.setEnabled(true);
            updateFilterButtons();
            showDifficultyFilterDialog();
        });

        statisticsButton.setOnClickListener(v -> {
            if (statisticsButton == null) return;
            currentFilter = "statistics";
            // 버튼이 사라지지 않도록 보장
            statisticsButton.setVisibility(View.VISIBLE);
            statisticsButton.setEnabled(true);
            updateFilterButtons();
            showStatisticsView();
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
            String courseId = record.getCourseId();
            if (courseId != null && courseIdToDifficulty.containsKey(courseId)) {
                String recordDifficulty = courseIdToDifficulty.get(courseId);
                if (selectedDifficulty.equals(recordDifficulty)) {
                    filteredRecords.add(record);
                }
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
        // 필터가 변경될 때마다 어댑터를 다시 생성하여 filteredRecords를 반영
        recordAdapter = new RunningRecordAdapter(filteredRecords, record -> {
            // 기록 클릭 시 상세 보기 (선택사항)
        });
        recordAdapter.setOnItemLongClickListener(record -> {
            // 길게 누르면 삭제/수정 다이얼로그 표시
            showRunRecordOptionsDialog(record);
        });
        
        // 코스 이름 캐시 설정
        recordAdapter.setCourseNameCache(courseNameCache);
        
        GoogleSignInUtils.setupRecyclerView(recordRecyclerView, recordAdapter, this);
        
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
                        showEditRunRecordDialog(documentId, userId, record);
                    } else if (which == 1) {
                        showDeleteConfirmDialog(documentId, userId);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showEditRunRecordDialog(String documentId, String userId, RunningRecord record) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("기록 수정");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        android.widget.TextView distanceLabel = new android.widget.TextView(this);
        distanceLabel.setText("거리 (km):");
        distanceLabel.setTextSize(14);
        layout.addView(distanceLabel);

        android.widget.EditText distanceEdit = new android.widget.EditText(this);
        distanceEdit.setText(String.format("%.2f", record.getTotalDistanceKm()));
        distanceEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(distanceEdit);

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
                String distanceStr = distanceEdit.getText().toString().trim();
                double distanceKm = Double.parseDouble(distanceStr);
                double distanceMeters = distanceKm * 1000.0;

                String timeStr = timeEdit.getText().toString().trim();
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
                        })
                        .addOnFailureListener(e -> {
                            GoogleSignInUtils.showToast(this, "기록 수정에 실패했습니다: " + e.getMessage());
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
                            })
                            .addOnFailureListener(e -> {
                                GoogleSignInUtils.showToast(this, "기록 삭제에 실패했습니다: " + e.getMessage());
                            });
                })
                .setNegativeButton("취소", null)
                .show();
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

