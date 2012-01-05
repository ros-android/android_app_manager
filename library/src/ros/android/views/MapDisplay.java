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
import java.lang.Object;
import android.util.Log;
import java.util.concurrent.locks.ReentrantLock;
import org.ros.node.service.ServiceResponseListener;

import org.ros.message.MessageListener;
import org.ros.node.Node;
import org.ros.node.topic.Subscriber;
import org.ros.exception.RosException;
import org.ros.exception.RemoteException;
import org.ros.message.nav_msgs.OccupancyGrid;
import org.ros.service.nav_msgs.GetMap;
import org.ros.node.service.ServiceClient;

import java.util.ArrayList;

/**
 * PanZoomDisplay which shows an OccupancyGrid map in a PanZoomView.
 */
public class MapDisplay extends PanZoomDisplay {
  private Subscriber<OccupancyGrid> mapSubscriber;
  private Bitmap mapBitmap, backgroundBitmap;
  private Matrix mapGridRelMap = new Matrix(); // from map metadata
  private Paint paint = new Paint();
  private String mapTopic = "map";
  private boolean haveMap = false;
  private ReentrantLock mapLock = new ReentrantLock();
  private ArrayList<MapDisplayStateCallback> callbacks = new ArrayList();
  private Node node;

  public enum State {
    STATE_STARTING,
    STATE_NEED_MAP,
    STATE_LOADING,
    STATE_WORKING,
    STATE_UNKNOWN
  }

  public interface MapDisplayStateCallback {
    public void onMapDisplayState(MapDisplay.State state);
  }

  private State state = MapDisplay.State.STATE_UNKNOWN;

  public void addCallback(MapDisplayStateCallback c) {
    callbacks.add(c);
  }

  public void resetState() {
    setState(MapDisplay.State.STATE_STARTING);
  }

  private void setState(State newState) {
    if (state != newState) {
      Log.i("MapDisplay", "New state: " + newState + ".");
      for (MapDisplayStateCallback c : callbacks) {
        c.onMapDisplayState(newState);
      }
    }
    state = newState;
  }

  public State getState() {
    return state;
  }
  
  private class MapUpdateThread extends Thread {
    private OccupancyGrid msg;
    private MapDisplay parent;

    public MapUpdateThread(MapDisplay parent, final OccupancyGrid msg) {
      this.msg = msg;
      this.parent = parent;
    }
    /**
     * Populate view with new map data. This must be called in the UI
     * thread.
     */
    public void run() {
      if (msg.info.height < 1 && msg.info.width < 1) {
        Log.e("MapDisplay", "Map message has no data.");
        return;
      }
      if (MapDisplay.this.getState() != MapDisplay.State.STATE_WORKING) {
        Log.i("MapDisplay", "Loading");
        MapDisplay.this.setState(MapDisplay.State.STATE_LOADING);
      }  else {
        Log.i("MapDisplay", "Already started, not setting state");
      }
      Log.i("MapDisplay", "handleMap() - locking thread");
      parent.mapLock.lock();
      Log.i("MapDisplay", "handleMap() - " + msg.info.height + " by " + msg.info.width);
      if (parent.backgroundBitmap != null && (parent.backgroundBitmap.getWidth() != (int)msg.info.width || parent.backgroundBitmap.getHeight() != (int)msg.info.height)) {
        Log.i("MapDisplay", "Recycle map");
        parent.backgroundBitmap.recycle();
        parent.backgroundBitmap = null;
      }
      if (parent.backgroundBitmap == null) {
        parent.backgroundBitmap = Bitmap.createBitmap((int)msg.info.width, (int)msg.info.height, Bitmap.Config.RGB_565);
        Log.i("MapDisplay", "Create map");
      }
      
      // copy the map data into the mapBitmap.
      int data_i = 0;
      
      int black = Color.rgb(0, 0, 0);
      int grey = Color.rgb(128, 128, 128);
      int white = Color.rgb(255, 255, 255);
      for (int y = 0; y < msg.info.height; y++) {
        for (int x = 0; x < msg.info.width; x++) {
          int cell = msg.data[data_i];
          data_i++;
          switch(cell) {
          case 100:
            parent.backgroundBitmap.setPixel(x, y, black);
            break;
          case 0:
            parent.backgroundBitmap.setPixel(x, y, white);
            break;
          default:
            parent.backgroundBitmap.setPixel(x, y, grey);
          }
        }
      }
      
      // This matrix definition presumes the map is flat on the XY plane
      // and that there is 0 rotation.  So just an offset and a scale.
      float res = msg.info.resolution;
      parent.mapGridRelMap.setValues(new float[]{ res,   0, (float)msg.info.origin.position.x,
                                                    0, res, (float)msg.info.origin.position.y,
                                                    0,   0, 1 });
      Log.i("MapDisplay", "mapGridRelMap = " + parent.mapGridRelMap.toString());
     
      
      Bitmap temp = parent.backgroundBitmap;
      parent.backgroundBitmap = parent.mapBitmap;
      parent.mapBitmap = temp;
      parent.haveMap = true;
      Log.i("MapDisplay", "Done");
      MapDisplay.this.setState(MapDisplay.State.STATE_WORKING);
      
      parent.mapLock.unlock();

      parent.postInvalidate();
      

    }
  }


  /**
   * Set the topic name for the map messages.  Defaults to "/map".
   */
  public void setTopic( String mapTopic ) {
    this.mapTopic = mapTopic;
  }
  public String getTopic() {
    return mapTopic;
  }

  public void refreshMap() {
    refreshMap(false);
  }

  public void refreshMap(final boolean setStateOnFailure) {
    try {
      ServiceClient<GetMap.Request, GetMap.Response> client = node.newServiceClient("/dynamic_map", "nav_msgs/GetMap");
      client.call(new GetMap.Request(),
                  new ServiceResponseListener<GetMap.Response>() {
                    @Override               
                    public void onSuccess(GetMap.Response message) {
                      new MapUpdateThread(MapDisplay.this, message.map).start();
                    }
                    @Override
                    public void onFailure(RemoteException e) {
                      //Often occurs if service is not started.
                      Log.i("MapDisplay", "Dynamic map service call failed: " + e.toString());
                      if (setStateOnFailure) {
                        Log.i("MapDisplay", "Likely, the service is not present. This is not a problem, the user needs to pick a map");
                        MapDisplay.this.setState(State.STATE_NEED_MAP);
                      }
                    }});
    } catch (Exception e) {
      Log.i("MapDisplay", "Map could not be requested");
      if (setStateOnFailure) {
        MapDisplay.this.setState(State.STATE_NEED_MAP);
      }
    }
  }

  @Override
  public void start( Node node ) throws RosException {
    this.node = node;
    try {
      setState(State.STATE_STARTING);
      Log.i("MapDisplay", "Waiting for map");
      refreshMap(true);
    } catch (Exception e) {
      Log.i("MapDisplay", "Map could not be requested");
      MapDisplay.this.setState(State.STATE_NEED_MAP);
    }
    Log.i("MapDisplay", "Done map selection");
    mapSubscriber = node.newSubscriber(mapTopic, "nav_msgs/OccupancyGrid");
    mapSubscriber.addMessageListener(
        new MessageListener<OccupancyGrid>() {
              @Override
              public void onNewMessage(final OccupancyGrid msg) {
                Log.i("MapDisplay", "Map recieved");
                new MapUpdateThread(MapDisplay.this, msg).start();
              }});
    Log.i("MapDisplay", "Map display started");
  }

  @Override
  public void stop() {
    if(mapSubscriber != null) {
      mapSubscriber.shutdown();
    }
    mapSubscriber = null;
    state = State.STATE_UNKNOWN;
    node = null;
  }

  @Override
  public void draw( Canvas canvas ) {
    Bitmap localMapBitmap = mapBitmap; //avoids race conditions
    //Log.i("MapDisplay", "Display map");
    if (localMapBitmap != null && haveMap) {
      canvas.drawBitmap(localMapBitmap, mapGridRelMap, paint);
    }
  }
}
