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

package ros.android.views;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import org.ros.DefaultNode;
import org.ros.MessageListener;
import org.ros.Node;
import org.ros.ServiceResponseListener;
import org.ros.Subscriber;
import org.ros.exception.RosInitException;
import org.ros.ServiceClient;
import org.ros.message.diagnostic_msgs.DiagnosticArray;
import org.ros.message.diagnostic_msgs.DiagnosticStatus;
import org.ros.message.diagnostic_msgs.KeyValue;
import org.ros.message.turtlebot_node.TurtlebotSensorState;
import org.ros.namespace.NameResolver;
import org.ros.internal.namespace.GraphName;
import org.ros.service.turtlebot_node.SetDigitalOutputs;
import org.ros.service.turtlebot_node.SetTurtlebotMode;
import ros.android.activity.R;

import java.util.ArrayList;
import java.util.HashMap;

public class TurtlebotDashboard extends LinearLayout {
  private ImageButton modeButton;
  private ProgressBar modeWaitingSpinner;
  private BatteryLevelView robotBattery;
  private BatteryLevelView laptopBattery;

  private Node node;
  private Subscriber<DiagnosticArray> diagnosticSubscriber;

  private boolean powerOn = false;
  private int numModeResponses;
  private int numModeErrors;

  public TurtlebotDashboard(Context context) {
    super(context);
    inflateSelf(context);
  }

  public TurtlebotDashboard(Context context, AttributeSet attrs) {
    super(context, attrs);
    inflateSelf(context);
  }

  private void inflateSelf(Context context) {
    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.turtlebot_dashboard, this);

    modeButton = (ImageButton) findViewById(R.id.mode_button);
    modeButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        onModeButtonClicked();
      }
    });

    modeWaitingSpinner = (ProgressBar) findViewById(R.id.mode_waiting_spinner);
    modeWaitingSpinner.setIndeterminate(true);
    modeWaitingSpinner.setVisibility(View.GONE);

    robotBattery = (BatteryLevelView) findViewById(R.id.robot_battery);
    laptopBattery = (BatteryLevelView) findViewById(R.id.laptop_battery);
  }

  /**
   * Set the ROS Node to use to get status data and connect it up. Disconnects
   * the previous node if there was one.
   */
  public void start(Node node) throws RosInitException {
    stop();
    this.node = node;
    try {
      diagnosticSubscriber =
          node.createSubscriber("diagnostics_agg", "diagnostic_msgs/DiagnosticArray", new MessageListener<DiagnosticArray>() {
            @Override
            public void onNewMessage(final DiagnosticArray msg) {
              TurtlebotDashboard.this.post(new Runnable() {
                @Override
                public void run() {
                  TurtlebotDashboard.this.handleDiagnosticArray(msg);
                }
              });
            }
          });

      NameResolver resolver = node.getResolver().createResolver(new GraphName("/turtlebot_node"));
    } catch( Exception ex ) {
      this.node = null;
      throw( new RosInitException( ex ));
    }
  }

  public void stop() {
    if(diagnosticSubscriber != null) {
      diagnosticSubscriber.shutdown();
    }
    diagnosticSubscriber = null;
    node = null;
  }

  /**
   * Populate view with new diagnostic data. This must be called in the UI
   * thread.
   */
  private void handleDiagnosticArray(DiagnosticArray msg) {
    String mode = null;
    for (DiagnosticStatus status : msg.status) {
      if (status.name.equals("/Power System/Battery")) {
        populateBatteryFromStatus(robotBattery, status);
      }
      if (status.name.equals("/Power System/Laptop Battery")) {
        populateBatteryFromStatus(laptopBattery, status);
      }
      if (status.name.equals("/Mode/Operating Mode")) {
        mode = status.message;
      }
    }
    showMode(mode);
  }

  private void onModeButtonClicked() {
      powerOn = !powerOn;

      SetTurtlebotMode.Request modeRequest = new SetTurtlebotMode.Request();
      SetDigitalOutputs.Request setDigOutRequest = new SetDigitalOutputs.Request();
      setDigOutRequest.digital_out_1 = 0;
      setDigOutRequest.digital_out_2 = 0;
      if (powerOn) {
        modeRequest.mode = TurtlebotSensorState.OI_MODE_FULL;
        setDigOutRequest.digital_out_0 = 1; // main breaker on
      } else {
        modeRequest.mode = TurtlebotSensorState.OI_MODE_PASSIVE;
        setDigOutRequest.digital_out_0 = 0; // main breaker off
      }

      setModeWaiting( true );

      numModeResponses = 0;
      numModeErrors = 0;

      // TODO: can't I save the modeServiceClient? Causes trouble.
      try {
	  ServiceClient<SetTurtlebotMode.Request, SetTurtlebotMode.Response> modeServiceClient =
	      node.createServiceClient("turtlebot_node/set_operation_mode", "turtlebot_node/SetTurtlebotMode");
        modeServiceClient.call(modeRequest, new ServiceResponseListener<SetTurtlebotMode.Response>() {
            @Override
              public void onSuccess(SetTurtlebotMode.Response message) {
              numModeResponses++;
              updateModeWaiting();
            }

            @Override
              public void onFailure(Exception e) {
              numModeResponses++;
              numModeErrors++;
              updateModeWaiting();
            }
          });
      } catch(Exception ex) {
        Toast.makeText(getContext(), "Exception in service call for set_operation_mode: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        Log.i("TurtlebotDashboard", "making toast.");
      }

      try {
        ServiceClient<SetDigitalOutputs.Request, SetDigitalOutputs.Response> setDigOutServiceClient =
          node.createServiceClient("turtlebot_node/set_digital_outputs", "turtlebot_node/SetDigitalOutputs");
        setDigOutServiceClient.call(setDigOutRequest,
                                    new ServiceResponseListener<SetDigitalOutputs.Response>() {
                                      @Override
                                        public void onSuccess(final SetDigitalOutputs.Response msg) {
                                        numModeResponses++;
                                        updateModeWaiting();
                                      }

                                      @Override
                                        public void onFailure(Exception e) {
                                        numModeResponses++;
                                        numModeErrors++;
                                        updateModeWaiting();
                                      }
                                    });
      } catch(Exception ex) {
        Toast.makeText(getContext(), "Exception in service call for set_digital_outputs: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        Log.i("TurtlebotDashboard", "making toast.");
      }
  }

  private void updateModeWaiting() {
    if( numModeResponses >= 2 ) {
      setModeWaiting( false );
    }
  }

  private void setModeWaiting(final boolean waiting) {
    post( new Runnable() {
        @Override public void run() {
          modeWaitingSpinner.setVisibility( waiting ? View.VISIBLE : View.GONE );
        }
      });
  }

  private void showMode(String mode) {
    if (mode == null) {
      modeButton.setColorFilter(Color.GRAY);
    } else if (mode.equals("Full")) {
      modeButton.setColorFilter(Color.GREEN);
      powerOn = true;
    } else if (mode.equals("Safe")) {
      modeButton.setColorFilter(Color.YELLOW);
      powerOn = true;
    } else if (mode.equals("Passive")) {
      modeButton.setColorFilter(Color.RED);
      powerOn = false;
    } else {
      modeButton.setColorFilter(Color.GRAY);
      Log.w("TurtlebotDashboard", "Unknown mode string: '" + mode + "'");
    }
    setModeWaiting(false);
  }

  private void populateBatteryFromStatus(BatteryLevelView view, DiagnosticStatus status) {
    HashMap<String, String> values = keyValueArrayToMap(status.values);
    try {
      float percent =
          100 * Float.parseFloat(values.get("Charge (Ah)"))
              / Float.parseFloat(values.get("Capacity (Ah)"));
      view.setBatteryPercent((int) percent);
      // TODO: set color red/yellow/green based on level (maybe with level-set
      // in XML)
    } catch (NumberFormatException ex) {
      // TODO: make battery level gray
    } catch (ArithmeticException ex) {
      // TODO: make battery level gray
    } catch (NullPointerException ex) {
      // Do nothing: data wasn't there.
    }
    try {
      view.setPluggedIn( Float.parseFloat(values.get("Current (A)")) > 0);
    } catch (NumberFormatException ex) {
    } catch (ArithmeticException ex) {
    } catch (NullPointerException ex) {
    }
  }

  private HashMap<String, String> keyValueArrayToMap(ArrayList<KeyValue> kvs) {
    HashMap<String, String> map = new HashMap<String, String>();
    for (KeyValue kv : kvs) {
      map.put(kv.key, kv.value);
    }
    return map;
  }
}
