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
import org.ros.exception.RosInitException;
import org.ros.ServiceClient;
import org.ros.internal.node.service.ServiceIdentifier;
import org.ros.message.Message;
import org.ros.message.app_manager.AppStatus;
import org.ros.message.geometry_msgs.Twist;
import org.ros.namespace.NameResolver;
import org.ros.service.app_manager.StartApp;
import ros.android.activity.AppManager;
import ros.android.activity.RosAppActivity;
import ros.android.views.SensorImageView;
import ros.android.util.Dashboard;
import android.widget.LinearLayout;

/**
 * @author kwc@willowgarage.com (Ken Conley)
 */
public class Teleop extends RosAppActivity implements OnTouchListener {
  private Publisher<Twist> twistPub;
  private SensorImageView cameraView;
  private Thread pubThread;
  private Twist touchCmdMessage;
  private float motionY;
  private float motionX;
  private Subscriber<AppStatus> statusSub;
  private Dashboard.DashboardInterface dashboard;
  private String robotAppName;
  private String baseControlTopic;
  private String cameraTopic;

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

    if (getIntent().hasExtra("base_control_topic")) {
      baseControlTopic = getIntent().getStringExtra("base_control_topic");
    } else {
      baseControlTopic = "turtlebot_node/cmd_vel";
    }

    if (getIntent().hasExtra("camera_topic")) {
      cameraTopic = getIntent().getStringExtra("camera_topic");
    } else {
      cameraTopic = "camera/rgb/image_color/compressed_throttle";
    }

    View joyView = findViewById(R.id.joystick);
    joyView.setOnTouchListener(this);

    cameraView = (SensorImageView) findViewById(R.id.image);
    // cameraView.setOnTouchListener(this);
    touchCmdMessage = new Twist();

    dashboard = null;
  }

  @Override
  protected void onNodeDestroy(Node node) {
    if (dashboard != null) {
      dashboard.stop();
      runOnUiThread(new Runnable() {
          @Override
            public void run() {
            LinearLayout top = (LinearLayout)findViewById(R.id.top_bar);
            top.removeView((View)dashboard);
            dashboard = null;
          }});
    }
    if (twistPub != null) {
      twistPub.shutdown();
      twistPub = null;
    }
    if (cameraView != null) {
      cameraView.stop();
      cameraView = null;
    }
    if (statusSub != null) {
      statusSub.shutdown();
      statusSub = null;
    }
    if (pubThread != null) {
      pubThread.interrupt();
      pubThread = null;
    }
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
      cameraView.start(node, appNamespace.resolve(cameraTopic));
      cameraView.post(new Runnable() {

        @Override
        public void run() {
          cameraView.setSelected(true);
        }
      });
      Log.i("Teleop", "init twistPub");
      twistPub = node.createPublisher(baseControlTopic, "geometry_msgs/Twist");
      createPublisherThread(twistPub, touchCmdMessage, 10);
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
      
      if (dashboard != null) {
        dashboard.stop();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              LinearLayout top = (LinearLayout)findViewById(R.id.top_bar);
              top.removeView((View)dashboard);
              dashboard = null;
            }});
      }
      dashboard = Dashboard.createDashboard(node, this);
      
      if (dashboard != null) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              LinearLayout top = (LinearLayout)findViewById(R.id.top_bar);
              LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
              Dashboard.DashboardInterface dash = dashboard;
              if (dash != null) {
                top.addView((View)dash, lparams);
              }
            }});
        dashboard.start(node);
      }
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
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onTouch(View arg0, MotionEvent motionEvent) {
    int action = motionEvent.getAction();
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
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

  private void safeToastStatus(final String message) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(Teleop.this, message, Toast.LENGTH_SHORT).show();
      }
    });
  }
}
