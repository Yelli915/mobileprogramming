package Run.U;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminCourseActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;

    private EditText courseNameEditText;
    private EditText courseDescriptionEditText;
    private EditText courseDistanceEditText;
    private EditText courseDifficultyEditText;
    private EditText courseEstimatedTimeEditText;
    private EditText coursePathEncodedEditText;
    private MaterialButton saveCourseButton;
    private MaterialButton importSeoulCoursesButton;
    private MaterialButton clearFirestoreButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_course);

        firestore = GoogleSignInUtils.getFirestore();
        firebaseAuth = GoogleSignInUtils.getAuth();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        courseNameEditText = findViewById(R.id.course_name_edit);
        courseDescriptionEditText = findViewById(R.id.course_description_edit);
        courseDistanceEditText = findViewById(R.id.course_distance_edit);
        courseDifficultyEditText = findViewById(R.id.course_difficulty_edit);
        courseEstimatedTimeEditText = findViewById(R.id.course_estimated_time_edit);
        coursePathEncodedEditText = findViewById(R.id.course_path_encoded_edit);
        saveCourseButton = findViewById(R.id.save_course_button);
        importSeoulCoursesButton = findViewById(R.id.import_seoul_courses_button);
        clearFirestoreButton = findViewById(R.id.clear_firestore_button);

        if (saveCourseButton != null) {
            saveCourseButton.setOnClickListener(v -> saveCourse());
        }

        if (importSeoulCoursesButton != null) {
            importSeoulCoursesButton.setOnClickListener(v -> {
                Log.d("AdminCourseActivity", "버튼 클릭 감지됨 - importSeoulCoursesFromApi() 호출");
                importSeoulCoursesFromApi();
            });
            importSeoulCoursesButton.setEnabled(true);
            importSeoulCoursesButton.setAlpha(1.0f);
            Log.d("AdminCourseActivity", "코스 일괄 업로드 버튼 초기화: 활성화 상태");
        } else {
            Log.e("AdminCourseActivity", "❌ import_seoul_courses_button을 찾을 수 없습니다!");
        }

        if (clearFirestoreButton != null) {
            clearFirestoreButton.setOnClickListener(v -> {
                Log.d("AdminCourseActivity", "데이터 비우기 버튼 클릭됨");
                clearFirestoreData();
            });
        }

        Log.d("AdminCourseActivity", "========================================");
        Log.d("AdminCourseActivity", "🚀 AdminCourseActivity onCreate() 완료");
        Log.d("AdminCourseActivity", "   버튼 상태: importSeoulCoursesButton = " + (importSeoulCoursesButton != null ? "찾음" : "null"));
        if (importSeoulCoursesButton != null) {
            Log.d("AdminCourseActivity", "   버튼 enabled: " + importSeoulCoursesButton.isEnabled());
            Log.d("AdminCourseActivity", "   버튼 alpha: " + importSeoulCoursesButton.getAlpha());
        }
        Log.d("AdminCourseActivity", "========================================");
        
        checkAdminPermission();
    }

    private void checkAdminPermission() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            finish();
            return;
        }

        GoogleSignInUtils.checkAdminRole(currentUser, isAdmin -> {
            if (!isAdmin) {
                GoogleSignInUtils.showToast(this, "관리자 권한이 필요합니다.");
                finish();
            }
        });
    }

    private void saveCourse() {
        Log.d("AdminCourseActivity", "saveCourse() 호출됨");
        
        if (courseNameEditText == null || courseDescriptionEditText == null ||
            courseDistanceEditText == null || courseDifficultyEditText == null ||
            courseEstimatedTimeEditText == null || coursePathEncodedEditText == null) {
            Log.e("AdminCourseActivity", "❌ 필수 EditText가 null입니다!");
            return;
        }

        String name = courseNameEditText.getText().toString().trim();
        String description = courseDescriptionEditText.getText().toString().trim();
        String distanceStr = courseDistanceEditText.getText().toString().trim();
        String difficulty = courseDifficultyEditText.getText().toString().trim();
        String estimatedTimeStr = courseEstimatedTimeEditText.getText().toString().trim();
        String pathEncoded = coursePathEncodedEditText.getText().toString().trim();

        Log.d("AdminCourseActivity", "입력값 확인 - name: " + name + ", distance: " + distanceStr + ", difficulty: " + difficulty);

        if (name.isEmpty()) {
            Log.w("AdminCourseActivity", "⚠️ 코스 이름이 비어있습니다.");
            GoogleSignInUtils.showToast(this, "코스 이름을 입력해주세요.");
            return;
        }

        if (description.isEmpty()) {
            Log.w("AdminCourseActivity", "⚠️ 코스 설명이 비어있습니다.");
            GoogleSignInUtils.showToast(this, "코스 설명을 입력해주세요.");
            return;
        }

        if (distanceStr.isEmpty()) {
            Log.w("AdminCourseActivity", "⚠️ 거리가 비어있습니다.");
            GoogleSignInUtils.showToast(this, "거리를 입력해주세요.");
            return;
        }

        if (difficulty.isEmpty()) {
            Log.w("AdminCourseActivity", "⚠️ 난이도가 비어있습니다.");
            GoogleSignInUtils.showToast(this, "난이도를 입력해주세요. (easy, medium, hard)");
            return;
        }

        if (estimatedTimeStr.isEmpty()) {
            Log.w("AdminCourseActivity", "⚠️ 예상 시간이 비어있습니다.");
            GoogleSignInUtils.showToast(this, "예상 시간을 입력해주세요. (분 단위)");
            return;
        }

        if (pathEncoded.isEmpty()) {
            Log.w("AdminCourseActivity", "⚠️ 경로 데이터가 비어있습니다.");
            GoogleSignInUtils.showToast(this, "경로 데이터를 입력해주세요.");
            return;
        }

        try {
            double distance = Double.parseDouble(distanceStr);
            int estimatedTime = Integer.parseInt(estimatedTimeStr);

            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser == null) {
                GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
                return;
            }

            Map<String, Object> courseData = new HashMap<>();
            courseData.put("name", name);
            courseData.put("description", description);
            courseData.put("totalDistance", distance * 1000.0);
            courseData.put("difficulty", difficulty);
            courseData.put("estimatedTime", estimatedTime * 60);
            courseData.put("pathEncoded", pathEncoded);
            courseData.put("adminCreatorId", currentUser.getUid());
            courseData.put("createdAt", FieldValue.serverTimestamp());

            Log.d("AdminCourseActivity", "코스 등록 시작 - Firestore에 저장 시도");
            
            firestore.collection("courses")
                    .add(courseData)
                    .addOnSuccessListener(documentReference -> {
                        Log.d("AdminCourseActivity", "✅ 코스 저장 성공: " + documentReference.getId());
                        GoogleSignInUtils.showToast(this, "코스가 등록되었습니다.");
                        
                        new AlertDialog.Builder(this)
                                .setTitle("등록 완료")
                                .setMessage("코스가 성공적으로 등록되었습니다.")
                                .setPositiveButton("확인", null)
                                .show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AdminCourseActivity", "❌ 코스 저장 실패", e);
                        Log.e("AdminCourseActivity", "   에러 메시지: " + e.getMessage());
                        GoogleSignInUtils.showToast(this, "코스 등록에 실패했습니다: " + e.getMessage());
                    });

        } catch (NumberFormatException e) {
            GoogleSignInUtils.showToast(this, "숫자 형식이 올바르지 않습니다.");
        }
    }

    private void importSeoulCoursesFromApi() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            return;
        }

        Log.d("AdminCourseActivity", "========================================");
        Log.d("AdminCourseActivity", "🎯 코스 일괄 업로드 버튼 클릭됨");
        Log.d("AdminCourseActivity", "   모든 코스 가져오기 시작...");
        
        importSeoulCoursesButton.setEnabled(false);
        importSeoulCoursesButton.setText("API 호출 중...");

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("모든 코스를 가져오는 중...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        long startTime = System.currentTimeMillis();
        
        PublicDataApiClient.fetchAllCourses(new PublicDataApiClient.ApiCallback() {
            @Override
            public void onSuccess(List<ApiCourseItem> courses) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                progressDialog.dismiss();
                
                Log.d("AdminCourseActivity", "✅ API 호출 성공 (" + elapsedTime + "ms)");
                Log.d("AdminCourseActivity", "   받은 코스 개수: " + (courses != null ? courses.size() : 0));
                
                if (courses == null || courses.isEmpty()) {
                    Log.w("AdminCourseActivity", "⚠️ 코스를 찾을 수 없습니다.");
                    resetImportButton();
                    GoogleSignInUtils.showToast(AdminCourseActivity.this, "코스를 찾을 수 없습니다.");
                    Log.d("AdminCourseActivity", "========================================");
                    return;
                }

                Log.d("AdminCourseActivity", "   업로드 확인 다이얼로그 표시");
                Log.d("AdminCourseActivity", "========================================");
                
                new AlertDialog.Builder(AdminCourseActivity.this)
                        .setTitle("코스 업로드 확인")
                        .setMessage(String.format("총 %d개의 코스를 업로드하시겠습니까?", courses.size()))
                        .setPositiveButton("업로드", (dialog, which) -> {
                            Log.d("AdminCourseActivity", "✅ 사용자가 업로드 확인");
                            uploadCoursesToFirestore(courses, currentUser.getUid());
                        })
                        .setNegativeButton("취소", (dialog, which) -> {
                            Log.d("AdminCourseActivity", "❌ 사용자가 업로드 취소");
                            resetImportButton();
                        })
                        .show();
            }

            @Override
            public void onFailure(String error) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                progressDialog.dismiss();
                resetImportButton();
                
                Log.e("AdminCourseActivity", "❌ API 호출 실패 (" + elapsedTime + "ms)");
                Log.e("AdminCourseActivity", "   에러 메시지: " + error);
                Log.d("AdminCourseActivity", "========================================");
                
                GoogleSignInUtils.showToast(AdminCourseActivity.this, "코스를 가져오는데 실패했습니다: " + error);
            }
        });
    }

    private void uploadCoursesToFirestore(List<ApiCourseItem> apiCourses, String adminCreatorId) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("코스를 업로드하는 중...");
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(apiCourses.size());
        progressDialog.setProgress(0);
        progressDialog.show();
        
        importSeoulCoursesButton.setEnabled(false);
        importSeoulCoursesButton.setText("업로드 중...");

        int totalCount = apiCourses.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicBoolean isCompleted = new AtomicBoolean(false);

        if (totalCount == 0) {
            progressDialog.dismiss();
            resetImportButton();
            GoogleSignInUtils.showToast(this, "업로드할 코스가 없습니다.");
            return;
        }

        Log.d("AdminCourseActivity", "코스 업로드 시작 - 총 " + totalCount + "개");

        for (ApiCourseItem apiCourse : apiCourses) {
            if (apiCourse.getCrsIdx() == null || apiCourse.getCrsIdx().isEmpty()) {
                skipCount.incrementAndGet();
                Log.w("AdminCourseActivity", "crsIdx가 없어 건너뜀: " + apiCourse.getCrsKorNm());
                updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                    checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                }
                continue;
            }

            uploadCourseWithDuplicateCheck(apiCourse, adminCreatorId, successCount, skipCount, failCount, 
                    completedCount, isCompleted, totalCount, progressDialog);
        }
    }

    private void uploadCourseWithDuplicateCheck(ApiCourseItem apiCourse, String adminCreatorId,
                                                  AtomicInteger successCount, AtomicInteger skipCount, AtomicInteger failCount,
                                                  AtomicInteger completedCount, AtomicBoolean isCompleted, int totalCount, ProgressDialog progressDialog) {
        String crsIdx = apiCourse.getCrsIdx();
        
        firestore.collection("courses")
                .whereEqualTo("crsIdx", crsIdx)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, Object> courseData = convertApiCourseToFirestoreData(apiCourse, adminCreatorId, querySnapshot.isEmpty());
                    
                    if (!querySnapshot.isEmpty()) {
                        String existingDocId = querySnapshot.getDocuments().get(0).getId();
                        Log.d("AdminCourseActivity", "중복 발견: " + apiCourse.getCrsKorNm() + " (기존 문서 ID: " + existingDocId + ")");
                        
                        firestore.collection("courses").document(existingDocId)
                                .update(courseData)
                                .addOnSuccessListener(aVoid -> {
                                    successCount.incrementAndGet();
                                    Log.d("AdminCourseActivity", "✅ 코스 업데이트 성공: " + apiCourse.getCrsKorNm());
                                    updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    failCount.incrementAndGet();
                                    Log.e("AdminCourseActivity", "❌ 코스 업데이트 실패: " + apiCourse.getCrsKorNm(), e);
                                    updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                });
                    } else {
                        firestore.collection("courses")
                                .add(courseData)
                                .addOnSuccessListener(documentReference -> {
                                    successCount.incrementAndGet();
                                    Log.d("AdminCourseActivity", "✅ 코스 업로드 성공: " + apiCourse.getCrsKorNm() + " (ID: " + documentReference.getId() + ")");
                                    updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    failCount.incrementAndGet();
                                    Log.e("AdminCourseActivity", "❌ 코스 업로드 실패: " + apiCourse.getCrsKorNm(), e);
                                    updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    failCount.incrementAndGet();
                    Log.e("AdminCourseActivity", "❌ 중복 체크 실패: " + apiCourse.getCrsKorNm(), e);
                    updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                    }
                });
    }

    private void fallbackToCreate(String existingDocId, ApiCourseItem apiCourse, String adminCreatorId,
                                   AtomicInteger successCount, AtomicInteger skipCount, AtomicInteger failCount,
                                   AtomicInteger completedCount, AtomicBoolean isCompleted, int totalCount, ProgressDialog progressDialog) {
        Map<String, Object> createData = convertApiCourseToFirestoreData(apiCourse, adminCreatorId, true);
        firestore.collection("courses").document(existingDocId)
                .set(createData)
                .addOnSuccessListener(aVoid -> {
                    successCount.incrementAndGet();
                    Log.d("AdminCourseActivity", "코스 재생성 성공: " + apiCourse.getCrsKorNm());
                    updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                    }
                })
                .addOnFailureListener(e -> {
                    failCount.incrementAndGet();
                    Log.e("AdminCourseActivity", "코스 재생성 실패: " + apiCourse.getCrsKorNm(), e);
                    updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                    }
                });
    }

    private void updateProgressDialog(ProgressDialog progressDialog, int completedCount, int totalCount) {
        if (progressDialog != null && progressDialog.isShowing()) {
            int progress = completedCount;
            progressDialog.setProgress(progress);
            double percent = (double) progress / totalCount * 100;
            progressDialog.setMessage(String.format("코스를 업로드하는 중... (%d/%d, %.0f%%)", 
                    progress, totalCount, percent));
        }
    }

    private void enableImportButton() {
        Log.d("AdminCourseActivity", "enableImportButton() 실행 중...");
        if (importSeoulCoursesButton != null) {
            importSeoulCoursesButton.setEnabled(true);
            importSeoulCoursesButton.setAlpha(1.0f);
            Log.d("AdminCourseActivity", "✅ 버튼 활성화 완료 - enabled: true, alpha: 1.0");
        } else {
            Log.e("AdminCourseActivity", "❌ importSeoulCoursesButton이 null입니다!");
        }
    }

    private void resetImportButton() {
        if (importSeoulCoursesButton != null) {
            importSeoulCoursesButton.setEnabled(true);
            importSeoulCoursesButton.setText("코스 일괄 업로드");
            importSeoulCoursesButton.setAlpha(1.0f);
        }
    }

    private void checkUploadComplete(int totalCount, AtomicInteger successCount, AtomicInteger skipCount, AtomicInteger failCount, ProgressDialog progressDialog) {
        progressDialog.dismiss();
        resetImportButton();
        
        String message = String.format(
                "업로드 완료!\n성공: %d개\n건너뜀: %d개\n실패: %d개",
                successCount.get(), skipCount.get(), failCount.get()
        );
        
        GoogleSignInUtils.showToast(this, "업로드 완료!");
        
        new AlertDialog.Builder(this)
                .setTitle("업로드 완료")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }

    private Map<String, Object> convertApiCourseToFirestoreData(ApiCourseItem apiCourse, String adminCreatorId, boolean isNewDocument) {
        Map<String, Object> courseData = new HashMap<>();

        courseData.put("name", apiCourse.getCrsKorNm() != null ? apiCourse.getCrsKorNm() : "");
        
        String description = apiCourse.getCrsContents();
        if (description == null || description.isEmpty()) {
            description = apiCourse.getCrsSummary();
        }
        courseData.put("description", description != null ? description : "");

        try {
            double distanceKm = Double.parseDouble(apiCourse.getCrsDstnc());
            courseData.put("totalDistance", distanceKm * 1000.0);
        } catch (NumberFormatException e) {
            courseData.put("totalDistance", 0.0);
        }

        String difficulty = convertCrsLevelToDifficulty(apiCourse.getCrsLevel());
        courseData.put("difficulty", difficulty);

        try {
            int timeMinutes = Integer.parseInt(apiCourse.getCrsTotlRqrmHour());
            courseData.put("estimatedTime", timeMinutes * 60);
        } catch (NumberFormatException e) {
            courseData.put("estimatedTime", 0);
        }

        courseData.put("pathEncoded", "");
        courseData.put("adminCreatorId", adminCreatorId);
        
        if (isNewDocument) {
            courseData.put("createdAt", FieldValue.serverTimestamp());
        }
        
        courseData.put("crsIdx", apiCourse.getCrsIdx());

        return courseData;
    }

    private String convertCrsLevelToDifficulty(String crsLevel) {
        if (crsLevel == null || crsLevel.isEmpty()) {
            return "medium";
        }

        try {
            int level = Integer.parseInt(crsLevel);
            if (level == 1) {
                return "easy";
            } else if (level == 2) {
                return "medium";
            } else if (level >= 3) {
                return "hard";
            }
        } catch (NumberFormatException e) {
            Log.w("AdminCourseActivity", "난이도 변환 실패: " + crsLevel);
        }

        return "medium";
    }

    private void clearFirestoreData() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("⚠️ 경고")
                .setMessage("Firestore의 courses 컬렉션의 모든 데이터를 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다!")
                .setPositiveButton("삭제", (dialog, which) -> {
                    Log.d("AdminCourseActivity", "사용자가 데이터 삭제 확인");
                    executeClearFirestore();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void executeClearFirestore() {
        clearFirestoreButton.setEnabled(false);
        clearFirestoreButton.setText("삭제 중...");

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("데이터를 삭제하는 중...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Log.d("AdminCourseActivity", "Firestore courses 컬렉션 삭제 시작");

        firestore.collection("courses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalCount = queryDocumentSnapshots.size();
                    if (totalCount == 0) {
                        progressDialog.dismiss();
                        resetClearButton();
                        GoogleSignInUtils.showToast(this, "삭제할 데이터가 없습니다.");
                        Log.d("AdminCourseActivity", "삭제할 데이터 없음");
                        return;
                    }

                    AtomicInteger deletedCount = new AtomicInteger(0);
                    AtomicInteger failCount = new AtomicInteger(0);
                    AtomicInteger completedCount = new AtomicInteger(0);
                    AtomicBoolean isCompleted = new AtomicBoolean(false);

                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMax(totalCount);
                    progressDialog.setProgress(0);

                    Log.d("AdminCourseActivity", "총 " + totalCount + "개 문서 삭제 시작");

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        firestore.collection("courses")
                                .document(document.getId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    deletedCount.incrementAndGet();
                                    int completed = completedCount.incrementAndGet();
                                    progressDialog.setProgress(completed);
                                    progressDialog.setMessage(String.format("삭제 중... (%d/%d)", completed, totalCount));

                                    if (completed >= totalCount && !isCompleted.getAndSet(true)) {
                                        progressDialog.dismiss();
                                        resetClearButton();
                                        String message = String.format("삭제 완료!\n성공: %d개\n실패: %d개", deletedCount.get(), failCount.get());
                                        GoogleSignInUtils.showToast(this, "삭제 완료!");
                                        new AlertDialog.Builder(this)
                                                .setTitle("삭제 완료")
                                                .setMessage(message)
                                                .setPositiveButton("확인", null)
                                                .show();
                                        Log.d("AdminCourseActivity", "✅ Firestore 삭제 완료: " + deletedCount.get() + "개");
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    failCount.incrementAndGet();
                                    int completed = completedCount.incrementAndGet();
                                    progressDialog.setProgress(completed);
                                    Log.e("AdminCourseActivity", "❌ 문서 삭제 실패: " + document.getId(), e);

                                    if (completed >= totalCount && !isCompleted.getAndSet(true)) {
                                        progressDialog.dismiss();
                                        resetClearButton();
                                        String message = String.format("삭제 완료!\n성공: %d개\n실패: %d개", deletedCount.get(), failCount.get());
                                        GoogleSignInUtils.showToast(this, "삭제 완료!");
                                        new AlertDialog.Builder(this)
                                                .setTitle("삭제 완료")
                                                .setMessage(message)
                                                .setPositiveButton("확인", null)
                                                .show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    resetClearButton();
                    Log.e("AdminCourseActivity", "❌ Firestore 조회 실패", e);
                    GoogleSignInUtils.showToast(this, "데이터 조회 실패: " + e.getMessage());
                });
    }

    private void resetClearButton() {
        if (clearFirestoreButton != null) {
            clearFirestoreButton.setEnabled(true);
            clearFirestoreButton.setText("데이터 비우기");
        }
    }
}

