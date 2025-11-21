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
import com.google.android.gms.maps.model.LatLng;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import java.util.Calendar;
import java.util.Date;
import java.util.Calendar;
import java.util.Date;

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
    private MaterialButton uploadGyeongbokgungRunButton;
    private MaterialButton clearGyeongbokgungButton;
    
    private ExecutorService batchExecutor;
    private volatile boolean isProcessingCancelled = false;

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
        uploadGyeongbokgungRunButton = findViewById(R.id.upload_gyeongbokgung_run_button);
        clearGyeongbokgungButton = findViewById(R.id.clear_gyeongbokgung_button);

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

        if (uploadGyeongbokgungRunButton != null) {
            uploadGyeongbokgungRunButton.setOnClickListener(v -> {
                Log.d("AdminCourseActivity", "경복궁 데이터 업로드 버튼 클릭됨");
                uploadGyeongbokgungRun();
            });
        }

        if (clearGyeongbokgungButton != null) {
            clearGyeongbokgungButton.setOnClickListener(v -> {
                Log.d("AdminCourseActivity", "경복궁 데이터 비우기 버튼 클릭됨");
                clearGyeongbokgungData();
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isProcessingCancelled = true;
        if (batchExecutor != null && !batchExecutor.isShutdown()) {
            batchExecutor.shutdownNow();
        }
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

            // pathEncoded에서 시작/종료 지점 추출
            GeoPoint startMarker = null;
            GeoPoint endMarker = null;
            try {
                List<LatLng> pathPoints = PolylineUtils.decode(pathEncoded);
                if (pathPoints != null && !pathPoints.isEmpty()) {
                    LatLng startPoint = pathPoints.get(0);
                    LatLng endPoint = pathPoints.get(pathPoints.size() - 1);
                    startMarker = new GeoPoint(startPoint.latitude, startPoint.longitude);
                    endMarker = new GeoPoint(endPoint.latitude, endPoint.longitude);
                }
            } catch (Exception e) {
                Log.w("AdminCourseActivity", "경로 디코딩 실패 - startMarker/endMarker 생략", e);
            }

            Map<String, Object> courseData = new HashMap<>();
            courseData.put("name", name);
            courseData.put("description", description);
            courseData.put("totalDistance", distance * 1000.0);
            courseData.put("difficulty", difficulty);
            courseData.put("estimatedTime", estimatedTime * 60);
            courseData.put("pathEncoded", pathEncoded);
            if (startMarker != null) {
                courseData.put("startMarker", startMarker);
            }
            if (endMarker != null) {
                courseData.put("endMarker", endMarker);
            }
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

        Log.d("AdminCourseActivity", "코스 업로드 시작 - 총 " + totalCount + "개 (20개씩 배치 처리)");
        
        isProcessingCancelled = false;
        batchExecutor = Executors.newSingleThreadExecutor();
        
        processCoursesInBatches(apiCourses, adminCreatorId, successCount, skipCount, failCount, 
                completedCount, isCompleted, totalCount, progressDialog, 0);
    }
    
    private void processCoursesInBatches(List<ApiCourseItem> apiCourses, String adminCreatorId,
                                         AtomicInteger successCount, AtomicInteger skipCount, AtomicInteger failCount,
                                         AtomicInteger completedCount, AtomicBoolean isCompleted, int totalCount,
                                         ProgressDialog progressDialog, int batchStartIndex) {
        if (isProcessingCancelled || batchExecutor == null || batchExecutor.isShutdown()) {
            Log.w("AdminCourseActivity", "처리가 취소되었거나 Executor가 종료됨");
            return;
        }
        
        final int BATCH_SIZE = 20;
        int batchEndIndex = Math.min(batchStartIndex + BATCH_SIZE, apiCourses.size());
        List<ApiCourseItem> currentBatch = new ArrayList<>(apiCourses.subList(batchStartIndex, batchEndIndex));
        
        Log.d("AdminCourseActivity", String.format("배치 처리 시작: %d~%d번째 코스 (%d개)", 
                batchStartIndex + 1, batchEndIndex, currentBatch.size()));
        
        Handler mainHandler = new Handler(Looper.getMainLooper());
        
        batchExecutor.execute(() -> {
            if (isProcessingCancelled) {
                return;
            }
            
            processBatchSequentially(currentBatch, adminCreatorId, successCount, skipCount, failCount,
                    completedCount, isCompleted, totalCount, progressDialog, mainHandler, batchStartIndex);
            
            if (!isProcessingCancelled && batchEndIndex < apiCourses.size()) {
                mainHandler.post(() -> {
                    if (!isProcessingCancelled) {
                        processCoursesInBatches(apiCourses, adminCreatorId, successCount, skipCount, failCount,
                                completedCount, isCompleted, totalCount, progressDialog, batchEndIndex);
                    }
                });
            } else if (batchEndIndex >= apiCourses.size()) {
                Log.d("AdminCourseActivity", "모든 배치 처리 완료 - Executor 종료");
                batchExecutor.shutdown();
            }
        });
    }
    
    private void processBatchSequentially(List<ApiCourseItem> batch, String adminCreatorId,
                                          AtomicInteger successCount, AtomicInteger skipCount, AtomicInteger failCount,
                                          AtomicInteger completedCount, AtomicBoolean isCompleted, int totalCount,
                                          ProgressDialog progressDialog, Handler mainHandler, int batchStartIndex) {
        for (int i = 0; i < batch.size(); i++) {
            if (isProcessingCancelled) {
                Log.w("AdminCourseActivity", "처리가 취소되어 배치 처리 중단");
                break;
            }
            
            ApiCourseItem apiCourse = batch.get(i);
            int currentIndex = batchStartIndex + i;
            
            if (apiCourse.getCrsIdx() == null || apiCourse.getCrsIdx().isEmpty()) {
                skipCount.incrementAndGet();
                Log.w("AdminCourseActivity", "crsIdx가 없어 건너뜀: " + apiCourse.getCrsKorNm());
                mainHandler.post(() -> {
                    if (!isProcessingCancelled && progressDialog != null && progressDialog.isShowing()) {
                        updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                        if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                            checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                        }
                    }
                });
                continue;
            }
            
            String pathEncoded = "";
            String gpxUrl = apiCourse.getGpxpath();
            
            if (gpxUrl != null && !gpxUrl.isEmpty()) {
                Log.d("AdminCourseActivity", String.format("GPX 다운로드 중 (%d/%d): %s", 
                        currentIndex + 1, totalCount, apiCourse.getCrsKorNm()));
                
                mainHandler.post(() -> {
                    if (!isProcessingCancelled && progressDialog != null && progressDialog.isShowing()) {
                        updateProgressDialog(progressDialog, currentIndex, totalCount, 
                                String.format("GPX 다운로드 중: %s (%d/%d)", apiCourse.getCrsKorNm(), currentIndex + 1, totalCount));
                    }
                });
                
                String gpxContent = downloadGpxContent(gpxUrl);
                if (gpxContent != null && !gpxContent.isEmpty()) {
                    List<LatLng> points = parseGpxToLatLngList(gpxContent);
                    if (points != null && !points.isEmpty()) {
                        pathEncoded = PolylineUtils.encode(points);
                        Log.d("AdminCourseActivity", String.format("GPX 파싱 완료: %d개 좌표 추출", points.size()));
                    } else {
                        Log.w("AdminCourseActivity", "GPX에서 좌표를 추출할 수 없음: " + apiCourse.getCrsKorNm());
                    }
                } else {
                    Log.w("AdminCourseActivity", "GPX 다운로드 실패: " + apiCourse.getCrsKorNm());
                }
            } else {
                Log.w("AdminCourseActivity", "gpxpath가 없음: " + apiCourse.getCrsKorNm());
            }
            
            final String finalPathEncoded = pathEncoded;
            mainHandler.post(() -> {
                if (!isProcessingCancelled) {
                    uploadCourseWithDuplicateCheck(apiCourse, adminCreatorId, finalPathEncoded, 
                            successCount, skipCount, failCount, completedCount, isCompleted, totalCount, progressDialog);
                }
            });
            
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w("AdminCourseActivity", "스레드가 인터럽트됨");
                break;
            }
        }
    }

    private void uploadCourseWithDuplicateCheck(ApiCourseItem apiCourse, String adminCreatorId, String pathEncoded,
                                                  AtomicInteger successCount, AtomicInteger skipCount, AtomicInteger failCount,
                                                  AtomicInteger completedCount, AtomicBoolean isCompleted, int totalCount, ProgressDialog progressDialog) {
        if (isProcessingCancelled) {
            return;
        }
        
        String crsIdx = apiCourse.getCrsIdx();
        
        firestore.collection("courses")
                .whereEqualTo("crsIdx", crsIdx)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isProcessingCancelled) {
                        return;
                    }
                    
                    Map<String, Object> courseData = convertApiCourseToFirestoreData(apiCourse, adminCreatorId, pathEncoded, querySnapshot.isEmpty());
                    
                    if (!querySnapshot.isEmpty()) {
                        String existingDocId = querySnapshot.getDocuments().get(0).getId();
                        Log.d("AdminCourseActivity", "중복 발견: " + apiCourse.getCrsKorNm() + " (기존 문서 ID: " + existingDocId + ")");
                        
                        firestore.collection("courses").document(existingDocId)
                                .update(courseData)
                                .addOnSuccessListener(aVoid -> {
                                    if (isProcessingCancelled) {
                                        return;
                                    }
                                    successCount.incrementAndGet();
                                    Log.d("AdminCourseActivity", "✅ 코스 업데이트 성공: " + apiCourse.getCrsKorNm());
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    }
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (isProcessingCancelled) {
                                        return;
                                    }
                                    failCount.incrementAndGet();
                                    Log.e("AdminCourseActivity", "❌ 코스 업데이트 실패: " + apiCourse.getCrsKorNm(), e);
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    }
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                });
                    } else {
                        firestore.collection("courses")
                                .add(courseData)
                                .addOnSuccessListener(documentReference -> {
                                    if (isProcessingCancelled) {
                                        return;
                                    }
                                    successCount.incrementAndGet();
                                    Log.d("AdminCourseActivity", "✅ 코스 업로드 성공: " + apiCourse.getCrsKorNm() + " (ID: " + documentReference.getId() + ")");
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    }
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (isProcessingCancelled) {
                                        return;
                                    }
                                    failCount.incrementAndGet();
                                    Log.e("AdminCourseActivity", "❌ 코스 업로드 실패: " + apiCourse.getCrsKorNm(), e);
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                                    }
                                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isProcessingCancelled) {
                        return;
                    }
                    failCount.incrementAndGet();
                    Log.e("AdminCourseActivity", "❌ 중복 체크 실패: " + apiCourse.getCrsKorNm(), e);
                    if (progressDialog != null && progressDialog.isShowing()) {
                        updateProgressDialog(progressDialog, completedCount.incrementAndGet(), totalCount);
                    }
                    if (completedCount.get() >= totalCount && !isCompleted.getAndSet(true)) {
                        checkUploadComplete(totalCount, successCount, skipCount, failCount, progressDialog);
                    }
                });
    }


    private void updateProgressDialog(ProgressDialog progressDialog, int completedCount, int totalCount) {
        updateProgressDialog(progressDialog, completedCount, totalCount, null);
    }
    
    private void updateProgressDialog(ProgressDialog progressDialog, int completedCount, int totalCount, String detailMessage) {
        if (progressDialog != null && progressDialog.isShowing()) {
            int progress = completedCount;
            progressDialog.setProgress(progress);
            double percent = (double) progress / totalCount * 100;
            String message = detailMessage != null 
                    ? String.format("%s (%d/%d, %.0f%%)", detailMessage, progress, totalCount, percent)
                    : String.format("코스를 업로드하는 중... (%d/%d, %.0f%%)", progress, totalCount, percent);
            progressDialog.setMessage(message);
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

    private Map<String, Object> convertApiCourseToFirestoreData(ApiCourseItem apiCourse, String adminCreatorId, String pathEncoded, boolean isNewDocument) {
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

        courseData.put("pathEncoded", pathEncoded != null ? pathEncoded : "");
        courseData.put("adminCreatorId", adminCreatorId);
        
        if (isNewDocument) {
            courseData.put("createdAt", FieldValue.serverTimestamp());
        }
        
        courseData.put("crsIdx", apiCourse.getCrsIdx());
        
        if (apiCourse.getGpxpath() != null && !apiCourse.getGpxpath().isEmpty()) {
            courseData.put("gpxUrl", apiCourse.getGpxpath());
        }

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
    
    private String downloadGpxContent(String gpxUrlString) {
        if (gpxUrlString == null || gpxUrlString.isEmpty()) {
            Log.w("AdminCourseActivity", "GPX URL이 비어있음");
            return null;
        }
        
        StringBuilder result = new StringBuilder();
        try {
            URL url = new URL(gpxUrlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "RunningApp/1.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
                reader.close();
                conn.disconnect();
                return result.toString();
            } else {
                Log.w("AdminCourseActivity", "GPX 다운로드 실패: HTTP " + responseCode + " - " + gpxUrlString);
                conn.disconnect();
                return null;
            }
        } catch (Exception e) {
            Log.e("AdminCourseActivity", "GPX 다운로드 중 오류: " + gpxUrlString, e);
            return null;
        }
    }
    
    private List<LatLng> parseGpxToLatLngList(String gpxXmlString) {
        List<LatLng> points = new ArrayList<>();
        if (gpxXmlString == null || gpxXmlString.isEmpty()) {
            return points;
        }
        
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(gpxXmlString));
            
            int eventType = parser.getEventType();
            double lat = 0, lon = 0;
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                
                if (eventType == XmlPullParser.START_TAG && "trkpt".equals(tagName)) {
                    String latStr = parser.getAttributeValue(null, "lat");
                    String lonStr = parser.getAttributeValue(null, "lon");
                    
                    if (latStr != null && lonStr != null) {
                        try {
                            lat = Double.parseDouble(latStr);
                            lon = Double.parseDouble(lonStr);
                            points.add(new LatLng(lat, lon));
                        } catch (NumberFormatException e) {
                            Log.w("AdminCourseActivity", "좌표 파싱 실패: lat=" + latStr + ", lon=" + lonStr);
                        }
                    }
                }
                
                eventType = parser.next();
            }
            
            Log.d("AdminCourseActivity", "GPX 파싱 완료: " + points.size() + "개 좌표 추출");
        } catch (Exception e) {
            Log.e("AdminCourseActivity", "GPX 파싱 중 오류", e);
        }
        
        return points;
    }

    private void uploadGyeongbokgungRun() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            return;
        }

        String userId = currentUser.getUid();
        String adminCreatorId = currentUser.getUid();

        // 경복궁 실제 외곽 경로 하드코딩 (경복궁 주변 도로를 따라가는 실제 러닝 코스)
        // 경복궁 중심 좌표: 37.5796, 126.9770
        // 실제 경복궁 주변 도로를 따라 시계방향으로 한 바퀴 도는 경로
        List<LatLng> gyeongbokgungPath = new ArrayList<>();
        
        // 광화문광장(시작점) -> 세종대로 -> 경복궁 북쪽 -> 인사동 -> 경복궁 남쪽 -> 광화문광장
        // 실제 GPS 좌표 기반 경로
        
        // 1. 광화문광장 (시작점)
        gyeongbokgungPath.add(new LatLng(37.5750, 126.9768));
        
        // 2. 세종대로를 따라 북쪽으로
        gyeongbokgungPath.add(new LatLng(37.5760, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5770, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5780, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5790, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5800, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5810, 126.9768));
        
        // 3. 경복궁 북쪽 도로 (신무문 방향)
        gyeongbokgungPath.add(new LatLng(37.5815, 126.9770));
        gyeongbokgungPath.add(new LatLng(37.5818, 126.9775));
        gyeongbokgungPath.add(new LatLng(37.5820, 126.9780));
        gyeongbokgungPath.add(new LatLng(37.5820, 126.9785));
        
        // 4. 경복궁 동쪽 도로 (건춘문 방향)
        gyeongbokgungPath.add(new LatLng(37.5815, 126.9790));
        gyeongbokgungPath.add(new LatLng(37.5805, 126.9795));
        gyeongbokgungPath.add(new LatLng(37.5795, 126.9800));
        gyeongbokgungPath.add(new LatLng(37.5785, 126.9800));
        gyeongbokgungPath.add(new LatLng(37.5775, 126.9800));
        
        // 5. 경복궁 남쪽 도로 (인사동 방향)
        gyeongbokgungPath.add(new LatLng(37.5765, 126.9795));
        gyeongbokgungPath.add(new LatLng(37.5760, 126.9790));
        gyeongbokgungPath.add(new LatLng(37.5755, 126.9785));
        gyeongbokgungPath.add(new LatLng(37.5752, 126.9780));
        gyeongbokgungPath.add(new LatLng(37.5750, 126.9775));
        
        // 6. 경복궁 서쪽 도로 (영추문 방향)
        gyeongbokgungPath.add(new LatLng(37.5750, 126.9770));
        gyeongbokgungPath.add(new LatLng(37.5750, 126.9765));
        gyeongbokgungPath.add(new LatLng(37.5752, 126.9760));
        gyeongbokgungPath.add(new LatLng(37.5755, 126.9755));
        gyeongbokgungPath.add(new LatLng(37.5760, 126.9750));
        gyeongbokgungPath.add(new LatLng(37.5765, 126.9748));
        gyeongbokgungPath.add(new LatLng(37.5770, 126.9745));
        gyeongbokgungPath.add(new LatLng(37.5775, 126.9743));
        gyeongbokgungPath.add(new LatLng(37.5780, 126.9740));
        
        // 7. 경복궁 북서쪽 도로
        gyeongbokgungPath.add(new LatLng(37.5785, 126.9740));
        gyeongbokgungPath.add(new LatLng(37.5790, 126.9742));
        gyeongbokgungPath.add(new LatLng(37.5795, 126.9745));
        gyeongbokgungPath.add(new LatLng(37.5800, 126.9748));
        gyeongbokgungPath.add(new LatLng(37.5805, 126.9750));
        gyeongbokgungPath.add(new LatLng(37.5810, 126.9752));
        gyeongbokgungPath.add(new LatLng(37.5815, 126.9755));
        gyeongbokgungPath.add(new LatLng(37.5818, 126.9760));
        gyeongbokgungPath.add(new LatLng(37.5820, 126.9765));
        
        // 8. 다시 세종대로로 합류하여 시작점으로 복귀
        gyeongbokgungPath.add(new LatLng(37.5815, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5805, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5795, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5785, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5775, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5765, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5755, 126.9768));
        gyeongbokgungPath.add(new LatLng(37.5750, 126.9768)); // 시작점으로 복귀

        // 경로를 Encoded Polyline으로 변환
        String pathEncoded = PolylineUtils.encode(gyeongbokgungPath);
        
        // 거리 계산 (대략적인 계산)
        double totalDistance = calculatePathDistance(gyeongbokgungPath); // 미터 단위
        long estimatedTime = (long) (totalDistance / 1000.0 * 4.0 * 60); // 4분/km 기준으로 계산 (초 단위)
        
        // 시작/종료 지점
        GeoPoint startMarker = new GeoPoint(gyeongbokgungPath.get(0).latitude, gyeongbokgungPath.get(0).longitude);
        GeoPoint endMarker = new GeoPoint(gyeongbokgungPath.get(gyeongbokgungPath.size() - 1).latitude, 
                                         gyeongbokgungPath.get(gyeongbokgungPath.size() - 1).longitude);

        // 먼저 코스가 있는지 확인
        firestore.collection("courses")
                .whereEqualTo("name", "경복궁 러닝코스")
                .get()
                .addOnCompleteListener(courseCheckTask -> {
                    if (courseCheckTask.isSuccessful() && !courseCheckTask.getResult().isEmpty()) {
                        // 코스가 이미 존재하는 경우
                        QueryDocumentSnapshot existingCourse = (QueryDocumentSnapshot) courseCheckTask.getResult().getDocuments().get(0);
                        String courseId = existingCourse.getId();
                        Log.d("AdminCourseActivity", "기존 경복궁 코스 사용: " + courseId);
                        saveRunRecord(userId, courseId, totalDistance, estimatedTime, pathEncoded, startMarker, endMarker);
                    } else {
                        // 코스가 없는 경우 새로 생성
                        Map<String, Object> courseData = new HashMap<>();
                        courseData.put("name", "경복궁 러닝코스");
                        courseData.put("description", "경복궁 테두리를 도는 러닝 코스입니다. 역사적인 궁궐을 감상하며 운동할 수 있습니다.");
                        courseData.put("totalDistance", totalDistance);
                        courseData.put("difficulty", "medium");
                        courseData.put("estimatedTime", estimatedTime);
                        courseData.put("pathEncoded", pathEncoded);
                        courseData.put("startMarker", startMarker);
                        courseData.put("endMarker", endMarker);
                        courseData.put("adminCreatorId", adminCreatorId);
                        courseData.put("createdAt", FieldValue.serverTimestamp());

                        firestore.collection("courses")
                                .add(courseData)
                                .addOnSuccessListener(documentReference -> {
                                    String courseId = documentReference.getId();
                                    Log.d("AdminCourseActivity", "경복궁 코스 생성 성공: " + courseId);
                                    saveRunRecord(userId, courseId, totalDistance, estimatedTime, pathEncoded, startMarker, endMarker);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("AdminCourseActivity", "경복궁 코스 생성 실패", e);
                                    GoogleSignInUtils.showToast(this, "코스 생성에 실패했습니다: " + e.getMessage());
                                });
                    }
                });
    }

    private double calculatePathDistance(List<LatLng> path) {
        if (path == null || path.size() < 2) {
            return 0;
        }
        
        double totalDistance = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            LatLng point1 = path.get(i);
            LatLng point2 = path.get(i + 1);
            
            // Haversine 공식을 사용한 거리 계산 (미터 단위)
            double lat1 = Math.toRadians(point1.latitude);
            double lat2 = Math.toRadians(point2.latitude);
            double lon1 = Math.toRadians(point1.longitude);
            double lon2 = Math.toRadians(point2.longitude);
            
            double dLat = lat2 - lat1;
            double dLon = lon2 - lon1;
            
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                      Math.cos(lat1) * Math.cos(lat2) *
                      Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            
            double distance = 6371000 * c; // 지구 반지름 6371km를 미터로 변환
            totalDistance += distance;
        }
        
        return totalDistance;
    }

    private void saveRunRecord(String userId, String courseId, double totalDistance, long estimatedTime, 
                              String pathEncoded, GeoPoint startMarker, GeoPoint endMarker) {
        // 이번 주 날짜 계산 (오늘부터 3일 전)
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -3);
        Date startTimeDate = calendar.getTime();
        Date endTimeDate = new Date(startTimeDate.getTime() + estimatedTime * 1000);

        // 평균 페이스 계산 (초/km)
        double totalDistanceKm = totalDistance / 1000.0;
        double averagePaceSeconds = estimatedTime / totalDistanceKm;

        // 러닝 기록 데이터 생성
        Map<String, Object> runData = new HashMap<>();
        runData.put("type", "sketch");
        runData.put("startTime", new Timestamp(startTimeDate));
        runData.put("endTime", new Timestamp(endTimeDate));
        runData.put("totalDistance", totalDistance);
        runData.put("totalTime", estimatedTime);
        runData.put("averagePace", averagePaceSeconds);
        runData.put("pathEncoded", pathEncoded);
        runData.put("courseId", courseId);
        runData.put("startMarker", startMarker);
        runData.put("endMarker", endMarker);
        runData.put("createdAt", FieldValue.serverTimestamp());

        // Firestore에 저장
        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .add(runData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("AdminCourseActivity", "경복궁 러닝 기록 저장 성공: " + documentReference.getId());
                    GoogleSignInUtils.showToast(this, "경복궁 러닝 기록이 추가되었습니다.");
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminCourseActivity", "경복궁 러닝 기록 저장 실패", e);
                    GoogleSignInUtils.showToast(this, "기록 저장에 실패했습니다: " + e.getMessage());
                });
    }

    private void clearGyeongbokgungData() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            GoogleSignInUtils.showToast(this, "로그인이 필요합니다.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("⚠️ 경고")
                .setMessage("경복궁 러닝코스와 관련된 모든 데이터를 삭제하시겠습니까?\n\n- 경복궁 코스\n- 경복궁 코스 관련 러닝 기록\n\n이 작업은 되돌릴 수 없습니다!")
                .setPositiveButton("삭제", (dialog, which) -> {
                    Log.d("AdminCourseActivity", "사용자가 경복궁 데이터 삭제 확인");
                    executeClearGyeongbokgung();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void executeClearGyeongbokgung() {
        clearGyeongbokgungButton.setEnabled(false);
        clearGyeongbokgungButton.setText("삭제 중...");

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("경복궁 데이터를 삭제하는 중...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Log.d("AdminCourseActivity", "경복궁 데이터 삭제 시작");

        // 1. 먼저 경복궁 코스 찾기
        firestore.collection("courses")
                .whereEqualTo("name", "경복궁 러닝코스")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        progressDialog.dismiss();
                        resetClearGyeongbokgungButton();
                        GoogleSignInUtils.showToast(this, "경복궁 코스가 없습니다.");
                        Log.d("AdminCourseActivity", "경복궁 코스 없음");
                        return;
                    }

                    // 경복궁 코스 ID 수집
                    List<String> courseIds = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        courseIds.add(document.getId());
                    }

                    Log.d("AdminCourseActivity", "경복궁 코스 발견: " + courseIds.size() + "개");

                    // 2. 먼저 모든 사용자의 경복궁 관련 러닝 기록 삭제
                    progressDialog.setMessage("경복궁 관련 러닝 기록 삭제 중...");
                    deleteGyeongbokgungRuns(courseIds, () -> {
                        // 3. 러닝 기록 삭제 완료 후 코스 삭제
                        progressDialog.setMessage("경복궁 코스 삭제 중... (" + courseIds.size() + "개)");
                        deleteGyeongbokgungCourses(courseIds, progressDialog);
                    }, progressDialog);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    resetClearGyeongbokgungButton();
                    Log.e("AdminCourseActivity", "❌ 경복궁 코스 조회 실패", e);
                    GoogleSignInUtils.showToast(this, "데이터 조회 실패: " + e.getMessage());
                });
    }

    private void deleteGyeongbokgungCourses(List<String> courseIds, ProgressDialog progressDialog) {
        if (courseIds.isEmpty()) {
            progressDialog.dismiss();
            resetClearGyeongbokgungButton();
            GoogleSignInUtils.showToast(this, "경복궁 데이터 삭제 완료!");
            return;
        }

        AtomicInteger deletedCourseCount = new AtomicInteger(0);
        AtomicInteger totalCourseCount = new AtomicInteger(courseIds.size());
        AtomicInteger completedCourseCount = new AtomicInteger(0);

        for (String courseId : courseIds) {
            // guidePoints 하위 컬렉션 먼저 삭제
            firestore.collection("courses")
                    .document(courseId)
                    .collection("guidePoints")
                    .get()
                    .addOnSuccessListener(guidePointsSnapshot -> {
                        int totalGuidePoints = guidePointsSnapshot.size();
                        
                        if (totalGuidePoints == 0) {
                            // guidePoints가 없으면 코스만 삭제
                            deleteCourseWithCallback(courseId, deletedCourseCount, totalCourseCount, completedCourseCount, progressDialog);
                        } else {
                            // guidePoints 삭제
                            AtomicInteger deletedGuidePoints = new AtomicInteger(0);
                            for (QueryDocumentSnapshot guidePointDoc : guidePointsSnapshot) {
                                firestore.collection("courses")
                                        .document(courseId)
                                        .collection("guidePoints")
                                        .document(guidePointDoc.getId())
                                        .delete()
                                        .addOnSuccessListener(aVoid -> {
                                            if (deletedGuidePoints.incrementAndGet() >= totalGuidePoints) {
                                                // guidePoints 삭제 완료 후 코스 삭제
                                                deleteCourseWithCallback(courseId, deletedCourseCount, totalCourseCount, completedCourseCount, progressDialog);
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("AdminCourseActivity", "guidePoint 삭제 실패: " + guidePointDoc.getId(), e);
                                            if (deletedGuidePoints.incrementAndGet() >= totalGuidePoints) {
                                                deleteCourseWithCallback(courseId, deletedCourseCount, totalCourseCount, completedCourseCount, progressDialog);
                                            }
                                        });
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AdminCourseActivity", "guidePoints 조회 실패: " + courseId, e);
                        // 조회 실패해도 코스는 삭제 시도
                        deleteCourseWithCallback(courseId, deletedCourseCount, totalCourseCount, completedCourseCount, progressDialog);
                    });
        }
    }

    private void deleteCourseWithCallback(String courseId, AtomicInteger deletedCourseCount, AtomicInteger totalCourseCount, 
                                         AtomicInteger completedCourseCount, ProgressDialog progressDialog) {
        firestore.collection("courses")
                .document(courseId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    int deleted = deletedCourseCount.incrementAndGet();
                    int completed = completedCourseCount.incrementAndGet();
                    Log.d("AdminCourseActivity", "경복궁 코스 삭제 완료: " + courseId + " (" + deleted + "/" + totalCourseCount.get() + ")");
                    
                    if (completed >= totalCourseCount.get()) {
                        progressDialog.dismiss();
                        resetClearGyeongbokgungButton();
                        String message = String.format("경복궁 데이터 삭제 완료!\n코스: %d개 삭제", deleted);
                        GoogleSignInUtils.showToast(this, "경복궁 데이터 삭제 완료!");
                        new AlertDialog.Builder(this)
                                .setTitle("삭제 완료")
                                .setMessage(message)
                                .setPositiveButton("확인", null)
                                .show();
                        Log.d("AdminCourseActivity", "✅ 경복궁 데이터 삭제 완료");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminCourseActivity", "경복궁 코스 삭제 실패: " + courseId, e);
                    int completed = completedCourseCount.incrementAndGet();
                    if (completed >= totalCourseCount.get()) {
                        progressDialog.dismiss();
                        resetClearGyeongbokgungButton();
                        GoogleSignInUtils.showToast(this, "경복궁 데이터 삭제 완료!");
                    }
                });
    }

    private void deleteGyeongbokgungRuns(List<String> courseIds, Runnable onComplete, ProgressDialog progressDialog) {
        if (courseIds.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // 모든 사용자 조회
        firestore.collection("users")
                .get()
                .addOnSuccessListener(usersSnapshot -> {
                    AtomicInteger totalRunsDeleted = new AtomicInteger(0);
                    AtomicInteger totalUsersProcessed = new AtomicInteger(0);
                    int totalUsers = usersSnapshot.size();

                    if (totalUsers == 0) {
                        Log.d("AdminCourseActivity", "사용자가 없습니다.");
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        return;
                    }

                    // whereIn은 최대 10개까지만 지원하므로, courseIds가 10개를 넘으면 개별적으로 처리
                    for (QueryDocumentSnapshot userDoc : usersSnapshot) {
                        String userId = userDoc.getId();
                        
                        // courseIds가 10개 이하면 whereIn 사용, 그 이상이면 개별 쿼리
                        if (courseIds.size() <= 10) {
                            deleteRunsForUser(userId, courseIds, totalRunsDeleted, totalUsersProcessed, totalUsers, onComplete);
                        } else {
                            // 10개 이상이면 각 courseId에 대해 개별 쿼리
                            deleteRunsForUserMultipleQueries(userId, courseIds, totalRunsDeleted, totalUsersProcessed, totalUsers, onComplete);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminCourseActivity", "❌ 사용자 조회 실패", e);
                    GoogleSignInUtils.showToast(this, "사용자 조회 실패: " + e.getMessage());
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }

    private void deleteRunsForUser(String userId, List<String> courseIds, AtomicInteger totalRunsDeleted, 
                                   AtomicInteger totalUsersProcessed, int totalUsers, Runnable onComplete) {
        firestore.collection("users")
                .document(userId)
                .collection("runs")
                .whereIn("courseId", courseIds)
                .get()
                .addOnSuccessListener(runsSnapshot -> {
                    int runsCount = runsSnapshot.size();
                    if (runsCount > 0) {
                        AtomicInteger deletedCount = new AtomicInteger(0);
                        for (QueryDocumentSnapshot runDoc : runsSnapshot) {
                            firestore.collection("users")
                                    .document(userId)
                                    .collection("runs")
                                    .document(runDoc.getId())
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        totalRunsDeleted.incrementAndGet();
                                        if (deletedCount.incrementAndGet() >= runsCount) {
                                            checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("AdminCourseActivity", "러닝 기록 삭제 실패: " + runDoc.getId(), e);
                                        if (deletedCount.incrementAndGet() >= runsCount) {
                                            checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                                        }
                                    });
                        }
                    } else {
                        checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminCourseActivity", "러닝 기록 조회 실패: " + userId, e);
                    checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                });
    }

    private void deleteRunsForUserMultipleQueries(String userId, List<String> courseIds, AtomicInteger totalRunsDeleted,
                                                  AtomicInteger totalUsersProcessed, int totalUsers, Runnable onComplete) {
        // courseIds를 10개씩 나누어 처리
        AtomicInteger batchProcessed = new AtomicInteger(0);
        int totalBatches = (courseIds.size() + 9) / 10; // 올림 계산
        
        for (int i = 0; i < courseIds.size(); i += 10) {
            int endIndex = Math.min(i + 10, courseIds.size());
            List<String> batch = courseIds.subList(i, endIndex);
            
            firestore.collection("users")
                    .document(userId)
                    .collection("runs")
                    .whereIn("courseId", batch)
                    .get()
                    .addOnSuccessListener(runsSnapshot -> {
                        int runsCount = runsSnapshot.size();
                        if (runsCount > 0) {
                            AtomicInteger deletedCount = new AtomicInteger(0);
                            for (QueryDocumentSnapshot runDoc : runsSnapshot) {
                                firestore.collection("users")
                                        .document(userId)
                                        .collection("runs")
                                        .document(runDoc.getId())
                                        .delete()
                                        .addOnSuccessListener(aVoid -> {
                                            totalRunsDeleted.incrementAndGet();
                                            if (deletedCount.incrementAndGet() >= runsCount) {
                                                if (batchProcessed.incrementAndGet() >= totalBatches) {
                                                    checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                                                }
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("AdminCourseActivity", "러닝 기록 삭제 실패: " + runDoc.getId(), e);
                                            if (deletedCount.incrementAndGet() >= runsCount) {
                                                if (batchProcessed.incrementAndGet() >= totalBatches) {
                                                    checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                                                }
                                            }
                                        });
                            }
                        } else {
                            if (batchProcessed.incrementAndGet() >= totalBatches) {
                                checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AdminCourseActivity", "러닝 기록 조회 실패: " + userId, e);
                        if (batchProcessed.incrementAndGet() >= totalBatches) {
                            checkAllUsersProcessed(totalUsersProcessed, totalUsers, totalRunsDeleted, onComplete);
                        }
                    });
        }
    }

    private void checkAllUsersProcessed(AtomicInteger totalUsersProcessed, int totalUsers, 
                                       AtomicInteger totalRunsDeleted, Runnable onComplete) {
        int processed = totalUsersProcessed.incrementAndGet();
        if (processed >= totalUsers) {
            Log.d("AdminCourseActivity", "경복궁 관련 러닝 기록 삭제 완료: " + totalRunsDeleted.get() + "개");
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private void resetClearGyeongbokgungButton() {
        if (clearGyeongbokgungButton != null) {
            clearGyeongbokgungButton.setEnabled(true);
            clearGyeongbokgungButton.setText("경복궁 데이터 비우기");
        }
    }
}

