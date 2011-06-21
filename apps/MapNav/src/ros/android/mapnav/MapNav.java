/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package ros.android.mapnav;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.ros.Node;
import org.ros.Publisher;
import org.ros.ServiceResponseListener;
import org.ros.Subscriber;
import org.ros.exception.RosInitException;
import org.ros.internal.node.service.ServiceClient;
import org.ros.internal.node.service.ServiceIdentifier;
import org.ros.message.Message;
import org.ros.message.app_manager.AppStatus;
import org.ros.message.geometry_msgs.Twist;
import org.ros.message.map_store.MapListEntry;
import org.ros.namespace.NameResolver;
import org.ros.service.app_manager.StartApp;
import org.ros.service.map_store.ListLastMaps;
import org.ros.service.map_store.PublishMap;

import ros.android.activity.AppManager;
import ros.android.activity.RosAppActivity;
import ros.android.views.SensorImageView;
import ros.android.views.SetInitialPoseDisplay;
import ros.android.views.SendGoalDisplay;
import ros.android.views.TurtlebotDashboard;
import ros.android.views.TurtlebotMapView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * @author kwc@willowgarage.com (Ken Conley)
 * @author hersh@willowgarage.com (Dave Hershberger)
 */
public class MapNav extends RosAppActivity implements OnTouchListener {
  private Publisher<Twist> twistPub;
  private SensorImageView cameraView;
  private TurtlebotMapView mapView;
  private Thread pubThread;
  private Twist touchCmdMessage;
  private float motionY;
  private float motionX;
  private Subscriber<AppStatus> statusSub;
  private TurtlebotDashboard dashboard;
  private ViewGroup mainLayout;
  private ViewGroup sideLayout;
  private String robotAppName;
  private ServiceIdentifier listMapsServiceIdentifier;
  private ServiceIdentifier publishMapServiceIdentifier;
  private SetInitialPoseDisplay poseSetter;
  private SendGoalDisplay goalSender;

  private enum ViewMode {
    CAMERA, MAP
  };

  private ViewMode viewMode;
  private boolean deadman;

  private ProgressDialog waitingDialog;
  private AlertDialog chooseMapDialog;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.main);

    robotAppName = getIntent().getStringExtra(AppManager.PACKAGE + ".robot_app_name");
    if( robotAppName == null ) {
      robotAppName = "turtlebot_teleop/android_map_nav";
    }

    View joyView = findViewById(R.id.joystick);
    joyView.setOnTouchListener(this);

    cameraView = (SensorImageView) findViewById(R.id.image);
    // cameraView.setOnTouchListener(this);
    touchCmdMessage = new Twist();

    dashboard = (TurtlebotDashboard) findViewById(R.id.dashboard);
    mapView = (TurtlebotMapView) findViewById(R.id.map_view);

    mainLayout = (ViewGroup) findViewById(R.id.main_layout);
    sideLayout = (ViewGroup) findViewById(R.id.side_layout);

    viewMode = ViewMode.CAMERA;

    mapView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        MapNav.this.swapViews();
      }
    });
    cameraView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        MapNav.this.swapViews();
      }
    });
    mapView.setClickable(true);
    cameraView.setClickable(false);

    poseSetter = new SetInitialPoseDisplay();
    poseSetter.disable();
    mapView.addDisplay( poseSetter );
    mapView.getPoser().addPosable( "/map", "/base_footprint", poseSetter );

    goalSender = new SendGoalDisplay();
    goalSender.disable();
    mapView.addDisplay( goalSender );
    mapView.getPoser().addPosable( "/map", "/base_footprint", goalSender );
  }

  /**
   * Swap the camera and map views.
   */
  private void swapViews() {
    // Figure out where the views were...
    ViewGroup mapViewParent;
    ViewGroup cameraViewParent;
    Log.i("MapNav", "viewMode = " + viewMode);
    if (viewMode == ViewMode.CAMERA) {
      Log.i("MapNav", "camera mode");
      mapViewParent = sideLayout;
      cameraViewParent = mainLayout;
    } else {
      Log.i("MapNav", "map mode");
      mapViewParent = mainLayout;
      cameraViewParent = sideLayout;
    }
    int mapViewIndex = mapViewParent.indexOfChild(mapView);
    int cameraViewIndex = cameraViewParent.indexOfChild(cameraView);

    // Remove the views from their old locations...
    mapViewParent.removeView(mapView);
    cameraViewParent.removeView(cameraView);

    // Add them to their new location...
    mapViewParent.addView(cameraView, mapViewIndex);
    cameraViewParent.addView(mapView, cameraViewIndex);

    // Remeber that we are in the other mode now.
    if (viewMode == ViewMode.CAMERA) {
      viewMode = ViewMode.MAP;
    } else {
      viewMode = ViewMode.CAMERA;
    }
    mapView.setClickable(viewMode != ViewMode.MAP);
    cameraView.setClickable(viewMode != ViewMode.CAMERA);
  }

  @Override
  protected void onNodeDestroy(Node node) {
    deadman = false;
    if (twistPub != null) {
      twistPub.shutdown();
      twistPub = null;
    }
    if (cameraView != null) {
      cameraView.stop();
      cameraView = null;
    }
    if (statusSub != null) {
      statusSub.cancel();
      statusSub = null;
    }
    if (pubThread != null) {
      pubThread.interrupt();
      pubThread = null;
    }
    listMapsServiceIdentifier = null;
    publishMapServiceIdentifier = null;
    dashboard.stop();
    mapView.stop();
    poseSetter.stop();
    goalSender.stop();
    super.onNodeDestroy(node);
  }

  private <T extends Message> void createPublisherThread(final Publisher<T> pub, final T message,
      final int rate) {
    pubThread = new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          while (true) {
            if( deadman ) {
              pub.publish(message);
            }
            Thread.sleep(1000 / rate);
          }
        } catch (InterruptedException e) {
        }
      }
    });
    Log.i("MapNav", "started pub thread");
    pubThread.start();
  }

  private void initRos() {
    try {
      Log.i("MapNav", "getNode()");
      Node node = getNode();
      NameResolver appNamespace = getAppNamespace(node);
      cameraView = (SensorImageView) findViewById(R.id.image);
      Log.i("MapNav", "init cameraView");
      cameraView.start(node, appNamespace.resolveName("camera/rgb/image_color/compressed_throttle"));
      cameraView.post(new Runnable() {

        @Override
        public void run() {
          cameraView.setSelected(true);
        }
      });
      Log.i("MapNav", "init twistPub");
      twistPub = node.createPublisher("turtlebot_node/cmd_vel", Twist.class);
      createPublisherThread(twistPub, touchCmdMessage, 10);
      poseSetter.start(node);
      goalSender.start(node);
    } catch (RosInitException e) {
      Log.e("MapNav", "initRos() caught exception: " + e.toString() + ", message = " + e.getMessage());
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Toast.makeText(MapNav.this, "starting app", Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onNodeCreate(Node node) {
    Log.i("MapNav", "startAppFuture");
    super.onNodeCreate(node);
    if( appManager != null ) {
      try {
        dashboard.start(node);
        mapView.start(node);
        startApp();
      } catch (RosInitException ex) {
        safeToastStatus( "Failed: " + ex.getMessage() );
      }
    } else {
      safeToastStatus( "App Manager failed to start." );
    }
  }

  private void startApp() {
    appManager.startApp(robotAppName,
        new ServiceResponseListener<StartApp.Response>() {
          @Override
          public void onSuccess(StartApp.Response message) {
            initRos();
            // TODO(kwc): add status code for app already running
            /*
             * if (message.started) { safeToastStatus("started"); initRos(); }
             * else { safeToastStatus(message.message); }
             */
          }

          @Override
          public void onFailure(Exception e) {
            safeToastStatus("Failed: " + e.getMessage());
          }
        });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.mapnav_options, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.kill:
      android.os.Process.killProcess(android.os.Process.myPid());
      return true;
    case R.id.set_pose:
      setPose();
      return true;
    case R.id.set_goal:
      setGoal();
      return true;
    case R.id.choose_map:
      readAvailableMapList();
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  private void setPose() {
    poseSetter.enable();
  }

  private void setGoal() {
    goalSender.enable();
  }

  private void readAvailableMapList() {
    safeShowWaitingDialog("Waiting for map list");
    Thread mapLoaderThread = new Thread(new Runnable() {
        @Override public void run() {
          try {
            if( listMapsServiceIdentifier == null ) {
              listMapsServiceIdentifier =
                getNode().lookupService(getNode().getResolver().resolveName("list_last_maps"), new ListLastMaps());
              if( listMapsServiceIdentifier == null ) {
                safeDismissWaitingDialog();
                safeToastStatus("list_last_maps service not found.");
                return;
              }
            }
            ServiceClient<ListLastMaps.Response> listMapsServiceClient =
              getNode().createServiceClient(listMapsServiceIdentifier, ListLastMaps.Response.class);
            listMapsServiceClient.call(new ListLastMaps.Request(), new ServiceResponseListener<ListLastMaps.Response>() {
                @Override public void onSuccess(ListLastMaps.Response message) {
                  Log.i("MapNav", "readAvailableMapList() Success");
                  safeDismissWaitingDialog();
                  showMapListDialog(message.map_list);
                }
                @Override public void onFailure(Exception e) {
                  Log.i("MapNav", "readAvailableMapList() Failure");
                  safeToastStatus("Reading map list failed: " + e.getMessage());
                  safeDismissWaitingDialog();
                }
              });
          } catch(Throwable ex) {
            Log.e("MapNav", "readAvailableMapList() caught exception.", ex);
            safeToastStatus("Listing maps couldn't even start: " + ex.getMessage());
            safeDismissWaitingDialog();
          }
        }
      });
    mapLoaderThread.start();
  }

  /**
   * Show a dialog with a list of maps.  Safe to call from any thread.
   */
  private void showMapListDialog(final ArrayList<MapListEntry> mapList) {
    // Make an array of map name/date strings.
    final CharSequence[] availableMapNames = new CharSequence[mapList.size()];
    for( int i = 0; i < mapList.size(); i++ ) {
      String displayString;
      String name = mapList.get(i).name;
      Date creationDate = new Date(mapList.get(i).date * 1000);
      String dateTime = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(creationDate);
      if( name != null && ! name.equals("") ) {
        displayString = name + " " + dateTime;
      } else {
        displayString = dateTime;
      }
      availableMapNames[i] = displayString;
    }

    runOnUiThread(new Runnable() {
        @Override public void run() {
          AlertDialog.Builder builder = new AlertDialog.Builder(MapNav.this);
          builder.setTitle("Choose a map");
          builder.setItems(availableMapNames, new DialogInterface.OnClickListener() {
              @Override public void onClick(DialogInterface dialog, int itemIndex) {
                loadMap( mapList.get( itemIndex ));
              }
            });
          chooseMapDialog = builder.create();
          chooseMapDialog.show();
        }
      });
  }

  @Override
  public boolean onTouch(View arg0, MotionEvent motionEvent) {
    int action = motionEvent.getAction();
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
      deadman = true;

      motionX = (motionEvent.getX() - (arg0.getWidth() / 2)) / (arg0.getWidth());
      motionY = (motionEvent.getY() - (arg0.getHeight() / 2)) / (arg0.getHeight());

      touchCmdMessage.linear.x = -2 * motionY;
      touchCmdMessage.linear.y = 0;
      touchCmdMessage.linear.z = 0;
      touchCmdMessage.angular.x = 0;
      touchCmdMessage.angular.y = 0;
      touchCmdMessage.angular.z = -5 * motionX;

    } else {
      deadman = false;
      touchCmdMessage.linear.x = 0;
      touchCmdMessage.linear.y = 0;
      touchCmdMessage.linear.z = 0;
      touchCmdMessage.angular.x = 0;
      touchCmdMessage.angular.y = 0;
      touchCmdMessage.angular.z = 0;
    }
    return true;
  }

  private void loadMap( MapListEntry mapListEntry ) {
    Log.i("MapNav", "loadMap(): " + mapListEntry.name);
    safeShowWaitingDialog("Loading map");
    try {
      if( publishMapServiceIdentifier == null ) {
        publishMapServiceIdentifier =
          getNode().lookupService(getNode().getResolver().resolveName("publish_map"), new PublishMap());
        if( publishMapServiceIdentifier == null ) {
          safeToastStatus("publish_map service not found.");
          safeDismissWaitingDialog();
          return;
        }
      }
      ServiceClient<PublishMap.Response> publishMapServiceClient =
        getNode().createServiceClient(publishMapServiceIdentifier, PublishMap.Response.class);
      PublishMap.Request req = new PublishMap.Request();
      req.map_id = mapListEntry.map_id;
      publishMapServiceClient.call(req, new ServiceResponseListener<PublishMap.Response>() {
          @Override public void onSuccess(PublishMap.Response message) {
            Log.i("MapNav", "loadMap() Success");
            safeDismissWaitingDialog();
            poseSetter.enable();
          }
          @Override public void onFailure(Exception e) {
            Log.i("MapNav", "loadMap() Failure");
            safeToastStatus("Loading map failed: " + e.getMessage());
            safeDismissWaitingDialog();
          }
        });
    } catch(Throwable ex) {
      Log.e("MapNav", "loadMap() caught exception.", ex);
      safeToastStatus("Publishing map couldn't even start: " + ex.getMessage());
      safeDismissWaitingDialog();
    }
  }

  private void safeToastStatus(final String message) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(MapNav.this, message, Toast.LENGTH_SHORT).show();
      }
    });
  }

  private void safeDismissChooseMapDialog() {
    runOnUiThread(new Runnable() {
        @Override public void run() {
          if( chooseMapDialog != null ) {
            chooseMapDialog.dismiss();
            chooseMapDialog = null;
          }
        }
      });
  }

  private void safeShowWaitingDialog(final CharSequence message) {
    runOnUiThread(new Runnable() {
        @Override public void run() {
          waitingDialog = ProgressDialog.show(MapNav.this, "", message, true);
        }
      });
  }

  private void safeDismissWaitingDialog() {
    runOnUiThread(new Runnable() {
        @Override public void run() {
          if( waitingDialog != null ) {
            waitingDialog.dismiss();
            waitingDialog = null;
          }
        }
      });
  }
}
