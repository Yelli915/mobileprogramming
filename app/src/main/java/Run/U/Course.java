package Run.U;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.firestore.GeoPoint;

public class Course {
    private String id;
    private String name;
    private String description;
    private double totalDistance; // meters
    private String difficulty; // easy, medium, hard
    private int estimatedTime; // seconds
    private String pathEncoded; // Encoded Polyline
    private GeoPoint startMarker;
    private GeoPoint endMarker;
    private String adminCreatorId;
    private long createdAt;

    public Course() {
    }

    public Course(String name, double distance, String difficulty, int estimatedTime) {
        this.name = name;
        this.totalDistance = distance * 1000; // km to meters
        this.difficulty = difficulty;
        this.estimatedTime = estimatedTime * 60; // minutes to seconds
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public double getDistance() {
        return totalDistance / 1000.0; // meters to km
    }

    public void setDistance(double distance) {
        this.totalDistance = distance * 1000;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getEstimatedTime() {
        return estimatedTime / 60; // seconds to minutes
    }

    public void setEstimatedTime(int estimatedTime) {
        this.estimatedTime = estimatedTime * 60;
    }

    public int getEstimatedTimeSeconds() {
        return estimatedTime;
    }

    public void setEstimatedTimeSeconds(int estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public String getPathEncoded() {
        return pathEncoded;
    }

    public void setPathEncoded(String pathEncoded) {
        this.pathEncoded = pathEncoded;
    }

    public GeoPoint getStartMarker() {
        return startMarker;
    }

    public void setStartMarker(GeoPoint startMarker) {
        this.startMarker = startMarker;
    }

    public LatLng getStartMarkerLatLng() {
        return GoogleSignInUtils.toLatLng(startMarker);
    }

    public GeoPoint getEndMarker() {
        return endMarker;
    }

    public void setEndMarker(GeoPoint endMarker) {
        this.endMarker = endMarker;
    }

    public LatLng getEndMarkerLatLng() {
        return GoogleSignInUtils.toLatLng(endMarker);
    }

    public String getAdminCreatorId() {
        return adminCreatorId;
    }

    public void setAdminCreatorId(String adminCreatorId) {
        this.adminCreatorId = adminCreatorId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getDifficultyKorean() {
        if (difficulty == null) {
            return "";
        }
        switch (difficulty.toLowerCase()) {
            case "easy":
                return "초급";
            case "medium":
                return "중급";
            case "hard":
                return "고급";
            default:
                return difficulty;
        }
    }

    public String getDistanceFormatted() {
        return String.format("%.1fkm", getDistance());
    }

    public String getEstimatedTimeFormatted() {
        return getEstimatedTime() + "분";
    }
}

