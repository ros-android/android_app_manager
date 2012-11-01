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

import geometry_msgs.Pose;
import geometry_msgs.PoseStamped;
import move_base_msgs.MoveBaseAction;
import move_base_msgs.MoveBaseActionFeedback;
import move_base_msgs.MoveBaseActionGoal;
import move_base_msgs.MoveBaseActionResult;
import move_base_msgs.MoveBaseFeedback;
import move_base_msgs.MoveBaseGoal;
import move_base_msgs.MoveBaseResult;

import org.ros.exception.RosException;
import org.ros.message.MessageListener;
import org.ros.message.Time;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.DefaultNodeFactory;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.Node;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import android.graphics.Matrix;
import android.util.FloatMath;
import android.util.Log;

////

/**
 * PanZoomDisplay which implements a draggable goal-pose setter.
 * 
 * The control has two parts: a central circle for translation and a smaller
 * outboard circle for rotation.
 * 
 * Behavior: When the display is enabled and a goal has not yet been set, the
 * pose is set by incoming Matrices via the Posable interface (i.e. originating
 * from TF messages describing where the robot thinks it currently is.
 * 
 * When the user touches inside one of the control circles, those incoming Poses
 * are ignored and the pose is updated by the user's drag gestures.
 * 
 * After the user lets go, the pose stays the same until the display times out.
 * (Timeout implemented in PoseInputDisplay).
 */
public class SendGoalDisplay extends PoseInputDisplay implements SimpleActionClientCallbacks<MoveBaseFeedback, MoveBaseResult> {
	private String goalTopic = "move_base_simple/goal";
	private String statusTopic = "move_base/status";
	private String fixedFrame = "/map";
	private boolean failed = false;
	private Publisher<PoseStamped> publisher;
	private Subscriber<GoalStatusArray> subscriber;
	private boolean followRobotMode;
	private SimpleActionClient<MoveBaseActionFeedback, MoveBaseActionGoal, MoveBaseActionResult, MoveBaseFeedback, MoveBaseGoal, MoveBaseResult> move_base_action;
	private NodeConfiguration nodeConfiguration;

	public SendGoalDisplay() {
		super();
		setColor(0x80ff80);
	}

	public void setNodeConfiguration(NodeConfiguration c) {
		nodeConfiguration = c;
	}

	public void setTopic(String topic) {
		this.goalTopic = topic;
	}

	public String getTopic() {
		return goalTopic;
	}

	public void setStatusTopic(String topic) {
		this.statusTopic = topic;
	}

	public String getStatusTopic() {
		return statusTopic;
	}

	/**
	 * Set the frame ID for the fixed frame which the goal pose is set relative
	 * to. Defaults to "/map".
	 */
	public void setFixedFrame(String fixedFrame) {
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
	public void setPose(Matrix poseRelFixedFrame) {
		if(followRobotMode) {
			super.setPose(poseRelFixedFrame);
		}
	}

	public void onStatusRecieved(final GoalStatusArray msg) {
		boolean hasFailed = false;
		Time latest = null;
		for(GoalStatus status : msg.getStatusList()) {
			if(latest == null || status.getGoalId().getStamp().compareTo(latest) == 1) {
				latest = status.getGoalId().getStamp();
				hasFailed = (status.getStatus() == GoalStatus.ABORTED || status.getStatus() == GoalStatus.REJECTED);
			}
		}
		if(hasFailed) {
			setColor(0xff8080);
		} else {
			setColor(0x80ff80);
		}
		if(failed != hasFailed) {
			postInvalidate();
			failed = hasFailed;
		}
	}

	public void activeCallback() {
		setColor(0x80ff80);
	}

	public void feedbackCallback(MoveBaseFeedback feedback) {
	}

	public void doneCallback(SimpleClientGoalState state, MoveBaseResult result) {
		if(state.getState() == SimpleClientGoalState.StateEnum.ABORTED || state.getState() == SimpleClientGoalState.StateEnum.REJECTED) {
			setColor(0xff8080);
		} else {
			setColor(0x80ff80);
		}
	}

	class MoveBaseActionSpec extends ActionSpec<MoveBaseAction, MoveBaseActionFeedback, MoveBaseActionGoal, MoveBaseActionResult, MoveBaseFeedback, MoveBaseGoal, MoveBaseResult> {

		public MoveBaseActionSpec() throws RosException {
			super(MoveBaseAction.class, "move_base_msgs/MoveBaseAction", "move_base_msgs/MoveBaseActionFeedback", "move_base_msgs/MoveBaseActionGoal", "move_base_msgs/MoveBaseActionResult", "move_base_msgs/MoveBaseFeedback", "move_base_msgs/MoveBaseGoal", "move_base_msgs/MoveBaseResult");
		}
	}

	private ConnectedNode node;
	
	@Override
	public void start(ConnectedNode node) throws RosException {
		super.start(node);
		this.node = node;
		try {
			MoveBaseActionSpec spec = new MoveBaseActionSpec();
			move_base_action = spec.buildSimpleActionClient("/move_base");

			// This is some bad code that is needed because action clients don't
			// prepend the name variable. Is this a bug in ActionClient?
			NodeConfiguration nc = NodeConfiguration.copyOf(nodeConfiguration);
			nc.setParentResolver(NameResolver.newFromNamespace("/move_base"));
			nc.setNodeName("/android_move_base");
			NodeMainExecutor nodeMainExecutor = DefaultNodeMainExecutor.newDefault();
			Node newNode = new DefaultNodeFactory(nodeMainExecutor.getScheduledExecutorService()).newNode(nc);
			// Should just refer to node.
			move_base_action.addClientPubSub(newNode);
		} catch(Exception e) {
			Log.e("SendGoalDisplay", "ActionClient failed!");
			e.printStackTrace();
			move_base_action = null;
			publisher = node.newPublisher(goalTopic, "geometry_msgs/PoseStamped");
			subscriber = node.newSubscriber(statusTopic, "actionlib_msgs/GoalStatusArray");
			subscriber.addMessageListener(new MessageListener<GoalStatusArray>() {
				@Override
				public void onNewMessage(final GoalStatusArray msg) {
					SendGoalDisplay.this.onStatusRecieved(msg);
				}
			});
		}
	}

	@Override
	public void stop() {
		super.stop();
		if(publisher != null) {
			publisher.shutdown();
		}
		publisher = null;
		if(subscriber != null) {
			subscriber.shutdown();
		}
		subscriber = null;
		if(move_base_action != null) {
			move_base_action.shutdown();
		}
		move_base_action = null;
	}

	@Override
	protected void onPose(float x, float y, float angle) {
		followRobotMode = false;

		PoseStamped goal = node.getTopicMessageFactory().newFromType(PoseStamped._TYPE);
		goal.getHeader().setFrameId(fixedFrame);
		Pose pose = goal.getPose();
		
		pose.getPosition().setX(x);
		pose.getPosition().setY(y);
		pose.getPosition().setZ(0);
		pose.getOrientation().setX(0);
		pose.getOrientation().setY(0);
		pose.getOrientation().setZ(FloatMath.sin(angle / 2f));
		pose.getOrientation().setW(FloatMath.cos(angle / 2f));

		if(move_base_action != null) {
			Log.i("SendGoalDisplay", "Sending action goal.");
			MoveBaseGoal base_goal = new MoveBaseGoal();
			base_goal.target_pose = goal;
			try {
				move_base_action.sendGoal(base_goal, this);
			} catch(Exception e) {
				Log.e("SendGoalDisplay", "Error on sending goal:");
				e.printStackTrace();
			}
			return;
		}

		if(publisher == null) {
			return;
		}

		Log.i("SendGoalDisplay", "Sending goal.");
		publisher.publish(goal);
	}
}
