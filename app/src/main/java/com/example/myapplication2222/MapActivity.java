package com.example.myapplication2222;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
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

    private List<Beacon> beaconList = new ArrayList<>();
    private TextView textView;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final double RSSI_FILTER_THRESHOLD = 1.0; // RSSI 필터 임계값
    private static final int TARGET_MAJOR_VALUE = 10011; // 필터링할 Major 값
    private static final int TARGET_MINOR_VALUE = 10011; // 필터링할 Minor 값
    private static final double A = -69; // 1미터 거리에서의 RSSI 값 (환경에 따라 변경 필요)
    private static final double N = 2.0; // 신호 전파 손실 계수 (환경에 따라 변경 필요)
    private static final double MAX_DISTANCE_INCREASE = 1.0; // 거리의 급격한 증가를 감지하는 임계값

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        textView = findViewById(R.id.Textview);

        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showLocationPermissionDialog();
            }
        }
    }

    private void showLocationPermissionDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("This app needs location access");
        builder.setMessage("Please grant location access so this app can detect beacons.");
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        });
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    beaconList.clear();
                    beaconList.addAll(beacons);
                }
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

    // Calculate average RSSI
    private double calculateAverageRSSI(List<Integer> rssiValues) {
        if (rssiValues.isEmpty()) return 0;
        int sum = 0;
        for (int value : rssiValues) {
            sum += value;
        }
        return (double) sum / rssiValues.size();
    }

    // Filter out RSSI values that are out of range
    private List<Integer> filterRSSIValues(List<Integer> rssiValues, double average, double threshold) {
        List<Integer> filtered = new ArrayList<>();
        for (int value : rssiValues) {
            if (Math.abs(value - average) <= threshold) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    // Double-filtering to remove outliers
    private List<Integer> doubleFilterRSSIValues(List<Integer> rssiValues, double threshold) {
        double initialAverage = calculateAverageRSSI(rssiValues);
        List<Integer> filteredOnce = filterRSSIValues(rssiValues, initialAverage, threshold);
        double newAverage = calculateAverageRSSI(filteredOnce);
        return filterRSSIValues(filteredOnce, newAverage, threshold);
    }

    // Distance calculation using RSSI
    private double calculateDistance(double rssi) {
        if (rssi == 0) {
            return -1.0; // signal not found
        }
        // Distance calculation using RSSI, A, and N
        double distance = Math.pow(10, (A - rssi) / (10 * N));
        return distance;
    }

    private final Handler handler = new Handler() {
        private final AtomicBoolean hasBeacon = new AtomicBoolean(false);
        private double previousDistance = -1; // Track previous distance
        private long lastBeaconTime = System.currentTimeMillis(); // Track last time beacons were detected

        @Override
        public void handleMessage(Message msg) {
            final StringBuilder sb = new StringBuilder();

            // Reset hasBeacon to false before checking
            hasBeacon.set(false);

            // Create a map to store RSSI values for each beacon
            Map<String, List<Integer>> beaconRSSIMap = new HashMap<>();

            // Flag to check if any beacon with major 10011 was found
            boolean foundBeacon = false;

            // Iterate through the list of beacons
            for (Beacon beacon : beaconList) {
                String uuid = beacon.getId1().toString();
                int major = beacon.getId2().toInt();
                int minor = beacon.getId3().toInt();
                String address = beacon.getBluetoothAddress();
                int rssi = beacon.getRssi();

                // Only process beacons with major and minor value 10011
                if (major == TARGET_MAJOR_VALUE && minor == TARGET_MINOR_VALUE) {
                    foundBeacon = true;

                    // Collect RSSI values for each beacon
                    if (!beaconRSSIMap.containsKey(address)) {
                        beaconRSSIMap.put(address, new ArrayList<>());
                    }
                    beaconRSSIMap.get(address).add(rssi);
                }
            }

            // If no valid beacons were found, update the last beacon detection time
            if (!foundBeacon) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBeaconTime > 1000) { // 1 second timeout
                    sb.append("No beacons with major 10011 and minor 10011 found.\n");
                }
            } else {
                // Process each beacon to calculate average and filter RSSI values
                for (Map.Entry<String, List<Integer>> entry : beaconRSSIMap.entrySet()) {
                    String address = entry.getKey();
                    List<Integer> rssiValues = entry.getValue();

                    // Double-filtering to remove outliers
                    List<Integer> filteredRSSI = doubleFilterRSSIValues(rssiValues, RSSI_FILTER_THRESHOLD);

                    if (!filteredRSSI.isEmpty()) {
                        // Calculate distance using the filtered RSSI
                        double distance = calculateDistance(calculateAverageRSSI(filteredRSSI));

                        // Check for sudden distance increases
                        if (previousDistance != -1 && Math.abs(distance - previousDistance) > MAX_DISTANCE_INCREASE) {
                            // If sudden increase detected, skip this distance
                            continue;
                        }

                        sb.append("Beacon Bluetooth Id : ").append(address).append("\n");
                        sb.append("Beacon UUID : ").append(entry.getKey()).append("\n");
                        sb.append("Major: ").append(TARGET_MAJOR_VALUE).append(" Minor: ").append(TARGET_MINOR_VALUE).append("\n");
                        sb.append("Distance : ").append(String.format("%.3f", distance)).append("m\n");
                        hasBeacon.set(true);

                        // Update previous distance
                        previousDistance = distance;
                    }
                }

                // Update the last beacon detection time
                lastBeaconTime = System.currentTimeMillis();
            }

            // Ensure the UI update is on the main thread
            runOnUiThread(() -> {
                if (hasBeacon.get()) {
                    textView.setText(sb.toString());
                } else {
                    textView.setText("No beacons with major 10011 and minor 10011 found.");
                }
            });

            // Schedule the handler to call itself again in 1 second
            sendEmptyMessageDelayed(0, 1000);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_COARSE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Coarse location permission granted");
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Functionality limited");
        builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.");
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}
