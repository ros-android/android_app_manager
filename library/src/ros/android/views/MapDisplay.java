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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import org.ros.MessageListener;
import org.ros.Node;
import org.ros.Subscriber;
import org.ros.exception.RosInitException;
import org.ros.message.nav_msgs.OccupancyGrid;

/**
 * PanZoomDisplay which shows an OccupancyGrid map in a PanZoomView.
 */
public class MapDisplay extends PanZoomDisplay {
  private Subscriber<OccupancyGrid> mapSubscriber;
  private Bitmap mapBitmap;
  private Matrix mapGridRelMap = new Matrix(); // from map metadata
  private Paint paint = new Paint();
  private String mapTopic = "map";
  private boolean haveMap = false;

  /**
   * Set the topic name for the map messages.  Defaults to "/map".
   */
  public void setTopic( String mapTopic ) {
    this.mapTopic = mapTopic;
  }
  public String getTopic() {
    return mapTopic;
  }

  @Override
  public void start( Node node ) throws RosInitException {
    mapSubscriber = node.createSubscriber(mapTopic, new MessageListener<OccupancyGrid>() {
        @Override
        public void onNewMessage(final OccupancyGrid msg) {
          getParent().post(new Runnable() {
              @Override
              public void run() {
                handleMap(msg);
              }
            });
        }
      }, OccupancyGrid.class);
  }

  @Override
  public void stop() {
    if(mapSubscriber != null) {
      mapSubscriber.cancel();
    }
    mapSubscriber = null;
  }

  @Override
  public void draw( Canvas canvas ) {
    if( haveMap ) {
      canvas.drawBitmap(mapBitmap, mapGridRelMap, paint);
    }
  }

  /**
   * Populate view with new map data. This must be called in the UI
   * thread.
   */
  private void handleMap(OccupancyGrid msg) {
    Log.i("MapDisplay", "handleMap()");
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
    float res = msg.info.resolution;
    mapGridRelMap.setValues( new float[]{ res, 0, (float)msg.info.origin.position.x,
                                          0, res, (float)msg.info.origin.position.y,
                                          0, 0, 1 });
    Log.i("TurtlebotMapView", "mapGridRelMap = " + mapGridRelMap.toString());

    postInvalidate();
  }
}
