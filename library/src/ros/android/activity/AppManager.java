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

import java.util.ArrayList;

import org.ros.exception.RemoteException;
import org.ros.exception.RosException;
import org.ros.internal.node.xmlrpc.XmlRpcTimeoutException;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseListener;
import org.ros.node.topic.Subscriber;

import android.util.Log;
import app_manager.App;
import app_manager.AppInstallationState;
import app_manager.AppList;
import app_manager.GetAppDetailsRequest;
import app_manager.GetAppDetailsResponse;
import app_manager.GetInstallationStateRequest;
import app_manager.GetInstallationStateResponse;
import app_manager.InstallAppRequest;
import app_manager.InstallAppResponse;
import app_manager.ListAppsRequest;
import app_manager.ListAppsResponse;
import app_manager.StartAppRequest;
import app_manager.StartAppResponse;
import app_manager.StopAppRequest;
import app_manager.StopAppResponse;
import app_manager.UninstallAppRequest;
import app_manager.UninstallAppResponse;

/**
 * Interact with a remote ROS App Manager.
 * 
 * @author kwc@willowgarage.com (Ken Conley)
 */
public class AppManager {
	static public final String PACKAGE = "ros.android.activity";

	private final ConnectedNode node;
	private AppList appList;
	private ArrayList<Subscriber> subscriptions;
	private NameResolver resolver;

	public interface TerminationCallback {
		public void onAppTermination();
	}

	private class TerminationCallbackInfo {
		public TerminationCallback callback;
		public String appname;

		public TerminationCallbackInfo(String appname, TerminationCallback callback) {
			this.appname = appname;
			this.callback = callback;
		}
	}

	private ArrayList<TerminationCallbackInfo> terminationCallbacks;

	public AppManager(ConnectedNode node, NameResolver resolver) throws RosException {
		this.node = node;
		this.resolver = resolver;
		subscriptions = new ArrayList<Subscriber>();
		terminationCallbacks = new ArrayList<TerminationCallbackInfo>();
		addAppListCallback(new MessageListener<AppList>() {
			@Override
			public void onNewMessage(AppList message) {
				if(appList != null) {
					for(App a : appList.getRunningApps()) {
						boolean stillRunning = false;
						for(App b : message.getRunningApps()) {
							if(b.getName().equals(a.getName())) {
								stillRunning = true;
							}
						}
						if(!stillRunning) {
							Log.i("AppManager", "Terminate application: " + a.getName());
							for(TerminationCallbackInfo c : terminationCallbacks) {
								if(c.appname == null) {
									Log.i("AppManager", "Terminate callback called");
									c.callback.onAppTermination();
								} else if(c.appname.equals(a.getName())) {
									Log.i("AppManager", "Terminate callback called");
									c.callback.onAppTermination();
								}
							}
						}
					}
				}
				appList = message;
			}
		});

	}

	public void addTerminationCallback(String appname, TerminationCallback callback) {
		terminationCallbacks.add(new TerminationCallbackInfo(appname, callback));
	}

	public void addAppListCallback(MessageListener<AppList> callback) throws RosException {
		Subscriber<AppList> s = node.newSubscriber(resolver.resolve("app_list"), "app_manager/AppList");
		s.addMessageListener(callback);
		subscriptions.add(s);
	}

	public void addExchangeListCallback(MessageListener<AppInstallationState> callback) throws RosException {
		Subscriber<AppInstallationState> s = node.newSubscriber(resolver.resolve("exchange_app_list"), "app_manager/AppInstallationState");
		s.addMessageListener(callback);
		subscriptions.add(s);
	}

	public AppList getAppList() {
		return appList;
	}

	private final org.ros.internal.node.response.StatusCode ERROR_STATUS = org.ros.internal.node.response.StatusCode.ERROR;

	public void listApps(final ServiceResponseListener<ListAppsResponse> callback) {
		try {
			ServiceClient<ListAppsRequest, ListAppsResponse> listAppsClient = node.newServiceClient(resolver.resolve("list_apps"), "app_manager/ListApps");
			listAppsClient.call(listAppsClient.newMessage(), callback);
		} catch(Throwable ex) {
			callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
		}
	}

	public void listExchangeApps(boolean remoteUpdate, final ServiceResponseListener<GetInstallationStateResponse> callback) {
		try {
			ServiceClient<GetInstallationStateRequest, GetInstallationStateResponse> listAppsClient = node.newServiceClient(resolver.resolve("list_exchange_apps"), "app_manager/GetInstallationState");
			GetInstallationStateRequest request = listAppsClient.newMessage();
			request.setRemoteUpdate(remoteUpdate);
			listAppsClient.call(request, callback);
		} catch(Throwable ex) {
			callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
		}
	}

	public void startApp(final String appName, final ServiceResponseListener<StartAppResponse> callback) {
		try{
			ServiceClient<StartAppRequest, StartAppResponse> startAppClient = node.newServiceClient(resolver.resolve("start_app"), "app_manager/StartApp");
			Log.i("AppManager", "Start app service client created");
			StartAppRequest request = startAppClient.newMessage();
			request.setName(appName);
			startAppClient.call(request, callback);
			Log.i("AppManager", "Done call");
		} catch(Throwable ex) {
			Log.i("AppManager", "Start apps failed: " + ex.toString());
			callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
		}
	}

	public void getAppDetails(final String appName, final ServiceResponseListener<GetAppDetailsResponse> callback) {
		try {
			ServiceClient<GetAppDetailsRequest, GetAppDetailsResponse> startAppClient = node.newServiceClient(resolver.resolve("get_app_details"), "app_manager/GetAppDetails");
			Log.i("AppManager", "Start app service client created");
			GetAppDetailsRequest request = startAppClient.newMessage();
			request.setName(appName);
			startAppClient.call(request, callback);
			Log.i("AppManager", "Done call");
		} catch(Throwable ex) {
			Log.i("AppManager", "Get app details failed: " + ex.toString());
			callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
		}
	}

	public void stopApp(final String appName, final ServiceResponseListener<StopAppResponse> callback) {
		try {
			ServiceClient<StopAppRequest, StopAppResponse> stopAppClient = node.newServiceClient(resolver.resolve("stop_app"), "app_manager/StopApp");
			StopAppRequest request = stopAppClient.newMessage();
			request.setName(appName);
			stopAppClient.call(request, callback);
		} catch(Throwable ex) {
			callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
		}
	}

	public void installApp(final String appName, final ServiceResponseListener<InstallAppResponse> callback) {
		try {
			ServiceClient<InstallAppRequest, InstallAppResponse> installAppClient = node.newServiceClient(resolver.resolve("install_app"), "app_manager/InstallApp");
			InstallAppRequest request = installAppClient.newMessage();
			request.setName(appName);
			installAppClient.call(request, callback);
		} catch(Throwable ex) {
			callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
		}
	}

	public void uninstallApp(final String appName, final ServiceResponseListener<UninstallAppResponse> callback) {
		try {
			ServiceClient<UninstallAppRequest, UninstallAppResponse> uninstallAppClient = node.newServiceClient(resolver.resolve("uninstall_app"), "app_manager/UninstallApp");
			UninstallAppRequest request = uninstallAppClient.newMessage();
			request.setName(appName);
			uninstallAppClient.call(request, callback);
		} catch(Throwable ex) {
			callback.onFailure(new RemoteException(ERROR_STATUS, ex.toString()));
		}
	}

	/**
	 * Blocks until App Manager is located.
	 * 
	 * @param node
	 * @param robotName
	 * @return
	 * @throws AppManagerNotAvailableException
	 * @throws RosException
	 */
	public static AppManager create(ConnectedNode node, String robotName) throws XmlRpcTimeoutException, AppManagerNotAvailableException, RosException {
		NameResolver resolver = node.getResolver().newChild(GraphName.of(robotName));
		try {
			return new AppManager(node, resolver);
		} catch(java.lang.RuntimeException ex) {
			throw new AppManagerNotAvailableException(ex);
		}
	}
}
