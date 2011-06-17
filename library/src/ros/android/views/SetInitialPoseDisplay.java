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

import org.ros.MessageListener;
import org.ros.Node;
import org.ros.Publisher;
import org.ros.exception.RosInitException;
import org.ros.message.geometry_msgs.PoseWithCovarianceStamped;

import ros.android.util.Posable;

/**
 * PanZoomDisplay which implements a draggable initial-pose setter.
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
 * drag gestures.  When the user releases their finger and the drag
 * ends, a message will be published on the initialpose topic, and the
 * behavior will revert to following the robot's position estimate.
 */
public class SetInitialPoseDisplay extends PanZoomDisplay implements Posable {
  private Paint paint = new Paint();
  private Paint linePaint = new Paint();
  private String initialPoseTopic = "initialpose";
  private String fixedFrame = "/map";
  private Publisher<PoseWithCovarianceStamped> initialPosePublisher;
  private Matrix estimatedRobotRelMap = new Matrix();
  private Matrix estimatedRobotRelView = new Matrix();
  private boolean dragging = false;
  private boolean turning = false;
  private Runnable disabler = new Runnable() {
      @Override public void run() {
        disable();
      }
    };

  // All these spot and handle geometry values are in view coordinates.
  private float handleCenterDistance = 100;
  private float spotDiameter = 100;
  private float handleDiameter = 75;
  private float spotRadius = spotDiameter / 2f;
  private float handleRadius = handleDiameter / 2f;
  private float centerX; // coordinates of the center spot relative to the view.
  private float centerY;
  private float handleAngleRadians; // relative to the view.
  private PointF lastPoint = new PointF();
  private PointF touchOffset = new PointF(); // distance from touch to center of control

  public SetInitialPoseDisplay() {
    paint.setColor(0x60ff8080);
    linePaint.setColor(0x60ff8080);
    linePaint.setStrokeWidth(20);
  }

  public void setTopic( String topic ) {
    this.initialPoseTopic = topic;
  }
  public String getTopic() {
    return initialPoseTopic;
  }

  /**
   * Set the frame ID for the fixed frame which the initial pose is
   * set relative to.  Defaults to "/map".
   */
  public void setFixedFrame( String fixedFrame ) {
    this.fixedFrame = fixedFrame;
  }
  public String getFixedFrame() {
    return fixedFrame;
  }

  @Override
  public void start( Node node ) throws RosInitException {
    super.start( node );
    initialPosePublisher = node.createPublisher( initialPoseTopic, PoseWithCovarianceStamped.class );
  }

  @Override
  public void stop() {
    super.stop();
    if( initialPosePublisher != null) {
      initialPosePublisher.shutdown();
    }
    initialPosePublisher = null;
  }

  /**
   * Set the pose matrix to be a copy of poseRelFixedFrame.
   */
  @Override
  public void setPose( Matrix poseRelFixedFrame ) {
    estimatedRobotRelMap.set( poseRelFixedFrame );
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
      paint.setColor(0xa0ff8080);
      linePaint.setColor(0xa0ff8080);
    } else {
      paint.setColor(0x60ff8080);
      linePaint.setColor(0x60ff8080);
    }

    if( !dragging && !turning ) {
      estimatedRobotRelView = getParent().getFixedRelView();
      estimatedRobotRelView.preConcat( estimatedRobotRelMap );

      // Find the center of the robot in view coordinates
      float[] center = new float[2];
      center[0] = 0f;
      center[1] = 0f;
      estimatedRobotRelView.mapPoints( center );
      centerX = center[0];
      centerY = center[1];

      // Find the direction vector (which way the robot is pointing) in view coordinates.
      float[] dirVector = {1f, 0f};
      estimatedRobotRelView.mapVectors( dirVector );

      handleAngleRadians = (float) Math.atan2( (double)dirVector[1], (double)dirVector[0] );
    }

    Matrix oldMatrix = canvas.getMatrix();
    canvas.setMatrix( getParent().getViewMatrix() );

    float handleX = centerX + FloatMath.cos( handleAngleRadians ) * handleCenterDistance;
    float handleY = centerY + FloatMath.sin( handleAngleRadians ) * handleCenterDistance;

    canvas.drawCircle( centerX, centerY, spotRadius, paint );
    canvas.drawCircle( handleX, handleY, handleRadius, paint );
    canvas.drawLine( centerX, centerY, handleX, handleY, linePaint );

    canvas.setMatrix( oldMatrix );
  }

  private boolean inCircle( MotionEvent event, float cx, float cy, float radius ) {
    float dx = event.getX() - cx;
    float dy = event.getY() - cy;
    return FloatMath.sqrt( dx*dx + dy*dy ) < radius;
  }

  public boolean onTouchEvent(MotionEvent event) {
    float handleX = centerX + FloatMath.cos( handleAngleRadians ) * handleCenterDistance;
    float handleY = centerY + FloatMath.sin( handleAngleRadians ) * handleCenterDistance;

    switch(event.getActionMasked()) {
    case MotionEvent.ACTION_DOWN: // first finger touch
      if( inCircle( event, centerX, centerY, spotRadius )) {
        dragging = true;
        turning = false;
        lastPoint.set( event.getX(), event.getY() );
        touchOffset.set( centerX - event.getX(), centerY - event.getY() );
        postponeTimeout();
        postInvalidate();
        return true;
      } else if( inCircle( event, handleX, handleY, handleRadius )) {
        dragging = false;
        turning = true;
        lastPoint.set( event.getX(), event.getY() );
        touchOffset.set( handleX - event.getX(), handleY - event.getY() );
        postponeTimeout();
        postInvalidate();
        return true;
      }
      break;
    case MotionEvent.ACTION_UP: // last finger released
      if( dragging || turning ) {
        sendInitialPose();
        dragging = false;
        turning = false;
        postponeTimeout();
        postInvalidate();
        return true;
      }
      break;
    case MotionEvent.ACTION_MOVE:
      if( dragging ) {
        centerX = event.getX() + touchOffset.x;
        centerY = event.getY() + touchOffset.y;
        sendInitialPose();
        postponeTimeout();
        postInvalidate();
        return true;
      } else if( turning ) {
        handleX = event.getX() + touchOffset.x;
        handleY = event.getY() + touchOffset.y;
        handleAngleRadians = (float) Math.atan2( (float)(handleY - centerY), (float)(handleX - centerX) );
        sendInitialPose();
        postponeTimeout();
        postInvalidate();
        return true;
      }
      break;
    }
    return false;
  }

  private void postponeTimeout() {
    getParent().removeCallbacks( disabler );
    getParent().postDelayed( disabler, 3 * 1000 );
  }

  private void sendInitialPose() {
    if( initialPosePublisher == null ) {
      return;
    }

    // We have initial pose (centerX, centerY, handleAngleRadians) in the view frame.
    // initialRelMap = viewRelMap * initialRelView
    // viewRelMap = mapRelView.inverse()

    Matrix initialRelView = new Matrix();
    initialRelView.setTranslate( centerX, centerY );
    initialRelView.preRotate( handleAngleRadians * 180f / (float) Math.PI );

    Matrix initialRelMap = new Matrix();
    if( getParent().getFixedRelView().invert( initialRelMap )) {
      initialRelMap.preConcat( initialRelView );
      float[] values = new float[9];
      initialRelMap.getValues( values );

      // Get the direction by mapping a unit vector through initialRelMap.
      float[] dirVector = {1f, 0f};
      initialRelMap.mapVectors( dirVector );
      float angleRelMap = (float) Math.atan2( (double)dirVector[1], (double)dirVector[0] );
      Log.i("SetInitialPoseDisplay", "angleRelMap = " + angleRelMap);

      PoseWithCovarianceStamped initialPose = new PoseWithCovarianceStamped();
      initialPose.header.frame_id = fixedFrame;
      initialPose.pose.pose.position.x = values[2];
      initialPose.pose.pose.position.y = values[5];
      initialPose.pose.pose.position.z = 0;
      initialPose.pose.pose.orientation.x = 0;
      initialPose.pose.pose.orientation.y = 0;
      initialPose.pose.pose.orientation.z = FloatMath.sin( angleRelMap / 2f );
      initialPose.pose.pose.orientation.w = FloatMath.cos( angleRelMap / 2f );
      initialPose.pose.covariance[6*0+0] = 0.5 * 0.5; // X uncertainty
      initialPose.pose.covariance[6*1+1] = 0.5 * 0.5; // Y uncertainty
      initialPose.pose.covariance[6*5+5] = (float)(Math.PI/12.0 * Math.PI/12.0);  // uncertainty of rotation about Z axis
      
      Log.i("SetInitialPoseDisplay", "Sending initial pose");
      initialPosePublisher.publish( initialPose );

    } else {
      Log.e("SetInitialPoseDisplay", "fixed frame rel view matrix not invertible!  Not sending initial pose.");
    }
  }
}
