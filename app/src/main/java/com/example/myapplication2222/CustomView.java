package com.example.myapplication2222;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class CustomView extends View {

    // 비콘 정보 저장을 위한 데이터 클래스
    private static class Beacon {
        float x;
        float y;
        float radius;
        Paint paint; // 비콘 색상 및 스타일을 위한 Paint 객체

        Beacon(float x, float y, float radius, Paint paint) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.paint = paint;
        }
    }

    // 비콘 리스트
    private List<Beacon> beacons = new ArrayList<>();
    private float userX = -1;
    private float userY = -1;

    // 페인트 객체
    private Paint userPaint;

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 비콘 페인트 초기화
        Paint yellowBeaconPaint = new Paint();
        yellowBeaconPaint.setColor(Color.YELLOW);
        yellowBeaconPaint.setStyle(Paint.Style.FILL);

        Paint redBeaconPaint = new Paint();
        redBeaconPaint.setColor(Color.RED);
        redBeaconPaint.setStyle(Paint.Style.FILL);

        Paint greenBeaconPaint = new Paint();
        greenBeaconPaint.setColor(Color.GREEN);
        greenBeaconPaint.setStyle(Paint.Style.FILL);

        // 비콘 추가
        beacons.add(new Beacon(4, 4, 50, yellowBeaconPaint)); // Yellow beacon
        beacons.add(new Beacon(2.5f, 1, 50, greenBeaconPaint)); // Green beacon
        beacons.add(new Beacon(1, 4, 50, redBeaconPaint)); // Red beacon

        // 사용자 페인트 초기화
        userPaint = new Paint();
        userPaint.setColor(Color.BLUE);
        userPaint.setStyle(Paint.Style.FILL);
    }

    // 비콘 위치 업데이트 메소드
    public void updateBeaconPosition(int index, float x, float y, float radius, int color) {
        if (index >= 0 && index < beacons.size()) {
            Beacon beacon = beacons.get(index);
            beacon.x = x;
            beacon.y = y;
            beacon.radius = radius;

            // 비콘 색상 업데이트
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            beacon.paint = paint; // 비콘 색상 업데이트
            invalidate(); // 뷰를 다시 그리도록 요청
        }
    }

    public void updateUserPosition(float x, float y) {
        this.userX = x;
        this.userY = y;
        invalidate(); // 뷰를 다시 그리도록 요청
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 비콘 그리기
        for (Beacon beacon : beacons) {
            // 비콘 원 그리기
            canvas.drawCircle(beacon.x, beacon.y, 20, beacon.paint); // 비콘 원 그리기

            // 비콘 반경 그리기 (선으로)
            Paint radiusPaint = new Paint();
            radiusPaint.setColor(Color.RED); // 반경 색상
            radiusPaint.setStyle(Paint.Style.STROKE); // 선 스타일
            radiusPaint.setStrokeWidth(5); // 선 두께
            canvas.drawCircle(beacon.x, beacon.y, beacon.radius, radiusPaint); // 비콘 반경 그리기
        }

        // 사용자 위치가 설정된 경우에만 그리기
        if (userX >= 0 && userY >= 0) {
            canvas.drawCircle(userX, userY, 15, userPaint); // 사용자 위치를 파란색으로 그리기
        }
    }



    // 사용자 위치를 설정하는 메소드
    public void setUserPosition(double x, double y) {
        // double 값을 float로 변환하여 사용자 위치 업데이트
        updateUserPosition((float) x, (float) y);
    }
}