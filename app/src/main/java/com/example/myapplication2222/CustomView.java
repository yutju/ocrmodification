package com.example.myapplication2222;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CustomView extends View {

    private float userX = -1;
    private float userY = -1;
    private Paint paint;

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
    }

    public void updateUserPosition(float x, float y) {
        this.userX = x;
        this.userY = y;
        invalidate(); // 호출 시 onDraw() 메서드가 호출됨
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (userX >= 0 && userY >= 0) {
            canvas.drawCircle(userX, userY, 20, paint); // 반지름 20
        }
    }
}
