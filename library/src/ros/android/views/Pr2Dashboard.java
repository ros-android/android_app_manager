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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import ros.android.util.Dashboard;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
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
import org.ros.message.pr2_msgs.DashboardState;
import org.ros.message.turtlebot_node.TurtlebotSensorState;
import org.ros.namespace.NameResolver;
import org.ros.internal.namespace.GraphName;
import org.ros.service.turtlebot_node.SetDigitalOutputs;
import org.ros.service.turtlebot_node.SetTurtlebotMode;
import org.ros.service.std_srvs.Empty;
import org.ros.service.pr2_power_board.PowerBoardCommand;
import ros.android.activity.R;
import android.content.DialogInterface;

import java.util.ArrayList;
import java.util.HashMap;

public class Pr2Dashboard extends android.widget.LinearLayout implements Dashboard.DashboardInterface {
  private ImageButton modeButton;
  private ProgressBar modeWaitingSpinner;
  private BatteryLevelView robotBattery;
  private BatteryLevelView laptopBattery; //TRASH
  private ImageView wirelessEstop;
  private ImageView physicalEstop;
  private Context context; //TRASH

  private enum Pr2RobotState {
    UNKNOWN,
    ANY,
    NONE,
    BREAKERS_OUT,
    MOTORS_OUT, 
    WORKING
  }
  private Pr2RobotState state = Pr2RobotState.UNKNOWN;
  private Pr2RobotState waitingState = Pr2RobotState.ANY;
  private int nBreakers;
  private long serialNumber;

  private Node node;
  private Subscriber<DashboardState> dashboardSubscriber;

  private boolean clickOnTransition = false;
  AlertDialog.Builder alertBuilder;

  //TRASH
  private boolean powerOn = false;
  private int numModeResponses;
  private int numModeErrors;

  public Pr2Dashboard(Context context) {
    super(context);
    this.context = context;
    inflateSelf(context);
  }

  public Pr2Dashboard(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    inflateSelf(context);
  }

  private void inflateSelf(Context context) {
    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.pr2_dashboard, this);

    modeButton = (ImageButton) findViewById(R.id.pr2_mode_button);
    modeButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        onModeButtonClicked();
      }
    });

    modeWaitingSpinner = (ProgressBar) findViewById(R.id.pr2_mode_waiting_spinner);
    modeWaitingSpinner.setIndeterminate(true);
    modeWaitingSpinner.setVisibility(View.GONE);
    setModeWaiting(true);

    robotBattery = (BatteryLevelView) findViewById(R.id.pr2_robot_battery);
    wirelessEstop = (ImageView) findViewById(R.id.pr2_wireless_estop);
    physicalEstop = (ImageView) findViewById(R.id.pr2_physical_estop);

    state = Pr2RobotState.UNKNOWN;
    waitingState = Pr2RobotState.ANY;
    clickOnTransition = false;

    alertBuilder = new AlertDialog.Builder(context).setTitle("Error").setCancelable(false)
                           .setNeutralButton("Dismiss", new DialogInterface.OnClickListener() {
                               public void onClick(DialogInterface dialog, int which) {}});
  }

  /**
   * Set the ROS Node to use to get status data and connect it up. Disconnects
   * the previous node if there was one.
   */
  public void start(Node node) throws RosInitException {
    stop();
    this.node = node;
    try {
      dashboardSubscriber =
          node.createSubscriber("dashboard_agg", "pr2_msgs/DashboardState", new MessageListener<DashboardState>() {
            @Override
            public void onNewMessage(final DashboardState msg) {
              Pr2Dashboard.this.post(new Runnable() {
                @Override
                public void run() {
                  Pr2Dashboard.this.handleDashboardState(msg);
                }
              });
            }
            });

      NameResolver resolver = node.getResolver().createResolver(new GraphName("/"));
    } catch( Exception ex ) {
      this.node = null;
      throw( new RosInitException( ex ));
    }
  }

  public void stop() {
    if(dashboardSubscriber != null) {
      dashboardSubscriber.shutdown();
    }
    dashboardSubscriber = null;
    node = null;
  }

  /**
   * Populate view with new diagnostic data. This must be called in the UI
   * thread.
   */
  private void handleDashboardState(DashboardState msg) {
    robotBattery.setBatteryPercent((int)msg.power_state.relative_capacity);
    robotBattery.setPluggedIn(msg.power_state.AC_present != 0);

    if (msg.power_board_state.wireless_stop == false) {
      physicalEstop.setColorFilter(Color.GRAY);
      wirelessEstop.setColorFilter(Color.RED);
    } else {
      wirelessEstop.setColorFilter(Color.GREEN);
      if (msg.power_board_state.run_stop == true) {
        physicalEstop.setColorFilter(Color.GREEN);
      } else {
        physicalEstop.setColorFilter(Color.RED);
      }
    }

    Pr2RobotState previous_state = state;

    boolean breaker_state = true;
    if (msg.power_board_state_valid == true && msg.power_board_state_valid == true) {
      for (int i = 0; i < msg.power_board_state.circuit_state.length; i++) {
        if (msg.power_board_state.circuit_state[i] != 3) { //Breaker invalid
          breaker_state = false;
        }
      }
      nBreakers = msg.power_board_state.circuit_state.length;
      serialNumber = msg.power_board_state.serial_num;
    } else {
      breaker_state = false;
    }

    if (breaker_state == false) {
      modeButton.setColorFilter(Color.RED);
      state = Pr2RobotState.BREAKERS_OUT;
    } else {
      if (msg.motors_halted_valid == true && msg.motors_halted.data == true) {
        modeButton.setColorFilter(Color.YELLOW);
        state = Pr2RobotState.MOTORS_OUT;
      } else { //FIXME: diagnostics
        modeButton.setColorFilter(Color.GREEN);
        state = Pr2RobotState.WORKING;
      }
    }

    if (state != previous_state) {
      if ((state == waitingState || waitingState == Pr2RobotState.ANY) && waitingState != Pr2RobotState.NONE) {
        setModeWaiting(false);
        waitingState = Pr2RobotState.NONE;
      }
      if (clickOnTransition) {
        clickOnTransition = false;
        onModeButtonClicked();
      }
    }

  }

  private void onModeButtonClicked() {
    switch (state) {
    case BREAKERS_OUT:
      waitingState = Pr2RobotState.MOTORS_OUT;
      setModeWaiting(true);
      clickOnTransition = true;
      //Send reset to the breakers.
      for (int i = 0; i < nBreakers; i++) { //FIXME: hack for number of breakers
        PowerBoardCommand.Request modeRequest = new PowerBoardCommand.Request();
        modeRequest.breaker_number = i;
        modeRequest.command = "start";
        modeRequest.serial_number = serialNumber;
        ServiceClient<PowerBoardCommand.Request, PowerBoardCommand.Response> modeServiceClient =
            node.createServiceClient("power_board/control", "pr2_power_board/PowerBoardCommand");
        modeServiceClient.call(modeRequest, new ServiceResponseListener<PowerBoardCommand.Response>() {
            @Override
            public void onSuccess(PowerBoardCommand.Response message) { } //Diagnostics will update. 
            @Override
            public void onFailure(Exception ex) {
              final Exception e = ex;
              Pr2Dashboard.this.post(new Runnable() {
                  public void run() {
                    alertBuilder.setMessage("Cannot reset the breakers: " + e.toString()).show();
                  }});
            }});
      }
      break;
    case MOTORS_OUT:
      waitingState = Pr2RobotState.WORKING;
      setModeWaiting(true);
      //Send reset to the motors.
      Empty.Request modeRequest = new Empty.Request();
      ServiceClient<Empty.Request, Empty.Response> modeServiceClient =
	      node.createServiceClient("pr2_etherCAT/reset_motors", "std_srvs/Empty");
      modeServiceClient.call(modeRequest, new ServiceResponseListener<Empty.Response>() {
          @Override
          public void onSuccess(Empty.Response message) { } //Diagnostics will update. 
          @Override
          public void onFailure(Exception ex) {
            final Exception e = ex;
            Pr2Dashboard.this.post(new Runnable() {
                public void run() {
                  alertBuilder.setMessage("Cannot reset the motors: " + e.toString()).show();
                }});
          }});
      break;
    case WORKING:
      //Nothing to do. We only shutdown the PR2 using the app chooser, because it is different.
      waitingState = Pr2RobotState.WORKING;
      break;
    default:
      Pr2Dashboard.this.post(new Runnable() {
          public void run() {
            alertBuilder.setMessage("Robot is in an unknown or invalid state. Please wait and try again.").show();
          }});
      break;
    }
  }
  
  private void foo() {

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

}
