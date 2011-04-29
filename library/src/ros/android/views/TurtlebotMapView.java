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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import javax.vecmath.Matrix4d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import org.ros.MessageListener;
import org.ros.Node;
import org.ros.Subscriber;
import org.ros.exceptions.RosInitException;
import org.ros.message.Time;
import org.ros.message.nav_msgs.OccupancyGrid;

import ros.tf.TfListener;
import ros.tf.StampedTransform;

import ros.android.activity.R;

/**
 * View of the latest map with the turtlebot drawn in where TF thinks it is.
 */
public class TurtlebotMapView extends View {
  private Subscriber<OccupancyGrid> mapSubscriber;
  private Bitmap mapBitmap;

  private TfListener tfListener = new TfListener();
  private StampedTransform robotPose;
  private Vector3d pos3d;
  private Quat4d rot3d;
  private Bitmap robotBitmap;
  private Paint robotPaint;
  private Timer updateTimer;

  // 2D coordinate transforms
  private Matrix robotImageRelRobot; // constant, based on robot image
  private Matrix robotRelMap; // from TF
  private Matrix mapGridRelMap; // from map metadata
  private Matrix mapRelView; // controlled by user pan and zoom

  private Matrix mapGridRelView; // computed for showing map
  private Matrix robotImageRelView; // computed for showing robot

  private boolean havePose;
  private boolean haveMap;

  private float oldDist; // Previous distance between two fingers on the screen.
  private PointF oldCenter; // Previous center between two fingers on the screen, or previous single finger position.
  private boolean oldCenterValid;

  public TurtlebotMapView(Context ctx) {
    super(ctx);
    init(ctx);
  }

  public TurtlebotMapView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  public TurtlebotMapView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    robotBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.turtlebot_top_view);
    robotPaint = new Paint();

    double robotImageResolution = .314 /* meters */ / 104 /* pixels */;
    double xOffsetPixels = 44;
    double yOffsetPixels = 52;

    // This transform assumes the robot in the image has its +x
    // pointing to the left and its +y pointing down.
    robotImageRelRobot = new Matrix();
    robotImageRelRobot.setValues(new float[]{(float)-robotImageResolution, 0, (float)(xOffsetPixels * robotImageResolution),
                                             0, (float)robotImageResolution, (float)(-yOffsetPixels * robotImageResolution),
                                             0, 0, 1});
    Log.i("TurtlebotMapView", "robotImageRelRobot = " + robotImageRelRobot.toString());

    // map grid coordinates are pixels with the origin in the lower-left corner
    mapRelView = new Matrix();
    mapRelView.setValues( new float[]{5f, 0, 0,
                                      0, -5f, 0,
                                      0, 0, 1f} );

    Log.i("TurtlebotMapView", "mapRelView = " + mapRelView.toString());

    mapGridRelView = new Matrix();
    robotImageRelView = new Matrix();
    robotRelMap = new Matrix();
    mapGridRelMap = new Matrix();
    havePose = false;
    haveMap = false;
    oldCenter = new PointF();
    oldCenterValid = false;
    oldDist = 0;
  }

  public void start(Node node, String mapTopic) throws RosInitException {
    stop();
    mapSubscriber =
        node.createSubscriber(mapTopic, new MessageListener<OccupancyGrid>() {
          @Override
          public void onNewMessage(final OccupancyGrid msg) {
            TurtlebotMapView.this.post(new Runnable() {
              @Override
              public void run() {
                TurtlebotMapView.this.handleMap(msg);
              }
            });
          }
        }, OccupancyGrid.class);

    tfListener.start(node);
    updateTimer = new Timer(true);
    updateTimer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          updateRobotPose();
        }
      }, 0, 100);
  }

  public void stop() {
    if( updateTimer != null ) {
      updateTimer.cancel();
      updateTimer.purge();
      updateTimer = null;
    }
    tfListener.stop();

    if(mapSubscriber != null) {
      mapSubscriber.cancel();
    }
    mapSubscriber = null;
  }

  /**
   * Populate view with new map data. This must be called in the UI
   * thread.
   */
  private void handleMap(OccupancyGrid msg) {
    Log.i("TurtlebotMapView", "handleMap()");
    if( mapBitmap != null && (mapBitmap.getWidth() != (int)msg.info.width || mapBitmap.getHeight() != (int)msg.info.height)) {
      mapBitmap.recycle();
      mapBitmap = null;
    }
    if( mapBitmap == null ) {
      mapBitmap = Bitmap.createBitmap((int)msg.info.width, (int)msg.info.height, Bitmap.Config.RGB_565);
    }

    // copy the map data into the mapBitmap.
    int data_i = 0;
    for (int y = 0; y < msg.info.height; y++) {
      for (int x = 0; x < msg.info.width; x++) {
        int cell = msg.data[data_i];
        data_i++;
        int red = 128;
        int green = 128;
        int blue = 128;
        switch(cell) {
        case 100:
          red = 255;
          green = 255;
          blue = 255;
          break;
        case 0:
          red = 0;
          green = 0;
          blue = 0;
          break;
        }
        mapBitmap.setPixel(x, y, Color.rgb(blue, green, red));
      }
    }

    haveMap = true;
    // This matrix definition presumes the map is flat on the XY plane
    // and that there is 0 rotation.  So just an offset and a scale.
    float res = (float)msg.info.resolution;
    mapGridRelMap.setValues( new float[]{ res, 0, (float)msg.info.origin.position.x,
                                          0, res, (float)msg.info.origin.position.y,
                                          0, 0, 1 });
    Log.i("TurtlebotMapView", "mapGridRelMap = " + mapGridRelMap.toString());

    postInvalidate();
  }

  private void updateRobotPose() {
    StampedTransform pose = tfListener.lookupTransform("map", "base_footprint",
                                                       Time.fromMillis(System.currentTimeMillis()));
    if(pose == null) {
      return;
    }
    if(robotPose == null || !pose.getMatrix4().equals(robotPose.getMatrix4())) {
      robotPose = pose;
      Matrix4d xform3d = robotPose.getMatrix4();
      Log.i("TurtlebotMapView", "robot pose = " + xform3d.toString());
      robotRelMap.setValues(new float[]{ (float)xform3d.m00, (float)xform3d.m01, (float)xform3d.m03,
                                         (float)xform3d.m10, (float)xform3d.m11, (float)xform3d.m13,
                                         0, 0, 1 });
      Log.i("TurtlebotMapView", "robotRelMap = " + robotRelMap.toString());

      havePose = true;
      postInvalidate();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if( haveMap ) {
      mapGridRelView.set( mapRelView );
      mapGridRelView.preConcat( mapGridRelMap );

      canvas.drawBitmap(mapBitmap, mapGridRelView, robotPaint);
    }

    if( havePose && haveMap ) {
      robotImageRelView.set( mapRelView );
      robotImageRelView.preConcat( robotRelMap );
      robotImageRelView.preConcat( robotImageRelRobot );

      Log.i("TurtlebotMapView", "robotImageRelView = " + robotImageRelView.toString());
      canvas.drawBitmap(robotBitmap, robotImageRelView, robotPaint);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // If we are clickable, don't try any other interaction.
    if( isClickable() ) {
      return super.onTouchEvent(event);
    }

    // One or two fingers dragging should pan, and two fingers
    // changing distance between each other should zoom.
    switch(event.getActionMasked()) {
    case MotionEvent.ACTION_DOWN: // first finger touch
      oldCenter.x = event.getX();
      oldCenter.y = event.getY();
      oldCenterValid = true;
      break;
    case MotionEvent.ACTION_POINTER_DOWN: // second or later finger touches
      findEventCenter(oldCenter, event);
      oldDist = findEventSpacing(event);
      oldCenterValid = true;
      break;
    case MotionEvent.ACTION_POINTER_UP: // second or later finger up
      oldCenterValid = false;
      break;
    case MotionEvent.ACTION_MOVE:
      // Drag regardless of number of touches
      PointF newCenter = new PointF();
      findEventCenter(newCenter, event);
      if(oldCenterValid) {
        mapRelView.postTranslate(newCenter.x - oldCenter.x,
                                 newCenter.y - oldCenter.y);
      }
      // If 2 fingers, also do zoom.
      if( event.getPointerCount() == 2 && oldDist > 10f ) {
        float newDist = findEventSpacing(event);
        if( newDist > 10f ) {
          mapRelView.postScale(newDist / oldDist, newDist / oldDist, newCenter.x, newCenter.y);
        }
        oldDist = newDist;
      }
      oldCenter.set(newCenter);
      oldCenterValid = true;
      invalidate();
      break;
    }
    return true;
  }

  private float findEventSpacing(MotionEvent event) {
    float dx = event.getX(0) - event.getX(1);
    float dy = event.getY(0) - event.getY(1);
    return FloatMath.sqrt(dx*dx + dy*dy);
  }

  private void findEventCenter(PointF centerOut, MotionEvent event) {
    if( event.getPointerCount() == 1 ) {
      centerOut.set( event.getX(0), event.getY(0) );
    } else {
      centerOut.set( (event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2 );
    }
  }
}
