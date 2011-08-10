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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;

import javax.vecmath.Quat4f;
import javax.vecmath.Matrix3f;

import ros.android.util.Posable;
import ros.android.util.FingerReceiver;
import ros.android.util.FingerTracker;

/**
 * PanZoomDisplay which implements a draggable pose input.
 *
 * The control has two parts: a central circle for translation and a
 * smaller outboard circle for rotation.
 *
 * Behavior: When the user is not actually touching and dragging part
 * of the control, the pose is set by incoming Matrices via the
 * Posable interface (i.e. originating from TF messages describing
 * where the robot thinks it currently is.
 *
 * When the user touches inside one of the control circles, those
 * incoming Poses are ignored and the pose is updated by the user's
 * drag gestures.
 */
abstract public class PoseInputDisplay extends PanZoomDisplay implements Posable {
  private Paint paint = new Paint();
  private Paint linePaint = new Paint();
  private Matrix estimatedRobotRelMap = new Matrix();
  private Matrix estimatedRobotRelView = new Matrix();
  private boolean dragging = false;
  private boolean turning = false;
  private boolean placeClick = false;
  private Runnable disabler = new Runnable() {
      @Override public void run() {
        disable();
      }
    };

  // All these spot and handle geometry values are in view coordinates.
  private float handleCenterDistance = 2.0f * 1.5f; //150;
  private float spotDiameter = 2.0f; //100;
  private float handleDiameter = 2.0f * 0.75f; //75;
  private float spotRadius = spotDiameter / 2f;
  private float handleRadius = handleDiameter / 2f;
  private float handleWidth = spotDiameter * 0.20f;
  private float centerX; // coordinates of the center spot relative to the view.
  private float centerY;
  private float handleX; // coordinates of the center spot relative to the view.
  private float handleY;
  private float handleAngleRadians; // relative to the view.

  private FingerReceiver translationHandler = new FingerReceiver() {
      private PointF touchOffset = new PointF(); // distance from touch to center of control

      @Override public boolean onDown( float x, float y ) {
        if (placeClick) {
          dragging = true;
          float[] center = new float[2];
          center[0] = x;
          center[1] = y;
          Matrix inv = new Matrix();
          if (!getParent().getFixedRelView().invert(inv)) {
            Log.e("PoseInputDisplay", "No inverse for matrix, can't transform");
            return false;
          }
          inv.mapPoints( center );
          centerX = center[0];
          centerY = center[1];
          setHandleAngleRadians( handleAngleRadians );
          postInvalidate();
          outputPose();
          dragging = false;
          placeClick = false;
          return true;
        }
        float[] center = new float[2];
        center[0] = centerX;
        center[1] = centerY;
        getParent().getFixedRelView().mapPoints( center );
        if( inCircle( x, y, center[0], center[1], getParent().getFixedRelView().mapRadius(spotRadius) )) {
          touchOffset.set( center[0] - x, center[1] - y );
          dragging = true;
          postInvalidate();
          return true;
        } else {
          return false;
        }
      }

      @Override public void onMove( float x, float y ) {
        if( dragging ) {
          float[] center = new float[2];
          center[0] = x + touchOffset.x;
          center[1] = y + touchOffset.y;
          Matrix inv = new Matrix();
          if (!getParent().getFixedRelView().invert(inv)) {
            Log.e("PoseInputDisplay", "No inverse for matrix, can't transform");
            return;
          }
          inv.mapPoints( center );
          centerX = center[0];
          centerY = center[1];
          setHandleAngleRadians( handleAngleRadians ); // update handleX and handleY based on the new center.
          outputPose();
          postInvalidate();
        }
      }

      @Override public void onUp() {
        dragging = false;
        postInvalidate();
      }
    };

  private FingerReceiver rotationHandler = new FingerReceiver() {
      private PointF touchOffset = new PointF(); // distance from touch to center of control

      @Override public boolean onDown( float x, float y ) {
        getParent().getFixedRelView();
        float[] handle = new float[2];
        handle[0] = handleX;
        handle[1] = handleY;
        getParent().getFixedRelView().mapPoints( handle );
        if( inCircle( x, y, handle[0], handle[1], getParent().getFixedRelView().mapRadius(handleRadius) )) {
          touchOffset.set( handle[0] - x, handle[1] - y );
          turning = true;
          postInvalidate();
          return true;
        } else {
          return false;
        }
      }

      @Override public void onMove( float x, float y ) {
        if( turning ) {
          float[] newHandle = new float[2];
          newHandle[0] = x + touchOffset.x;
          newHandle[1] = y + touchOffset.y;
          Matrix inv = new Matrix();
          if (!getParent().getFixedRelView().invert(inv)) {
            Log.e("PoseInputDisplay", "No inverse for matrix, can't transform");
            return;
          }
          inv.mapPoints( newHandle );
          setHandleAngleRadians( (float) Math.atan2( (float)-(newHandle[1] - centerY), (float)(newHandle[0] - centerX) ));
          outputPose();
          postInvalidate();
        }
      }

      @Override public void onUp() {
        turning = false;
        postInvalidate();
      }
    };

  private FingerTracker fingerTracker;
  private int color;

  public void placeOnClick() {
    placeClick = true;
  }

  public void setColor( int newColor ) {
    color = newColor;
    paint.setColor( color );
    paint.setAlpha( 0x60 );
    linePaint.setColor( color );
    linePaint.setAlpha( 0x60 );
  }
  public int getColor() {
    return color;
  }

  private void setHandleAngleRadians( float radians ) {
    handleAngleRadians = radians;
    handleX = centerX + FloatMath.cos( handleAngleRadians ) * handleCenterDistance;
    handleY = centerY - FloatMath.sin( handleAngleRadians ) * handleCenterDistance;
  }

  public PoseInputDisplay() {
    setColor(0);
    linePaint.setStrokeWidth(handleWidth);

    fingerTracker = new FingerTracker();
    fingerTracker.addReceiver( translationHandler );
    fingerTracker.addReceiver( rotationHandler );
  }

  @Override
  public boolean onTouchEvent( MotionEvent event ) {
    if( isEnabled() ) {
      return fingerTracker.onTouch( getParent(), event );
    } else {
      return false;
    }
  }

  /**
   * Set the pose matrix to be a copy of poseRelFixedFrame.
   */
  @Override
  public void setPose( Matrix poseRelFixedFrame ) {
    estimatedRobotRelMap.set( poseRelFixedFrame );
    if( !dragging && !turning ) {
      estimatedRobotRelView = getParent().getFixedRelView();
      estimatedRobotRelView.preConcat( estimatedRobotRelMap );

      // Find the center of the robot in view coordinates
      float[] center = new float[2];
      center[0] = 0f;
      center[1] = 0f;
      estimatedRobotRelMap.mapPoints( center );
      centerX = center[0];
      centerY = center[1];

      // Find the direction vector (which way the robot is pointing) in view coordinates.
      float[] dirVector = {1f, 0f};
      estimatedRobotRelMap.mapVectors( dirVector );
      
      setHandleAngleRadians( (float) Math.atan2( -(double)dirVector[1], (double)dirVector[0] ));
    }
  }

  /**
   * Return a copy of the current pose matrix.
   */
  @Override
  public Matrix getPose() {
    Matrix result = new Matrix();
    result.set( estimatedRobotRelMap );
    return result;
  }

  @Override
  public void reset() {}

  @Override
  public void draw( Canvas canvas ) {
    if( dragging || turning ) {
      paint.setAlpha( 0xa0 );
      linePaint.setAlpha( 0xa0 );
    } else {
      paint.setAlpha( 0x60 );
      linePaint.setAlpha( 0x60 );
    }
    canvas.drawCircle( centerX, centerY, spotRadius, paint );
    canvas.drawCircle( handleX, handleY, handleRadius, paint );
    canvas.drawLine( centerX, centerY, handleX, handleY, linePaint );
  }

  private boolean inCircle( float x, float y, float cx, float cy, float radius ) {
    float dx = x - cx;
    float dy = y - cy;
    return FloatMath.sqrt( dx*dx + dy*dy ) < radius;
  }

  private void outputPose() {
    // We have pose (centerX, centerY, handleAngleRadians) in the view frame.
    // poseRelMap = viewRelMap * poseRelView
    // viewRelMap = mapRelView.inverse()

    Matrix poseRelView = new Matrix();
    poseRelView.setTranslate( centerX, centerY );
    poseRelView.preRotate( handleAngleRadians * 180f / (float) Math.PI );

    Matrix poseRelMap = new Matrix();
    if( getParent().getFixedRelView().invert( poseRelMap )) {
      poseRelMap.preConcat( poseRelView );
      float[] values = new float[9];
      poseRelMap.getValues( values );

      // Get the direction by mapping a unit vector through poseRelMap.
      float[] dirVector = {1f, 0f};
      poseRelMap.mapVectors( dirVector );
      float angleRelMap = (float) Math.atan2( (double)dirVector[1], (double)dirVector[0] );
      Log.i("PoseInputDisplay", "angleRelMap = " + angleRelMap);

      onPose( centerX, centerY, angleRelMap );
    } else {
      Log.e("PoseInputDisplay", "fixed frame rel view matrix not invertible!  Not calling onPose().");
    }
  }

  /**
   * Override to actually do something with the pose input by the user.
   * This is called every time the pose is updated by user touch events.
   */
  abstract protected void onPose( float x, float y, float angle );
}
