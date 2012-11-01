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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import org.yaml.snakeyaml.Yaml;
import java.util.List;
import org.ros.node.parameter.ParameterTree;
import java.lang.Thread;

import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.exception.RosException;

import ros.android.util.PlaneTfChangeListener;

import ros.android.activity.R;

/**
 * View of the latest map with the turtlebot drawn in where TF thinks it is.
 */
public class MapView extends PanZoomView {
  private PlaneTfChangeListener tfChangeListener;
  private BitmapDisplay robotDisplay;
  private String footprintParam;
  private String baseScanTopic;
  private String baseScanFrame;
  private LaserScanDisplay scanDisplay;
  private MapDisplay mapDisplay;

  private static final float turtlebotDiameter = .314f; // meters

  public MapView(Context ctx) {
    super(ctx);
    init(ctx);
  }

  public MapView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  public MapView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public void refreshMap() {
    mapDisplay.refreshMap();
  }

  private void init(Context context) {
    tfChangeListener = new PlaneTfChangeListener();

    //******** configure map display ********
    mapDisplay = new MapDisplay();
    addDisplay( mapDisplay );

    //******** configure robot display ********
    robotDisplay = new BitmapDisplay();
    Bitmap robotBitmap = BitmapFactory.decodeResource( context.getResources(), R.drawable.turtlebot_top_view );
    robotDisplay.setBitmap( robotBitmap );
    Log.i("MapView", "robot image is " + robotBitmap.getWidth() + " by " + robotBitmap.getHeight() );
    // This transform assumes the robot in the image has its +x
    // pointing to the left and its +y pointing down.
    Matrix robotImageRelRobot = new Matrix();
    double robotImageResolution = turtlebotDiameter /* meters */ / robotBitmap.getHeight() /* pixels */;
    // 104 is the original height of the image, and 44 and 52 are
    // measured relative to that.  Android seems to be scaling the
    // bitmap on some platforms, so I am explicitly scaling the
    // offsets here.
    double xOffsetPixels = 44 * robotBitmap.getHeight() / 104; // These constants are tied to the image at R.drawable.turtlebot_top_view.
    double yOffsetPixels = 52 * robotBitmap.getHeight() / 104;
    robotImageRelRobot.setValues(new float[]{(float)-robotImageResolution, 0, (float)(xOffsetPixels * robotImageResolution),
                                             0, (float)robotImageResolution, (float)(-yOffsetPixels * robotImageResolution),
                                             0, 0, 1});
    robotDisplay.setBitmapRelPose( robotImageRelRobot );
    tfChangeListener.addPosable( "/map", "/base_footprint", robotDisplay );
    addDisplay( robotDisplay );

    //******** configure laser scan display ********
    scanDisplay = null;
  }

  public void setFootprintParam(String footprintParam) {
    this.footprintParam = footprintParam;
  }


  public void setBaseScanTopic(String s) {
    this.baseScanTopic = s;
  }

  public void setBaseScanFrame(String s) {
    this.baseScanFrame = s;
  }

  public PlaneTfChangeListener getPoser() {
    return tfChangeListener;
  }

  public void addMapDisplayCallback(MapDisplay.MapDisplayStateCallback c) {
    mapDisplay.addCallback(c);
  }

  public void resetMapDisplayState() {
    mapDisplay.resetState();
  }
  
  private class FootprintThread extends Thread {
    private MapView view;
    private ConnectedNode node;
    private String footprintParam;
    public FootprintThread(MapView view, ConnectedNode node, String footprintParam) {
      super();
      this.view = view;
      this.node = node;
      this.footprintParam = footprintParam;
    }
    
    public void run() {
      //This is sort of a hack to wait for the footprint parameter.
      int it = 0;
      while (!node.getParameterTree().has(footprintParam) && it < 30) {
        try {
          Thread.sleep(1000);
        } catch(java.lang.InterruptedException e) {
          it = 30;
        }
        it++;
      }

      ParameterTree tree = node.getParameterTree();
      Log.i("MapView", "Creating Footprint from " + footprintParam);
      List footprint = tree.getList(footprintParam);
      double[] footprintX = new double[footprint.toArray().length];
      double[] footprintY = new double[footprint.toArray().length];

      double mostExtremeX = 0, mostExtremeY = 0;

      int i = 0;
      for (Object o : footprint) {
        Object[] ob = (Object[])o;
        //This is a hack here - ATP
        footprintX[i] = -new Double(ob[0].toString()).doubleValue();
        footprintY[i] = new Double(ob[1].toString()).doubleValue();
        if ((footprintX[i] > mostExtremeX && footprintX[i] > 0)
            || (footprintX[i] < -mostExtremeX && footprintX[i] < 0)
            || i == 0) {
          mostExtremeX = (footprintX[i] > 0) ? footprintX[i] : -footprintX[i];
        }
        if ((footprintY[i] > mostExtremeY && footprintY[i] > 0)
            || (footprintY[i] < -mostExtremeY && footprintY[i] < 0)
            || i == 0) {
          mostExtremeY = (footprintY[i] > 0) ? footprintY[i] : -footprintY[i];
        }
        i++;
      }

      final int robotBitmapSize = 100;
      double xScale = robotBitmapSize / 2.0 / (mostExtremeX);
      double yScale = robotBitmapSize / 2.0 / (mostExtremeY);
      double scale = (xScale < yScale) ? xScale : yScale;

      Bitmap bitmap = Bitmap.createBitmap(robotBitmapSize, robotBitmapSize, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(bitmap);
      Paint p = new Paint();
      Path path = new Path();

      c.drawColor(Color.TRANSPARENT);

      path.lineTo((float)(footprintX[footprintX.length - 1] * scale + 50), 
                  (float)(footprintY[footprintY.length - 1] * scale + 50));
      for (i = 0; i < footprintX.length; i++) {
        Log.i("MapView", "(" + (footprintX[i] * scale + 50) + ", " + (footprintY[i] * scale + 50) + ")");
        path.lineTo((float)(footprintX[i] * scale + 50), (float)(footprintY[i] * scale + 50));
      }
      c.drawPath(path, p);

      
      Matrix robotImageRelRobot = new Matrix();
      robotImageRelRobot.setValues(new float[]{(float)-(1.0 / scale), 0, (float)(50.0 / scale),
                                               0, (float)(1.0 / scale), (float)(-50.0 / scale),
                                               0, 0, 1});
      view.robotDisplay.setBitmapRelPose(robotImageRelRobot);
      view.robotDisplay.setBitmap(bitmap);
    }
  }

  @Override
  public void start(ConnectedNode node) throws RosException { 
    if (footprintParam != null) {
      new FootprintThread(this, node, footprintParam).start();
    }
    if (scanDisplay == null) {
      scanDisplay = new LaserScanDisplay();
      scanDisplay.setTopic(baseScanTopic != null ? baseScanTopic : "scan");
      tfChangeListener.addPosable( "/map", baseScanFrame != null ? baseScanFrame : "/kinect_depth_frame", scanDisplay );
      addDisplay( 1, scanDisplay );
    }
    super.start(node);
    tfChangeListener.start( node );
  }

  @Override
  public void stop() {
    super.stop();
    tfChangeListener.stop();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    super.onSizeChanged( w, h, oldW, oldH );

    float[] values = new float[9];
    robotDisplay.getPose().getValues( values );
    centerPoint( new PointF( values[2], values[5] ));
  }
}
