package Run.U;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private GoogleSignInClient googleSignInClient;
    private TextView welcomeText;
    private ImageButton logoutButton;
    private MaterialButton startNormalRunButton;
    private MaterialButton startCourseRunButton;

    // 통계 TextViews
    private TextView totalDistanceText;
    private TextView totalTimeText;
    private TextView runCountText;

    // 최근 기록
    private LinearLayout recentRunsList;
    private TextView noRunsText;
    private TextView viewAllButton;
    private ListenerRegistration recentRunsListener;
    private java.util.Map<String, String> courseNameCache = new java.util.HashMap<>(); // 코스 이름 캐시

    // 관리자 카드 및 버튼
    private androidx.cardview.widget.CardView adminCard;
    private MaterialButton adminCourseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        firebaseAuth = GoogleSignInUtils.getAuth();
        firestore = GoogleSignInUtils.getFirestore();
        googleSignInClient = GoogleSignInUtils.getGoogleSignInClient(this);

        // View 초기화
        welcomeText = findViewById(R.id.welcome_text);
        logoutButton = findViewById(R.id.logout_button);
        startNormalRunButton = findViewById(R.id.start_normal_run_button);
        startCourseRunButton = findViewById(R.id.start_course_run_button);

        totalDistanceText = findViewById(R.id.total_distance_text);
        totalTimeText = findViewById(R.id.total_time_text);
        runCountText = findViewById(R.id.run_count_text);
        recentRunsList = findViewById(R.id.recent_runs_list);
        noRunsText = findViewById(R.id.no_runs_text);
        viewAllButton = findViewById(R.id.view_all_button);

        // 관리자 뷰 초기화
        adminCard = findViewById(R.id.admin_card);
        adminCourseButton = findViewById(R.id.admin_course_button);

        // 버튼 클릭 리스너
        logoutButton.setOnClickListener(v -> showLogoutDialog());

        startNormalRunButton.setOnClickListener(v -> startNormalRun());
        startCourseRunButton.setOnClickListener(v -> startCourseSelection());

        viewAllButton.setOnClickListener(v -> {
            // 전체 기록 Activity로 이동 (가장 최근 기록 표시)
            Intent intent = new Intent(MainActivity.this, RunningRecordActivity.class);
            startActivity(intent);
        });

        // 관리자 코스 등록 버튼
        if (adminCourseButton != null) {
            adminCourseButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AdminCourseActivity.class);
                startActivity(intent);
            });
        }

        // 전체 기록 보기 버튼
        if (viewAllButton != null) {
            viewAllButton.setOnClickListener(v -> {
                // 전체 기록 Activity로 이동 (가장 최근 기록 표시)
                Intent intent = new Intent(MainActivity.this, RunningRecordActivity.class);
                startActivity(intent);
            });
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateWelcomeMessage();
        loadWeeklyStats();
        loadRecentRuns();
        checkAdminRole();
    }

    private void updateWelcomeMessage() {
        if (welcomeText == null) {
            return;
        }
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = GoogleSignInUtils.getUserDisplayName(currentUser);
            if (displayName != null && !displayName.isEmpty()) {
                if (displayName.contains("@")) {
                    displayName = displayName.split("@")[0];
                }
                try {
                    String welcomeMessage = getString(R.string.welcome_message, displayName);
                    welcomeText.setText(welcomeMessage);
                } catch (Exception e) {
                    welcomeText.setText(displayName + "님, 안녕하세요!");
                }
            }
        }
    }

    private void checkAdminRole() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            if (adminCard != null) {
                adminCard.setVisibility(View.GONE);
            }
            return;
        }

        GoogleSignInUtils.checkAdminRole(currentUser, isAdmin -> {
            if (isAdmin && adminCard != null) {
                adminCard.setVisibility(View.VISIBLE);
            } else if (adminCard != null) {
                adminCard.setVisibility(View.GONE);
            }
        });
    }

    private void loadWeeklyStats() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || totalDistanceText == null || totalTimeText == null || runCountText == null) {
            return;
        }

        // 이번 주 시작일 계산 (월요일)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date weekStart = calendar.getTime();

        String userId = currentUser.getUid();

        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .whereGreaterThanOrEqualTo("startTime", weekStart)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalDistance = 0.0;
                    long totalTime = 0;
                    int runCount = queryDocumentSnapshots != null ? queryDocumentSnapshots.size() : 0;

                    if (queryDocumentSnapshots != null) {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Double distance = doc.getDouble("totalDistance");
                            Long time = doc.getLong("totalTime");

                            if (distance != null) {
                                totalDistance += distance; // 미터 단위
                            }
                            if (time != null) {
                                totalTime += time; // 초 단위
                            }
                        }
                    }

                    // UI 업데이트 (빈 상태도 0으로 표시)
                    if (totalDistanceText != null) {
                        totalDistanceText.setText(String.format("%.1f km", totalDistance / 1000.0));
                    }
                    if (totalTimeText != null) {
                        if (totalTime > 0) {
                            long hours = totalTime / 3600;
                            long minutes = (totalTime % 3600) / 60;
                            if (hours > 0) {
                                totalTimeText.setText(String.format("%d:%02d", hours, minutes));
                            } else {
                                totalTimeText.setText(String.format("%d분", minutes));
                            }
                        } else {
                            totalTimeText.setText("0분");
                        }
                    }
                    if (runCountText != null) {
                        runCountText.setText(String.format("%d회", runCount));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "주간 통계 로드 실패", e);
                    // 실패 시에도 빈 상태로 표시
                    if (totalDistanceText != null) {
                        totalDistanceText.setText("0.0 km");
                    }
                    if (totalTimeText != null) {
                        totalTimeText.setText("0분");
                    }
                    if (runCountText != null) {
                        runCountText.setText("0회");
                    }
                });
    }

    private void loadRecentRuns() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || recentRunsList == null || noRunsText == null) {
            return;
        }

        String userId = currentUser.getUid();

        // 기존 리스너 제거
        if (recentRunsListener != null) {
            recentRunsListener.remove();
        }

        // 실시간 리스너 등록
        recentRunsListener = firestore.collection("users")
                .document(userId)
                .collection("runs")
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(3)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("MainActivity", "최근 기록 리스너 오류", e);
                        return;
                    }

                    if (snapshot != null) {
                        // 첫 로드인지 확인 (getDocumentChanges가 비어있으면 전체 스냅샷)
                        if (snapshot.getDocumentChanges().isEmpty()) {
                            // 초기 로드: 전체 아이템 다시 그리기
                            refreshRecentRunsList(snapshot);
                        } else {
                            // 변경사항만 처리
                            for (DocumentChange dc : snapshot.getDocumentChanges()) {
                                QueryDocumentSnapshot document = dc.getDocument();
                                
                                switch (dc.getType()) {
                                    case ADDED:
                                        // 기록 추가
                                        addRecentRunItem(document);
                                        Log.d("MainActivity", "최근 기록 추가됨: " + document.getId());
                                        break;
                                    case MODIFIED:
                                        // 기록 수정: 해당 아이템 찾아서 업데이트
                                        updateRecentRunItem(document);
                                        Log.d("MainActivity", "최근 기록 수정됨: " + document.getId());
                                        break;
                                    case REMOVED:
                                        // 기록 삭제: 해당 아이템 제거
                                        removeRecentRunItem(document.getId());
                                        Log.d("MainActivity", "최근 기록 삭제됨: " + document.getId());
                                        break;
                                }
                            }
                            
                            // 빈 상태 확인
                            if (recentRunsList != null && recentRunsList.getChildCount() == 0) {
                                if (noRunsText != null) {
                                    noRunsText.setVisibility(View.VISIBLE);
                                }
                                if (recentRunsList != null) {
                                    recentRunsList.setVisibility(View.GONE);
                                }
                            } else if (recentRunsList != null && recentRunsList.getChildCount() > 0) {
                                if (noRunsText != null) {
                                    noRunsText.setVisibility(View.GONE);
                                }
                                if (recentRunsList != null) {
                                    recentRunsList.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    }
                });
    }

    private void refreshRecentRunsList(com.google.firebase.firestore.QuerySnapshot snapshot) {
        if (recentRunsList == null) {
            return;
        }

        // 기존 아이템 모두 제거
        recentRunsList.removeAllViews();

        if (snapshot == null || snapshot.isEmpty()) {
            // 빈 상태 표시
            if (noRunsText != null) {
                noRunsText.setVisibility(View.VISIBLE);
            }
            if (recentRunsList != null) {
                recentRunsList.setVisibility(View.GONE);
            }
        } else {
            if (noRunsText != null) {
                noRunsText.setVisibility(View.GONE);
            }
            if (recentRunsList != null) {
                recentRunsList.setVisibility(View.VISIBLE);
                // 최근 기록 추가 (이미 정렬되어 있음)
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                    if (doc instanceof QueryDocumentSnapshot) {
                        addRecentRunItem((QueryDocumentSnapshot) doc);
                    }
                }
            }
        }
    }

    private void addRecentRunItem(QueryDocumentSnapshot doc) {
        if (recentRunsList == null) {
            return;
        }

        // 이미 존재하는지 확인
        String documentId = doc.getId();
        if (findRecentRunItemView(documentId) != null) {
            // 이미 존재하면 업데이트만
            updateRecentRunItem(doc);
            return;
        }

        // 간단한 기록 아이템 생성
        TextView itemView = createRecentRunItemView(doc);
        recentRunsList.addView(itemView);
    }

    private TextView createRecentRunItemView(QueryDocumentSnapshot doc) {
        TextView itemView = new TextView(this);
        Double distance = doc.getDouble("totalDistance");
        Long time = doc.getLong("totalTime");
        String courseId = doc.getString("courseId");

        String distanceStr = distance != null ?
                String.format("%.2f km", distance / 1000.0) : "0.00 km";

        String timeStr = "";
        if (time != null) {
            long minutes = time / 60;
            long seconds = time % 60;
            timeStr = String.format("%d:%02d", minutes, seconds);
        }

        // 초기 텍스트 설정 (코스 이름 없이)
        String initialText = String.format("📍 %s • ⏱ %s", distanceStr, timeStr);
        itemView.setText(initialText);
        itemView.setTextSize(14);
        itemView.setTextColor(getResources().getColor(R.color.accent_white, null));
        itemView.setPadding(0, 16, 0, 16);
        
        // 문서 ID를 태그로 저장
        String documentId = doc.getId();
        itemView.setTag(documentId);
        
        // 코스 이름이 있으면 가져오기
        if (courseId != null && !courseId.isEmpty()) {
            loadCourseNameAndUpdateView(courseId, itemView, distanceStr, timeStr);
        }
        
        // 길게 누르면 삭제/수정 다이얼로그 표시
        itemView.setOnLongClickListener(v -> {
            showRunRecordOptionsDialog(documentId, doc);
            return true;
        });
        
        // 클릭하면 전체 기록 화면으로 이동
        itemView.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RunningRecordActivity.class);
            startActivity(intent);
        });
        
        itemView.setClickable(true);
        itemView.setFocusable(true);
        return itemView;
    }

    private void loadCourseNameAndUpdateView(String courseId, TextView itemView, String distanceStr, String timeStr) {
        // 캐시에서 먼저 확인
        if (courseNameCache.containsKey(courseId)) {
            String courseName = courseNameCache.get(courseId);
            itemView.setText(String.format("📍 %s %s • ⏱ %s", courseName, distanceStr, timeStr));
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
                            if (itemView.getTag() != null) { // 뷰가 아직 유효한지 확인
                                itemView.setText(String.format("📍 %s %s • ⏱ %s", courseName, distanceStr, timeStr));
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("MainActivity", "코스 이름 로드 실패: " + courseId, e);
                });
    }

    private TextView findRecentRunItemView(String documentId) {
        if (recentRunsList == null) {
            return null;
        }
        
        for (int i = 0; i < recentRunsList.getChildCount(); i++) {
            View child = recentRunsList.getChildAt(i);
            if (child instanceof TextView && documentId.equals(child.getTag())) {
                return (TextView) child;
            }
        }
        return null;
    }

    private void updateRecentRunItem(QueryDocumentSnapshot doc) {
        String documentId = doc.getId();
        TextView itemView = findRecentRunItemView(documentId);
        
        if (itemView != null) {
            // 기존 뷰 업데이트
            Double distance = doc.getDouble("totalDistance");
            Long time = doc.getLong("totalTime");
            String courseId = doc.getString("courseId");

            String distanceStr = distance != null ?
                    String.format("%.2f km", distance / 1000.0) : "0.00 km";

            String timeStr = "";
            if (time != null) {
                long minutes = time / 60;
                long seconds = time % 60;
                timeStr = String.format("%d:%02d", minutes, seconds);
            }

            // 초기 텍스트 설정
            String initialText = String.format("📍 %s • ⏱ %s", distanceStr, timeStr);
            itemView.setText(initialText);
            
            // 코스 이름이 있으면 가져오기
            if (courseId != null && !courseId.isEmpty()) {
                loadCourseNameAndUpdateView(courseId, itemView, distanceStr, timeStr);
            }
            
            // 리스너 재설정 (doc 업데이트)
            itemView.setOnLongClickListener(v -> {
                showRunRecordOptionsDialog(documentId, doc);
                return true;
            });
        } else {
            // 뷰가 없으면 추가
            addRecentRunItem(doc);
        }
    }

    private void removeRecentRunItem(String documentId) {
        TextView itemView = findRecentRunItemView(documentId);
        if (itemView != null && recentRunsList != null) {
            recentRunsList.removeView(itemView);
        }
    }

    private void showRunRecordOptionsDialog(String documentId, QueryDocumentSnapshot doc) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            return;
        }

        String userId = currentUser.getUid();
        
        // 기록 정보 가져오기
        Double distance = doc.getDouble("totalDistance");
        Long time = doc.getLong("totalTime");
        String distanceStr = distance != null ? String.format("%.2f km", distance / 1000.0) : "0.00 km";
        String timeStr = "";
        if (time != null) {
            long minutes = time / 60;
            long seconds = time % 60;
            timeStr = String.format("%d:%02d", minutes, seconds);
        }

        new AlertDialog.Builder(this)
                .setTitle("기록 관리")
                .setMessage(String.format("거리: %s\n시간: %s", distanceStr, timeStr))
                .setItems(new String[]{"수정", "삭제"}, (dialog, which) -> {
                    if (which == 0) {
                        // 수정
                        showEditRunRecordDialog(documentId, userId, doc);
                    } else if (which == 1) {
                        // 삭제
                        showDeleteConfirmDialog(documentId, userId);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showEditRunRecordDialog(String documentId, String userId, QueryDocumentSnapshot doc) {
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
        Double currentDistance = doc.getDouble("totalDistance");
        distanceEdit.setText(currentDistance != null ? String.format("%.2f", currentDistance / 1000.0) : "0.00");
        distanceEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(distanceEdit);

        // 시간 입력 (분:초)
        android.widget.TextView timeLabel = new android.widget.TextView(this);
        timeLabel.setText("시간 (분:초):");
        timeLabel.setTextSize(14);
        timeLabel.setPadding(0, 20, 0, 0);
        layout.addView(timeLabel);

        android.widget.EditText timeEdit = new android.widget.EditText(this);
        Long currentTime = doc.getLong("totalTime");
        if (currentTime != null) {
            long minutes = currentTime / 60;
            long seconds = currentTime % 60;
            timeEdit.setText(String.format("%d:%02d", minutes, seconds));
        } else {
            timeEdit.setText("0:00");
        }
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
                long totalSeconds = 0;
                if (timeParts.length == 2) {
                    long minutes = Long.parseLong(timeParts[0]);
                    long seconds = Long.parseLong(timeParts[1]);
                    totalSeconds = minutes * 60 + seconds;
                } else {
                    // 분만 입력한 경우
                    totalSeconds = Long.parseLong(timeStr) * 60;
                }

                // 평균 페이스 계산
                double averagePaceSeconds = 0;
                if (distanceKm > 0) {
                    averagePaceSeconds = totalSeconds / distanceKm;
                }

                // Firestore 업데이트
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("totalDistance", distanceMeters);
                updates.put("totalTime", totalSeconds);
                updates.put("averagePace", averagePaceSeconds);

                firestore.collection("users")
                        .document(userId)
                        .collection("runs")
                        .document(documentId)
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            GoogleSignInUtils.showToast(this, "기록이 수정되었습니다.");
                            Log.d("MainActivity", "기록 수정 성공: " + documentId);
                        })
                        .addOnFailureListener(e -> {
                            GoogleSignInUtils.showToast(this, "기록 수정에 실패했습니다: " + e.getMessage());
                            Log.e("MainActivity", "기록 수정 실패", e);
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
                                Log.d("MainActivity", "기록 삭제 성공: " + documentId);
                                // 실시간 리스너가 자동으로 UI 업데이트
                            })
                            .addOnFailureListener(e -> {
                                GoogleSignInUtils.showToast(this, "기록 삭제에 실패했습니다: " + e.getMessage());
                                Log.e("MainActivity", "기록 삭제 실패", e);
                            });
                })
                .setNegativeButton("취소", null)
                .show();
    }


    private void startNormalRun() {
        Intent intent = new Intent(MainActivity.this, RunningStartActivity.class);
        startActivity(intent);
    }

    private void startCourseSelection() {
        Intent intent = new Intent(MainActivity.this, SketchRunActivity.class);
        startActivity(intent);
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_dialog_title)
                .setMessage(R.string.logout_dialog_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> performLogout())
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 리스너 제거
        if (recentRunsListener != null) {
            recentRunsListener.remove();
            recentRunsListener = null;
        }
    }

    private void performLogout() {
        firebaseAuth.signOut();
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
