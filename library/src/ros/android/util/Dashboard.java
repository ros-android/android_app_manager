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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.app.Activity;
import org.ros.node.Node;
import org.ros.node.parameter.ParameterTree;
import org.ros.exception.RosException;
import android.util.Log;

public class Dashboard {
  public interface DashboardInterface {
    /**
     * Set the ROS Node to use to get status data and connect it up. Disconnects
     * the previous node if there was one.
     */
    public void start(Node node) throws RosException;
    public void stop();
  }

  private DashboardInterface dashboard;
  private Activity activity;
  private ViewGroup view;
  private ViewGroup.LayoutParams lparams;

  public Dashboard(Activity activity) {
    dashboard = null;
    this.activity = activity;
    this.view = null;
    this.lparams = null;
  }

  public void setView(ViewGroup view, ViewGroup.LayoutParams lparams) {
    this.view = view;
    this.lparams = lparams;
  }

  public void stop() {
    activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Dashboard.DashboardInterface dash = dashboard;
          if (dash != null) {
            dash.stop();
            view.removeView((View)dash);
          }
          dashboard = null;
        }});
  }

  public void start(Node node) throws RosException {
    if (dashboard != null) { //FIXME: should we re-start the dashboard? I think this is really an error.
      return;
    }
    dashboard = Dashboard.createDashboard(node, activity);
    if (dashboard != null) {
      activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            //LinearLayout top = (LinearLayout)findViewById(R.id.top_bar);
            /*LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);*/
            Dashboard.DashboardInterface dash = dashboard;
            ViewGroup localView = view;
            if (dash != null && localView != null) {
              localView.addView((View)dash, lparams);
            }
          }});
      dashboard.start(node);
    }
  }

  private static DashboardInterface createDashboard(Class dashClass, Context context) {
    ClassLoader classLoader = Dashboard.class.getClassLoader();
    Object[] args = new Object[1];
    DashboardInterface result = null;
    args[0] = context;
    try {
      Class contextClass = Class.forName("android.content.Context");
      result = (DashboardInterface)dashClass.getConstructor(contextClass).newInstance(args);
    } catch (Exception ex) {
      Log.e("Dashboard", "Error during dashboard instantiation:", ex);
      result = null;
    }
    return result;
  }

  private static DashboardInterface createDashboard(String className, Context context) {
    Class dashClass = null;
    try {
      dashClass = Class.forName(className);
    } catch (Exception ex) {
      Log.e("Dashboard", "Error during dashboard class loading:", ex);
      return null;
    }
    return createDashboard(dashClass, context);
    
  }


  /**
   * Dynamically locate and create a dashboard.
   */
  private static DashboardInterface createDashboard(Node node, Context context) {
    ParameterTree tree = node.newParameterTree();
    String dashboardClassName = tree.getString("robot/dashboard/class_name");
    if (dashboardClassName != null) {
      if (!dashboardClassName.equals("")) {
        return createDashboard(dashboardClassName, context);
      }
    }
    return createDashboard("ros.android.views.TurtlebotDashboard", context);
  }

}