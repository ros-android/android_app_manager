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

import android.graphics.Matrix;
import android.util.FloatMath;
import android.util.Log;

import org.ros.Node;
import org.ros.Publisher;
import org.ros.exception.RosInitException;
import org.ros.message.geometry_msgs.PoseStamped;

/**
 * PanZoomDisplay which implements a draggable goal-pose setter.
 *
 * The control has two parts: a central circle for translation and a
 * smaller outboard circle for rotation.
 *
 * Behavior: When the display is enabled and a goal has not yet been
 * set, the pose is set by incoming Matrices via the Posable interface
 * (i.e. originating from TF messages describing where the robot
 * thinks it currently is.
 *
 * When the user touches inside one of the control circles, those
 * incoming Poses are ignored and the pose is updated by the user's
 * drag gestures.
 *
 * After the user lets go, the pose stays the same until the display
 * times out.  (Timeout implemented in PoseInputDisplay).
 */
public class SendGoalDisplay extends PoseInputDisplay {
  private String goalTopic = "move_base_simple/goal";
  private String fixedFrame = "/map";
  private Publisher<PoseStamped> publisher;
  private boolean followRobotMode;

  public SendGoalDisplay() {
    super();
    Log.i("SendGoalDisplay", "constructor");
    setColor( 0x80ff80 );
  }

  public void setTopic( String topic ) {
    Log.i("SendGoalDisplay", "setTopic");
    this.goalTopic = topic;
  }
  public String getTopic() {
    return goalTopic;
  }

  /**
   * Set the frame ID for the fixed frame which the goal pose is
   * set relative to.  Defaults to "/map".
   */
  public void setFixedFrame( String fixedFrame ) {
    this.fixedFrame = fixedFrame;
  }
  public String getFixedFrame() {
    return fixedFrame;
  }

  @Override
  public void enable() {
    super.enable();
    followRobotMode = true;
  }

  @Override
  public void setPose( Matrix poseRelFixedFrame ) {
    if( followRobotMode ) {
      super.setPose( poseRelFixedFrame );
    }
  }

  @Override
  public void start( Node node ) throws RosInitException {
    super.start( node );
    publisher = node.createPublisher( goalTopic, "geometry_msgs/PoseStamped" );
  }

  @Override
  public void stop() {
    super.stop();
    if( publisher != null) {
      publisher.shutdown();
    }
    publisher = null;
  }

  @Override
  protected void onPose( float x, float y, float angle ) {
    followRobotMode = false;

    if( publisher == null ) {
      return;
    }

    PoseStamped goal = new PoseStamped();
    goal.header.frame_id = fixedFrame;
    goal.pose.position.x = x;
    goal.pose.position.y = y;
    goal.pose.position.z = 0;
    goal.pose.orientation.x = 0;
    goal.pose.orientation.y = 0;
    goal.pose.orientation.z = FloatMath.sin( angle / 2f );
    goal.pose.orientation.w = FloatMath.cos( angle / 2f );

    Log.i("SendGoalDisplay", "Sending goal.");
    publisher.publish( goal );
  }
}
