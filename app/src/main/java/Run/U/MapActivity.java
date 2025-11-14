package Run.U;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private GoogleMap googleMap;
    private Button btnStart;
    private Button btnPause;
    private Button btnStop;

    private boolean isTracking = false;
    private boolean isPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        initViews();
        setButtonListeners();
        initMap();
    }

    private void initViews() {
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);

        btnStart.setEnabled(true);
        btnPause.setEnabled(false);
        btnStop.setEnabled(false);
    }

    private void setButtonListeners() {
        btnStart.setOnClickListener(v -> startTracking());
        btnPause.setOnClickListener(v -> pauseOrResumeTracking());
        btnStop.setOnClickListener(v -> stopTracking());
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "지도를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "SupportMapFragment is null");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        enableMyLocationIfPermitted();
        moveCameraToDefaultLocation();
    }

    private void enableMyLocationIfPermitted() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void moveCameraToDefaultLocation() {
        if (googleMap == null) {
            return;
        }
        LatLng gwangwoonUniversity = new LatLng(37.620282, 127.058846);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gwangwoonUniversity, 16f));
        googleMap.addMarker(new MarkerOptions()
                .position(gwangwoonUniversity)
                .title("광운대학교"));
    }

    private void startTracking() {
        if (googleMap == null) {
            Toast.makeText(this, "지도가 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        isTracking = true;
        isPaused = false;
        btnStart.setEnabled(false);
        btnPause.setEnabled(true);
        btnPause.setText("일시정지");
        btnStop.setEnabled(true);

        Toast.makeText(this, "운동을 시작합니다.", Toast.LENGTH_SHORT).show();
    }

    private void pauseOrResumeTracking() {
        if (!isTracking) {
            return;
        }

        isPaused = !isPaused;
        if (isPaused) {
            btnPause.setText("재개");
            Toast.makeText(this, "일시정지되었습니다.", Toast.LENGTH_SHORT).show();
        } else {
            btnPause.setText("일시정지");
            Toast.makeText(this, "다시 시작합니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopTracking() {
        if (!isTracking) {
            return;
        }
        isTracking = false;
        isPaused = false;

        btnStart.setEnabled(true);
        btnPause.setEnabled(false);
        btnPause.setText("일시정지");
        btnStop.setEnabled(false);

        Toast.makeText(this, "운동을 종료합니다.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocationIfPermitted();
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
