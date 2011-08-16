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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.widget.ImageView;
import android.util.AttributeSet;
import android.util.Log;
import org.yaml.snakeyaml.Yaml;
import java.util.List;
import org.ros.node.parameter.ParameterTree;
import java.lang.Thread;
import org.ros.message.geometry_msgs.Twist;
import android.view.MotionEvent;
import org.ros.node.Node;
import org.ros.exception.RosException;
import ros.android.util.PlaneTfChangeListener;
import android.view.View.OnTouchListener;
import android.view.View;
import org.ros.node.Node;
import org.ros.node.topic.Publisher;
import org.ros.node.service.ServiceResponseListener;
import org.ros.node.topic.Subscriber;
import org.ros.exception.RosException;
import org.ros.exception.RemoteException;
import org.ros.node.service.ServiceClient;
import org.ros.internal.node.service.ServiceIdentifier;
import org.ros.message.Message;

import ros.android.activity.R;

/**
 * View for screen-based joystick teleop.
 */
public class JoystickView extends ImageView implements OnTouchListener {
  private String baseControlTopic;
  private Twist touchCmdMessage;
  private Thread pubThread;
  private float motionY;
  private float motionX;
  private Publisher<Twist> twistPub;

  public JoystickView(Context ctx) {
    super(ctx);
    init(ctx);
  }

  public JoystickView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  public JoystickView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    baseControlTopic = "turtlebot_node/cmd_vel";
    touchCmdMessage = new Twist();
  }

  public void setBaseControlTopic(String t) {
    baseControlTopic = t;
    this.setOnTouchListener(this);
  }

  private <T extends Message> void createPublisherThread(final Publisher<T> pub, final T message,
      final int rate) {
    pubThread = new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          while (true) {
            pub.publish(message);
            Thread.sleep(1000 / rate);
          }
        } catch (InterruptedException e) {
        }
      }
    });
    Log.i("JoystickView", "started pub thread");
    pubThread.start();
  }
  
  public void start(Node node) throws RosException { 
    Log.i("JoystickView", "init twistPub");
    twistPub = node.newPublisher(baseControlTopic, "geometry_msgs/Twist");
    createPublisherThread(twistPub, touchCmdMessage, 10);
  }

  public void stop() {
    if (pubThread != null) {
      pubThread.interrupt();
      pubThread = null;
    }
    if (twistPub != null) {
      twistPub.shutdown();
      twistPub = null;
    }
  }

  
  @Override
  public boolean onTouch(View arg0, MotionEvent motionEvent) {
    int action = motionEvent.getAction();
    if (arg0 == this && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
      motionX = (motionEvent.getX() - (arg0.getWidth() / 2)) / (arg0.getWidth());
      motionY = (motionEvent.getY() - (arg0.getHeight() / 2)) / (arg0.getHeight());

      touchCmdMessage.linear.x = -2 * motionY;
      touchCmdMessage.linear.y = 0;
      touchCmdMessage.linear.z = 0;
      touchCmdMessage.angular.x = 0;
      touchCmdMessage.angular.y = 0;
      touchCmdMessage.angular.z = -5 * motionX;
    } else {
      touchCmdMessage.linear.x = 0;
      touchCmdMessage.linear.y = 0;
      touchCmdMessage.linear.z = 0;
      touchCmdMessage.angular.x = 0;
      touchCmdMessage.angular.y = 0;
      touchCmdMessage.angular.z = 0;
    }
    return true;
  }
}
