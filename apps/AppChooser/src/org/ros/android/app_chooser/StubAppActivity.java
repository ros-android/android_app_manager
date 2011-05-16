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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.ros.Node;
import org.ros.ServiceResponseListener;
import org.ros.message.app_manager.StatusCodes;
import org.ros.service.app_manager.StartApp;
import org.ros.service.app_manager.StopApp;

import ros.android.activity.AppStartCallback;
import ros.android.activity.AppManager;
import ros.android.activity.RosAppActivity;

public class StubAppActivity extends RosAppActivity implements AppStartCallback {
  private String robotAppName;
  private String robotAppDisplayName;
  private TextView statusView;
  private Button startButton;
  private Button stopButton;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    robotAppName = getIntent().getStringExtra(AppManager.PACKAGE + ".robot_app_name");
    robotAppDisplayName = getIntent().getStringExtra(
        AppManager.PACKAGE + ".robot_app_display_name");

    setTitle(robotAppDisplayName);
    setContentView(R.layout.stub_app);
    statusView = (TextView) findViewById(R.id.status_view);
    stopButton = (Button) findViewById(R.id.stop_button);
    startButton = (Button) findViewById(R.id.start_button);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // RosActivity super-superclass onPause() destroys the node
    // running appManager, so disable the buttons until onNodeCreate()
    // is called with a valid appManager.
    setButtonsEnabled(false);
  }

  private void startApp() {
    if( appManager == null ) {
      safeSetStatus("Failed: appManager is not ready.");
      return;
    }
    appManager.startApp(robotAppName, new ServiceResponseListener<StartApp.Response>() {
      @Override
      public void onSuccess(StartApp.Response message) {
        if (message.started) {
          safeSetStatus("started");
        } else {
          safeSetStatus(message.message);
        }
      }

      @Override
      public void onFailure(Exception e) {
        safeSetStatus("Failed: " + e.getMessage());
      }
    });
  }

  public void onStartClicked(View view) {
    setStatus("Starting...");
    // TODO: add guard so that we cannot start multiple times
    startApp();
    setStatus("Launching");
  }

  public void onStopClicked(View view) {
    if( appManager == null ) {
      setStatus("Failed: appManager is not ready.");
      return;
    }
    setStatus("Stopping...");
    appManager.stopApp("*", new ServiceResponseListener<StopApp.Response>() {

      @Override
      public void onSuccess(StopApp.Response message) {
        if (message.stopped || message.error_code == StatusCodes.NOT_RUNNING) {
          safeSetStatus("Stopped.");
        } else {
          safeSetStatus("ERROR: " + message.message);
        }
      }

      @Override
      public void onFailure(Exception e) {
        safeSetStatus("Failed: cannot contact robot!");
      }
    });
  }

  public void onExitClicked(View view) {
    finish();
  }

  /**
   * Set the status text. Safe to call from any thread.
   */
  private void safeSetStatus(final String status) {
    statusView.post(new Runnable() {
      @Override
      public void run() {
        setStatus(status);
      }
    });
  }

  private void setStatus(String status) {
    statusView.setText(status);
  }

  @Override
  public void appStartResult(boolean success, int resultCode, String message) {
    if (success) {
      safeSetStatus("started");
    } else {
      safeSetStatus(message);
    }
  }

  private void setButtonsEnabled(final boolean enabled) {
    statusView.post(new Runnable() {
      @Override
      public void run() {
        stopButton.setEnabled(enabled);
        startButton.setEnabled(enabled);
      }
    });
  }

  @Override
  protected void onNodeCreate(Node node) {
    super.onNodeCreate(node);
    if( appManager == null ) {
      safeSetStatus("Failed to initialize appManager.");
    } else {
      setButtonsEnabled(true);
    }
  }
}
