
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

import org.ros.message.MessageListener;
import org.ros.node.Node;
import android.graphics.Canvas;
import org.ros.node.topic.Subscriber;
import org.ros.exception.RosException;
import org.ros.message.nav_msgs.Path;
import org.ros.message.geometry_msgs.PoseStamped;
import android.util.Log;
import java.util.ArrayList;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * PanZoomDisplay which shows a path in a PanZoomView.
 */
public class PathDisplay extends PanZoomDisplay {
  private String pathTopic = "/move_base/NavfnROS/plan";
  private Subscriber pathSubscriber;
  private ArrayList<double []> path;
  private Paint paint = new Paint();


  private void onPathRecieved(Path path) {
    ArrayList<double []> p = new ArrayList<double []>();
    for (PoseStamped i : path.poses) {
      double[] n = new double[2];
      n[0] = i.pose.position.x;
      n[1] = i.pose.position.y;
      p.add(n);
    }
    this.path = p;
  }

  public void setColor ( int color ) {
    paint.setColor(color);
  }

  /**
   * Set the topic name for the path messages.  Defaults to "/move_base_node/NavfnROS/plan".
   */
  public void setTopic( String pathTopic ) {
    this.pathTopic = pathTopic;
  }
  public String getTopic() {
    return pathTopic;
  }

  @Override
  public void start( Node node ) throws RosException {
    final PathDisplay parent = this;
    pathSubscriber = node.newSubscriber(pathTopic, "nav_msgs/Path",
        new MessageListener<Path>() {
              @Override
              public void onNewMessage(final Path msg) {
                PathDisplay.this.onPathRecieved(msg);
              }});
    Log.i("PathDisplay", "Path display started");
  }

  @Override
  public void stop() {
    if(pathSubscriber != null) {
      pathSubscriber.shutdown();
    }
    pathSubscriber = null;
  }

  @Override
  public void draw( Canvas canvas ) {
    ArrayList<double []> p = path; //Avoid race conditions
    if (p != null) {
      if (p.size() > 0) {
        double[] prev = p.get(0);
        for (double[] next : p) {
          canvas.drawLine((float)prev[0], (float)prev[1], (float)next[0], (float)next[1], paint);
          prev = next;
        }
      }
    }
  }
}