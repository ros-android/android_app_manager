/*
 * Copyright (c) 2011, Sjoerd van den Dries
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
 *     * Neither the name of the Technische Universiteit Eindhoven nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
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

package ros.tf;

import javax.vecmath.Vector3d;
import javax.vecmath.Quat4d;

import org.ros.Node;
import org.ros.Publisher;
import org.ros.message.Time;
import org.ros.message.tf.tfMessage;
import org.ros.message.geometry_msgs.TransformStamped;
import org.ros.message.geometry_msgs.Vector3;
import org.ros.message.geometry_msgs.Quaternion;

import java.util.ArrayList;

/**
 *
 * @author Sjoerd van den Dries
 * @version March 3, 2011
 *
 * Class for broadcasting tf messages.
 *
 */
public class TfBroadcaster {

  private Publisher<tfMessage> tfPublisher;

  /**
   * Create a publisher from the given node.  Must be called before
   * any sendTransform() calls.
   */
  public void start(Node node) throws org.ros.exception.RosInitException {
    stop();
    tfPublisher = node.createPublisher("/tf", "tf/tfMessage");
  }

  /**
   * Shutdown the publisher if it exists.
   */
  public void stop() {
    if( tfPublisher != null ) {
      tfPublisher.shutdown();
    }
    tfPublisher = null;
  }

  /**
   * Publishes a tf message on the tf topic with the specified parameters.
   * Only call this after start() and before stop().
   */
  public void sendTransform(Vector3d transl, Quat4d rot, Time time, String parentFrame, String childFrame) {
    // convert translation vector and quaternion to geometry messages
    Vector3 tMsg = new Vector3();
    Quaternion rMsg = new Quaternion();
    tMsg.x = transl.x; tMsg.y = transl.y; tMsg.z = transl.z;
    rMsg.x = rot.x; rMsg.y = rot.y; rMsg.z = rot.z; rMsg.w = rot.w;

    // create TransformStamped message (is a geometry msg, do NOT confuse with StampedTransform class)
    TransformStamped tfMsg = new TransformStamped();
    tfMsg.header.frame_id = parentFrame;
    tfMsg.header.stamp = time;
    tfMsg.child_frame_id = childFrame;
    tfMsg.transform.translation = tMsg;
    tfMsg.transform.rotation = rMsg;

    // create tfMessage and add TransformStamped message to it
    tfMessage msg = new tfMessage();
    msg.transforms = new ArrayList<TransformStamped>();
    msg.transforms.add(tfMsg);

    // publish the message
    tfPublisher.publish(msg);
  }

  /**
   * Publishes a tf message on the tf topic with the specified transform.
   * Only call this after start() and before stop().
   */
  public void sendTransform(StampedTransform t) {
    sendTransform(t.getTranslation(), t.getRotation(), t.timeStamp, t.frameID, t.childFrameID);
  }
}
