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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Environment;
import org.ros.exception.RosException;

import java.io.File;

/** 
 * Helpers for Android sdcard access.
 * @author ethan.rublee@gmail.com (Ethan Rublee)
 */
public class SdCardSetup {

  /**
   * Checks if the external storage is mounted and writable.
   * 
   * @return true if the external storage is mounted and writable.
   */
  public static boolean isReady() {
    boolean externalStorageAvailable = false;
    boolean externalStorageWriteable = false;
    String state = Environment.getExternalStorageState();

    if (Environment.MEDIA_MOUNTED.equals(state)) {
      // We can read and write the media
      externalStorageAvailable = externalStorageWriteable = true;
    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
      // We can only read the media
      externalStorageAvailable = true;
      externalStorageWriteable = false;
    } else {
      // Something else is wrong. It may be one of many other states, but all we
      // need
      // to know is we can neither read nor write
      externalStorageAvailable = externalStorageWriteable = false;
    }
    return externalStorageAvailable && externalStorageWriteable;
  }

  /**
   * Display an alert for the user to mount their sdcard.
   * 
   * @param ctx
   *          The application context with which to display the alert.
   */
  public static void promptUserForMount(Context ctx) {
    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
    builder.setTitle("Please mount your (writable) sdcard.");
    builder.setCancelable(true);
    builder.setOnCancelListener(new OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
          dialog.dismiss();
        }
      });
    builder.create().show();
  }

  /**
   * Helper function for geting the ros directory. This will create it if it
   * doesn't exist.  This will fail if isReady() returns false.
   * 
   * @return A file that will likely be /sdcard/ros
   * @throws RosException
   */
  public static File getRosDir() {
    File sdcard = Environment.getExternalStorageDirectory();
    File ros_dir = new File(sdcard, "ros");
    if (!ros_dir.exists())
      ros_dir.mkdirs();
    return ros_dir;
  }
}
