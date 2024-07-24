package com.example.myapplication2222;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.altbeacon.beacon.*;

import java.util.Collection;

public class MapActivity extends AppCompatActivity implements BeaconConsumer {

    private BeaconManager beaconManager;
    private RangeNotifier rangeNotifier; // RangeNotifier 변수 선언

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // RangeNotifier 초기화
        rangeNotifier = new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                boolean beaconDetected = false; // 비콘이 감지되었는지 여부를 나타내는 플래그

                for (Beacon beacon : beacons) {
                    // 비콘의 UUID, Major, Minor 값 비교
                    if (beacon.getId1().toString().equals("d6d6aaaabbbbccccddddeeeeffff0000") &&
                            beacon.getId2().toInt() == 10011 && beacon.getId3().toInt() == 10011) {
                        // 비콘이 감지되고 UUID, Major, Minor 값이 일치하는 경우
                        beaconDetected = true; // 비콘이 감지되었음을 표시
                        runOnUiThread(() -> Toast.makeText(MapActivity.this, "비콘 감지: " + beacon.getId1(), Toast.LENGTH_LONG).show());
                        break; // 일치하는 비콘을 찾았으므로 반복문 종료
                    }
                }

                // 비콘이 감지되지 않은 경우
                if (!beaconDetected) {
                    runOnUiThread(() -> Toast.makeText(MapActivity.this, "비콘을 감지할 수 없습니다.", Toast.LENGTH_LONG).show());
                }
            }
        };

        // BeaconManager 인스턴스 생성
        beaconManager = BeaconManager.getInstanceForApplication(this);

        // iBeacon 포맷 설정
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT));

        // 비콘 서비스 연결
        beaconManager.bind(this);

        // 위치 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(rangeNotifier); // RangeNotifier 추가

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 액티비티 종료 시 BeaconManager의 RangeNotifier 제거
        beaconManager.removeRangeNotifier(rangeNotifier);
        // BeaconManager의 연결 해제
        beaconManager.unbind(this);
    }
}