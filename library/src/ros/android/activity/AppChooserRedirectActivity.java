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
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ContentValues;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.content.ActivityNotFoundException;

/**
 * @author selliott@willowgarage.com
 */
public class AppChooserRedirectActivity extends Activity {

  private static final int REDIRECT_DIALOG = 0;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTitle("Please access this application through the application chooser!");
    showDialog(REDIRECT_DIALOG);
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    final Dialog dialog;
    Button button;
    switch (id) {
      case REDIRECT_DIALOG:
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Redirect.");
        builder.setMessage("This application must be accessed through the ROS Application Chooser. To launch the App Chooser click the button below. Then choose the application you downloaded from the list of apps.");
        builder.setNeutralButton( "Launch App Chooser", new DialogButtonClickHandler() );
        builder.setOnKeyListener( new DialogKeyListener()); 
        dialog = builder.create();
        break;
      default:
        dialog = null;
    }
    return dialog;
  }
  
  public class DialogButtonClickHandler implements DialogInterface.OnClickListener {
    public void onClick( DialogInterface dialog, int clicked ) {
      switch( clicked ) {
        case DialogInterface.BUTTON_NEUTRAL:
          removeDialog(REDIRECT_DIALOG);
          Intent intent = getPackageManager().getLaunchIntentForPackage("org.ros.android.app_chooser");
          intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
          try {
            startActivity(intent);
          } 
          catch (ActivityNotFoundException e) {
            Toast.makeText(AppChooserRedirectActivity.this, 
            "App Chooser not installed. Please install the ROS Application Chooser from the Android Market.", 
            Toast.LENGTH_LONG).show();
          }     
          finish();
          break;
      }
    }
  }
  

  public class DialogKeyListener implements DialogInterface.OnKeyListener {
    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
        Dialog dlg = (Dialog) dialog;
        removeDialog(REDIRECT_DIALOG);
        Intent intent = getPackageManager().getLaunchIntentForPackage("org.ros.android.app_chooser");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
          startActivity(intent);
        }
        catch (ActivityNotFoundException e) {
          Toast.makeText(AppChooserRedirectActivity.this,
          "App Chooser not installed. Please install the ROS Application Chooser from the Android Market.",
          Toast.LENGTH_LONG).show();
        }
        finish();
        return true;
      }
      return false;
    }
  }
}
