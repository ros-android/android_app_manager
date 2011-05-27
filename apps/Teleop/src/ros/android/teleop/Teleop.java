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

package ros.android.teleop;

import android.app.Dialog;
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
import org.ros.exceptions.RosInitException;
import org.ros.internal.node.service.ServiceClient;
import org.ros.internal.node.service.ServiceIdentifier;
import org.ros.message.Message;
import org.ros.message.app_manager.AppStatus;
import org.ros.message.geometry_msgs.Twist;
import org.ros.namespace.NameResolver;
import org.ros.service.app_manager.StartApp;
import org.ros.service.map_store.NameLatestMap;
import ros.android.activity.AppManager;
import ros.android.activity.RosAppActivity;
import ros.android.views.SensorImageView;
import ros.android.views.TurtlebotDashboard;
import ros.android.views.TurtlebotMapView;

/**
 * @author kwc@willowgarage.com (Ken Conley)
 */
public class Teleop extends RosAppActivity implements OnTouchListener {
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
  private ServiceIdentifier nameMapServiceIdentifier;

  private static final int NAME_MAP_DIALOG_ID = 0;

  private enum ViewMode {
    CAMERA, MAP
  };

  private ViewMode viewMode;
  private boolean deadman;

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
      robotAppName = "turtlebot_teleop/android_teleop";
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
        Teleop.this.swapViews();
      }
    });
    cameraView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Teleop.this.swapViews();
      }
    });
    mapView.setClickable(true);
    cameraView.setClickable(false);
  }

  /**
   * Swap the camera and map views.
   */
  private void swapViews() {
    // Figure out where the views were...
    ViewGroup mapViewParent;
    ViewGroup cameraViewParent;
    Log.i("Teleop", "viewMode = " + viewMode);
    if (viewMode == ViewMode.CAMERA) {
      Log.i("Teleop", "camera mode");
      mapViewParent = sideLayout;
      cameraViewParent = mainLayout;
    } else {
      Log.i("Teleop", "map mode");
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
    nameMapServiceIdentifier = null;
    dashboard.stop();
    mapView.stop();
    super.onNodeDestroy(node);
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
    Log.i("Teleop", "started pub thread");
    pubThread.start();
  }

  private void initRos() {
    try {
      Log.i("Teleop", "getNode()");
      Node node = getNode();
      NameResolver appNamespace = getAppNamespace(node);
      cameraView = (SensorImageView) findViewById(R.id.image);
      Log.i("Teleop", "init cameraView");
      cameraView.start(node, appNamespace.resolveName("camera/rgb/image_color/compressed_throttle"));
      cameraView.post(new Runnable() {

        @Override
        public void run() {
          cameraView.setSelected(true);
        }
      });
      Log.i("Teleop", "init twistPub");
      twistPub = node.createPublisher("turtlebot_node/cmd_vel", Twist.class);
      createPublisherThread(twistPub, touchCmdMessage, 10);

      nameMapServiceIdentifier =
        node.lookupService(node.getResolver().resolveName("name_latest_map"), new NameLatestMap());
    } catch (RosInitException e) {
      Log.e("Teleop", "initRos() caught exception: " + e.toString() + ", message = " + e.getMessage());
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Toast.makeText(Teleop.this, "starting app", Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onNodeCreate(Node node) {
    Log.i("Teleop", "startAppFuture");
    super.onNodeCreate(node);
    try {
      dashboard.start(node);
      mapView.start(node, "map");
      startApp();
    } catch (RosInitException ex) {
      Toast.makeText(Teleop.this, "Failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
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
    inflater.inflate(R.menu.teleop_options, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.kill:
      android.os.Process.killProcess(android.os.Process.myPid());
      return true;
    case R.id.name_map:
      showDialog(NAME_MAP_DIALOG_ID);
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
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

  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog;
    Button button;
    switch (id) {
    case NAME_MAP_DIALOG_ID:
      dialog = new Dialog(this);
      dialog.setContentView(R.layout.name_map_dialog);
      dialog.setTitle("Set map name");

      final EditText nameField = (EditText) dialog.findViewById(R.id.name_editor);
      nameField.setOnKeyListener(new View.OnKeyListener() {
          @Override
          public boolean onKey(View view, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
              String newName = nameField.getText().toString();
              if (newName != null && newName.length() > 0) {
                setMapName(newName);
              }
              dismissDialog(NAME_MAP_DIALOG_ID);
              return true;
            } else {
              return false;
            }
          }
        });

      button = (Button) dialog.findViewById(R.id.cancel_button);
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          dismissDialog(NAME_MAP_DIALOG_ID);
        }
      });
      break;
    default:
      dialog = null;
    }
    return dialog;
  }

  private void setMapName(final String newName) {
    Log.i("Teleop", "Map should soon be named " + newName);
    int debug = 0;
    if( nameMapServiceIdentifier != null ) {
      try {
        Log.i("Teleop", "setMapName() 1");
        ServiceClient<NameLatestMap.Response> nameMapServiceClient =
          getNode().createServiceClient(nameMapServiceIdentifier, NameLatestMap.Response.class);
        Log.i("Teleop", "setMapName() 2");
        NameLatestMap.Request nameMapRequest = new NameLatestMap.Request();
        nameMapRequest.map_name = newName;
        Log.i("Teleop", "setMapName() 3");
        nameMapServiceClient.call(nameMapRequest, new ServiceResponseListener<NameLatestMap.Response>() {
            @Override public void onSuccess(NameLatestMap.Response message) {
              Log.i("Teleop", "setMapName() Success ");
              // TODO: put success/failure info into response and show it.
              safeToastStatus("Map has been named " + newName);
            }

            @Override public void onFailure(Exception e) {
              Log.i("Teleop", "setMapName() Failure");
              safeToastStatus("Naming map failed: " + e.getMessage());
            }
          });
      } catch(Throwable ex) {
        Log.e("Teleop", "setMapName() caught exception: " + ex.toString());
        safeToastStatus("Naming map couldn't even start: " + ex.getMessage());
      }
    } else {
      Log.e("Teleop", "setMapName(): nameMapServiceIdentifier is null.");
    }
  }

  private void safeToastStatus(final String message) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(Teleop.this, message, Toast.LENGTH_SHORT).show();
      }
    });
  }

}