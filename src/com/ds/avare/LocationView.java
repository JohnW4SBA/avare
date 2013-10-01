/*
Copyright (c) 2012, Zubair Khan (governer@gmail.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.ds.avare;


import java.util.LinkedList;

import com.ds.avare.gdl90.NexradBitmap;
import com.ds.avare.gps.GpsParams;
import com.ds.avare.hobbsMeter.FlightTimer;
import com.ds.avare.place.Destination;
import com.ds.avare.place.Obstacle;
import com.ds.avare.place.Runway;
import com.ds.avare.position.Coordinate;
import com.ds.avare.position.KMLRecorder;
import com.ds.avare.position.Movement;
import com.ds.avare.position.Origin;
import com.ds.avare.position.Pan;
import com.ds.avare.position.Projection;
import com.ds.avare.position.Scale;
import com.ds.avare.shapes.TFRShape;
import com.ds.avare.shapes.Tile;
import com.ds.avare.storage.DataSource;
import com.ds.avare.storage.Preferences;
import com.ds.avare.touch.GestureInterface;
import com.ds.avare.touch.MultiTouchController;
import com.ds.avare.touch.MultiTouchController.MultiTouchObjectCanvas;
import com.ds.avare.touch.MultiTouchController.PointInfo;
import com.ds.avare.touch.MultiTouchController.PositionAndScale;
import com.ds.avare.utils.BitmapHolder;
import com.ds.avare.utils.Helper;
import com.ds.avare.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * @author zkhan
 * This is a view that user sees 99% of the time. Has moving map on it.
 */
public class LocationView extends View implements MultiTouchObjectCanvas<Object>, OnTouchListener {

    /**
     * paint for onDraw
     */
    private Paint                      mPaint;
    /**
     * Current GPS location
     */
    private GpsParams                  mGpsParams;

    /**
     * The plane on screen
     */
    private BitmapHolder               mAirplaneBitmap;
    private BitmapHolder               mRunwayBitmap;
    private BitmapHolder               mLineBitmap;
    private BitmapHolder               mObstacleBitmap;
    private BitmapHolder               mLineHeadingBitmap;
    
    /**
     * The magic of multi touch
     */
    private MultiTouchController<Object> mMultiTouchC;
    /**
     * The magic of multi touch
     */
    private PointInfo                   mCurrTouchPoint;
    /**
     * Gesture like long press, double touch outside of multi-touch
     */
    private GestureDetector             mGestureDetector;
    /**
     * Cache
     */
    private Context                     mContext;
    /**
     * Current movement from center
     */
    private Movement                    mMovement;
    /**
     * GPS status string if it fails, set by activity
     */
    private String                      mErrorStatus;
    /**
     * For use of text alignment
     */
    private float                       mFontHeight;
   
    /**
     * Task that would draw tiles on bitmap.
     */
    private TileDrawTask                mTileDrawTask; 
    private Thread                      mTileDrawThread;

    /**
     * Task that would draw obstacles
     */
    private ObstacleTask                mObstacleTask; 
    private Thread                      mObstacleThread;

    /**
     * Task that finds closets airport.
     */
    private ClosestAirportTask          mClosestTask; 

    /**
     * Storage service that contains all the state
     */
    private StorageService              mService;

    /**
     * Translation of current pan 
     */
    private Pan                         mPan;
    
    private DataSource                  mImageDataSource;
    
    /**
     * To tell activity to do something on a gesture or touch
     */
    private GestureInterface            mGestureCallBack; 

    /**
     * Scale factor based on pinch zoom
     */
    private Scale                       mScale;
    
    /*
     * A hashmap to load only required tiles.
     */
    
    private Preferences                 mPref;
    
    private float                      mTextDiv;
    
    private TextPaint                   mTextPaint;
    
    private Layout                      mTFRLayout;
    
    private Typeface                    mFace;
    
    private String                      mOnChart;
    
    /**
     * These are longitude and latitude at top left (0,0)
     */
    private Origin                      mOrigin;
    
    /*
     * TFR.
     */
    private int                         mTFRColor;
    
    /*
     * Projection of a touch point
     */
    private Projection                  mPointProjection;
    
    /*
     * Obstacles
     */
    private LinkedList<Obstacle>        mObstacles;
    
    /*
     * Is it drawing?
     */
    private boolean                   mDraw;

    /*
     * Threshold for terrain
     */
    private float                      mThreshold;
    

    private boolean                    mTrackUp;
    
    /*
     * Shadow length 
     */
    private static final int SHADOW = 4;
    
    /**
     * Resolution pixel / lon , lat of center tile
     */
    private double                     mPx;
    private double                     mPy;
    
    private double                     mAdjustPan;
    
    /*
     * Text on screen color
     */
    private static final int TEXT_COLOR = Color.WHITE; 
    private static final int TEXT_COLOR_OPPOSITE = Color.BLACK; 
    
    FlightTimer	mFlightTimer;	// Counter for a flight timer
    KMLRecorder	mKMLRecorder;	// For writing plots to a KML file
    
    /**
     * @param context
     */
    private void setup(Context context) {
        
        /*
         * Set up all graphics.
         */
        mContext = context;
        mPan = new Pan();
        mScale = new Scale();
        mOrigin = new Origin();
        mMovement = new Movement();
        mErrorStatus = null;
        mThreshold = 0;
        mPx = 1;
        mPy = 1;
        mAdjustPan = 1;
        mOnChart = null;
        mTrackUp = false;
        mImageDataSource = null;
        mGpsParams = new GpsParams(null);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mTFRColor = Color.BLACK;
        mPointProjection = null;
        mDraw = false;
        
        mPref = new Preferences(context);
        mTextDiv = mPref.isPortrait() ? 24.f : 15.f;
        
        mFace = Typeface.createFromAsset(mContext.getAssets(), "LiberationMono-Bold.ttf");
        mPaint.setTypeface(mFace);
        mFontHeight = 8; // This is just double of all shadows

        mTextPaint = new TextPaint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTypeface(mFace);

        mTileDrawTask = new TileDrawTask();
        mTileDrawThread = new Thread(mTileDrawTask);
        mTileDrawThread.start();

        mObstacleTask = new ObstacleTask();
        mObstacleThread = new Thread(mObstacleTask);
        mObstacleThread.start();

        setOnTouchListener(this);
        mAirplaneBitmap = new BitmapHolder(context, mPref.isHelicopter() ? R.drawable.heli : R.drawable.plane);
        mLineBitmap = new BitmapHolder(context, R.drawable.line);
        mLineHeadingBitmap = new BitmapHolder(context, R.drawable.line_heading);
        mRunwayBitmap = new BitmapHolder(context, R.drawable.runway_extension);
        mObstacleBitmap = new BitmapHolder(context, R.drawable.obstacle);
        mMultiTouchC = new MultiTouchController<Object>(this);
        mCurrTouchPoint = new PointInfo();
        
        mGestureDetector = new GestureDetector(context, new GestureListener());
        
        mFlightTimer = new FlightTimer(this);
        mKMLRecorder = new KMLRecorder(this);
    }
    
    /**
     * 
     */
    private void tfrReset() {
        mOrigin.update(mGpsParams, mScale, mPan,
                mMovement.getLongitudePerPixel(), mMovement.getLatitudePerPixel(),
                getWidth(), getHeight()); 
    }

    /**
     * @param context
     * Default for tools, do not call
     */
    public LocationView(Context context) {
        super(context);
        setup(context);
    }

    /**
     * @param context
     * Default for tools, do not call
     */
    public LocationView(Context context, AttributeSet aset) {
        super(context, aset);
        setup(context);
    }

    /**
     * @param context
     * Default for tools, do not call
     */
    public LocationView(Context context, AttributeSet aset, int arg) {
        super(context, aset, arg);
        setup(context);
    }

    /* (non-Javadoc)
     * @see android.view.View#onDraw(android.graphics.Canvas)
     */
    @Override
    public void onDraw(Canvas canvas) {
        drawMap(canvas);
    }
       
    /* (non-Javadoc)
     * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
     */
    @Override
    public boolean onTouch(View view, MotionEvent e) {
        if(e.getAction() == MotionEvent.ACTION_UP) {
            /*
             * Do not draw point. Only when long press and down.
             */
            mPointProjection = null;
            mTFRLayout = null;
        }
        mGestureDetector.onTouchEvent(e);
        return mMultiTouchC.onTouchEvent(e);
    }


    /* (non-Javadoc)
     * @see com.ds.avare.MultiTouchController.MultiTouchObjectCanvas#getDraggableObjectAtPoint(com.ds.avare.MultiTouchController.PointInfo)
     */
    public Object getDraggableObjectAtPoint(PointInfo pt) {
        return this;
    }

    /* (non-Javadoc)
     * @see com.ds.avare.MultiTouchController.MultiTouchObjectCanvas#getPositionAndScale(java.lang.Object, com.ds.avare.MultiTouchController.PositionAndScale)
     */
    public void getPositionAndScale(Object obj, PositionAndScale objPosAndScaleOut) {
        objPosAndScaleOut.set(mPan.getMoveX(), mPan.getMoveY(), true,
                mScale.getScaleFactorRaw(), false, 0, 0, false, 0);
    }

    /* (non-Javadoc)
     * @see com.ds.avare.MultiTouchController.MultiTouchObjectCanvas#selectObject(java.lang.Object, com.ds.avare.MultiTouchController.PointInfo)
     */
    public void selectObject(Object obj, PointInfo touchPoint) {
        touchPointChanged(touchPoint);
    }

    /* (non-Javadoc)
     * @see com.ds.avare.MultiTouchController.MultiTouchObjectCanvas#setPositionAndScale(java.lang.Object, com.ds.avare.MultiTouchController.PositionAndScale, com.ds.avare.MultiTouchController.PointInfo)
     */
    public boolean setPositionAndScale(Object obj,PositionAndScale newObjPosAndScale, PointInfo touchPoint) {
        touchPointChanged(touchPoint);
        if(false == mCurrTouchPoint.isMultiTouch()) {
            /*
             * Do nnot
             */
            if(mDraw && (!mTrackUp) && mService != null) {
                float x = mCurrTouchPoint.getX();
                float y = mCurrTouchPoint.getY();
                /*
                 * Threshold the drawing so we do not generate too many points
                 */
                mService.getDraw().addPoint(x, y, mOrigin);
                return true;
            }

            /*
             * XXX: Till track up pan in problematic, freeze it to current location.
             * 
             */
            if(mPan.setMove(
                    mTrackUp ? 
                            mPan.getMoveX() : 
                            newObjPosAndScale.getXOff(),
                    mTrackUp ? 
                            mPan.getMoveY() : 
                            newObjPosAndScale.getYOff())) {
                /*
                 * Query when we have moved one tile. This will happen in background.
                 */
                dbquery(true);
            }
        }
        else {
            /*
             * on double touch find distance and bearing between two points.
             */
            if(!mTrackUp) {
                if(mPointProjection == null) {
                    double x0 = mCurrTouchPoint.getXs()[0];
                    double y0 = mCurrTouchPoint.getYs()[0];
                    double x1 = mCurrTouchPoint.getXs()[1];
                    double y1 = mCurrTouchPoint.getYs()[1];
    
                    double lon0 = mOrigin.getLongitudeOf(x0);
                    double lat0 = mOrigin.getLatitudeOf(y0);
                    double lon1 = mOrigin.getLongitudeOf(x1);
                    double lat1 = mOrigin.getLatitudeOf(y1);
                    mPointProjection = new Projection(lon0, lat0, lon1, lat1);
                }
            }

            /*
             * Clamp scaling.
             */
            
            mScale.setScaleFactor(newObjPosAndScale.getScale());

        }
        tfrReset();
        invalidate();
        return true;
    }
    
    /**
     * @param touchPoint
     */
    private void touchPointChanged(PointInfo touchPoint) {
        mCurrTouchPoint.set(touchPoint);
        invalidate();
    }

    /**
     * 
     * @param force
     */
    private void dbquery(boolean force) {

        if(mService == null) {
            return;
        }
        
        if(mImageDataSource == null) {
            return;
        }
        
        if(null == mService) {
            return;                
        }

        if(!force) {
            double offsets[] = new double[2];
            double p[] = new double[2];
                                        
            if(mImageDataSource.isWithin(mGpsParams.getLongitude(), 
                    mGpsParams.getLatitude(), offsets, p)) {
                /*
                 * We are within same tile no need for query.
                 */
                mMovement = new Movement(offsets, p);
                postInvalidate();
                return;
            }
        }

        /*
         * Find
         */
        mTileDrawTask.lat = mGpsParams.getLatitude();
        mTileDrawTask.lon = mGpsParams.getLongitude();
        mTileDrawThread.interrupt();
    }

    /**
     * This function will rotate and move a bitmap to a given lon/lat on screen
     * @param b
     * @param angle
     * @param lon
     * @param lat
     * @param div Shift the image half way up so it could be centered on y axis
     */
    private void rotateBitmapIntoPlace(BitmapHolder b, float angle, double lon, double lat, boolean div) {
        float x = (float)mOrigin.getOffsetX(lon);
        float y = (float)mOrigin.getOffsetY(lat);                        
                            
        b.getTransform().setTranslate(
                x - b.getWidth() / 2,
                y - (div ? b.getHeight() / 2 : b.getHeight()));
        
        b.getTransform().postRotate(angle, x, y);   
    }
    

    /**
     *
     * @param canvas
     */
    private void drawTiles(Canvas canvas) {
        mPaint.setShadowLayer(0, 0, 0, 0);
  
        if(null != mService) {
            
            for(int tilen = 0; tilen < mService.getTiles().getTilesNum(); tilen++) {
                
                BitmapHolder tile = mService.getTiles().getTile(tilen);
                
                /*
                 * Scale, then move under the plane which is at center
                 */
                boolean nochart = false;
                if(null == tile) {
                    nochart = true;
                }
                else if(null == tile.getBitmap()) {
                    nochart = true;
                }
                if(nochart) {
                    continue;
                }

                if(mPref.isNightMode() && (mPref.getChartType().equals("3") || mPref.getChartType().equals("4"))) {
                    /*
                     * IFR charts invert color at night
                     */
                    Helper.invertCanvasColors(mPaint);
                }
                else if(mPref.getChartType().equals("5")) {
                    /*
                     * Terrain
                     */
                    Helper.setThreshold(mPaint, mThreshold);
                }
                
                /*
                 * Pretty straightforward. Pan and draw individual tiles.
                 */
                tile.getTransform().setScale(mScale.getScaleFactor(), mScale.getScaleCorrected());
                tile.getTransform().postTranslate(
                        getWidth()  / 2.f
                        - BitmapHolder.WIDTH  / 2.f * mScale.getScaleFactor() 
                        + ((tilen % mService.getTiles().getXTilesNum()) * BitmapHolder.WIDTH - BitmapHolder.WIDTH * (int)(mService.getTiles().getXTilesNum() / 2)) * mScale.getScaleFactor()
                        + mPan.getMoveX() * mScale.getScaleFactor()
                        + mPan.getTileMoveX() * BitmapHolder.WIDTH * mScale.getScaleFactor()
                        - (float)mMovement.getOffsetLongitude() * mScale.getScaleFactor(),
                        
                        getHeight() / 2.f 
                        - BitmapHolder.HEIGHT / 2.f * mScale.getScaleCorrected()  
                        + mPan.getMoveY() * mScale.getScaleCorrected()
                        + ((tilen / mService.getTiles().getXTilesNum()) * BitmapHolder.HEIGHT - BitmapHolder.HEIGHT * (int)(mService.getTiles().getYTilesNum() / 2)) * mScale.getScaleCorrected() 
                        + mPan.getTileMoveY() * BitmapHolder.HEIGHT * mScale.getScaleCorrected()
                        - (float)mMovement.getOffsetLatitude() * mScale.getScaleCorrected());
                
                Bitmap b = tile.getBitmap();
                if(null != b) {
                    canvas.drawBitmap(b, tile.getTransform(), mPaint);
                }
                
                Helper.restoreCanvasColors(mPaint);
            }
        }
    }

    /**
     * 
     * @param canvas
     */
    private void drawTFR(Canvas canvas) {
        mPaint.setColor(Color.RED);
        mPaint.setShadowLayer(0, 0, 0, 0);
        
        /*
         * Draw TFRs, TFR
         */            
        LinkedList<TFRShape> shapes = null;
        if(null != mService) {
            shapes = mService.getTFRShapes();
        }
        if(null != shapes) {
            mPaint.setColor(Color.RED);
            mPaint.setStrokeWidth(8);
            mPaint.setShadowLayer(0, 0, 0, 0);
            for(int shape = 0; shape < shapes.size(); shape++) {
                TFRShape cshape = shapes.get(shape);
                if(cshape.isVisible()) {
                    /*
                     * Find offsets of TFR then draw it
                     */
                    cshape.drawShape(canvas, mOrigin, mScale, mMovement, mPaint, mFace);
                }
            }
        }
    }


    /**
     * 
     * @param canvas
     */
    private void drawCornerTexts(Canvas canvas) {

        /*
         * Misc text in the information text location on the view like GPS status,
         * Maps status, and point destination/destination bearing, altitude, ...
         * Add shadows for better viewing
         */
        if(mPref.shouldShowBackground()) {
            mPaint.setShadowLayer(0, 0, 0, 0);
            mPaint.setColor(TEXT_COLOR_OPPOSITE);
            mPaint.setAlpha(0x7f);
            canvas.drawRect(0, 0, getWidth(), getHeight() / mTextDiv * 2 + SHADOW, mPaint);            
            mPaint.setAlpha(0xff);
        }
        mPaint.setShadowLayer(SHADOW, SHADOW, SHADOW, Color.BLACK);
        

        /*
         * Status
         */
        if(mErrorStatus != null) {
            mPaint.setTextAlign(Align.RIGHT);
            mPaint.setColor(Color.RED);
            canvas.drawText(mErrorStatus,
                    getWidth(), getHeight() / mTextDiv * 2, mPaint);
        }
        else {
            
            mPaint.setColor(TEXT_COLOR);

            mPaint.setTextAlign(Align.RIGHT);
            /*
             * Heading, Speed
             */
            canvas.drawText(
                    Helper.makeLine(mGpsParams.getSpeed(), Preferences.speedConversionUnit, null, mGpsParams.getBearing(), mGpsParams.getDeclinition()),
                    getWidth(), getHeight() / mTextDiv * 2, mPaint);
            
        }

        /*
         * Variation
         */
        /*
         * Altitude
         */
        mPaint.setColor(TEXT_COLOR);
        mPaint.setTextAlign(Align.LEFT);
        canvas.drawText(Helper.calculateAltitudeFromThreshold(mThreshold), 0, getHeight() / mTextDiv * 2, mPaint);

        /*
         * Point top right
         */
        mPaint.setColor(TEXT_COLOR);
        if(mPointProjection != null) {
            mPaint.setTextAlign(Align.RIGHT);
            /*
             * Draw distance from point
             */
            canvas.drawText(Helper.makeLine(mPointProjection.getDistance(), Preferences.distanceConversionUnit, "     ", mPointProjection.getBearing(), mGpsParams.getDeclinition()),
                    getWidth(), getHeight() / mTextDiv, mPaint);
            mPaint.setTextAlign(Align.LEFT);
            canvas.drawText(mPointProjection.getGeneralDirectionFrom(mGpsParams.getDeclinition()),
                    0, getHeight() / mTextDiv, mPaint);
        }
        else if(mService != null && mService.getDestination() != null) {
            mPaint.setTextAlign(Align.RIGHT);
            /*
             * Else dest
             */
            canvas.drawText(mService.getDestination().toString(),
                    getWidth(), getHeight() / mTextDiv, mPaint);
            mPaint.setTextAlign(Align.LEFT);
            String name = mService.getDestination().getID();
            if(name.contains("&")) {
                /*
                 * If this string is too long, cut it.
                 */
                name = Destination.GPS;
            }
            else if (name.length() > 8) {
                name = name.substring(0, 7);
            }
            canvas.drawText(name,
                    0, getHeight() / mTextDiv, mPaint);
        }
        /*
         * Chart only when dest not set
         */
        if(null != mOnChart && null != mService && null == mPointProjection) {
            if(null == mService.getDestination()) {
                canvas.drawText(mOnChart, 0, getHeight() / mTextDiv, mPaint);
            }
        }
        
        // If we are configured to use the flight timer, then display its
        // current value top center.
        //
        if(mPref.useFlightTimer()) {
            mPaint.setTextAlign(Align.CENTER);
            if(mFlightTimer.isRunning() == true) {	// If it's running (we are flying)...
            	mPaint.setColor(Color.GREEN);		// ...then paint the text in green
            } else {								// otherwise
            	mPaint.setColor(Color.YELLOW);		// paint in yellow (not flying)
            }
        	canvas.drawText(mFlightTimer.getValue(), getWidth() / 2, getHeight() / mTextDiv, mPaint);
        }
    }

    /**
     * 
     * @param canvas
     */
    private void drawTrack(Canvas canvas) {
        if(null == mService) {
            return;
        }

        if(mService.getDestination() != null && null == mPointProjection) {
            if(mPref.isTrackEnabled() && (!mPref.isSimulationMode())) {
                mPaint.setColor(Color.MAGENTA);
                mPaint.setStrokeWidth(4);
                if(mService.getDestination().isFound() && !mService.getPlan().isActive()) {
                    mService.getDestination().getTrackShape().drawShape(canvas, mOrigin, mScale, mMovement, mPaint, mFace);
                }
                else if (mService.getPlan().isActive()) {
                    mService.getPlan().getTrackShape().drawShape(canvas, mOrigin, mScale, mMovement, mPaint, mFace);                    
                }
            }
            if(!mPref.isSimulationMode()) {
                /*
                 * Draw actual track
                 */
                if(null != mLineBitmap && mGpsParams != null) {
                    rotateBitmapIntoPlace(mLineBitmap, (float)mService.getDestination().getBearing(),
                            mGpsParams.getLongitude(), mGpsParams.getLatitude(), false);
                    canvas.drawBitmap(mLineBitmap.getBitmap(), mLineBitmap.getTransform(), mPaint);
                }
                /*
                 * Draw actual heading
                 */
                if(null != mLineHeadingBitmap && mGpsParams != null) {
                    rotateBitmapIntoPlace(mLineHeadingBitmap, (float)mGpsParams.getBearing(),
                            mGpsParams.getLongitude(), mGpsParams.getLatitude(), false);
                    canvas.drawBitmap(mLineHeadingBitmap.getBitmap(), mLineHeadingBitmap.getTransform(), mPaint);
                }
            }
        }
    }

    /**
     * 
     * @param canvas
     */
    private void drawDrawing(Canvas canvas) {
        if(null == mService) {
            return;
        }

        /*
         * Get draw points.
         */
        mPaint.setColor(Color.BLUE);
        mPaint.setStrokeWidth(8);
        mService.getDraw().drawShape(canvas, mPaint, mOrigin);
        
    }

    /**
     * 
     * @param canvas
     */
    private void drawMETARText(Canvas canvas) {
        /*
         * Draw TFRs, TFR
         */
        /*
         * Write TFR report
         * Use a static layout for showing as overlay and formatted to fit
         */
        float top = getHeight() / mTextDiv * 2 + mFontHeight;
        if(null != mTFRLayout) {
            mPaint.setColor(mTFRColor);
            mPaint.setShadowLayer(SHADOW, SHADOW, SHADOW, Color.BLACK);
            canvas.drawRect(SHADOW, top, getWidth() - SHADOW, mTFRLayout.getHeight() + top, mPaint);
            canvas.save();
            canvas.translate(SHADOW + 2, top);
            mPaint.setShadowLayer(0, 0, 0, Color.BLACK);
            mTFRLayout.draw(canvas);
            canvas.restore();        
        }
    }

    /**
     * 
     * @param canvas
     */
    private void drawObstacles(Canvas canvas) {
        if(mPref.shouldShowObstacles()) {
            if((mObstacles != null) && (null == mPointProjection)) {
                mPaint.setShadowLayer(0, 0, 0, 0);
                for (Obstacle o : mObstacles) {
                    rotateBitmapIntoPlace(mObstacleBitmap, 0, o.getLongitude(), o.getLatitude(), false);
                    canvas.drawBitmap(mObstacleBitmap.getBitmap(), mObstacleBitmap.getTransform(), mPaint);
                }
            }
        }
    }

    
    /**
     * 
     * @param canvas
     */
    private void drawNexrad(Canvas canvas) {
        if(mService == null) {
            return;
        }
        SparseArray<NexradBitmap> bitmaps = mService.getNexradImages();
        if(null == bitmaps) {
            return;
        }
        for(int i = 0; i < bitmaps.size(); i++) {
            int key = bitmaps.keyAt(i);
            NexradBitmap b = bitmaps.get(key);
            BitmapHolder bitmap = b.getBitmap();
            
            if(null != bitmap) {                 
                /*
                 * 
                 */
                float scalex = (float)(b.getScaleX() / mPx);
                float scaley = (float)(b.getScaleY() / mPy);
                float x = (float)mOrigin.getOffsetX(b.getLonTopLeft());
                float y = (float)mOrigin.getOffsetY(b.getLatTopLeft());
                bitmap.getTransform().setScale(scalex * mScale.getScaleFactor(), scaley * mScale.getScaleCorrected());
                bitmap.getTransform().postTranslate(x, y);
    
                canvas.drawBitmap(bitmap.getBitmap(), bitmap.getTransform(), mPaint);
            }
        }
    }
    
    /**
     * 
     * @param canvas
     */
    private void drawAircraft(Canvas canvas) {
        mPaint.setShadowLayer(0, 0, 0, 0);
        mPaint.setColor(Color.WHITE);

        if(null != mAirplaneBitmap && null == mPointProjection) {
            
            /*
             * Rotate and move to a panned location
             */
            rotateBitmapIntoPlace(mAirplaneBitmap, (float)mGpsParams.getBearing(),
                    mGpsParams.getLongitude(), mGpsParams.getLatitude(), true);
            canvas.drawBitmap(mAirplaneBitmap.getBitmap(), mAirplaneBitmap.getTransform(), mPaint);
        }        

    }


    /**
     * 
     * @param canvas
     */
    private void drawRunways(Canvas canvas) {
        
        if(!mPref.shouldExtendRunways()) {
            return;
        }
        if(null == mService) {
            return;
        }

        if(null != mRunwayBitmap && null != mService.getDestination() && null == mPointProjection) {

            LinkedList<Runway> runways = mService.getDestination().getRunways();
            if(runways != null) {
                
                for(Runway r : runways) {
                    /*
                     * Rotate and move to a panned location
                     */
                    float heading = r.getTrue();
                    if(Runway.INVALID == heading) {
                        continue;
                    }
                    
                    double lon = r.getLongitude();
                    double lat = r.getLatitude();
                    if(Runway.INVALID == lon || Runway.INVALID == lat) {
                        /*
                         * If we did not get any lon/lat of this runway, use airport lon/lat
                         */
                        lon = mService.getDestination().getLocation().getLongitude();
                        lat = mService.getDestination().getLocation().getLatitude();
                    }
                    
                    rotateBitmapIntoPlace(mRunwayBitmap, heading, lon, lat, false);

                    /*
                     * Draw it.
                     */
                    canvas.drawBitmap(mRunwayBitmap.getBitmap(), mRunwayBitmap.getTransform(), mPaint);
                }

                /*
                 * Loop again to over write text
                 */
                mPaint.setShadowLayer(SHADOW, SHADOW, SHADOW, Color.BLACK);
                mPaint.setColor(TEXT_COLOR);
                mPaint.setTextAlign(Align.CENTER);

                for(Runway r : runways) {
                    /*
                     * Rotate and move to a panned location
                     */
                    float heading = r.getTrue();
                    if(Runway.INVALID == heading) {
                        continue;
                    }
                    
                    double lon = r.getLongitude();
                    double lat = r.getLatitude();
                    float x;
                    float y;
                    if(Runway.INVALID == lon || Runway.INVALID == lat) {
                        /*
                         * If we did not get any lon/lat of this runway, use airport lon/lat
                         */
                        lon = mService.getDestination().getLocation().getLongitude();
                        lat = mService.getDestination().getLocation().getLatitude();
                    }
                    x = (float)mOrigin.getOffsetX(lon);
                    y = (float)mOrigin.getOffsetY(lat);                        
                                        
                    /*
                     * Draw it.
                     */
                    String num = r.getNumber();
                    int xfact;
                    int yfact;
                    /*
                     * If parallel runways, draw their text displaced so it does not overlap
                     */
                    if(num.contains("C")) {
                        xfact = yfact = mRunwayBitmap.getHeight() * 3 / 4;
                    }
                    else if(num.contains("L")){
                        xfact = yfact = mRunwayBitmap.getHeight() / 2;                        
                    }
                    else {
                        xfact = yfact = mRunwayBitmap.getHeight();                                                
                    }
                    
                    /*
                     * Draw text with simple rotation math.
                     */
                    canvas.drawText(num,
                            x + xfact * (float)Math.sin(Math.toRadians(heading - 180)),
                            y - yfact * (float)Math.cos(Math.toRadians(heading - 180)), mPaint);
                }
            }
        }   
    }

    /**
     * Draws concentric circles around the current aircraft position showing distance.
     * @param canvas
     */
    private void drawDistanceRings(Canvas canvas) {
    	final int Ring1x[] = { 320, 160,  80,  40};
    	final int Ring2x[] = { 640, 320, 160,  80};
    	final int Ring3x[] = {1280, 640, 320, 160};
    	final String Ring1Text[] = {"10", "5",  "2.5", "1.25"};
    	final String Ring2Text[] = {"20", "10", "5",   "2.5"};
    	final String Ring3Text[] = {"40", "20", "10",  "5"};

    	double distanceFactor = 1;
    	if (mPref.getDistanceUnit().equals("mi")) {
    		distanceFactor = 1 / 1.15;
    	} else if (mPref.getDistanceUnit().equals("km")) {
    		distanceFactor = 1 / 1.852;
    	}
    	
        if(mPref.showDistanceRings() != false) {
            float x = (float)(mOrigin.getOffsetX(mGpsParams.getLongitude()));
            float y = (float)(mOrigin.getOffsetY(mGpsParams.getLatitude()));                        
            float scaleFactor = mScale.getScaleFactor();
            
            int ringScale = 0;
        	if((scaleFactor * distanceFactor) > 2) {
        		ringScale = 3;
        	} else if ((scaleFactor * distanceFactor) > 1) {
        		ringScale = 2;
        	} else if ((scaleFactor * distanceFactor) > .5) {
        		ringScale = 1;
        	}

        	float ring1R = (float)(Ring1x[ringScale] * scaleFactor * distanceFactor);
        	float ring2R = (float)(Ring2x[ringScale] * scaleFactor * distanceFactor);
        	float ring3R = (float)(Ring3x[ringScale] * scaleFactor * distanceFactor);
        	
        	mPaint.setStrokeWidth(5);
        	mPaint.setStyle(Paint.Style.STROKE);
        	mPaint.setColor(Color.WHITE);
        	canvas.drawCircle(x, y, ring1R, mPaint);
        	canvas.drawCircle(x, y, ring2R, mPaint);
        	canvas.drawCircle(x, y, ring3R, mPaint);
        	mPaint.setColor(TEXT_COLOR);
        	mPaint.setShadowLayer(SHADOW, SHADOW, SHADOW, Color.BLACK);
        	float oldSize = mPaint.getTextSize();
        	mPaint.setTextSize(45);
        	mPaint.setStyle(Paint.Style.FILL);

        	Rect textSize = new Rect();
            
            mPaint.getTextBounds(Ring1Text[ringScale], 0, Ring1Text[ringScale].length(), textSize);
        	canvas.drawText(Ring1Text[ringScale], x + ring1R - (textSize.right / 2),  y, mPaint);

            mPaint.getTextBounds(Ring2Text[ringScale], 0, Ring2Text[ringScale].length(), textSize);
        	canvas.drawText(Ring2Text[ringScale],  x + ring2R - (textSize.right / 2), y, mPaint);

            mPaint.getTextBounds(Ring3Text[ringScale], 0, Ring3Text[ringScale].length(), textSize);
            canvas.drawText(Ring3Text[ringScale],  x + ring3R - (textSize.right / 2), y, mPaint);
        	mPaint.setTextSize(oldSize);
        }
    }

    /**
     * Draw the tracks to show our previous positions. If tracking is enabled, there is
     * a linked list of gps coordinates attached to this view with the most recent one at the end
     * of that list. Start at the end value to begin the drawing and as soon as we find one that is 
     * not in the range of this display, we can assume that we're done.
     * @param canvas
     */
    private void drawTracks(Canvas canvas) {
    	// Some pre-conditions that would prevent us from drawing anything
    	//
    	LinkedList<Coordinate> ph = mKMLRecorder.getPositionHistory();
        if(mPref.shouldDrawTracks() && ph != null) {
            if((ph.size() > 1) && (null == mPointProjection)) {
            	if(mService == null){
            		return;
            	}

                // Get the position point at the end of the list. This is our starting 
                // location. From here, search backward until we find the first location
                // that is in the current display range.
            	//
                int idx = 0;
                for (idx = ph.size() - 1; idx > 0; idx--) {
                	if (mOrigin.isInDisplayRange(ph.get(idx)) == true)
                		break;
                }

                // If no points are found in range, then there is nothing to plot
                //
                if (idx == 0) 
                	return;
                
            	// Set the brush color and width
            	//
            	mPaint.setColor(Color.MAGENTA);
                mPaint.setStrokeWidth(5);
            	mPaint.setStyle(Paint.Style.FILL);

                // Get the first visible GPS point to start at
            	//
                Coordinate gpsPos1 = ph.get(idx);
            	for( ; idx >= 0; idx--) {
            		
            		// Get the next position to draw to
            		//
            		Coordinate gpsPos2 = ph.get(idx);
            		
            		// Check the location against our display bounds. If this point is still on our 
            		// display area then it is OK to draw the line. If it is out of bounds, assume that 
            		// we are done with our plotting
            		//
            		if(mOrigin.isInDisplayRange(gpsPos2)) {
        	            float x1 = (float)(mOrigin.getOffsetX(gpsPos1.getLongitude()));
        	            float y1 = (float)(mOrigin.getOffsetY(gpsPos1.getLatitude()));                        

        	            float x2 = (float)(mOrigin.getOffsetX(gpsPos2.getLongitude()));
        	            float y2 = (float)(mOrigin.getOffsetY(gpsPos2.getLatitude()));                        

        	            canvas.drawLine(x1, y1, x2, y2, mPaint);

        	            // Set the end point as our new start point and do this all again
        	            //
        	            gpsPos1 = gpsPos2;
            		} else return; // Point is out of range, we are done
            	}
            }
        }
    }

    /**
     * @param canvas
     * Does pretty much all drawing on screen
     */
    private void drawMap(Canvas canvas) {
    	
    	mPaint.setTextSize(getHeight() / mTextDiv);
        mTextPaint.setTextSize(getHeight() / mTextDiv * 3 / 4);
    	
        if(mTrackUp && (mGpsParams != null)) {
            canvas.save();
            /*
             * Rotate around current position
             */
            float x = (float)mOrigin.getOffsetX(mGpsParams.getLongitude());
            float y = (float)mOrigin.getOffsetY(mGpsParams.getLatitude());
            canvas.rotate(-(int)mGpsParams.getBearing(), x, y);
        }
    	drawTiles(canvas);
    	drawNexrad(canvas);
        drawDrawing(canvas);
        drawRunways(canvas);
    	drawTFR(canvas);
        drawTrack(canvas);
        drawObstacles(canvas);
        drawAircraft(canvas);
        drawDistanceRings(canvas);	// Circles showing distance from current position
        drawTracks(canvas);			// Historical positions
        if(mTrackUp) {
            canvas.restore();
        }
    	drawMETARText(canvas);
    	drawCornerTexts(canvas);
    }    

    /**
     * 
     * @param factor
     */
    public void updateThreshold(float threshold) {
        mThreshold = threshold;
        invalidate();
    }

    /**
     * @param destination
     */
    public void updateDestination() {
        /*
         * Comes from database
         */
        if(null == mService) {
            return;
        }
        if(null != mService.getDestination()) {
            if(mService.getDestination().isFound()) {
                /*
                 * Set pan to zero since we entered new destination
                 * and we want to show it without pan.
                 */
                mPan = new Pan();
                tfrReset();                
            }
        }
    }

    /**
     * 
     */
    public void forceReload() {
        dbquery(true);        
    }

    public GpsParams getGpsParams() {
    	return mGpsParams;
    }

    public Preferences getPref() {
    	return mPref;
    }
    /**
     * @param params
     */
    public void updateParams(GpsParams params) {
        /*
         * Comes from location manager
         */
        mGpsParams = params;

        tfrReset();
        /*
         * Database query for new location / pan location.
         */
        dbquery(false);
        
        
        /*
         * Do not overwhelm, obstacles
         */
        mObstacleTask.alt = mGpsParams.getAltitude();
        mObstacleTask.lon = mGpsParams.getLongitude();
        mObstacleTask.lat = mGpsParams.getLatitude();
        
        mObstacleThread.interrupt();
    }

    
    /**
     * @param params
     */
    public void initParams(GpsParams params, StorageService service) {
        /*
         * Comes from storage service. This will do nothing for fresh start,
         * but it will load previous combo on re-activation
         */
        mService = service;
        mMovement = mService.getMovement();
        mImageDataSource = mService.getDBResource();
        if(null == mMovement) {
            mMovement = new Movement();
        }
        mPan = mService.getPan();
        if(null == mPan) {
            mPan = new Pan();
        }
        if(null != params) {
            mGpsParams = params;
        }
        else if (null != mService.getDestination()) {
            mGpsParams = new GpsParams(mService.getDestination().getLocation());
        }
        else {
            mGpsParams = new GpsParams(null);
        }
        mScale.setScaleAt(mGpsParams.getLatitude());
        dbquery(true);
        postInvalidate();
    }

    /**
     * @param status
     */
    public void updateErrorStatus(String status) {
        /*
         * Comes from timer of activity
         */
        mErrorStatus = status;
        postInvalidate();
    }
    
    

    /**
     * @author zkhan
     *
     */
    private class TileDrawTask implements Runnable {
        private double offsets[] = new double[2];
        private double p[] = new double[2];
        public double lon;
        public double lat;
        private int     movex;
        private int     movey;
        private String   tileNames[];
        private Tile centerTile;
        private Tile gpsTile;
        public boolean running = true;

        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        public void run() {
            
            Thread.currentThread().setName("Tile");

            while(running) {
                try {
                    Thread.sleep(1000 * 3600);
                }
                catch(Exception e) {
                    
                }

                if(mImageDataSource == null) {
                    continue;
                }
                
                /*
                 * Now draw in background
                 */
                gpsTile = mImageDataSource.findClosest(lon, lat, offsets, p, mScale.downSample());
                
                if(gpsTile == null) {
                    continue;
                }
                
                movex = mPan.getTileMoveXWithoutTear();
                movey = mPan.getTileMoveYWithoutTear();
                
                String newt = gpsTile.getNeighbor(movey, movex);
                centerTile = mImageDataSource.findTile(newt);
                if(null == centerTile) {
                    continue;
                }
                
                if(null == mService) {
                    continue;
                }
    
                /*
                 * Neighboring tiles with center and pan
                 */
                int i = 0;
                tileNames = new String[mService.getTiles().getTilesNum()];
                for(int tiley = -(int)(mService.getTiles().getYTilesNum() / 2) ; 
                        tiley <= (mService.getTiles().getYTilesNum() / 2); tiley++) {
                    for(int tilex = -(int)(mService.getTiles().getXTilesNum() / 2); 
                            tilex <= (mService.getTiles().getXTilesNum() / 2) ; tilex++) {
                        tileNames[i++] = centerTile.getNeighbor(tiley, tilex);
                    }
                }
                
                /*
                 * Load tiles, draw in UI thread
                 */
                mPan.setMove((float)(mPan.getMoveX() * mAdjustPan), (float)(mPan.getMoveY() * mAdjustPan));
                mService.getTiles().reload(tileNames, mAdjustPan != 1);
                mService.getTiles().flip();

                mScale.setScaleAt(centerTile.getLatitude());
                mOnChart = centerTile.getChart();

                /*
                 * Update TFR shapes if they exist in this area.
                 */
                LinkedList<TFRShape> shapes = mService.getTFRShapes();
                if(null != shapes) {
                    for(int shape = 0; shape < shapes.size(); shape++) {
                        shapes.get(shape).prepareIfVisible(centerTile.getLongitude(),
                                centerTile.getLatitude());
                    }
                }
                            

                /*
                 * And pan
                 */
                mPan.setTileMove(movex, movey);
                mService.setPan(mPan);
                mMovement = new Movement(offsets, p);
                mService.setMovement(mMovement);
                mPy = centerTile.getPy();
                mPx = centerTile.getPx();
                
                synchronized(LocationView.this) {
                    mAdjustPan = 1;
                }
    
                postInvalidate();
            }
        }
    }    

    /**
     * @author zkhan
     *
     */
    private class ClosestAirportTask extends AsyncTask<Object, String, String> {
        private Double lon;
        private Double lat;
        private String text;
        
        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */     
        @Override
        protected String doInBackground(Object... vals) {
            
            Thread.currentThread().setName("Closest");

            String airport = null;
            lon = (Double)vals[0];
            lat = (Double)vals[1];
            text = (String)vals[2];
            if(null != mService) {
                airport = mService.getDBResource().findClosestAirportID(lon, lat);
            }
            if(null == airport) {
                airport = "" + Helper.truncGeo(lat) + "&" + Helper.truncGeo(lon);
            }
            return airport;
        }
        
        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String airport) {
            if(text != null) {
                /*
                 * Take TFR text over TFR text
                 */
                mTextPaint.setColor(Color.WHITE);
                mTFRLayout = new StaticLayout(text.trim(), mTextPaint, getWidth(),
                        Layout.Alignment.ALIGN_NORMAL, 1, 0, true);
            }

            if(null != mGestureCallBack) {
                mGestureCallBack.gestureCallBack(GestureInterface.LONG_PRESS, airport);
            }
            invalidate();
        }
        
    }

    
    /**
     * @author zkhan
     * Find obstacles
     */
    private class ObstacleTask implements Runnable {
        public Double lon;
        public Double lat;
        public Double alt;
        
        public boolean running = true;
        
        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        public void run() {
            Thread.currentThread().setName("Obstacle");

            while(running) {

                try {
                    Thread.sleep(1000 * 3600);
                }
                catch (Exception e) {
                    
                }
                if(null != mImageDataSource) {
                    /*
                     * Find obstacles in background as well
                     */
                    mObstacles = mImageDataSource.findObstacles(lon, lat, alt.intValue());
                }                
            }
        }
    }

    /**
     * Center to the location
     */
    public void center() {
        /*
         * On double tap, move to center
         */
        mPan = new Pan();
        mScale.setScaleFactor(1);
        mScale.setScaleAt(mGpsParams.getLatitude());
        if(mService != null) {
            mService.getTiles().forceReload();
        }
        dbquery(true);
        tfrReset();
        postInvalidate();
    }
            
    /**
     * @author zkhan
     *s
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            if(null != mService) {
                /*
                 * Add separation between chars
                 */
                mService.getDraw().addSeparation();
            }
            return true;
        }

        /* (non-Javadoc)
         * @see android.view.GestureDetector.SimpleOnGestureListener#onLongPress(android.view.MotionEvent)
         */
        @Override
        public void onLongPress(MotionEvent e) {
            
            /*
             * on long press, find a point where long press was done
             */
            double x = mCurrTouchPoint.getX();
            double y = mCurrTouchPoint.getY();

            /*
             * In draw, long press has no meaning other than to clear the output from the activity
             */
            if(mDraw) {
                mGestureCallBack.gestureCallBack(GestureInterface.LONG_PRESS, "");
                return;
            }

            // If the long press is in the top 2 lines, then clear the HOBBS meter
            // TODO - perhaps pop up a menu to change the units as well ? Could be a gesture
            // to bring up a dialog dealing with all things in the top 2 text lines.
            if(y < (mTextDiv * 3)) {
            	mFlightTimer.reset();
            	return;
            }

            /*
             * XXX:
             * For track up, currently there is no math to find anything with long press.
             */
            if(mTrackUp) {
                return;
            }

            /*
             * Notify activity of gesture.
             */
            
            double lon2 = mOrigin.getLongitudeOf(x);
            double lat2 = mOrigin.getLatitudeOf(y);
            mPointProjection = new Projection(mGpsParams.getLongitude(), mGpsParams.getLatitude(), lon2, lat2);
            
            String text = null;
                       
            /*
             * Get TFR text if touched on its top
             */
            LinkedList<TFRShape> shapes = null;
            if(null != mService) {
                shapes = mService.getTFRShapes();
            }
            if(null != shapes) {
                for(int shape = 0; shape < shapes.size(); shape++) {
                    TFRShape cshape = shapes.get(shape);
                    if(cshape.isVisible()) {

                        /*
                         * Set TFR color
                         */
                        mTFRColor = Color.RED;
                        text = cshape.getTextIfTouched(x, y);
                        if(null != text) {
                            break;
                        }
                    }
                }
            }

            /*
             * Get airport touched on
             */
            if(null != mClosestTask) {
                mClosestTask.cancel(true);
            }
            mClosestTask = new ClosestAirportTask();
            mClosestTask.execute(lon2, lat2, text);
        }
    }


    /**
     * 
     * @param gestureInterface
     */
    public void setGestureCallback(GestureInterface gestureInterface) {
        mGestureCallBack = gestureInterface;
    }

    /**
     * 
     * @param b
     */
    public void setDraw(boolean b) {
        mDraw = b;
        invalidate();
    }

    /**
     * 
     * @param b
     */
    public boolean getDraw() {
        return mDraw;
    }

    /*
     * Gets chart on which this view is.
     */
    public String getChart() {
        return mOnChart;
    }
    
    /**
     * 
     * @param tu
     */
    public void setTrackUp(boolean tu) {
        mTrackUp = tu;
        invalidate();
    }
    
    /**
     * 
     */
    public void cleanup() {
        mObstacleTask.running = false;
        mTileDrawTask.running = false;
        mTileDrawThread.interrupt();
        mObstacleThread.interrupt();
    }
    
    /**
     * 
     * @param in
     */
    public boolean zoomIn(boolean in) {
        if(mAdjustPan != 1) {
            /**
             * Make sure that last operation finished. 
             */
            return true;
        }
        boolean changed;
        synchronized(this) {
            int fac = mScale.getMacroFactor();
            int scale = mScale.getZoomFactor();
            if(in) {
                changed = mScale.setMacroFactor(fac / scale);
                if(changed) {
                    mAdjustPan = (double)scale;
                }
            }
            else {
                changed = mScale.setMacroFactor(fac * scale);
                if(changed) {
                    mAdjustPan = 1.0 / (double)scale;
                }
            }
            if(changed && mService != null) {
                forceReload();
            }
        }
        
        return changed;
    }

    /**
     * Called when the user presses the "tracks" button on the viewlocation screen to
     * toggle the state of the saving of GPS positions.
     * @param b enable/disable tracking
     */
    public void setTracks(boolean b) {
        if(b == true)
        	mKMLRecorder.start();
        else
        	mKMLRecorder.stop();
    }

    /**
     * Are we currently saving the location information
     * @return Boolean to indicate whether we are actively writing tracks
     */
    public boolean getTracks() {
        return mKMLRecorder.isRecording();
    }
}
