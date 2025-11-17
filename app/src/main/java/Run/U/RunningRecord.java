package Run.U;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.firestore.GeoPoint;
import java.util.ArrayList;
import java.util.List;

public class RunningRecord {
    private String id;
    private String date;
    private String distance;
    private String runningType;
    private String time;
    private String averagePace;
    private double totalDistanceKm;
    private long elapsedTimeMs;
    private String pathEncoded; // Encoded Polyline
    private List<GeoPoint> routePoints; // 경로 좌표 리스트
    private String userId;
    private String courseId; // 코스 기반 러닝인 경우
    private long createdAt;

    public RunningRecord() {
        this.routePoints = new ArrayList<>();
    }

    public RunningRecord(String date, String distance, String runningType, String time, String averagePace) {
        this.date = date;
        this.distance = distance;
        this.runningType = runningType;
        this.time = time;
        this.averagePace = averagePace;
        this.routePoints = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public String getRunningType() {
        return runningType;
    }

    public void setRunningType(String runningType) {
        this.runningType = runningType;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getAveragePace() {
        return averagePace;
    }

    public void setAveragePace(String averagePace) {
        this.averagePace = averagePace;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public void setElapsedTimeMs(long elapsedTimeMs) {
        this.elapsedTimeMs = elapsedTimeMs;
    }

    public String getPathEncoded() {
        return pathEncoded;
    }

    public void setPathEncoded(String pathEncoded) {
        this.pathEncoded = pathEncoded;
    }

    public List<GeoPoint> getRoutePoints() {
        return routePoints;
    }

    public void setRoutePoints(List<GeoPoint> routePoints) {
        this.routePoints = routePoints;
    }

    public List<LatLng> getRoutePointsLatLng() {
        return GoogleSignInUtils.toLatLngList(routePoints);
    }

    public void addRoutePoint(GeoPoint point) {
        if (routePoints == null) {
            routePoints = new ArrayList<>();
        }
        routePoints.add(point);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    // 포맷팅 메서드 (Course와 일관성 유지)
    public String getDistanceFormatted() {
        if (totalDistanceKm > 0) {
            return GoogleSignInUtils.formatDistanceKm(totalDistanceKm);
        }
        return distance != null ? distance : "0.0km";
    }

    public String getTimeFormatted() {
        if (elapsedTimeMs > 0) {
            return GoogleSignInUtils.formatElapsedTimeShort(elapsedTimeMs);
        }
        return time != null ? time : "--:--";
    }

    public String getPaceFormatted() {
        return averagePace != null ? averagePace : "--:--/km";
    }
}

