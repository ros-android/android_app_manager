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
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;

import org.ros.Node;
import org.ros.exception.RosInitException;

import ros.android.util.PlaneTfChangeListener;

import ros.android.activity.R;

/**
 * View of the latest map with the turtlebot drawn in where TF thinks it is.
 */
public class TurtlebotMapView extends PanZoomView {
  private PlaneTfChangeListener tfChangeListener;
  private BitmapDisplay robotDisplay;

  private static final float turtlebotDiameter = .314f; // meters

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
    tfChangeListener = new PlaneTfChangeListener();

    //******** configure map display ********
    addDisplay( new MapDisplay() );

    //******** configure robot display ********
    robotDisplay = new BitmapDisplay();
    robotDisplay.setBitmap( BitmapFactory.decodeResource( context.getResources(), R.drawable.turtlebot_top_view ));
    // This transform assumes the robot in the image has its +x
    // pointing to the left and its +y pointing down.
    Matrix robotImageRelRobot = new Matrix();
    double robotImageResolution = turtlebotDiameter /* meters */ / 104 /* pixels */;
    double xOffsetPixels = 44; // These constants are tied to the image at R.drawable.turtlebot_top_view.
    double yOffsetPixels = 52;
    robotImageRelRobot.setValues(new float[]{(float)-robotImageResolution, 0, (float)(xOffsetPixels * robotImageResolution),
                                             0, (float)robotImageResolution, (float)(-yOffsetPixels * robotImageResolution),
                                             0, 0, 1});
    robotDisplay.setBitmapRelPose( robotImageRelRobot );
    tfChangeListener.addPosable( "/map", "/base_footprint", robotDisplay );
    addDisplay( robotDisplay );

    //******** configure laser scan display ********
    LaserScanDisplay scanDisplay = new LaserScanDisplay();
    scanDisplay.setTopic("narrow_scan");
    tfChangeListener.addPosable( "/map", "/kinect_depth_frame", scanDisplay );
    addDisplay( scanDisplay );
  }

  @Override
  public void start(Node node) throws RosInitException {
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
