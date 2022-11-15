package com.panda.a6o6test.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.panda.a6o6test.R;
import com.panda.a6o6test.camera.FaceRectReceiver;
import com.panda.a6o6test.sensors.RotationOrientationListener;
import com.panda.a6o6test.sensors.SensorConstants;

/**
 * A custom View for showing "HUD", reacting to orientation and face detection
 */
public class HudView extends View implements RotationOrientationListener, FaceRectReceiver {

    private Paint paintFace, paintHud, paintBounds;
    private RectF rectFace;
    private Path rotatedPlane, plane, horizon, shiftedHorizon, horizonBounds, rollBounds, shiftedRollBounds;

    public HudView(Context context) {
        super(context);
        init(context);
    }

    public HudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public HudView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // init all paints
    private void init(Context context){
        paintFace = new Paint();
        paintFace.setStyle(Paint.Style.STROKE);
        paintFace.setStrokeWidth(2f);
        paintFace.setColor(context.getColor(R.color.face_box));

        paintHud = new Paint();
        paintHud.setStrokeWidth(3f);
        paintHud.setStyle(Paint.Style.STROKE);
        paintHud.setColor(context.getColor(R.color.hud_dynamic));

        paintBounds = new Paint();
        paintBounds.setStrokeWidth(3f);
        paintBounds.setStyle(Paint.Style.STROKE);
        paintBounds.setColor(context.getColor(R.color.hud_static));

        initPaths(getWidth(), getHeight());

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        initPaths(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initPaths(w, h);
    }

    @Override
    public void setFaceRect(Rect rect, int degrees, int width, int height){
        //TODO this now has to receive the same transformation matrix used in making the preview. Would take additional time.
        if(rect != null){
            this.rectFace = new RectF(rect);
            Matrix matrix = new Matrix();
            matrix.setScale(-1, 1);
            matrix.postRotate(degrees);

            //just guessing the scale - no time for playing with adapting the image sizes
            matrix.postScale(0.6f, 0.6f);

            matrix.postTranslate(width, height);
            matrix.mapRect(this.rectFace);
            matrix.reset();
        }else{
            this.rectFace = null;
        }
    }

    @Override
    public void resetFaceRect() {
        this.rectFace = null;
    }

    // init HUD shapes
    private void initPaths(int x, int y){
        int hx = x/2;
        int hy = y/2;
        plane = new Path();
        plane.moveTo(100, hy);
        plane.lineTo(hx-60,hy);
        plane.lineTo(hx-30,hy+30);
        plane.lineTo(hx,hy);
        plane.lineTo(hx+30,hy+30);
        plane.lineTo(hx+60,hy);
        plane.lineTo(x-100,hy);

        rotatedPlane = new Path(plane);

        horizon = new Path();
        horizon.moveTo(0, hy);
        horizon.lineTo(100, hy);
        horizon.moveTo(x-100, hy);
        horizon.lineTo(x, hy);

        shiftedHorizon = new Path(horizon);

        RectF bounds = new RectF(100, hy-hx+100, x-100, hy+hx-100);
        float pitchTolerance = SensorConstants.PITCH_TOLERANCE_DEG*(getHeight()/180f);
        rollBounds = new Path();
        rollBounds.addArc(bounds, -1 * SensorConstants.ROLL_TOLERANCE_DEG, 2 * SensorConstants.ROLL_TOLERANCE_DEG);

        shiftedRollBounds = new Path(rollBounds);

        horizonBounds = new Path();
        horizonBounds.moveTo(0, hy-pitchTolerance);
        horizonBounds.lineTo(100, hy-pitchTolerance);
        horizonBounds.moveTo(0, hy+pitchTolerance);
        horizonBounds.lineTo(100, hy+pitchTolerance);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(rectFace !=null) {
            canvas.drawRect(rectFace, paintFace);
        }
        canvas.drawPath(horizonBounds, paintBounds);
        canvas.drawPath(shiftedRollBounds, paintBounds);
        canvas.drawPath(rotatedPlane, paintHud);
        canvas.drawPath(shiftedHorizon, paintHud);
        postInvalidate();
    }

    @Override
    public void onOrientationChanged(float pitch, float roll) {
        if(plane == null || horizon == null){
            return;
        }

        float dy = pitch * (getHeight()/180f);
        Matrix rm = new Matrix();
        rm.setRotate(roll, getWidth()/2f, getHeight()/2f);
        rm.postTranslate(0, dy);
        plane.transform(rm, rotatedPlane);
        rm.reset();
        rm.setTranslate(0, dy);
        horizon.transform(rm, shiftedHorizon);
        rollBounds.transform(rm, shiftedRollBounds);
    }
}
