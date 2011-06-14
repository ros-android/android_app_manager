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
import org.ros.Subscriber;
import org.ros.exception.RosInitException;
import org.ros.message.sensor_msgs.LaserScan;

/**
 * PanZoomDisplay which shows a top view of a laser range finder scan.
 */
public class LaserScanDisplay extends PosablePanZoomDisplay {
  private Paint paint = new Paint();
  private String scanTopic;
  private LaserScan rangeScan;
  private boolean haveScan = false;
  private Subscriber<LaserScan> scanSubscriber;

  public LaserScanDisplay() {
    paint.setColor(0x80ffff00);
  }

  public void setTopic( String scanTopic ) {
    this.scanTopic = scanTopic;
  }
  public String getTopic() {
    return scanTopic;
  }

  @Override
  public void start( Node node ) throws RosInitException {
    super.start( node );
    scanSubscriber =
        node.createSubscriber(scanTopic, new MessageListener<LaserScan>() {
          @Override
          public void onNewMessage(final LaserScan msg) {
            rangeScan = msg;
            haveScan = true;
            postInvalidate();
          }
        }, LaserScan.class);
  }

  @Override
  public void stop() {
    super.stop();
    if(scanSubscriber != null) {
      scanSubscriber.cancel();
    }
    scanSubscriber = null;
  }

  @Override
  public void drawAtPose( Canvas canvas ) {
    if( haveScan ) {
      float[] lineEndPoints = new float[rangeScan.ranges.length*4];
      int numEndPoints = 0;
      float angle = rangeScan.angle_min;
      for( float range: rangeScan.ranges ) {
        // Only process ranges which are in the valid range.
        if( rangeScan.range_min <= range && range <= rangeScan.range_max ) {
          PointF near = new PointF( FloatMath.cos(angle) * rangeScan.range_min, FloatMath.sin(angle) * rangeScan.range_min ); 
          PointF far = new PointF( FloatMath.cos(angle) * range, FloatMath.sin(angle) * range );
          lineEndPoints[numEndPoints++] = near.x;
          lineEndPoints[numEndPoints++] = near.y;
          lineEndPoints[numEndPoints++] = far.x;
          lineEndPoints[numEndPoints++] = far.y;
        }
        angle += rangeScan.angle_increment;
      }
      canvas.drawLines(lineEndPoints, 0, numEndPoints, paint);
    }
  }
}
