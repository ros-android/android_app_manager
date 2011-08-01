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

package ros.android.activity;

import android.app.AlertDialog;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import org.ros.node.DefaultNodeFactory;
import org.ros.node.Node;
import org.ros.exception.RosException;
import ros.android.util.RobotId;
import ros.android.util.MasterChooser;
import ros.android.util.MasterChecker;
import ros.android.util.WiFiChecker;
import ros.android.util.ControlChecker;
import ros.android.util.RobotDescription;
import org.ros.node.NodeConfiguration;
import java.lang.Runnable;
import java.lang.System;
import android.net.wifi.WifiManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URI;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
/**
 *
 * @author hersh@willowgarage.com
 * @author erublee@willowgarage.com
 * @author kwc@willowgarage.com (Ken Conley)
 *
 */
public class RosActivity extends Activity {
  private MasterChooser masterChooser;
  private Node node; //todo change?
  private Exception errorException;
  private String errorMessage;
  private Handler uiThreadHandler = new Handler();
  private Thread nodeThread;
  private boolean doShutdown = false;
  private boolean doTerminate = false;

  public RosActivity() {
    doShutdown = false;
    masterChooser = new MasterChooser(this);
  }

  public Exception getErrorException() {
    return errorException;
  }

  public void setErrorException(Exception errorException) {
    this.errorException = errorException;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public RobotDescription getCurrentRobot() {
    return masterChooser.getCurrentRobot();
  }

  public void shutdownRobot() {
    Log.i("RosActivity", "Shutting down robot");
    doShutdown = true;
  }
  public void terminateRobot() {
    Log.i("RosActivity", "Shutting down and terminating robot");
    doShutdown = true;
    doTerminate = true;
  }


  /**
   * Re-launch the MasterChooserActivity to choose a new ROS master. The results
   * are handled in onActivityResult() and onResume() since launching a new
   * activity necessarily pauses this current one.
   */
  public void chooseNewMaster() {
    masterChooser.launchChooserActivity();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (nodeThread != null) {
      nodeThread.interrupt();
      nodeThread = null;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent result_intent) {
    if (masterChooser.handleActivityResult(requestCode, resultCode, result_intent)) {
      // Save before checking validity in case someone wants to force
      // the next app to use the chooser.
      masterChooser.saveCurrentRobot();
      if (!masterChooser.hasRobot()) {
        Toast.makeText(this, "Cannot run without a ROS master.", Toast.LENGTH_LONG).show();
        finish();
      }
    }
  }

  /**
   * Wraps the alert dialog so it can be used as a yes/no function
   */
  private class AlertDialogWrapper {
    private int state;
    private AlertDialog dialog;
    private RosActivity context;

    public AlertDialogWrapper(RosActivity context, AlertDialog.Builder builder, String yesButton, String noButton) {
      state = 0;
      this.context = context;
      dialog = builder.setPositiveButton(yesButton, new DialogInterface.OnClickListener() {
                              public void onClick(DialogInterface dialog, int which) { state = 1; }})
                      .setNegativeButton(noButton, new DialogInterface.OnClickListener() {
                              public void onClick(DialogInterface dialog, int which) { state = 2; }})
                      .create();
    }

    public AlertDialogWrapper(RosActivity context, AlertDialog.Builder builder, String okButton) {
      state = 0;
      this.context = context;
      dialog = builder.setNeutralButton(okButton, new DialogInterface.OnClickListener() {
                              public void onClick(DialogInterface dialog, int which) { state = 1; }})
                      .create();
    }


    public void setMessage(String m) {
      dialog.setMessage(m);
    }

    public boolean show(String m) {
      setMessage(m);
      return show();
    }

    public boolean show() {
      state = 0;
      context.runOnUiThread(new Runnable() { 
          public void run() {
            dialog.show();
          }});
      //Kind of a hack. Do we know a better way?
      while (state == 0) {
        try {
          Thread.sleep(1L);
        } catch (Exception e) {
          break;
        }
      }
      return state == 1;
    }
    
  }

  private class ProgressDialogWrapper {
    private ProgressDialog progress;
    private RosActivity activity;

    public ProgressDialogWrapper(RosActivity activity) {
      this.activity = activity;
      progress = null;
    }

    public void dismiss() {
      if (progress != null) {
        progress.dismiss();
      }
      progress = null;
    }

    public void show(String title, String text) {
      if (progress != null) {
        this.dismiss();
      }
      progress = ProgressDialog.show(activity, title, text, true, false);
      progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    }
  }

  /**
   * Read the current ROS master URI from external storage and set up the ROS
   * node from the resulting node context. If the current master is not set or
   * is invalid, launch the MasterChooserActivity to choose one or scan a new
   * one.
   */
  @Override
  protected void onResume() {
    doShutdown = false;
    doTerminate = false;

    super.onResume();
    if (node == null) {
      masterChooser.loadCurrentRobot();
      if (masterChooser.hasRobot()) { //A robot is in the current robot YAML file
        final RobotId id = masterChooser.getCurrentRobot().getRobotId(); //Used in the classes below to find the robot id.

        //Create alert dialog to see if the user wants to switch WiFi networks.
        final AlertDialogWrapper wifiDialog =  new AlertDialogWrapper(this,
                   new AlertDialog.Builder(this).setTitle("Change Wifi?").setCancelable(false),
                   "Yes", "No");
        
        //Create alert dialog to see if the user wants to evict another user.
        final AlertDialogWrapper evictDialog =  new AlertDialogWrapper(this,
                   new AlertDialog.Builder(this).setTitle("Evict User?").setCancelable(false),
                   "Yes", "No");

        //Create alert dialog for issues.
        final AlertDialogWrapper errorDialog =  new AlertDialogWrapper(this,
                   new AlertDialog.Builder(this).setTitle("Could Not Connect").setCancelable(false),
                   "Ok");

        //Create the progress bar
        final ProgressDialogWrapper progress = new ProgressDialogWrapper(this);

        //Run a set of checkers in series.

        //The last step - ensure the master is up.
        final MasterChecker mc = new MasterChecker(
             new MasterChecker.RobotDescriptionReceiver() {
               public void receive(RobotDescription robotDescription) {
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                     }});
                 createNode();
               }
             },
             new MasterChecker.FailureHandler() {
               public void handleFailure(String reason) {
                 final String reason2 = reason;
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                     }
                   });
                 errorDialog.show("Cannot contact ROS master: " + reason2);
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       masterChooser.launchChooserActivity();
                     }
                   });
               }
             });

        //Ensure the robot is in a good state
        final ControlChecker cc = new ControlChecker(
             new ControlChecker.SuccessHandler() {
               public void handleSuccess() {
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                       progress.show("Connecting...", "Connecting to ROS master");
                     }});
                 mc.beginChecking(id);
               }
             },
             new ControlChecker.FailureHandler() {
               public void handleFailure(String reason) {
                 final String reason2 = reason;
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                     }
                   });
                 errorDialog.show("Cannot connect to control robot: " + reason2);
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       masterChooser.launchChooserActivity();
                     }
                   });
               }
             },
             new ControlChecker.EvictionHandler() {
               public boolean doEviction(String current) {
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                     }});
                 evictDialog.setMessage(current + " is running custom software on this robot. Do you want to evict this user?");
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.show("Connecting...", "Deactivating robot");
                     }});
                 return evictDialog.show();
               }
             },
             new ControlChecker.StartHandler() {
               public void handleStarting() {
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                       progress.show("Connecting...", "Starting robot");
                     }});
               }
             });

        //Ensure that the correct WiFi network is selected.
        final WiFiChecker wc = new WiFiChecker(
             new WiFiChecker.SuccessHandler() {
               public void handleSuccess() {
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                       progress.show("Connecting...", "Checking robot state");
                     }});
                 cc.beginChecking(id);
               }
             },
             new WiFiChecker.FailureHandler() {
               public void handleFailure(String reason) {
                 final String reason2 = reason;
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                     }
                   });
                 errorDialog.show("Cannot connect to robot WiFi: " + reason2);
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       masterChooser.launchChooserActivity();
                     }
                   });
               }
             },
             new WiFiChecker.ReconnectionHandler() {
               public boolean doReconnection(String from, String to) {
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.dismiss();
                     }});
                 wifiDialog.setMessage("To use this robot, you must switch wifi networks. Do you want to switch from " + from + " to " + to + "?");
                 runOnUiThread(new Runnable() { 
                     public void run() {
                       progress.show("Connecting...", "Switching wifi networks");
                     }});
                 return wifiDialog.show();
               }
             });

        progress.show("Connecting...", "Checking wifi connection");
        //Start the checkers.
        wc.beginChecking(id, (WifiManager)getSystemService(WIFI_SERVICE));
      } else {
        Toast.makeText(this, "please select a robot", Toast.LENGTH_SHORT).show();
        // we don't have a master yet.
        masterChooser.launchChooserActivity();
      }
    }
  }

  private void controlTerminate() {
    final ProgressDialogWrapper progress = new ProgressDialogWrapper(this);
    if (getCurrentRobot() != null) {
      if (getCurrentRobot().getRobotId() != null) {
        if (getCurrentRobot().getRobotId().getControlUri() != null) {
          runOnUiThread(new Runnable() { 
              public void run() {
                progress.show("Deactivating...", "Deactivating robot");
              }});
          String uri = getCurrentRobot().getRobotId().getControlUri() + "?action=STOP_ROBOT";
          try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet();
            request.setURI(new URI(uri));
            HttpResponse response = client.execute(request);
            BufferedReader in = new BufferedReader
              (new InputStreamReader(response.getEntity().getContent()));
            StringBuffer sb = new StringBuffer("");
            String line = "";
            String NL = System.getProperty("line.separator");
            while ((line = in.readLine()) != null) {
              sb.append(line + NL);
            }
            in.close();
            String page = sb.toString();
            Log.d("RosActivity", "Shutdown: " + uri);
            Log.d("RosActivity", page);
          } catch (java.io.IOException ex) {
            Log.e("RosActivity", "IOError: " + uri, ex);
          } catch (java.net.URISyntaxException ex) {
            Log.e("RosActivity", "URI Invalid: " + uri, ex);
          }
          runOnUiThread(new Runnable() { 
              public void run() {
                progress.dismiss();
              }});
        }
      }
    }
  }

  /**
   * Sets up nodeThread and starts ROS {@link Node}.
   */
  private void createNode() {
    nodeThread = new Thread() {
      @Override
      public void run() {
        try {
          NodeConfiguration config = masterChooser.createConfiguration();
          if (config == null) {
            Log.e("RosAndroid", "Configuration for node is null!");
          }
          String milis = Long.toString(System.currentTimeMillis());
          String name = "android" + milis;
          Log.i("RosAndroid", "Creating node \"" + name + "\"");
          node = new DefaultNodeFactory().newNode(name, config);
        } catch (Exception e) {
	    Log.e("RosAndroid", "Exception while creating node.", e);
          node = null;
          setErrorMessage("failed to create node" + e.getMessage());
          setErrorException(e);
          return;
        }
        if( node == null ) {
          setErrorMessage("failed to create node for unknown reasons");
          Log.e("RosAndroid", "Unknown error upon node creation");
        } else {
          onNodeCreate(node);
          try {
            while (!doShutdown) {
              Thread.sleep(10);
            }
          } catch (InterruptedException e) {
            Log.i("RosAndroid", "node thread exiting");
          }
          Log.i("RosAndroid", "Shutting down");
          onNodeDestroy(node);
          try {
            node.shutdown();
          } catch (Exception e) {
            Log.i("RosAndroid", "Master already down");
          }
          node = null;
          if (doShutdown) {
            if (doTerminate) {
              controlTerminate();
            }
            masterChooser.setCurrentRobot(null);
            masterChooser.saveCurrentRobot();
            chooseNewMaster();
          }
        }
      }
    };
    nodeThread.start();
  }

  /**
   * Subclasses should override to do publisher/subscriber initialization in the
   * node thread.
   *
   * @param node
   */
  protected void onNodeCreate(Node node) {

  }

  /**
   * Subclasses should override to cleanup node-related resources.
   *
   * @param node
   */
  protected void onNodeDestroy(Node node) {

  }

  /**
   * Retrieve the ROS {@link Node} for this {@link Activity}. The {@link Node}
   * is stopped during {@code onPause()} and reinitialized during
   * {@code onResume()}. It is not safe to maintain a handle on the {@link Node}
   * instance.
   *
   * @return Initialized {@link Node} instance.
   * @throws RosException
   *           If {@link Node} was not successfully initialized. Exception will
   *           contain original initialization exception.
   */
  public Node getNode() throws RosException {
    if (node == null) {
      throw new RosException(getErrorException());
    }
    return node;
  }

  public NodeConfiguration getNodeConfiguration() {
    NodeConfiguration r = null;
    if (masterChooser != null) {
      try {
        r = masterChooser.createConfiguration();
      } catch (Exception e) {
        r = null;
      }
    }
    return r;
  }
}
