package com.example.myapplication2222;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private Set<String> previousBeaconAddresses = new HashSet<>();

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
            hasBeacon.set(false); // 비콘 발견 여부 초기화
            final Map<String, List<Integer>> beaconRSSIMap = new HashMap<>(); // 비콘 RSSI 값을 저장할 맵
            boolean foundBeacon = false; // 비콘 발견 여부 플래그

            final List<Beacon> beaconListLocal = new ArrayList<>(beaconList); // 비콘 리스트 복사

            for (Beacon beacon : beaconListLocal) {
                int major = beacon.getId2().toInt(); // 비콘의 major 값
                int minor = beacon.getId3().toInt(); // 비콘의 minor 값
                int rssi = beacon.getRssi(); // 비콘의 RSSI 값
                String address = beacon.getBluetoothAddress(); // 비콘의 블루투스 주소

                // 목표 major 값과 특정 minor 값일 경우
                if (major == TARGET_MAJOR_VALUE && (minor == 1 || minor == 2 || minor == 3)) {
                    foundBeacon = true; // 비콘 발견 플래그 설정
                    if (!beaconRSSIMap.containsKey(address)) {
                        beaconRSSIMap.put(address, new ArrayList<>()); // 새로운 비콘 주소 추가
                    }
                    beaconRSSIMap.get(address).add(rssi); // RSSI 값을 맵에 추가
                }
            }

            // 비콘을 발견하지 못한 경우
            if (!foundBeacon) {
                final long currentTime = System.currentTimeMillis();
                if (currentTime - lastBeaconTime > SAMPLE_INTERVAL_MS) {
                    sb.append("Major 10011 비콘을 찾을 수 없습니다.\n"); // 로그 메시지 추가
                }
            } else {
                final List<BeaconDistance> beaconDistances = new ArrayList<>(); // 비콘 거리 리스트

                for (Map.Entry<String, List<Integer>> entry : beaconRSSIMap.entrySet()) {
                    final String address = entry.getKey(); // 비콘 주소
                    final List<Integer> rssiValues = entry.getValue(); // RSSI 값 리스트

                    // RSSI 값의 스무딩 및 필터링
                    final List<Double> smoothedRSSI = new ArrayList<>();
                    for (int rssi : rssiValues) {
                        final double smoothedValue = movingAverage.addValue(rssi); // 이동 평균 추가
                        smoothedRSSI.add(gaussianFilter.applyFilter(smoothedValue)); // 가우시안 필터 적용
                    }

                    final List<Double> filteredRSSI = new ArrayList<>(doubleFilterRSSIValues(smoothedRSSI, RSSI_FILTER_THRESHOLD)); // 필터링된 RSSI 값

                    if (!filteredRSSI.isEmpty()) {
                        final double averageRSSI = calculateAverageRSSI(filteredRSSI); // 평균 RSSI 계산
                        final double distance = calculateDistance(averageRSSI); // 거리 계산

                        Beacon currentBeacon = null;
                        // 현재 비콘 정보를 찾기
                        for (Beacon beacon : beaconListLocal) {
                            if (beacon.getBluetoothAddress().equals(address)) {
                                currentBeacon = beacon;
                                break;
                            }
                        }

                        if (currentBeacon != null) {
                            final int minor = currentBeacon.getId3().toInt(); // 비콘의 minor 값
                            final double beaconX, beaconY; // 비콘의 X, Y 좌표
                            final int color; // 비콘 색상

                            // minor 값에 따라 비콘의 위치와 색상 정의
                            switch (minor) {
                                case 1:
                                    beaconX = 1;
                                    beaconY = 4;
                                    color = Color.RED; // 빨간색
                                    break;
                                case 2:
                                    beaconX = 4;
                                    beaconY = 4;
                                    color = Color.YELLOW; // 노란색
                                    break;
                                case 3:
                                    beaconX = 2.5;
                                    beaconY = 1;
                                    color = Color.GREEN; // 초록색
                                    break;
                                default:
                                    continue; // minor 값이 일치하지 않으면 건너뜀
                            }

                            beaconDistances.add(new BeaconDistance(beaconX, beaconY, distance)); // 비콘 거리 추가

                            sb.append("비콘 블루투스 ID : ").append(address).append("\n") // 로그 메시지 추가
                                    .append("Major: ").append(TARGET_MAJOR_VALUE).append(" Minor: ").append(minor).append("\n")
                                    .append("거리 : ").append(String.format("%.3f", distance)).append("m\n");
                            hasBeacon.set(true); // 비콘 발견 상태 업데이트

                            runOnUiThread(() -> {
                                final float mapWidth = customView.getWidth(); // 맵의 너비
                                final float mapHeight = customView.getHeight(); // 맵의 높이
                                final float beaconXScreen = (float) (beaconX / 5.0 * mapWidth); // 스크린 X 좌표 계산
                                final float beaconYScreen = (float) ((5.0 - beaconY) / 5.0 * mapHeight); // 스크린 Y 좌표 계산
                                final float maxMapDimension = Math.min(mapWidth, mapHeight); // 맵 최대 차원
                                final float maxStoreDimension = 5.0f; // 스토어 최대 차원
                                final float radius = (float) (distance / maxStoreDimension * maxMapDimension); // 비콘의 반지름 계산

                                // 커스텀 뷰에 비콘 위치 업데이트
                                int beaconIndex = beaconDistances.size() - 1; // 현재 비콘에 대해 마지막 인덱스 사용
                                customView.updateBeaconPosition(beaconIndex, beaconXScreen, beaconYScreen, radius, color);
                            });
                        }
                    }
                }

                final Location calculatedLocation = calculateLocation(beaconDistances);
                if (calculatedLocation != null) {
                    sb.append("Calculated Location: Latitude: ").append(calculatedLocation.getLatitude()).append(", Longitude: ").append(calculatedLocation.getLongitude()).append("\n");

                    runOnUiThread(() -> {
                        // 비콘의 위치와 색상을 화면에 업데이트
                        for (int i = 0; i < beaconDistances.size(); i++) {
                            BeaconDistance beaconDistance = beaconDistances.get(i);
                            int beaconIndex = i; // 비콘 인덱스 사용
                            float beaconXScreen = (float) beaconDistance.getX();
                            float beaconYScreen = (float) beaconDistance.getY();
                            float radius = (float) beaconDistance.getDistance();

                            // 색상 정의
                            int color = Color.BLUE; // 예시로 색상을 BLUE로 설정, 필요에 따라 변경

                            // 인덱스를 사용하여 비콘 위치 업데이트
                            customView.updateBeaconPosition(beaconIndex, beaconXScreen, beaconYScreen, radius, color);
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
                startLocationUpdates(); // 위치 권한이 허용되면 업데이트 시작
            } else {
                // 권한이 거부된 경우 사용자에게 알림
                new AlertDialog.Builder(this)
                        .setTitle("Permission Denied")
                        .setMessage("Location access is needed to detect beacons.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
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
        beaconManager.unbind(this);
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