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

import org.ros.exceptions.RosNameException;

import org.ros.internal.namespace.GraphName;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

public class RobotDescription implements java.io.Serializable {
  public static final String CONNECTING = "connecting...";
  public static final String OK = "ok";
  public static final String NAME_UNKNOWN = "Unknown";
  public static final String TYPE_UNKNOWN = "Unknown";

  private static final long serialVersionUID = 1L;
  private String masterUri;
  private String robotName;
  private String robotType;
  private String connectionStatus;
  private Date timeLastSeen;

  // TODO(kwc): add in canonicalization of robotName

  public RobotDescription(String masterUri, String robotName, String robotType, Date timeLastSeen)
      throws InvalidRobotDescriptionException {
    try {
      GraphName.validateName(robotName);
      if (masterUri == null || masterUri.length() == 0) {
        throw new InvalidRobotDescriptionException("Empty Master URI");
      }
      new URL(masterUri);
    } catch (RosNameException e) {
      throw new InvalidRobotDescriptionException("Bad robot name: " + robotName);
    } catch (MalformedURLException e) {
      throw new InvalidRobotDescriptionException("Bad Master URI: " + masterUri);
    }
    this.masterUri = masterUri;
    this.robotName = robotName;
    this.robotType = robotType;
    this.timeLastSeen = timeLastSeen;
  }

  public void copyFrom(RobotDescription other) {
    masterUri = other.masterUri;
    robotName = other.robotName;
    robotType = other.robotType;
    connectionStatus = other.connectionStatus;
    timeLastSeen = other.timeLastSeen;
  }

  public String getMasterUri() {
    return masterUri;
  }

  public void setMasterUri(String masterUri) {
    this.masterUri = masterUri;
  }

  public String getRobotName() {
    return robotName;
  }

  public void setRobotName(String robotName) {
    this.robotName = robotName;
  }

  public String getRobotType() {
    return robotType;
  }

  public void setRobotType(String robotType) {
    this.robotType = robotType;
  }

  public String getConnectionStatus() {
    return connectionStatus;
  }

  public void setConnectionStatus(String connectionStatus) {
    this.connectionStatus = connectionStatus;
  }

  public Date getTimeLastSeen() {
    return timeLastSeen;
  }

  public void setTimeLastSeen(Date timeLastSeen) {
    this.timeLastSeen = timeLastSeen;
  }

  public boolean isUnknown() {
    return this.robotName == NAME_UNKNOWN;
  }

  public static RobotDescription createUnknown(String masterUri)
      throws InvalidRobotDescriptionException {
      return new RobotDescription(masterUri, NAME_UNKNOWN, TYPE_UNKNOWN, new Date());
  }

}
