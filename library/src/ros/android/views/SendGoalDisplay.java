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

import org.ros.node.Node;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.ros.message.MessageListener;
import org.ros.message.Time;
import org.ros.exception.RosException;
import org.ros.message.geometry_msgs.PoseStamped;
import org.ros.message.actionlib_msgs.GoalStatusArray;
import org.ros.message.actionlib_msgs.GoalStatus;
////
import org.ros.actionlib.client.SimpleActionClient;
import org.ros.actionlib.client.SimpleActionClientCallbacks;
import org.ros.actionlib.state.SimpleClientGoalState;
import org.ros.message.move_base_msgs.MoveBaseActionFeedback;
import org.ros.message.move_base_msgs.MoveBaseActionGoal;
import org.ros.message.move_base_msgs.MoveBaseAction;
import org.ros.message.move_base_msgs.MoveBaseActionResult;
import org.ros.message.move_base_msgs.MoveBaseFeedback;
import org.ros.message.move_base_msgs.MoveBaseGoal;
import org.ros.message.move_base_msgs.MoveBaseResult;
import org.ros.actionlib.ActionSpec;
import org.ros.node.NodeConfiguration;
import org.ros.namespace.NameResolver;


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
public class SendGoalDisplay extends PoseInputDisplay implements SimpleActionClientCallbacks<MoveBaseFeedback, MoveBaseResult> {
  private String goalTopic = "move_base_simple/goal";
  private String statusTopic = "move_base/status";
  private String fixedFrame = "/map";
  private boolean failed = false;
  private Publisher<PoseStamped> publisher;
  private Subscriber<GoalStatusArray> subscriber;
  private boolean followRobotMode;
  private SimpleActionClient<MoveBaseActionFeedback, MoveBaseActionGoal, 
    MoveBaseActionResult, MoveBaseFeedback, MoveBaseGoal, MoveBaseResult> move_base_action;
  private NodeConfiguration nodeConfiguration;

  public SendGoalDisplay() {
    super();
    setColor( 0x80ff80 );
  }

  public void setNodeConfiguration(NodeConfiguration c) {
    nodeConfiguration = c;
  }

  public void setTopic( String topic ) {
    this.goalTopic = topic;
  }
  public String getTopic() {
    return goalTopic;
  }

  public void setStatusTopic( String topic ) {
    this.statusTopic = topic;
  }
  public String getStatusTopic() {
    return statusTopic;
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

  public void onStatusRecieved(final GoalStatusArray msg) {
    boolean hasFailed = false;
    Time latest = null;
    for (GoalStatus status : msg.status_list) {
      if (latest == null || status.goal_id.stamp.compareTo(latest) == 1) {
        latest = status.goal_id.stamp;
        hasFailed = (status.status == GoalStatus.ABORTED || status.status == GoalStatus.REJECTED);
      }
    }
    if (hasFailed) {
      setColor( 0xff8080 );
    } else {
      setColor( 0x80ff80 );
    }
    if (failed != hasFailed) {
      postInvalidate();
      failed = hasFailed;
    }
  }

  public void activeCallback() {
    setColor( 0x80ff80 );
  }

  public void feedbackCallback(MoveBaseFeedback feedback) {
  }

  public void doneCallback(SimpleClientGoalState state, MoveBaseResult result) {
    if (state.getState() == SimpleClientGoalState.StateEnum.ABORTED
        || state.getState() == SimpleClientGoalState.StateEnum.REJECTED) {
      setColor( 0xff8080 );
    } else {
      setColor( 0x80ff80 );
    }
  }

  @Override
  public void start( Node node ) throws RosException {
    super.start( node );
    try {
      move_base_action = new SimpleActionClient("move_base_client_android", new ActionSpec<MoveBaseAction, 
                                                MoveBaseActionFeedback, MoveBaseActionGoal, 
                                                MoveBaseActionResult, MoveBaseFeedback, 
                                                MoveBaseGoal, MoveBaseResult>
                                                (MoveBaseAction.class,
                                                 "move_base_msgs/MoveBaseAction",
                                                 "move_base_msgs/MoveBaseActionFeedback", 
                                                 "move_base_msgs/MoveBaseActionGoal",
                                                 "move_base_msgs/MoveBaseActionResult", 
                                                 "move_base_msgs/MoveBaseFeedback",
                                                 "move_base_msgs/MoveBaseGoal",
                                                 "move_base_msgs/MoveBaseResult"));
      NodeConfiguration nc = NodeConfiguration.copyOf(nodeConfiguration);
      nc.setParentResolver(NameResolver.create("/move_base"));
      move_base_action.main(nc); //TODO: fix this so we don't create a new node
    } catch (Exception e) {
      Log.e("SendGoalDisplay", "ActionClient failed!");
      e.printStackTrace();
      move_base_action = null;
      publisher = node.newPublisher( goalTopic, "geometry_msgs/PoseStamped" );
      subscriber = node.newSubscriber( statusTopic, "actionlib_msgs/GoalStatusArray",
                                       new MessageListener<GoalStatusArray>() {
                                         @Override
                                         public void onNewMessage(final GoalStatusArray msg) {
                                           SendGoalDisplay.this.onStatusRecieved(msg);
                                         }});
    }
  }

  @Override
  public void stop() {
    super.stop();
    if( publisher != null) {
      publisher.shutdown();
    }
    publisher = null;
    if( subscriber != null) {
      subscriber.shutdown();
    }
    subscriber = null;
    if(  move_base_action != null) {
       move_base_action.shutdown();
    }
     move_base_action = null;
  }

  @Override
  protected void onPose( float x, float y, float angle ) {
    followRobotMode = false;

    PoseStamped goal = new PoseStamped();
    goal.header.frame_id = fixedFrame;
    goal.pose.position.x = x;
    goal.pose.position.y = y;
    goal.pose.position.z = 0;
    goal.pose.orientation.x = 0;
    goal.pose.orientation.y = 0;
    goal.pose.orientation.z = FloatMath.sin( angle / 2f );
    goal.pose.orientation.w = FloatMath.cos( angle / 2f );
    
    if (move_base_action != null) {
      Log.i("SendGoalDisplay", "Sending action goal.");
      MoveBaseGoal base_goal = new MoveBaseGoal();
      base_goal.target_pose = goal;
      try {
        move_base_action.sendGoal(base_goal, this);
      } catch (Exception e) {
        Log.e("SendGoalDisplay", "Error on sending goal:");
        e.printStackTrace();
      }
      return;
    }


    if( publisher == null ) {
      return;
    }


    Log.i("SendGoalDisplay", "Sending goal.");
    publisher.publish( goal );
  }
}
