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

import org.ros.android.RosActivity;
import org.ros.exception.RemoteException;
import org.ros.exception.RosException;
import org.ros.internal.node.xmlrpc.XmlRpcTimeoutException;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMainExecutor;
import org.ros.node.service.ServiceResponseListener;

import ros.android.util.Dashboard;
import ros.android.util.RobotDescription;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;
import app_manager.StartApp;
import app_manager.StartAppResponse;

/**
 * Activity for Android that acts as a client for an external ROS app.
 * 
 * @author kwc@willowgarage.com (Ken Conley)
 */
public class RosAppActivity extends RosActivity {
	protected AppManager appManager;

	private int dashboardResourceId = 0;
	private int mainWindowId = 0;
	private String robotAppName = null, defaultAppName = null;
	private Dashboard dashboard = null;
	private boolean startApplication = true;
	private boolean applicationStarted = false;

	protected RosAppActivity(String notificationTicker, String notificationTitle) {
		super(notificationTicker, notificationTitle);
	}
	
	@Override
	protected void init(NodeMainExecutor nodeMainExecutor) {
		// TODO Auto-generated method stub

	}

	protected void setDashboardResource(int r) {
		dashboardResourceId = r;
	}

	protected void setMainWindowResource(int r) {
		mainWindowId = r;
	}

	protected void setDefaultAppName(String name) {
		if(name == null) {
			startApplication = false;
		}
		defaultAppName = name;
	}

	private AppManager createAppManagerCb(ConnectedNode node, RobotDescription robotDescription) throws RosException, XmlRpcTimeoutException, AppManagerNotAvailableException {
		// TODO: prevent connecting to app manager of unknown robots
		if(robotDescription == null) {
			throw new RosException("no robot available");
		} else {
			Log.i("RosAndroid", "Using Robot: " + robotDescription.getRobotName() + " " + robotDescription.getRobotId().toString());
			return AppManager.create(node, robotDescription.getRobotName());
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(dashboardResourceId == 0) {
			Log.e("RosAndroid", "You must set the dashboard resource ID in your RosAppActivity");
			return;
		}
		if(mainWindowId == 0) {
			Log.e("RosAndroid", "You must set the dashboard resource ID in your RosAppActivity");
			return;
		}
		if(defaultAppName == null && startApplication) {
			Log.e("RosAndroid", "You must set the default app name in your RosAppActivity");
			return;
		}

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(mainWindowId);

		robotAppName = getIntent().getStringExtra(AppManager.PACKAGE + ".robot_app_name");
		if(robotAppName == null) {
			robotAppName = defaultAppName;
		}

		if(dashboard == null) {
			dashboard = new Dashboard(this);
			dashboard.setView((LinearLayout) findViewById(dashboardResourceId), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		}
	}

	protected NameResolver getAppNamespace(Node node) throws RosException {
		RobotDescription robotDescription = getCurrentRobot();
		if(robotDescription == null) {
			throw new RosException("no robot available");
		}
		NameResolver resolver = node.getResolver();
		GraphName name = GraphName.of(robotDescription.getRobotName());
		GraphName apps = name.join(GraphName.of("application"));
		return resolver.newChild(apps);
	}

	private ProgressDialog progress;

	protected void onAppTerminate() {
		RosAppActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				new AlertDialog.Builder(RosAppActivity.this).setTitle("App Termination").setMessage("The application has terminated on the server, so the client is exiting.").setCancelable(false).setNeutralButton("Exit", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						RosAppActivity.this.finish();
					}
				}).create().show();
			}
		});
	}

	
	
	protected void onNodeCreate(ConnectedNode node) {
		Log.i("RosAndroid", "RosAppActivity.onNodeCreate");
		super.onNodeCreate(node);
		RobotDescription robotDescription = getCurrentRobot();
		try {
			appManager = createAppManagerCb(node, robotDescription);
		} catch(RosException e) {
			Log.e("RosAndroid", "ros init failed", e);
			appManager = null;
		} catch(XmlRpcTimeoutException e) {
			Log.e("RosAndroid", "ros init failed", e);
			appManager = null;
		} catch(AppManagerNotAvailableException e) {
			Log.e("RosAndroid", "ros init failed", e);
			appManager = null;
		}
		if(appManager != null && startApplication) {
			appManager.addTerminationCallback(robotAppName, new AppManager.TerminationCallback() {
				@Override
				public void onAppTermination() {
					RosAppActivity.this.onAppTerminate();
				}
			});
		}
		try {
			// Start up the application on the robot and start the dashboard.
			dashboard.start(node);
			if(startApplication && false) {
				applicationStarted = false;
				startApp();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(progress != null) {
							progress.dismiss();
						}
						progress = ProgressDialog.show(RosAppActivity.this, "Starting...", "Starting application...", true, false);
						progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
					}
				});
				try {
					while(!applicationStarted) {
						Thread.sleep(100);
					}
				} catch(java.lang.InterruptedException e) {
					Log.i("RosAndroid", "Caught interrupted exception while spinning");
				}
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(progress != null) {
							progress.dismiss();
						}
						progress = null;
					}
				});
			} else {
				Log.i("RosAndroid", "Not starting application");
			}
		} catch(Exception ex) {
			Log.e("$rootclass", "Init error: " + ex.toString());
			safeToastStatus("Failed: " + ex.getMessage());
		}

	}

	
	@Override
	protected void onDestroy() {
		if(dashboard != null) {
			dashboard.stop();
		}
		appManager = null;
		super.onDestroy();
	}

	/** Starts the application on the robot. Calls the service with the name */
	private void startApp() {
		Log.i("RosAndroid", "Starting application");
		appManager.startApp(robotAppName, new ServiceResponseListener<StartAppResponse>() {
			@Override
			public void onSuccess(StartAppResponse message) {
				Log.i("RosAndroid", "App started successfully");
				RosAppActivity.this.applicationStarted = true;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(progress != null) {
							progress.dismiss();
						}
						progress = null;
					}
				});
			}

			@Override
			public void onFailure(RemoteException e) {
				Log.e("RosAndroid", "App failed to start!");
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(progress != null) {
							progress.dismiss();
						}
						progress = null;
						new AlertDialog.Builder(RosAppActivity.this).setTitle("Failed").setCancelable(false).setNeutralButton("Ok", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								android.os.Process.killProcess(android.os.Process.myPid());
							}
						}).setMessage("The application failed to load").create();
					}
				});
			}
		});
	}

	/** Displays a status tip at the bottom of the screen from any thread. */
	protected void safeToastStatus(final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(RosAppActivity.this, message, Toast.LENGTH_SHORT).show();
			}
		});
	}

}
