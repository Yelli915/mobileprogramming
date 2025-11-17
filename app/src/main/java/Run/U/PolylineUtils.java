package Run.U;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;

public class PolylineUtils {
    
    public static String encode(List<LatLng> path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        long lastLat = 0;
        long lastLng = 0;
        StringBuilder result = new StringBuilder();

        for (LatLng point : path) {
            long lat = Math.round(point.latitude * 1e5);
            long lng = Math.round(point.longitude * 1e5);

            long dLat = lat - lastLat;
            long dLng = lng - lastLng;

            encodeValue(dLat, result);
            encodeValue(dLng, result);

            lastLat = lat;
            lastLng = lng;
        }

        return result.toString();
    }

    private static void encodeValue(long value, StringBuilder result) {
        value = value < 0 ? ~(value << 1) : value << 1;
        while (value >= 0x20) {
            result.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        result.append((char) (value + 63));
    }

    public static List<LatLng> decode(String encoded) {
        List<LatLng> path = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return path;
        }

        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int result = 1;
            int shift = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63 - 1;
                result += b << shift;
                shift += 5;
            } while (b >= 0x1f);
            lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            result = 1;
            shift = 0;
            do {
                b = encoded.charAt(index++) - 63 - 1;
                result += b << shift;
                shift += 5;
            } while (b >= 0x1f);
            lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            path.add(new LatLng(lat * 1e-5, lng * 1e-5));
        }

        return path;
    }
}

