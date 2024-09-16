package com.example.myapplication2222;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
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
import android.widget.Button;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

// 이동 평균 계산을 위한 클래스
class MovingAverage {
    private final int windowSize;
    private final List<Integer> values;
    private int sum;

    public MovingAverage(int windowSize) {
        this.windowSize = windowSize;
        this.values = new ArrayList<>(windowSize);
        this.sum = 0;
    }

    public double addValue(int value) {
        if (values.size() == windowSize) {
            sum -= values.remove(0);
        }
        values.add(value);
        sum += value;
        return (double) sum / values.size();
    }
}

// 가우시안 필터 클래스
class GaussianFilter {
    private final int windowSize;
    private final List<Double> values;
    private final double[] kernel;

    public GaussianFilter(int windowSize) {
        this.windowSize = windowSize;
        this.values = new ArrayList<>(windowSize);
        this.kernel = createGaussianKernel(windowSize);
    }

    private double[] createGaussianKernel(int size) {
        double[] kernel = new double[size];
        double sigma = size / 6.0;
        double mean = size / 2.0;
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            double x = i - mean;
            kernel[i] = Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += kernel[i];
        }
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    public double applyFilter(double newValue) {
        if (values.size() == windowSize) {
            values.remove(0);
        }
        values.add(newValue);
        double result = 0.0;
        int n = values.size();
        for (int i = 0; i < n; i++) {
            result += values.get(i) * kernel[i];
        }
        return result;
    }
}

public class MapActivity extends AppCompatActivity implements BeaconConsumer {

    private static final String TAG = "Beacontest";
    private BeaconManager beaconManager;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location currentLocation; // 현재 GPS 위치
    private List<Beacon> beaconList = new ArrayList<>();
    private CustomView customView;

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final double RSSI_FILTER_THRESHOLD = 1.0; // RSSI 값 차이 허용 임계값
    private static final int TARGET_MAJOR_VALUE = 10011;
    private static final double A = -70; // RSSI 상수
    private static final double N = 2.0; // 거리 감쇠 지수
    private static final int MOVING_AVERAGE_WINDOW_SIZE = 10; // 이동 평균 윈도우 크기
    private static final int GAUSSIAN_FILTER_WINDOW_SIZE = 10; // 가우시안 필터 윈도우 크기
    private static final long SAMPLE_INTERVAL_MS = 1000; // 샘플링 간격 (1초)
    private static final double DISTANCE_VARIATION_THRESHOLD = 1.0; // 거리 차이 허용 임계값 (미터)

    private MovingAverage movingAverage; // 이동 평균 인스턴스
    private GaussianFilter gaussianFilter; // 가우시안 필터 인스턴스

    private Button runButton;  // 버튼 추가

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
                currentLocation = location; // GPS 위치 업데이트
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

        movingAverage = new MovingAverage(MOVING_AVERAGE_WINDOW_SIZE); // 이동 평균 초기화
        gaussianFilter = new GaussianFilter(GAUSSIAN_FILTER_WINDOW_SIZE); // 가우시안 필터 초기화

        // Button을 레이아웃에서 찾고, 클릭 리스너 설정
        runButton = findViewById(R.id.button);  // Button의 ID는 activity_map.xml에서 설정된 ID와 일치해야 합니다.
        runButton.setOnClickListener(v -> {
            // 버튼 클릭 시 비콘 탐지 시작
            handler.sendEmptyMessage(0);
        });
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

    private double calculateAverageRSSI(List<Double> rssiValues) {
        if (rssiValues.isEmpty()) return 0;
        double sum = 0;
        for (double value : rssiValues) {
            sum += value;
        }
        return sum / rssiValues.size();
    }

    private List<Double> filterRSSIValues(List<Double> rssiValues, double average, double threshold) {
        List<Double> filtered = new ArrayList<>();
        for (double value : rssiValues) {
            if (Math.abs(value - average) <= threshold) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private List<Double> doubleFilterRSSIValues(List<Double> rssiValues, double threshold) {
        double initialAverage = calculateAverageRSSI(rssiValues);
        List<Double> filteredOnce = filterRSSIValues(rssiValues, initialAverage, threshold);
        double newAverage = calculateAverageRSSI(filteredOnce);
        return filterRSSIValues(filteredOnce, newAverage, threshold);
    }

    private double calculateDistance(double rssi) {
        if (rssi == 0) {
            return -1.0;
        }
        return Math.pow(10, (A - rssi) / (10 * N));
    }

    private double filterDistanceVariation(List<Double> distances) {
        if (distances.isEmpty()) return 0;
        double average = calculateAverageRSSI(distances);
        List<Double> filteredDistances = new ArrayList<>();
        for (double distance : distances) {
            if (Math.abs(distance - average) <= DISTANCE_VARIATION_THRESHOLD) {
                filteredDistances.add(distance);
            }
        }
        return calculateAverageRSSI(filteredDistances);
    }

    private Location calculateLocation(List<BeaconDistance> beaconDistances) {
        if (beaconDistances.size() < 3) {
            return null; // 최소 3개의 비콘이 필요함
        }

        // 삼변측량 알고리즘을 사용하여 위치 계산
        double x1 = beaconDistances.get(0).getX();
        double y1 = beaconDistances.get(0).getY();
        double d1 = beaconDistances.get(0).getDistance();

        double x2 = beaconDistances.get(1).getX();
        double y2 = beaconDistances.get(1).getY();
        double d2 = beaconDistances.get(1).getDistance();

        double x3 = beaconDistances.get(2).getX();
        double y3 = beaconDistances.get(2).getY();
        double d3 = beaconDistances.get(2).getDistance();

        double A = 2 * (x2 - x1);
        double B = 2 * (y2 - y1);
        double C = 2 * (x3 - x1);
        double D = 2 * (y3 - y1);

        double E = (d1 * d1 - d2 * d2) - (x1 * x1 - x2 * x2) - (y1 * y1 - y2 * y2);
        double F = (d1 * d1 - d3 * d3) - (x1 * x1 - x3 * x3) - (y1 * y1 - y3 * y3);

        double x = (E - F * B / D) / (A - C * B / D);
        double y = (E - A * x) / B;

        Location location = new Location("calculatedLocation");
        location.setLatitude(x);
        location.setLongitude(y);
        return location;
    }

    private final Handler handler = new Handler() {
        private final AtomicBoolean hasBeacon = new AtomicBoolean(false);
        private long lastBeaconTime = System.currentTimeMillis();

        @Override
        public void handleMessage(Message msg) {
            final StringBuilder sb = new StringBuilder();
            hasBeacon.set(false);

            final Map<String, List<Integer>> beaconRSSIMap = new HashMap<>();
            boolean foundBeacon = false;

            // beaconList를 final로 설정
            final List<Beacon> beaconListLocal = new ArrayList<>(beaconList);

            for (Beacon beacon : beaconListLocal) {
                final String address = beacon.getBluetoothAddress();
                final int major = beacon.getId2().toInt();
                final int minor = beacon.getId3().toInt();  // minor 값을 가져옵니다.
                final int rssi = beacon.getRssi();

                if (major == TARGET_MAJOR_VALUE) {
                    foundBeacon = true;

                    if (!beaconRSSIMap.containsKey(address)) {
                        beaconRSSIMap.put(address, new ArrayList<>());
                    }
                    beaconRSSIMap.get(address).add(rssi);
                }
            }

            if (!foundBeacon) {
                final long currentTime = System.currentTimeMillis();
                if (currentTime - lastBeaconTime > SAMPLE_INTERVAL_MS) {
                    sb.append("No beacons with major 10011 found.\n");
                }
            } else {
                final List<BeaconDistance> beaconDistances = new ArrayList<>();

                for (Map.Entry<String, List<Integer>> entry : beaconRSSIMap.entrySet()) {
                    final String address = entry.getKey();
                    final List<Integer> rssiValues = entry.getValue();

                    // 이동 평균과 가우시안 필터를 적용하여 RSSI 필터링
                    final List<Double> smoothedRSSI = new ArrayList<>();
                    for (int rssi : rssiValues) {
                        final double smoothedValue = movingAverage.addValue(rssi);
                        smoothedRSSI.add(gaussianFilter.applyFilter(smoothedValue));
                    }

                    final List<Double> filteredRSSI = new ArrayList<>(doubleFilterRSSIValues(smoothedRSSI, RSSI_FILTER_THRESHOLD));

                    if (!filteredRSSI.isEmpty()) {
                        final double averageRSSI = calculateAverageRSSI(filteredRSSI);
                        final double distance = calculateDistance(averageRSSI);

                        // 비콘의 위치 및 색상 설정
                        for (Beacon beacon : beaconListLocal) {
                            final int minor = beacon.getId3().toInt(); // minor 값을 가져옵니다.
                            final double beaconX;
                            final double beaconY;
                            final String color;

                            // 비콘의 `minor` 값에 따라 색상 및 위치 설정
                            if (minor == 1) {
                                beaconX = 1;
                                beaconY = 4;
                                color = "red";
                            } else if (minor == 2) {
                                beaconX = 4;
                                beaconY = 4;
                                color = "yellow";
                            } else if (minor == 3) {
                                beaconX = 2.5;
                                beaconY = 1;
                                color = "green";
                            } else {
                                beaconX = 0; // Default
                                beaconY = 0; // Default
                                color = "gray"; // Default color if needed
                            }

                            beaconDistances.add(new BeaconDistance(beaconX, beaconY, distance));

                            sb.append("Beacon Bluetooth Id : ").append(address).append("\n");
                            sb.append("Major: ").append(TARGET_MAJOR_VALUE).append(" Minor: ").append(minor).append("\n");
                            sb.append("Distance : ").append(String.format("%.3f", distance)).append("m\n");
                            hasBeacon.set(true);

                            runOnUiThread(() -> {
                                final float mapWidth = customView.getWidth();
                                final float mapHeight = customView.getHeight();
                                final float beaconXScreen = (float) (beaconX / 5.0 * mapWidth);
                                final float beaconYScreen = (float) (beaconY / 5.0 * mapHeight);
                                final float maxMapDimension = Math.min(mapWidth, mapHeight);
                                final float maxStoreDimension = 5.0f;
                                final float radius = (float) (distance / maxStoreDimension * maxMapDimension);

                                customView.updateBeaconPosition(color, beaconXScreen, beaconYScreen, radius);
                            });
                        }
                    }
                }

                final Location calculatedLocation = calculateLocation(beaconDistances);
                if (calculatedLocation != null) {
                    sb.append("Calculated Location: Latitude: ").append(calculatedLocation.getLatitude()).append(", Longitude: ").append(calculatedLocation.getLongitude()).append("\n");

                    runOnUiThread(() -> {
                        // 비콘의 위치와 색상을 화면에 업데이트
                        for (BeaconDistance beaconDistance : beaconDistances) {
                            customView.updateBeaconPosition("blue", (float) beaconDistance.getX(), (float) beaconDistance.getY(), (float) beaconDistance.getDistance());
                        }

                        // 사용자 위치 업데이트
                        if (calculatedLocation != null) {
                            customView.updateUserPosition((float) calculatedLocation.getLatitude(), (float) calculatedLocation.getLongitude());
                        }
                    });
                }

                lastBeaconTime = System.currentTimeMillis();
            }

            runOnUiThread(() -> {
                final TextView textView = findViewById(R.id.TextView);
                if (textView != null) {
                    if (hasBeacon.get()) {
                        textView.setText(sb.toString());
                    } else {
                        textView.setText("No beacons with major 10011 found.");
                    }
                } else {
                    Log.e(TAG, "TextView is null");
                }
            });

            sendEmptyMessageDelayed(0, SAMPLE_INTERVAL_MS);
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
            .setOnDismissListener(dialog -> {})
            .show();
}

@Override
protected void onResume() {
    super.onResume();
    // handler.sendEmptyMessage(0); // 이제 버튼으로 탐지를 시작하므로, 이 부분은 삭제합니다.
}

@Override
protected void onPause() {
    super.onPause();
    handler.removeMessages(0);
}

// 비콘과 거리 정보를 담는 클래스
private static class BeaconDistance {
    private double x;
    private double y;
    private double distance;

    BeaconDistance(double x, double y, double distance) {
        this.x = x;
        this.y = y;
        this.distance = distance;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getDistance() {
        return distance;
    }
}
}
