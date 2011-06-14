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

package ros.android.util;

import android.graphics.Matrix;

import java.util.HashMap;
import java.util.ArrayList;

import javax.vecmath.Matrix4d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import org.ros.MessageListener;
import org.ros.Node;
import org.ros.Subscriber;
import org.ros.exception.RosInitException;
import org.ros.message.geometry_msgs.TransformStamped;
import org.ros.message.tf.tfMessage;

/**
 * Listener for tf messages which does not compose transforms, it just
 * looks for matching transforms, flattens them to a plane, and sends
 * them to Posable objects.
 * 
 * This can listen to tf messages coming from a change_notifier node
 * (in package "tf"), for instance, which can be configured to emit
 * transforms between specific pairs of frames.
 *
 * The flattening onto a plane is always along the Z axis, it is not
 * configurable.
 */
public class PlaneTfChangeListener {
  private Subscriber<tfMessage> tfSubscriber;
  private String tfTopic = "tf_changes";

  /**
   * Top-level key is fixed frame name, next-level key is moving frame
   * name, final value is list of objects which need the poses.
   */
  private HashMap<String, HashMap<String, ArrayList<Posable>>> transformReceivers =
    new HashMap<String, HashMap<String, ArrayList<Posable>>>();

  /**
   * Set the topic name on which to listen to tf messages.  Default is
   * "tf_changes" which is where the tf "change_notifier" node
   * publishes by default.
   */
  public void setTopic( String tfTopic ) {
    this.tfTopic = tfTopic;
  }
  public String getTopic() {
    return tfTopic;
  }

  /**
   * Set up a Posable object to receive transforms between the given
   * moving and fixed frames.  Multiple Posable objects can listen to
   * the same transform.
   */
  public void addPosable( String fixedFrame, String movingFrame, Posable posable ) {
    HashMap<String, ArrayList<Posable>> fixedMap = transformReceivers.get( fixedFrame );
    if( fixedMap == null ) {
      fixedMap = new HashMap<String, ArrayList<Posable>>();
      transformReceivers.put( fixedFrame, fixedMap );
    }

    ArrayList<Posable> posables = fixedMap.get( movingFrame );
    if( posables == null ) {
      posables = new ArrayList<Posable>();
      fixedMap.put( movingFrame, posables );
    }

    posables.add( posable );
  }

  /**
   * Remove a given posable subscription to the given transform.
   */
  public void removePosable( String fixedFrame, String movingFrame, Posable posable ) {
    HashMap<String, ArrayList<Posable>> fixedMap = transformReceivers.get( fixedFrame );
    if( fixedMap == null ) {
      return;
    }
    ArrayList<Posable> posables = fixedMap.get( movingFrame );
    if( posables == null ) {
      return;
    }
    while( posables.remove( posable )) {}
  }

  /**
   * Start listening to tf messages.
   */
  public void start( Node node ) throws RosInitException {
    tfSubscriber = node.createSubscriber(tfTopic, new MessageListener<tfMessage>() {
        @Override
        public void onNewMessage(final tfMessage msg) {
          if (msg != null) {
            handleTfMessage( msg );
          }
        }
      }, tfMessage.class);
  }

  /**
   * Stop listening to tf messages.
   */
  public void stop() {
    if( tfSubscriber != null ) {
      tfSubscriber.cancel();
    }
    tfSubscriber = null;
  }

  /**
   * Return the list of Posable objects receiving the given transform.
   */
  private ArrayList<Posable> getReceivers( String fixedFrame, String movingFrame ) {
    HashMap<String, ArrayList<Posable>> fixedMap = transformReceivers.get( fixedFrame );
    if( fixedMap == null ) {
      return null;
    }
    return fixedMap.get( movingFrame );
  }

  /**
   * Flatten each transform received and send them out to the
   * corresponding Posables (if any).
   */
  private void handleTfMessage( tfMessage msg ) {
    Matrix matrix = new Matrix();

    for(TransformStamped tf : msg.transforms) {
      ArrayList<Posable> receivers = getReceivers( tf.header.frame_id, tf.child_frame_id );
      if( receivers != null ) {
        setMatrixFromTransformStamped( matrix, tf );
        for( Posable receiver: receivers ) {
          receiver.setPose( matrix );
        }
      }
    }
  }

  /**
   * Flatten the 3D transform in xform along the Z axis into the 2D
   * transform matrix2d.
   */
  static public void setMatrixFromTransformStamped( Matrix matrix2d, TransformStamped xform ) {
    org.ros.message.geometry_msgs.Vector3 trans = xform.transform.translation;
    org.ros.message.geometry_msgs.Quaternion rot = xform.transform.rotation;

    Matrix4d xform3d = new Matrix4d(new Quat4d(rot.x, rot.y, rot.z, rot.w),
                                    new Vector3d(trans.x, trans.y, trans.z),
                                    1);
    matrix2d.setValues(new float[]{ (float)xform3d.m00, (float)xform3d.m01, (float)xform3d.m03,
                                    (float)xform3d.m10, (float)xform3d.m11, (float)xform3d.m13,
                                    0, 0, 1 });
  }
}
