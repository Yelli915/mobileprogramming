package Run.U;

import com.google.firebase.firestore.GeoPoint;

public class GuidePoint {
    private String id;
    private GeoPoint location;
    private String message;
    private int order;

    public GuidePoint() {
        // Firestore용 빈 생성자
    }

    public GuidePoint(String id, GeoPoint location, String message, int order) {
        this.id = id;
        this.location = location;
        this.message = message;
        this.order = order;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
