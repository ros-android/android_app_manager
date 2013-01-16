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

import android.graphics.Matrix;
import android.graphics.Canvas;

import ros.android.util.Posable;

/**
 * Extension of PanZoomDisplay which implements the Posable interface,
 * so displays can be easily written which receive pose matrices from,
 * for example, a PlaneTfChangeListener.
 *
 * Subclasses should implement drawAtPose() instead of draw(), as this
 * class implements draw() to include the pose transform and then call
 * drawAtPose().
 */
public abstract class PosablePanZoomDisplay extends PanZoomDisplay implements Posable {
  private Matrix poseRelFixedFrame = new Matrix();
  private boolean havePose = false;

  /**
   * Set the pose matrix to be a copy of poseRelFixedFrame.
   */
  @Override
  public void setPose( Matrix poseRelFixedFrame ) {
    this.poseRelFixedFrame.set( poseRelFixedFrame );
    havePose = true;
    postInvalidate();
  }
  /**
   * Return a copy of the current pose matrix.
   */
  @Override
  public Matrix getPose() {
    Matrix result = new Matrix();
    result.set( poseRelFixedFrame );
    return result;
  }

  /**
   * Invalidate the current pose.  draw() will not call drawAtPose()
   * until after another call to setPose().
   */
  @Override
  public void reset() {
    havePose = false;
  }

  @Override
  public final void draw( Canvas canvas ) {
    if( havePose ) {
      Matrix oldMatrix = canvas.getMatrix();

      canvas.concat( poseRelFixedFrame );
      drawAtPose( canvas );

      canvas.setMatrix( oldMatrix );
    }
  }

  /**
   * Override to draw your display.  drawAtPose() is only called if
   * the current pose is valid (setPose() has been called since
   * construction or reset()).
   * @param canvas Draw your display with this canvas.  Its transform
   *        will already be set to the position and scale set by the
   *        combination of user touch events and pose transforms sent
   *        via setPose().
   */
  protected abstract void drawAtPose( Canvas canvas );
}
