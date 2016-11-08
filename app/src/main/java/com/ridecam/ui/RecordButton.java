package com.ridecam.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Button;

public class RecordButton extends Button {

    private Bitmap mIcon;
    private Paint mPaint;
    private Rect mSrcRect;
    private int mIconPadding;
    private int mIconSize;

    public RecordButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    public RecordButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RecordButton(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int shift = (mIconSize / 2) + mIconPadding;

        canvas.save();
        canvas.translate(-shift, 0);

        super.onDraw(canvas);

        if (mIcon != null) {
            float textWidth = getPaint().measureText((String)getText());
            int left = (int)((getWidth() / 2f) + (textWidth / 2f) + (2*mIconPadding));
            int top = getHeight()/2 - mIconSize/2;

            Rect destRect = new Rect(left, top, left + mIconSize, top + mIconSize);
            canvas.drawBitmap(mIcon, mSrcRect, destRect, mPaint);
        }

        canvas.restore();
    }

    private void init(Context context, AttributeSet attrs) {
        mIconPadding = Utils.toPixels(4, context.getResources().getDisplayMetrics());
        mIconSize = Utils.toPixels(24, context.getResources().getDisplayMetrics());
    }

    public void setIcon(Drawable drawable) {
        mIcon = drawableToBitmap(drawable);
        mPaint = new Paint();
        mSrcRect = new Rect(0, 0, mIcon.getWidth(), mIcon.getHeight());
    }

    public static Bitmap drawableToBitmap (Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

}
