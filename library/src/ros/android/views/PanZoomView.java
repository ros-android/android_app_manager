/*
 * Copyright (c) 2011, Willow Garage, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Willow Garage, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
 
package ros.android.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;

import java.util.ArrayList;

import org.ros.Node;
import org.ros.exception.RosInitException;

import ros.android.activity.R;

/**
 * 2D container view class for showing a map with a robot on it,
 * sensor data from the robot, etc.
 *
 * In this class, the "view" frame is the coordinate frame of the
 * screen area of the View, with the origin a the top left, X
 * increasing to the right, Y increasing down, measured in units of
 * pixels.
 *
 * The "fixed" or "map" frame is a "world" coordinate frame fixed to
 * something in the world, with X and Y on the floor and +Z pointing
 * up.  Units are meters.
 *
 * Touch input is used to sense drag and pinch gestures to pan and
 * zoom the fixed frame around within the view.  Child PanZoomDisplay
 * objects can be added and will be dragged around and scaled with the
 * fixed frame.
 */
public class PanZoomView extends View {
  private Matrix mapRelView; // controlled by user pan and zoom

  private float oldDist; // Previous distance between two fingers on the screen.
  private PointF oldCenter; // Previous center between two fingers on the screen, or previous single finger position.
  private boolean oldCenterValid;
  private boolean firstSize;

  private ArrayList<PanZoomDisplay> displays;
  private Node node;
  private Matrix viewMatrix;

  public PanZoomView(Context ctx) {
    super(ctx);
    init(ctx);
  }

  public PanZoomView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  public PanZoomView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    mapRelView = new Matrix();
    oldCenter = new PointF();
    oldCenterValid = false;
    oldDist = 0;
    firstSize = true;
    displays = new ArrayList<PanZoomDisplay>();
  }

  /**
   * Return a copy of the transform of the fixed frame relative to the
   * view frame.
   */
  public Matrix getFixedRelView() {
    Matrix result = new Matrix();
    result.set( mapRelView );
    return result;
  }

  /**
   * Add the given display to this view.  If the view has already been
   * started (via start()), the display is started as well.
   */
  public void addDisplay( final PanZoomDisplay display ) {
    display.setParent( this );
    displays.add( display );
    if( node != null ) {
      final Node thisNode = node;
      new Thread( new Runnable() {
          @Override public void run() {
            try {
              display.start( thisNode );
            } catch( RosInitException ex ) {
              Log.e( "PanZoomView", "failed to start display.", ex );
            }
          }
        }).start();
    }
    postInvalidate();
  }

  /**
   * Remove the given display from this view.  If the view has been
   * started (via start()) and the display was in this view, the
   * display is first stopped.
   * @returns true if the display was in this view, false if it was not.
   */
  public boolean removeDisplay( PanZoomDisplay display ) {
    if( displays.remove( display )) {
      display.setParent( null );
      if( node != null ) {
        display.stop();
      }
      return true;
    } else {
      return false;
    }
  }

  public void start(Node node) throws RosInitException {
    stop();

    this.node = node;

    for( PanZoomDisplay display: displays ) {
      display.start( node );
    }
  }

  public void stop() {
    node = null;

    for( PanZoomDisplay display: displays ) {
      display.stop();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw( canvas );
    viewMatrix = canvas.getMatrix();

    canvas.concat( mapRelView );
    for( PanZoomDisplay display: displays ) {
      display.draw( canvas );
    }

    canvas.setMatrix( viewMatrix );
  }

  /**
   * Return a copy of the original view matrix from the Canvas passed
   * to onDraw().  This lets child displays access the original matrix
   * for view-based drawing within their draw() functions.
   */
  public Matrix getViewMatrix() {
    Matrix result = new Matrix();
    result.set( viewMatrix );
    return result;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // If we are clickable, don't try any other interaction.
    if( isClickable() ) {
      return super.onTouchEvent(event);
    }

    for( PanZoomDisplay display: displays ) {
      if( display.onTouchEvent( event )) {
        return true;
      }      
    }

    // One or two fingers dragging should pan, and two fingers
    // changing distance between each other should zoom.
    switch(event.getActionMasked()) {
    case MotionEvent.ACTION_DOWN: // first finger touch
      oldCenter.x = event.getX();
      oldCenter.y = event.getY();
      oldCenterValid = true;
      break;
    case MotionEvent.ACTION_POINTER_DOWN: // second or later finger touches
      findEventCenter(oldCenter, event);
      oldDist = findEventSpacing(event);
      oldCenterValid = true;
      break;
    case MotionEvent.ACTION_POINTER_UP: // second or later finger up
      oldCenterValid = false;
      break;
    case MotionEvent.ACTION_MOVE:
      // Drag regardless of number of touches
      PointF newCenter = new PointF();
      findEventCenter(newCenter, event);
      if(oldCenterValid) {
        mapRelView.postTranslate(newCenter.x - oldCenter.x,
                                 newCenter.y - oldCenter.y);
      }
      // If 2 fingers, also do zoom.
      if( event.getPointerCount() == 2 && oldDist > 10f ) {
        float newDist = findEventSpacing(event);
        if( newDist > 10f ) {
          mapRelView.postScale(newDist / oldDist, newDist / oldDist, newCenter.x, newCenter.y);
        }
        oldDist = newDist;
      }
      oldCenter.set(newCenter);
      oldCenterValid = true;
      invalidate();
      break;
    }
    return true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    if(firstSize) {
      float pixelsPerMeter = 150;
      mapRelView.setValues(new float[]{pixelsPerMeter, 0, w/2,
                                       0, -pixelsPerMeter, h/2,
                                       0, 0, 1});
      firstSize = false;
    }
    for( PanZoomDisplay display: displays ) {
      display.onSizeChanged( w, h, oldW, oldH );
    }
  }

  /**
   * Center a point in the view without changing the scale.
   * The point is relative to the fixed ("map") frame.
   */
  public void centerPoint( PointF pointRelMap ) {
    Matrix pointRelView = new Matrix();
    pointRelView.set( mapRelView );
    pointRelView.preTranslate( pointRelMap.x, pointRelMap.y );

    float[] values = new float[9];
    pointRelView.getValues(values);
    float pointRelViewX = values[2]; // These should be in pixel units.
    float pointRelViewY = values[5];

    // Translate mapRelView by the difference between the view center
    // and the point.
    mapRelView.postTranslate( getWidth()/2f - pointRelViewX,
                              getHeight()/2f - pointRelViewY );
    postInvalidate();
  }

  private float findEventSpacing(MotionEvent event) {
    float dx = event.getX(0) - event.getX(1);
    float dy = event.getY(0) - event.getY(1);
    return FloatMath.sqrt(dx*dx + dy*dy);
  }

  private void findEventCenter(PointF centerOut, MotionEvent event) {
    if( event.getPointerCount() == 1 ) {
      centerOut.set( event.getX(0), event.getY(0) );
    } else {
      centerOut.set( (event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2 );
    }
  }
}
