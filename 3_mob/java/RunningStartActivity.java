package com.example.mob_3_start;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public class RunningStartActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 2000L; // 2 seconds

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;

    // Views
    private TextView timerTextView;
    private TextView distanceTextView;
    private TextView averagePaceTextView;
    private TextView instantPaceTextView;
    private Button pauseButton;
    private Button endRunButton;
    private Button startTimerButton;

    // Running state
    private boolean isRunning = false;
    private boolean isPaused = false;
    private long startTime = 0;
    private long pausedTime = 0;
    private long totalPausedTime = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable = null;

    // Distance tracking
    private double totalDistance = 0.0;
    private Location lastLocation = null;
    private Handler locationUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable locationUpdateRunnable = null;

    // Pace calculation
    private double averagePace = 0.0;
    private double instantPace = 0.0;

    private DecimalFormat decimalFormat = new DecimalFormat("0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        timerTextView = findViewById(R.id.timerTextView);
        distanceTextView = findViewById(R.id.distanceTextView);
        averagePaceTextView = findViewById(R.id.averagePaceTextView);
        instantPaceTextView = findViewById(R.id.instantPaceTextView);
        pauseButton = findViewById(R.id.pauseButton);
        endRunButton = findViewById(R.id.endRunButton);
        startTimerButton = findViewById(R.id.startTimerButton);

        // Set up back button
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize map
        FragmentManager fragmentManager = getSupportFragmentManager();
        SupportMapFragment mapFragment = (SupportMapFragment) fragmentManager.findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Button click listeners
        pauseButton.setOnClickListener(v -> togglePause());

        endRunButton.setOnClickListener(v -> endRun());

        startTimerButton.setOnClickListener(v -> startRunning());

        // Request location permissions
        if (checkLocationPermission()) {
            requestLocationUpdates();
        } else {
            requestLocationPermission();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        // Enable my location button if permission granted
        if (checkLocationPermission()) {
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
            getCurrentLocation();
        }
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (map != null) {
                    map.setMyLocationEnabled(true);
                    map.getUiSettings().setMyLocationButtonEnabled(true);
                }
                getCurrentLocation();
                requestLocationUpdates();
            }
        }
    }

    private void getCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        map.addMarker(new MarkerOptions().position(currentLatLng).title("현재 위치"));
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                        lastLocation = location;
                    }
                }
            });
        }
    }

    private void requestLocationUpdates() {
        if (!checkLocationPermission()) return;

        locationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                updateDistance(location);
                                lastLocation = location;
                            }
                        }
                    });
                }
                locationUpdateHandler.postDelayed(this, LOCATION_UPDATE_INTERVAL);
            }
        };
        locationUpdateHandler.post(locationUpdateRunnable);
    }

    private void updateDistance(Location newLocation) {
        if (lastLocation != null) {
            double distance = lastLocation.distanceTo(newLocation) / 1000.0; // Convert to km
            totalDistance += distance;
            distanceTextView.setText(decimalFormat.format(totalDistance) + " km");

            // Update map marker
            LatLng currentLatLng = new LatLng(newLocation.getLatitude(), newLocation.getLongitude());
            map.clear();
            map.addMarker(new MarkerOptions().position(currentLatLng).title("현재 위치"));
            map.moveCamera(CameraUpdateFactory.newLatLng(currentLatLng));
        }
    }

    private void startRunning() {
        if (!isRunning) {
            isRunning = true;
            isPaused = false;
            startTime = System.currentTimeMillis();
            totalPausedTime = 0;
            totalDistance = 0.0;
            lastLocation = null;

            // Reset displays
            distanceTextView.setText("0.00 km");
            averagePaceTextView.setText("--:--/km");
            instantPaceTextView.setText("--:--/km");

            // Start timer
            startTimer();

            // Start location updates
            getCurrentLocation();

            // Update button states
            startTimerButton.setEnabled(false);
            pauseButton.setEnabled(true);
        }
    }

    private void togglePause() {
        if (isRunning) {
            if (isPaused) {
                // Resume
                isPaused = false;
                totalPausedTime += (System.currentTimeMillis() - pausedTime);
                pauseButton.setText("일시 정지");
                startTimer();
            } else {
                // Pause
                isPaused = true;
                pausedTime = System.currentTimeMillis();
                pauseButton.setText("재개");
                stopTimer();
            }
        }
    }

    private void endRun() {
        isRunning = false;
        isPaused = false;
        stopTimer();
        stopLocationUpdates();

        // Reset button states
        startTimerButton.setEnabled(true);
        pauseButton.setEnabled(false);
        pauseButton.setText("일시 정지");

        // Reset values
        timerTextView.setText("00:00:00");
        distanceTextView.setText("0.00 km");
        averagePaceTextView.setText("--:--/km");
        instantPaceTextView.setText("--:--/km");
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    long elapsedTime = System.currentTimeMillis() - startTime - totalPausedTime;
                    updateTimerDisplay(elapsedTime);
                    updatePace(elapsedTime);
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
        }
    }

    private void updateTimerDisplay(long elapsedTime) {
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60;

        timerTextView.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void updatePace(long elapsedTime) {
        if (totalDistance > 0) {
            double elapsedHours = elapsedTime / 3600000.0;
            averagePace = totalDistance / elapsedHours; // km/h

            // Convert to min/km format
            double averagePaceMinPerKm = 60.0 / averagePace;
            int avgMinutes = (int) averagePaceMinPerKm;
            int avgSeconds = (int) ((averagePaceMinPerKm - avgMinutes) * 60);
            averagePaceTextView.setText(String.format("%02d:%02d/km", avgMinutes, avgSeconds));

            // Calculate instant pace (using last location update)
            if (lastLocation != null) {
                // This is a simplified instant pace calculation
                // In a real app, you'd track speed from GPS
                instantPace = averagePace;
                double instantPaceMinPerKm = 60.0 / instantPace;
                int instMinutes = (int) instantPaceMinPerKm;
                int instSeconds = (int) ((instantPaceMinPerKm - instMinutes) * 60);
                instantPaceTextView.setText(String.format("%02d:%02d/km", instMinutes, instSeconds));
            }
        }
    }

    private void stopLocationUpdates() {
        if (locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        stopLocationUpdates();
    }
}

