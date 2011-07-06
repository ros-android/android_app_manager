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
import ros.android.util.FingerReceiver;
import ros.android.util.FingerTracker;

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
 * drag gestures.
 */
public class SetInitialPoseDisplay extends PoseInputDisplay {
  private String initialPoseTopic = "initialpose";
  private String fixedFrame = "/map";
  private Publisher<PoseWithCovarianceStamped> initialPosePublisher;

  public SetInitialPoseDisplay() {
    super();
    setColor( 0xff8080 );
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
    initialPosePublisher =
	node.createPublisher( initialPoseTopic, "geometry_msgs/PoseWithCovarianceStamped" );
  }

  @Override
  public void stop() {
    super.stop();
    if( initialPosePublisher != null) {
      initialPosePublisher.shutdown();
    }
    initialPosePublisher = null;
  }

  @Override protected void onPose( float x, float y, float angle ) {
    if( initialPosePublisher == null ) {
      return;
    }

    PoseWithCovarianceStamped initialPose = new PoseWithCovarianceStamped();
    initialPose.header.frame_id = fixedFrame;
    initialPose.pose.pose.position.x = x;
    initialPose.pose.pose.position.y = y;
    initialPose.pose.pose.position.z = 0;
    initialPose.pose.pose.orientation.x = 0;
    initialPose.pose.pose.orientation.y = 0;
    initialPose.pose.pose.orientation.z = FloatMath.sin( angle / 2f );
    initialPose.pose.pose.orientation.w = FloatMath.cos( angle / 2f );
    initialPose.pose.covariance[6*0+0] = 0.5 * 0.5; // X uncertainty
    initialPose.pose.covariance[6*1+1] = 0.5 * 0.5; // Y uncertainty
    initialPose.pose.covariance[6*5+5] = (float)(Math.PI/12.0 * Math.PI/12.0);  // uncertainty of rotation about Z axis

    Log.i("SetInitialPoseDisplay", "Sending initial pose");
    initialPosePublisher.publish( initialPose );
  }
}
