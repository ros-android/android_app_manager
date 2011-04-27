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

package ros.app_manager;

import org.apache.commons.logging.Log;
import org.ros.Node;
import org.ros.NodeConfiguration;
import org.ros.NodeMain;
import org.ros.message.app_manager.App;
import org.ros.service.app_manager.ListApps;
import org.ros.service.app_manager.StartApp;

import java.util.ArrayList;

/**
 * @author kwc@willowgarage.com (Ken Conley)
 */
public class TestAppManager implements NodeMain {
  private AppManager appManager;

  @Override
  public void run(NodeConfiguration context) {
    Node node = null;
    try {
      node = new Node("app_manager_client", context);
      final Log log = node.getLog();
      log.info("Creating app manager");
      appManager = new AppManager(node, "turtlebot");

      log.info("Listing apps");
      BasicAppManagerCallback<ListApps.Response> callback = new BasicAppManagerCallback<ListApps.Response>();
      appManager.listApps(callback);
      ArrayList<App> availableApps = callback.waitForResponse(10 * 1000).available_apps;
      for (App app : availableApps) {
        log.info("Available app: " + app.display_name);
      }
      log.info("Getting running apps");
      appManager.listApps(callback);
      ArrayList<App> runningApps = callback.waitForResponse(10 * 1000).running_apps;
      for (App app : runningApps) {
        log.info("Running app: " + app.display_name);
      }

      log.info("calling start app");
      BasicAppManagerCallback<StartApp.Response> startCallback = new BasicAppManagerCallback<StartApp.Response>();
      appManager.startApp("foo/fakeApp", startCallback);
      StartApp.Response startResponse = startCallback.waitForResponse(10 * 1000);
      log.info("start app called");
      if (!startResponse.started) {
        log.info("fake app failed to start (this is good).  response message is: "
            + startResponse.message);
      } else {
        log.error("fake app started, this is weird");
      }

      /*
      StartAppFuture startAppFuture = new StartAppFuture(appManager,
          "turtlebot_teleop/android_teleop", false);
      startAppFuture.start();
      while (startAppFuture.getResultCode() == StartAppFuture.PENDING) {
        Thread.sleep(100);
      }
      System.out.println(startAppFuture.getResultMessage());
*/
    } catch (Exception e) {
      if (node != null) {
        node.getLog().fatal(e);
      } else {
        e.printStackTrace();
      }
    }
    System.exit(0);
  }
}
