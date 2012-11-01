/*
 * Copyright (c) 2011, Willow Garage, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Willow Garage, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package ros.android.views;

import org.ros.exception.RosException;
import org.ros.node.ConnectedNode;

import android.graphics.Canvas;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Abstract base class for displays added to a PanZoomView.
 */
public abstract class PanZoomDisplay {
  private PanZoomView parent;
  private boolean enabled = true;

  public void setParent( PanZoomView parent ) {
    this.parent = parent;
  }
  public PanZoomView getParent() {
    return parent;
  }

  public boolean isEnabled() {
    return enabled;
  }
  public void enable() {
    enabled = true;
    postInvalidate();
  }
  public void disable() {
    enabled = false;
    postInvalidate();
    Log.i("PanZoomDisplay", "disable()");
  }

  /**
   * Call postInvalidate() on the parent view.
   * Call this after source data has changed to trigger a redraw.
   * Safe from any thread.
   */
  public void postInvalidate() {
    if( parent != null ) {
      parent.postInvalidate();
    }
  }

  /**
   * Called when the node has been created.
   */
  public void start( ConnectedNode node ) throws RosException {}

  /**
   * Called when the node is about to be destroyed.
   */
  public void stop() {}

  /**
   * Override to draw your display.
   * @param canvas Draw your display with this canvas.  Its transform
   *        will already be set to the position and scale selected by the
   *        user via touch events.
   */
  public abstract void draw( Canvas canvas );

  /**
   * Override to handle changes to the view size.
   */
  public void onSizeChanged( int w, int h, int oldW, int oldH ) {}

  /**
   * Override to handle touch events.  Return true if your display has
   * consumed the event, false otherwise.
   */
  public boolean onTouchEvent(MotionEvent event) {
    return false;
  }
}
