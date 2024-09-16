package com.example.myapplication2222;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CustomView extends View {

    private float yellowBeaconX = -1;
    private float yellowBeaconY = -1;
    private float yellowRadius = -1;

    private float redBeaconX = -1;
    private float redBeaconY = -1;
    private float redRadius = -1;

    private float greenBeaconX = -1;
    private float greenBeaconY = -1;
    private float greenRadius = -1;

    private float userX = -1;
    private float userY = -1;

    private Paint yellowBeaconPaint;
    private Paint redBeaconPaint;
    private Paint greenBeaconPaint;
    private Paint radiusPaint;
    private Paint userPaint;

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        yellowBeaconPaint = new Paint();
        yellowBeaconPaint.setColor(Color.YELLOW);
        yellowBeaconPaint.setStyle(Paint.Style.FILL);

        redBeaconPaint = new Paint();
        redBeaconPaint.setColor(Color.RED);
        redBeaconPaint.setStyle(Paint.Style.FILL);

        greenBeaconPaint = new Paint();
        greenBeaconPaint.setColor(Color.GREEN);
        greenBeaconPaint.setStyle(Paint.Style.FILL);

        radiusPaint = new Paint();
        radiusPaint.setColor(Color.BLUE);
        radiusPaint.setStyle(Paint.Style.STROKE);
        radiusPaint.setStrokeWidth(5);

        userPaint = new Paint();
        userPaint.setColor(Color.BLUE);
        userPaint.setStyle(Paint.Style.FILL);
    }

    // 색상 정보를 포함하는 메소드
    public void updateBeaconPosition(String color, float x, float y, float radius) {
        switch (color) {
            case "yellow":
                this.yellowBeaconX = x;
                this.yellowBeaconY = y;
                this.yellowRadius = radius;
                break;
            case "red":
                this.redBeaconX = x;
                this.redBeaconY = y;
                this.redRadius = radius;
                break;
            case "green":
                this.greenBeaconX = x;
                this.greenBeaconY = y;
                this.greenRadius = radius;
                break;
        }
        invalidate(); // 뷰를 다시 그리도록 요청
    }

    // 색상 정보가 없는 경우를 위한 메소드
    public void updateBeaconPosition(float x, float y, float radius) {
        // 기본 색상 설정 (예: yellow)
        updateBeaconPosition("yellow", x, y, radius);
    }

    public void updateUserPosition(float x, float y) {
        this.userX = x;
        this.userY = y;
        invalidate(); // 뷰를 다시 그리도록 요청
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 비콘 위치와 반경이 설정된 경우에만 그리기
        if (yellowBeaconX >= 0 && yellowBeaconY >= 0 && yellowRadius >= 0) {
            canvas.drawCircle(yellowBeaconX, yellowBeaconY, 20, yellowBeaconPaint);
            canvas.drawCircle(yellowBeaconX, yellowBeaconY, yellowRadius, radiusPaint);
        }

        if (redBeaconX >= 0 && redBeaconY >= 0 && redRadius >= 0) {
            canvas.drawCircle(redBeaconX, redBeaconY, 20, redBeaconPaint);
            canvas.drawCircle(redBeaconX, redBeaconY, redRadius, radiusPaint);
        }

        if (greenBeaconX >= 0 && greenBeaconY >= 0 && greenRadius >= 0) {
            canvas.drawCircle(greenBeaconX, greenBeaconY, 20, greenBeaconPaint);
            canvas.drawCircle(greenBeaconX, greenBeaconY, greenRadius, radiusPaint);
        }

        // 사용자 위치가 설정된 경우에만 그리기
        if (userX >= 0 && userY >= 0) {
            canvas.drawCircle(userX, userY, 15, userPaint);
        }
    }
}
