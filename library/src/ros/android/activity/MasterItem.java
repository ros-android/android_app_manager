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

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import ros.android.util.MasterChecker;
import ros.android.util.RobotDescription;

/**
 * Data class behind view of one item in the list of ROS Masters. Gets created
 * with a master URI and a local host name, then starts a {@link MasterChecker}
 * to look up robot name and type.
 * 
 * @author hersh@willowgarage.com
 */
public class MasterItem implements MasterChecker.RobotDescriptionReceiver,
    MasterChecker.FailureHandler {
  private MasterChecker checker;
  private View view;
  private RobotDescription description;
  private MasterChooserActivity parentMca;
  private String errorReason;

  public MasterItem(RobotDescription robotDescription, MasterChooserActivity parentMca) {
    errorReason = "";
    this.parentMca = parentMca;
    this.description = robotDescription;
    this.description.setConnectionStatus(RobotDescription.CONNECTING);
    checker = new MasterChecker(this, this);
    checker.beginChecking(this.description.getMasterUri());
  }

  public boolean isOk() {
    return this.description.getConnectionStatus().equals(RobotDescription.OK);
  }

  @Override
  public void receive(RobotDescription robotDescription) {
    description.copyFrom(robotDescription);
    description.setConnectionStatus(RobotDescription.OK);
    safePopulateView();
  }

  @Override
  public void handleFailure(String reason) {
    errorReason = reason;
    description.setConnectionStatus(RobotDescription.ERROR);
    safePopulateView();
  }

  public View getView(Context context, View convert_view, ViewGroup parent) {
    LayoutInflater inflater = (LayoutInflater) context
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    // Using convert_view here seems to cause the wrong view to show
    // up sometimes, so I'm always making new ones.
    view = inflater.inflate(R.layout.master_item, null);
    populateView();
    return view;
  }

  private void safePopulateView() {
    if (view != null) {
      final MasterChooserActivity mca = parentMca;

      view.post(new Runnable() {
        @Override
        public void run() {
          populateView();
          mca.writeRobotList();
        }
      });
    }
  }

  private void populateView() {
    Log.i("MasterItem", "connection status = " + description.getConnectionStatus());
    boolean isOk = description.getConnectionStatus().equals(RobotDescription.OK);
    boolean isError = description.getConnectionStatus().equals(RobotDescription.ERROR);
    boolean isConnecting = description.getConnectionStatus().equals(RobotDescription.CONNECTING);

    ProgressBar progress = (ProgressBar) view.findViewById(R.id.progress_circle);
    progress.setIndeterminate(true);
    progress.setVisibility(isConnecting ? View.VISIBLE : View.GONE );

    ImageView errorImage = (ImageView) view.findViewById(R.id.error_icon);
    errorImage.setVisibility(isError ? View.VISIBLE : View.GONE );

    ImageView iv = (ImageView) view.findViewById(R.id.robot_icon);
    iv.setVisibility(isOk ? View.VISIBLE : View.GONE);
    if (description.getRobotType() == null) {
      iv.setImageResource(R.drawable.question_mark);
    } else if (description.getRobotType().equals("pr2")) {
      iv.setImageResource(R.drawable.pr2);
    } else if (description.getRobotType().equals("turtlebot")) {
      iv.setImageResource(R.drawable.turtlebot);
    } else {
      iv.setImageResource(R.drawable.question_mark);
    }

    TextView tv;
    tv = (TextView) view.findViewById(R.id.uri);
    tv.setText(description.getMasterUri().toString());

    tv = (TextView) view.findViewById(R.id.name);
    tv.setText(description.getRobotName());

    tv = (TextView) view.findViewById(R.id.status);
    tv.setText(errorReason);
  }
}
