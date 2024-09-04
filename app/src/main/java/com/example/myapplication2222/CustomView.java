package com.example.myapplication2222;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CustomView extends View {

    private float beaconX = -1;
    private float beaconY = -1;
    private float radius = -1;

    private Paint beaconPaint;
    private Paint radiusPaint;

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        beaconPaint = new Paint();
        beaconPaint.setColor(Color.RED);
        beaconPaint.setStyle(Paint.Style.FILL);

        radiusPaint = new Paint();
        radiusPaint.setColor(Color.BLUE);
        radiusPaint.setStyle(Paint.Style.STROKE);
        radiusPaint.setStrokeWidth(5);
    }

    // 비콘 위치와 반경을 업데이트하는 메서드
    public void updateBeaconPosition(float x, float y, float radius) {
        this.beaconX = x;
        this.beaconY = y;
        this.radius = radius;
        invalidate(); // 뷰를 다시 그리도록 요청
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 비콘 위치와 반경이 설정된 경우에만 그리기
        if (beaconX >= 0 && beaconY >= 0 && radius >= 0) {
            // 비콘 위치를 나타내는 점 그리기
            canvas.drawCircle(beaconX, beaconY, 20, beaconPaint); // 비콘 위치 표시 (반지름 20)

            // 비콘 반경 그리기
            canvas.drawCircle(beaconX, beaconY, radius, radiusPaint); // 반경에 따라 원을 그림
        }
    }
}
