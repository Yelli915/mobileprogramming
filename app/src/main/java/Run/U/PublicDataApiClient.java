package Run.U;

import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class PublicDataApiClient {
    private static final String TAG = "PublicDataApiClient";
    private static final String BASE_URL = "https://apis.data.go.kr/B551011/Durunubi/courseList";
    private static final String SERVICE_KEY = "e8af3401a710d0505d8be509bb0af0a4c70f480de8d55271fb6ea0cae0967aa5";

    public interface ApiCallback {
        void onSuccess(List<ApiCourseItem> courses);
        void onFailure(String error);
    }

    private static class Result {
        final List<ApiCourseItem> courses;
        final String error;

        Result(List<ApiCourseItem> courses, String error) {
            this.courses = courses;
            this.error = error;
        }
    }

    public static void fetchAllCourses(ApiCallback callback) {
        new FetchAllCoursesTask(callback).execute();
    }

    private static class FetchAllCoursesTask extends AsyncTask<Void, Void, Result> {
        private final ApiCallback callback;
        private static final int ROWS_PER_PAGE = 100;

        FetchAllCoursesTask(ApiCallback callback) {
            this.callback = callback;
        }

        @Override
        protected Result doInBackground(Void... voids) {
            long startTime = System.currentTimeMillis();
            List<ApiCourseItem> allCourses = new ArrayList<>();
            
            try {
                Log.d(TAG, "========================================");
                Log.d(TAG, "🚀 모든 코스 가져오기 시작");
                
                int totalCount = 0;
                int currentPage = 1;
                boolean hasMorePages = true;
                
                while (hasMorePages) {
                    StringBuilder urlBuilder = new StringBuilder(BASE_URL);
                    urlBuilder.append("?serviceKey=").append(URLEncoder.encode(SERVICE_KEY, "UTF-8"));
                    urlBuilder.append("&MobileOS=ETC");
                    urlBuilder.append("&MobileApp=RunningApp");
                    urlBuilder.append("&_type=json");
                    urlBuilder.append("&numOfRows=").append(ROWS_PER_PAGE);
                    urlBuilder.append("&pageNo=").append(currentPage);

                    String fullUrl = urlBuilder.toString();
                    Log.d(TAG, "   페이지 " + currentPage + " 요청 중...");
                    
                    URL url = new URL(fullUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-type", "application/json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, "   📡 HTTP 응답 코드: " + responseCode);

                    if (responseCode >= 200 && responseCode <= 300) {
                        BufferedReader rd = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = rd.readLine()) != null) {
                            sb.append(line);
                        }
                        rd.close();
                        conn.disconnect();

                        String responseBody = sb.toString();
                        PageResult pageResult = parsePage(responseBody);
                        
                        if (pageResult.error != null) {
                            return new Result(null, pageResult.error);
                        }
                        
                        if (currentPage == 1) {
                            totalCount = pageResult.totalCount;
                            Log.d(TAG, "   전체 코스 개수: " + totalCount);
                        }
                        
                        allCourses.addAll(pageResult.courses);
                        Log.d(TAG, "   페이지 " + currentPage + " 완료: " + pageResult.courses.size() + "개 (누적: " + allCourses.size() + "개)");
                        
                        int totalPages = (int) Math.ceil((double) totalCount / ROWS_PER_PAGE);
                        if (currentPage >= totalPages || pageResult.courses.isEmpty()) {
                            hasMorePages = false;
                        } else {
                            currentPage++;
                        }
                    } else {
                        String errorMsg = "페이지 " + currentPage + " API 호출 실패: HTTP " + responseCode;
                        Log.e(TAG, "❌ " + errorMsg);
                        conn.disconnect();
                        return new Result(null, errorMsg);
                    }
                }
                
                long elapsedTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, "✅ 모든 코스 가져오기 완료 (" + elapsedTime + "ms)");
                Log.d(TAG, "   총 " + allCourses.size() + "개 코스 수집");
                Log.d(TAG, "========================================");
                
                return new Result(allCourses, null);
            } catch (Exception e) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                Log.e(TAG, "❌ 페이지네이션 중 오류 발생 (" + elapsedTime + "ms)", e);
                Log.e(TAG, "   오류 메시지: " + e.getMessage());
                e.printStackTrace();
                return new Result(null, "네트워크 오류: " + e.getMessage());
            }
        }
        
        private static class PageResult {
            final List<ApiCourseItem> courses;
            final int totalCount;
            final String error;
            
            PageResult(List<ApiCourseItem> courses, int totalCount, String error) {
                this.courses = courses;
                this.totalCount = totalCount;
                this.error = error;
            }
        }
        
        private PageResult parsePage(String jsonString) {
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                JSONObject response = jsonObject.getJSONObject("response");
                JSONObject header = response.getJSONObject("header");
                
                String resultCode = header.optString("resultCode", "N/A");
                String resultMsg = header.optString("resultMsg", "N/A");
                
                if (!"0000".equals(resultCode)) {
                    return new PageResult(null, 0, "API 오류: " + resultMsg);
                }
                
                JSONObject body = response.getJSONObject("body");
                int totalCount = body.optInt("totalCount", 0);
                
                JSONObject items = body.getJSONObject("items");
                JSONArray itemArray;
                
                if (items.has("item") && !items.isNull("item")) {
                    Object itemObj = items.get("item");
                    if (itemObj instanceof JSONArray) {
                        itemArray = items.getJSONArray("item");
                    } else {
                        itemArray = new JSONArray();
                        itemArray.put(items.getJSONObject("item"));
                    }
                } else {
                    itemArray = new JSONArray();
                }

                List<ApiCourseItem> courses = new ArrayList<>();
                for (int i = 0; i < itemArray.length(); i++) {
                    JSONObject item = itemArray.getJSONObject(i);
                    ApiCourseItem course = parseCourseItem(item);
                    if (course != null) {
                        courses.add(course);
                    }
                }

                return new PageResult(courses, totalCount, null);
            } catch (Exception e) {
                Log.e(TAG, "❌ JSON 파싱 오류", e);
                return new PageResult(null, 0, "데이터 파싱 오류: " + e.getMessage());
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            if (result.courses != null) {
                callback.onSuccess(result.courses);
            } else {
                callback.onFailure(result.error);
            }
        }


        private ApiCourseItem parseCourseItem(JSONObject item) {
            try {
                ApiCourseItem course = new ApiCourseItem();
                if (item.has("routeIdx")) course.setRouteIdx(item.getString("routeIdx"));
                if (item.has("crsIdx")) course.setCrsIdx(item.getString("crsIdx"));
                if (item.has("crsKorNm")) course.setCrsKorNm(item.getString("crsKorNm"));
                if (item.has("crsDstnc")) course.setCrsDstnc(item.getString("crsDstnc"));
                if (item.has("crsTotlRqrmHour")) course.setCrsTotlRqrmHour(item.getString("crsTotlRqrmHour"));
                if (item.has("crsLevel")) course.setCrsLevel(item.getString("crsLevel"));
                if (item.has("crsCycle")) course.setCrsCycle(item.getString("crsCycle"));
                if (item.has("crsContents")) course.setCrsContents(item.getString("crsContents"));
                if (item.has("crsSummary")) course.setCrsSummary(item.getString("crsSummary"));
                if (item.has("crsTourInfo")) course.setCrsTourInfo(item.getString("crsTourInfo"));
                if (item.has("travelerinfo")) course.setTravelerinfo(item.getString("travelerinfo"));
                if (item.has("sigun")) course.setSigun(item.getString("sigun"));
                if (item.has("brdDiv")) course.setBrdDiv(item.getString("brdDiv"));
                if (item.has("gpxpath")) course.setGpxpath(item.getString("gpxpath"));
                if (item.has("createdtime")) course.setCreatedtime(item.getString("createdtime"));
                if (item.has("modifiedtime")) course.setModifiedtime(item.getString("modifiedtime"));
                return course;
            } catch (Exception e) {
                Log.e(TAG, "아이템 파싱 오류", e);
                return null;
            }
        }

    }
}

