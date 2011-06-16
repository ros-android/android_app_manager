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

import org.ros.MessageListener;
import org.ros.Node;
import org.ros.Publisher;
import org.ros.exception.RosInitException;
import org.ros.message.geometry_msgs.PoseWithCovarianceStamped;

import ros.android.util.Posable;

/**
 * PanZoomDisplay which implements a draggable initial-pose setter.
 */
public class SetInitialPoseDisplay extends PanZoomDisplay implements Posable {
  private Paint paint = new Paint();
  private Paint linePaint = new Paint();
  private String initialPoseTopic = "initialpose";
  private Publisher<PoseWithCovarianceStamped> initialPosePublisher;
  private Matrix estimatedRobotRelMap = new Matrix();
  private Matrix estimatedRobotRelView = new Matrix();
  private boolean haveEstimatedPose = false;

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
    haveEstimatedPose = true;
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

  /**
   * Invalidate the current pose.
   */
  @Override
  public void reset() {
    haveEstimatedPose = false;
  }

  @Override
  public void draw( Canvas canvas ) {
    if( haveEstimatedPose ) {
      Matrix oldMatrix = canvas.getMatrix();

      estimatedRobotRelView = getParent().getFixedRelView();
      estimatedRobotRelView.preConcat( estimatedRobotRelMap );

      // Find the center of the robot in view coordinates
      float[] center = new float[2];
      center[0] = 0f;
      center[1] = 0f;
      estimatedRobotRelView.mapPoints( center );

      // Find the direction vector (which way the robot is pointing) in view coordinates.
      float[] dirVector = new float[2];
      dirVector[0] = 1f;
      dirVector[1] = 0f;
      estimatedRobotRelView.mapVectors( dirVector );

      // Normalize the direction vector.
      float dirLen = PointF.length( dirVector[0], dirVector[1] );
      dirVector[0] /= dirLen;
      dirVector[1] /= dirLen;

      canvas.setMatrix( getParent().getViewMatrix() );

      float handleCenterDistance = 100;
      float spotDiameter = 100;
      float handleDiameter = 75;
      float spotRadius = spotDiameter / 2f;
      float handleRadius = handleDiameter / 2f;
      float centerX = center[0];
      float centerY = center[1];
      float handleX = center[0] + dirVector[0] * handleCenterDistance;
      float handleY = center[1] + dirVector[1] * handleCenterDistance;

      canvas.drawCircle( centerX, centerY, spotRadius, paint );
      canvas.drawCircle( handleX, handleY, handleRadius, paint );
      canvas.drawLine( centerX, centerY, handleX, handleY, linePaint );

      canvas.setMatrix( oldMatrix );
    }
  }
}
