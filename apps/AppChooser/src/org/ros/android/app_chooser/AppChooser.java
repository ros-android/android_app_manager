/*
 * Software License Agreement (BSD License)
 *
 * Copyright (c) 2011, Willow Garage, Inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of Willow Garage, Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *    
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.ros.android.app_chooser;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Button;
import org.ros.MessageListener;
import org.ros.Node;
import org.ros.exception.RosInitException;
import org.ros.message.app_manager.App;
import org.ros.message.app_manager.AppList;
import ros.android.activity.RosAppActivity;
import android.widget.LinearLayout;
import ros.android.util.Dashboard;
import android.app.AlertDialog;
import android.content.DialogInterface;

import org.ros.ServiceResponseListener;
import org.ros.message.app_manager.StatusCodes;
import org.ros.service.app_manager.StopApp;

import java.util.ArrayList;

/**
 * Show a grid of applications that a given robot is capable of, and launch
 * whichever is chosen.
 */
public class AppChooser extends RosAppActivity {

  private ArrayList<App> availableAppsCache;
  private ArrayList<App> runningAppsCache;
  private long availableAppsCacheTime;
  private Dashboard.DashboardInterface dashboard;
  private TextView robotNameView;
  private Button deactivate;
  private Button stopApps;

  public AppChooser() {
    availableAppsCache = new ArrayList<App>();
    availableAppsCacheTime = 0;
    dashboard = null;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    robotNameView = (TextView) findViewById(R.id.robot_name_view);

    deactivate = (Button) findViewById(R.id.deactivate_robot);
    deactivate.setVisibility(deactivate.GONE);
    stopApps = (Button) findViewById(R.id.stop_applications);
    stopApps.setVisibility(stopApps.GONE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    setStatus("");
    // TODO: start spinner
    updateAppList(availableAppsCache, runningAppsCache);
  }

  /**
   * Must be run in UI thread.
   * 
   * @param apps
   */
  protected void updateAppList(final ArrayList<App> apps, final ArrayList<App> runningApps) {
    Log.i("RosAndroid", "updating gridview");
    GridView gridview = (GridView) findViewById(R.id.gridview);
    gridview.setAdapter(new AppAdapter(AppChooser.this, apps, runningApps));
    gridview.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        AppLauncher.launch(AppChooser.this, apps.get(position));
      }
    });
    if (runningApps != null) {
      if (runningApps.toArray().length != 0) {
        stopApps.setVisibility(stopApps.VISIBLE);
      } else {
        stopApps.setVisibility(stopApps.GONE);
      }
    }
    Log.i("RosAndroid", "gridview updated");
  }

  @Override
  protected void onNodeCreate(Node node) {
    Log.i("RosAndroid", "AppChooser.onNodeCreate");
    try {
      super.onNodeCreate(node);
    } catch( Exception ex ) {
      safeSetStatus("Failed: " + ex.getMessage());
      node = null;
      return;
    }

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        robotNameView.setText(getCurrentRobot().getRobotName());
      }});
    
    if (dashboard != null) {
      runOnUiThread(new Runnable() {
          @Override
          public void run() {
            LinearLayout top = (LinearLayout)findViewById(R.id.top_bar);
            top.removeView((View)dashboard);
          }});
      dashboard = null;
    }
    if (getCurrentRobot().getRobotId().getControlUri() != null) {
      runOnUiThread(new Runnable() {
          @Override
          public void run() {
            deactivate.setVisibility(deactivate.VISIBLE);
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
            top.addView((View)dashboard, lparams);
          }});
      
      try {
        dashboard.start(node);
      } catch (RosInitException ex) {
        safeSetStatus("Failed: " + ex.getMessage());
      }
    }

    if (appManager == null) {
      safeSetStatus("Robot not available");
      return;
    }
    
    //Note, I've temporarily disabled caching.
    if (System.currentTimeMillis() - availableAppsCacheTime >= 0 * 1000) {
      Log.i("RosAndroid", "sending list apps request");
    }

    try {
      appManager.addAppListCallback(new MessageListener<AppList>() {
        @Override
        public void onNewMessage(AppList message) {
          availableAppsCache = message.available_apps;
          runningAppsCache = message.running_apps;
          Log.i("RosAndroid", "ListApps.Response: " + availableAppsCache.size() + " apps");
          availableAppsCacheTime = System.currentTimeMillis();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateAppList(availableAppsCache, runningAppsCache);
            }
          });
        }

      });
    } catch (RosInitException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  @Override
  protected void onNodeDestroy(Node node) {
    Log.i("RosAndroid", "onNodeDestroy");
    super.onNodeDestroy(node);
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
          deactivate.setVisibility(deactivate.GONE);
        }});
    if (dashboard != null) {
      dashboard.stop();
      runOnUiThread(new Runnable() {
          @Override
          public void run() {
            LinearLayout top = (LinearLayout)findViewById(R.id.top_bar);
            top.removeView((View)dashboard);
          }});
    }
  }

  public void chooseNewMasterClicked(View view) {
    chooseNewMaster();
  }

  public void deactivateRobotClicked(View view) {
    new AlertDialog.Builder(this).setTitle("Deactivate Robot").setCancelable(false)
      .setMessage("Are you sure you want to deactivate the robot? This will power down the"
                  + " robot's arms and allow others to run custom software on it.")
      .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) { terminateRobot(); }})
      .setNegativeButton("No", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) { }})
      .create().show();
  }

  public void stopApplicationsClicked(View view) {
    appManager.stopApp("*", new ServiceResponseListener<StopApp.Response>() {
      @Override
      public void onSuccess(StopApp.Response message) {
        if (message.stopped || message.error_code == StatusCodes.NOT_RUNNING) {
          //safeSetStatus("Stopped.");
        } else {
          //safeSetStatus("ERROR: " + message.message);
        }
      }
      @Override
      public void onFailure(Exception e) {
        //safeSetStatus("Failed: cannot contact robot!");
      }
    });
    
  }

  private void setStatus(String status_message) {
    TextView statusView = (TextView) findViewById(R.id.status_view);
    if (statusView != null) {
      statusView.setText(status_message);
    }
  }

  private void safeSetStatus(final String statusMessage) {
    final TextView statusView = (TextView) findViewById(R.id.status_view);
    if (statusView != null) {
      statusView.post(new Runnable() {

        @Override
        public void run() {
          statusView.setText(statusMessage);
        }
      });
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.app_chooser_menu, menu);
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
}
