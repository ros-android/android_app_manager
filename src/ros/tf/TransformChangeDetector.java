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
 *
 * @author hersh@willowgarage.com (Dave Hershberger)
 */

package ros.tf;

import org.ros.message.Time;

/**
 * TransformChangeDetector takes a TfListener and provides a function
 * which checks to see if a given transform has changed since a
 * previous check.
 */
public class TransformChangeDetector {

  private TfListener mainListener;
  private String targetFrame;
  private String sourceFrame;

  private StampedTransform previousTransform;

  /**
   * Constructor.
   * @param mainListener the TfListener to read data from.
   * @param targetFrame the frame the transform will transform points into.
   * @param sourceFrame the frame the transform will transform points from.
   */
  public TransformChangeDetector( TfListener mainListener,
                                  String targetFrame,
                                  String sourceFrame ) {
    this.mainListener = mainListener;
    this.targetFrame = targetFrame;
    this.sourceFrame = sourceFrame;
  }

  /**
   * Check the TfListener to see if the transform has changed, and
   * call the callback if it has.
   *
   * @returns The new transform if it exists and is different than the
   *          last time getChangedTransform() was called, otherwise null.
   */
  public StampedTransform getChangedTransform() {
    StampedTransform newTransform = mainListener.lookupTransform(targetFrame, sourceFrame,
                                                                 Time.fromMillis(System.currentTimeMillis()));
    if(newTransform != null) {
      if(previousTransform == null || !newTransform.getMatrix4().equals(previousTransform.getMatrix4())) {
        previousTransform = newTransform;
        return newTransform;
      }
    }
    return null;
  }
}
