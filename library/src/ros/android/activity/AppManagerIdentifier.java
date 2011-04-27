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

import org.ros.service.app_manager.StartApp;

import org.ros.service.app_manager.StopApp;

import org.ros.internal.namespace.GraphName;
import org.ros.internal.node.service.ServiceDefinition;
import org.ros.internal.node.service.ServiceIdentifier;
import org.ros.namespace.NameResolver;
import org.ros.service.app_manager.ListApps;

import java.net.URI;

/**
 * @author kwc@willowgarage.com (Ken Conley)
 */
public class AppManagerIdentifier {

  private final NameResolver resolver;
  private final URI serviceUri;

  public AppManagerIdentifier(NameResolver resolver, URI serviceUri) {
    this.serviceUri = serviceUri;
    this.resolver = resolver;
  }

  public ServiceIdentifier getListAppsIdentifier() {
    ListApps serviceMeta = new ListApps();
    ServiceDefinition serviceDefinition = new ServiceDefinition(new GraphName(
        resolver.resolveName("list_apps")), serviceMeta.getDataType(), serviceMeta.getMD5Sum());
    return new ServiceIdentifier(serviceUri, serviceDefinition);
  }

  public ServiceIdentifier getStartAppIdentifier() {
    StartApp serviceMeta = new StartApp();
    ServiceDefinition serviceDefinition = new ServiceDefinition(new GraphName(
        resolver.resolveName("start_app")), serviceMeta.getDataType(), serviceMeta.getMD5Sum());
    return new ServiceIdentifier(serviceUri, serviceDefinition);
  }

  public ServiceIdentifier getStopAppIdentifier() {
    StopApp serviceMeta = new StopApp();
    ServiceDefinition serviceDefinition = new ServiceDefinition(new GraphName(
        resolver.resolveName("stop_app")), serviceMeta.getDataType(), serviceMeta.getMD5Sum());
    return new ServiceIdentifier(serviceUri, serviceDefinition);
  }

}
