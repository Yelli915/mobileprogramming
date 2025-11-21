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

    public static void fetchCourses(int numOfRows, String filterRegion, ApiCallback callback) {
        new FetchCoursesTask(numOfRows, filterRegion, callback).execute();
    }

    private static class FetchCoursesTask extends AsyncTask<Void, Void, Result> {
        private final int numOfRows;
        private final String filterRegion;
        private final ApiCallback callback;

        FetchCoursesTask(int numOfRows, String filterRegion, ApiCallback callback) {
            this.numOfRows = numOfRows;
            this.filterRegion = filterRegion;
            this.callback = callback;
        }

        @Override
        protected Result doInBackground(Void... voids) {
            try {
                StringBuilder urlBuilder = new StringBuilder(BASE_URL);
                urlBuilder.append("?serviceKey=").append(URLEncoder.encode(SERVICE_KEY, "UTF-8"));
                urlBuilder.append("&MobileOS=ETC");
                urlBuilder.append("&MobileApp=RunningApp");
                urlBuilder.append("&_type=json");
                urlBuilder.append("&numOfRows=").append(numOfRows);
                urlBuilder.append("&pageNo=1");

                URL url = new URL(urlBuilder.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-type", "application/json");

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300) {
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
                    Log.d(TAG, "API 응답 받음: " + responseBody.substring(0, Math.min(200, responseBody.length())));

                    return parseResponse(responseBody, filterRegion);
                } else {
                    String errorMsg = "API 호출 실패: HTTP " + conn.getResponseCode();
                    Log.e(TAG, errorMsg);
                    return new Result(null, errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "API 호출 중 오류", e);
                return new Result(null, "네트워크 오류: " + e.getMessage());
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

        private Result parseResponse(String jsonString, String filterRegion) {
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                JSONObject response = jsonObject.getJSONObject("response");
                JSONObject body = response.getJSONObject("body");
                JSONObject items = body.getJSONObject("items");

                JSONArray itemArray;
                if (items.has("item")) {
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

                List<ApiCourseItem> allCourses = new ArrayList<>();
                for (int i = 0; i < itemArray.length(); i++) {
                    JSONObject item = itemArray.getJSONObject(i);
                    ApiCourseItem course = parseCourseItem(item);
                    if (course != null) {
                        allCourses.add(course);
                    }
                }

                List<ApiCourseItem> filteredCourses;
                if (filterRegion != null && !filterRegion.trim().isEmpty()) {
                    filteredCourses = filterByRegion(allCourses, filterRegion);
                    Log.d(TAG, String.format("총 %d개 중 %s 지역 필터링 결과: %d개", 
                            allCourses.size(), filterRegion, filteredCourses.size()));
                } else {
                    filteredCourses = allCourses;
                }

                return new Result(filteredCourses, null);
            } catch (Exception e) {
                Log.e(TAG, "JSON 파싱 오류", e);
                return new Result(null, "데이터 파싱 오류: " + e.getMessage());
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

        private List<ApiCourseItem> filterByRegion(List<ApiCourseItem> courses, String region) {
            List<ApiCourseItem> filtered = new ArrayList<>();
            for (ApiCourseItem course : courses) {
                String sigun = course.getSigun();
                if (sigun != null && sigun.contains(region)) {
                    filtered.add(course);
                }
            }
            return filtered;
        }
    }
}

