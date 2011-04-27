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

package ros.android.util;

import android.util.Log;

import org.ros.Node;
import org.ros.ParameterClient;

import java.util.Date;
import java.util.Random;

/**
 * Threaded ROS-master checker. Runs a thread which checks for a valid ROS
 * master and sends back a {@link RobotDescription} (with robot name and type)
 * on success or a failure reason on failure.
 * 
 * @author hersh@willowgarage.com
 */
public class MasterChecker {
  public interface RobotDescriptionReceiver {
    /** Called on success with a description of the robot that got checked. */
    void receive(RobotDescription robotDescription);
  }

  public interface FailureHandler {
    /**
     * Called on failure with a short description of why it failed, like
     * "exception" or "timeout".
     */
    void handleFailure(String reason);
  }

  private CheckerThread checkerThread;
  private RobotDescriptionReceiver foundMasterCallback;
  private FailureHandler failureCallback;

  /** Constructor. Should not take any time. */
  public MasterChecker(RobotDescriptionReceiver foundMasterCallback, FailureHandler failureCallback) {
    this.foundMasterCallback = foundMasterCallback;
    this.failureCallback = failureCallback;
  }

  /**
   * Start the checker thread with the given master URI. If the thread is
   * already running, kill it first and then start anew. Returns immediately.
   */
  public void beginChecking(String masterUri) {
    stopChecking();
    if (masterUri == null || masterUri.equals("")) {
      failureCallback.handleFailure("empty master URI");
      return;
    }
    checkerThread = new CheckerThread(masterUri);
    checkerThread.start();
  }

  /** Stop the checker thread. */
  public void stopChecking() {
    if (checkerThread != null && checkerThread.isAlive()) {
      checkerThread.interrupt();
    }
  }

  private class CheckerThread extends Thread {
    public RobotDescription robotDescription;

    public CheckerThread(String masterUri) {
      robotDescription = new RobotDescription();
      robotDescription.masterUri = masterUri;
      robotDescription.robotName = null;
      robotDescription.robotType = null;
      robotDescription.timeLastSeen = null;

      setDaemon(true);

      // don't require callers to explicitly kill all the old checker threads.
      setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
          failureCallback.handleFailure("exception: " + ex.getMessage());
        }
      });
    }

    @Override
    public void run() {
      while (true) {
        try {
          Node node = new Node("master_checker_" + new Random().nextInt(),
              MasterChooser.createConfiguration(robotDescription.masterUri));
          ParameterClient paramClient = node.createParameterClient();
          robotDescription.robotName = (String) paramClient.getParam("robot/name");
          robotDescription.robotType = (String) paramClient.getParam("robot/type");
          robotDescription.timeLastSeen = new Date(); // current time.
          foundMasterCallback.receive(robotDescription);
          return;
        } catch (Exception ex) {
          Log.e("RosAndroid", "Exception while creating node in MasterChecker for master URI "
              + robotDescription.masterUri);
          failureCallback.handleFailure("exception");
        }
        try {
          sleep(1000 /* ms */);
        } catch (Exception ex) {
        }
      }
    }
  }
}
