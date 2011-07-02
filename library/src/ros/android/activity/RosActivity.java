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

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import org.ros.DefaultNode;
import org.ros.Node;
import org.ros.exception.RosInitException;
import ros.android.util.MasterChooser;
import ros.android.util.RobotDescription;
import org.ros.NodeConfiguration;

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

  public RosActivity() {
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
   * Read the current ROS master URI from external storage and set up the ROS
   * node from the resulting node context. If the current master is not set or
   * is invalid, launch the MasterChooserActivity to choose one or scan a new
   * one.
   */
  @Override
  protected void onResume() {
    super.onResume();
    if (node == null) {
      masterChooser.loadCurrentRobot();
      if (masterChooser.hasRobot()) {
        Toast.makeText(this, "attaching to robot", Toast.LENGTH_SHORT).show();
        createNode();
      } else {
        Toast.makeText(this, "finding a robot", Toast.LENGTH_SHORT).show();
        // we don't have a master yet.
        masterChooser.launchChooserActivity();
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
          node = new DefaultNode("android", config);
        } catch (Exception e) {
	    Log.e("RosAndroid", "Exception while creating node.", e);
          node = null;
          setErrorMessage("failed to create node" + e.getMessage());
          setErrorException(e);
        }
        if( node != null ) {
          onNodeCreate(node);
          try {
            while (true) {
              Thread.sleep(10);
            }
          } catch (InterruptedException e) {
            Log.i("RosAndroid", "node thread exiting");
          }
          onNodeDestroy(node);
          node.shutdown();
          node = null;
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
   * @throws RosInitException
   *           If {@link Node} was not successfully initialized. Exception will
   *           contain original initialization exception.
   */
  public Node getNode() throws RosInitException {
    if (node == null) {
      throw new RosInitException(getErrorException());
    }
    return node;
  }
}
