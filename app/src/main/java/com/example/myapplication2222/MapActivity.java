package com.example.myapplication2222;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapActivity extends AppCompatActivity implements BeaconConsumer {

    private static final String TAG = "Beacontest";
    private BeaconManager beaconManager;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private double gpsLat = 0.0;
    private double gpsLon = 0.0;

    private List<Beacon> beaconList = new ArrayList<>();
    private CustomView customView;

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final double RSSI_FILTER_THRESHOLD = 1.0;
    private static final int TARGET_MAJOR_VALUE = 10011;
    private static final int TARGET_MINOR_VALUE = 10011;
    private static final double A = -69;
    private static final double N = 2.0;
    private static final double MAX_DISTANCE_INCREASE = 1.0;

    // 비콘 위치를 50x50 단위로 설정
    private static final Map<String, double[]> BEACON_POSITIONS = new HashMap<String, double[]>() {{
        put("beacon1", new double[]{25.0, 50.0});
        put("beacon2", new double[]{0.0, 0.0});
        put("beacon3", new double[]{50.0, 0.0});
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        customView = findViewById(R.id.custom_view);

        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                gpsLat = location.getLatitude();
                gpsLon = location.getLongitude();
                Log.d(TAG, "Location updated: Lat=" + gpsLat + " Lon=" + gpsLon);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showLocationPermissionDialog();
            } else {
                startLocationUpdates();
            }
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
    }

    private void showLocationPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("This app needs location access")
                .setMessage("Please grant location access so this app can detect beacons and your location.")
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dialog -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION))
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier((beacons, region) -> {
            if (beacons.size() > 0) {
                beaconList.clear();
                beaconList.addAll(beacons);
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting ranging", e);
        }
    }

    public void OnButtonClicked(View view) {
        handler.sendEmptyMessage(0);
    }

    private double calculateAverageRSSI(List<Integer> rssiValues) {
        if (rssiValues.isEmpty()) return 0;
        int sum = 0;
        for (int value : rssiValues) {
            sum += value;
        }
        return (double) sum / rssiValues.size();
    }

    private List<Integer> filterRSSIValues(List<Integer> rssiValues, double average, double threshold) {
        List<Integer> filtered = new ArrayList<>();
        for (int value : rssiValues) {
            if (Math.abs(value - average) <= threshold) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private List<Integer> doubleFilterRSSIValues(List<Integer> rssiValues, double threshold) {
        double initialAverage = calculateAverageRSSI(rssiValues);
        List<Integer> filteredOnce = filterRSSIValues(rssiValues, initialAverage, threshold);
        double newAverage = calculateAverageRSSI(filteredOnce);
        return filterRSSIValues(filteredOnce, newAverage, threshold);
    }

    private double calculateDistance(double rssi) {
        if (rssi == 0) {
            return -1.0;
        }
        return Math.pow(10, (A - rssi) / (10 * N));
    }

    private double[] performTrilateration(double[][] positions, double[] distances) {
        double x1 = positions[0][0], y1 = positions[0][1];
        double x2 = positions[1][0], y2 = positions[1][1];
        double x3 = positions[2][0], y3 = positions[2][1];
        double r1 = distances[0], r2 = distances[1], r3 = distances[2];

        double A = 2 * (x2 - x1);
        double B = 2 * (y2 - y1);
        double C = r1 * r1 - r2 * r2 - x1 * x1 + x2 * x2 - y1 * y1 + y2 * y2;
        double D = 2 * (x3 - x2);
        double E = 2 * (y3 - y2);
        double F = r2 * r2 - r3 * r3 - x2 * x2 + x3 * x3 - y2 * y2 + y3 * y3;

        double x = (C * E - F * B) / (E * A - B * D);
        double y = (C * D - A * F) / (B * D - A * E);

        // 변환 비율 적용
        return new double[]{x, y};
    }

    private final Handler handler = new Handler() {
        private final AtomicBoolean hasBeacon = new AtomicBoolean(false);
        private double previousDistance = -1;
        private long lastBeaconTime = System.currentTimeMillis();

        @Override
        public void handleMessage(Message msg) {
            final StringBuilder sb = new StringBuilder();

            hasBeacon.set(false);

            Map<String, List<Integer>> beaconRSSIMap = new HashMap<>();

            boolean foundBeacon = false;

            for (Beacon beacon : beaconList) {
                String address = beacon.getBluetoothAddress();
                int major = beacon.getId2().toInt();
                int minor = beacon.getId3().toInt();
                int rssi = beacon.getRssi();

                if (major == TARGET_MAJOR_VALUE && minor == TARGET_MINOR_VALUE) {
                    foundBeacon = true;

                    if (!beaconRSSIMap.containsKey(address)) {
                        beaconRSSIMap.put(address, new ArrayList<>());
                    }
                    beaconRSSIMap.get(address).add(rssi);
                }
            }

            if (!foundBeacon) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBeaconTime > 1000) {
                    sb.append("No beacons with major 10011 and minor 10011 found.\n");
                }
            } else {
                double[][] positions = new double[3][2];
                double[] distances = new double[3];
                int index = 0;

                for (Map.Entry<String, List<Integer>> entry : beaconRSSIMap.entrySet()) {
                    String address = entry.getKey();
                    List<Integer> rssiValues = entry.getValue();

                    List<Integer> filteredRSSI = doubleFilterRSSIValues(rssiValues, RSSI_FILTER_THRESHOLD);

                    if (!filteredRSSI.isEmpty()) {
                        double distance = calculateDistance(calculateAverageRSSI(filteredRSSI));

                        if (previousDistance != -1 && Math.abs(distance - previousDistance) > MAX_DISTANCE_INCREASE) {
                            continue;
                        }

                        if (BEACON_POSITIONS.containsKey(address)) {
                            positions[index] = BEACON_POSITIONS.get(address);
                            distances[index] = distance;
                            index++;
                        }

                        sb.append("Beacon Bluetooth Id : ").append(address).append("\n");
                        sb.append("Major: ").append(TARGET_MAJOR_VALUE).append(" Minor: ").append(TARGET_MINOR_VALUE).append("\n");
                        sb.append("Distance : ").append(String.format("%.3f", distance)).append("m\n");
                        hasBeacon.set(true);

                        previousDistance = distance;
                    }
                }

                if (index == 1) {
                    positions[1] = BEACON_POSITIONS.get("beacon2");
                    distances[1] = 3.0;
                    index++;

                    positions[2] = BEACON_POSITIONS.get("beacon3");
                    distances[2] = 4.0;
                    index++;
                }

                if (index == 3) {
                    double[] position = performTrilateration(positions, distances);
                    sb.append("\nEstimated Position:\n");
                    sb.append("X: ").append(String.format("%.3f", position[0])).append("m\n");
                    sb.append("Y: ").append(String.format("%.3f", position[1])).append("m\n");

                    runOnUiThread(() -> {
                        float mapWidth = customView.getWidth();
                        float mapHeight = customView.getHeight();
                        // 50x50 맵에서 사용자의 위치를 변환
                        float mapX = (float) (position[0] / 50.0 * mapWidth);
                        float mapY = (float) (position[1] / 50.0 * mapHeight);
                        customView.updateUserPosition(mapX, mapY);
                    });
                }

                lastBeaconTime = System.currentTimeMillis();
            }

            runOnUiThread(() -> {
                TextView textView = findViewById(R.id.TextView);
                if (textView != null) {
                    if (hasBeacon.get()) {
                        textView.setText(sb.toString());
                    } else {
                        textView.setText("No beacons with major 10011 and minor 10011 found.");
                    }
                } else {
                    Log.e(TAG, "TextView is null");
                }
            });

            sendEmptyMessageDelayed(0, 1000);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Fine location permission granted");
                startLocationUpdates();
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Functionality limited")
                .setMessage("Since location access has not been granted, this app will not be able to discover beacons or use location services.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
