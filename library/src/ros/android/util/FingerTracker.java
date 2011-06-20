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

package ros.android.util;

import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;

public class FingerTracker implements View.OnTouchListener {
  private ArrayList<FingerReceiver> inactiveReceivers = new ArrayList<FingerReceiver>();
  private HashMap<Integer, FingerReceiver> idToReceiverMap = new HashMap<Integer, FingerReceiver>();

  public void addReceiver( FingerReceiver receiver ) {
    inactiveReceivers.add( receiver );
  }

  public void removeReceiver( FingerReceiver receiver ) {
    if( !inactiveReceivers.remove( receiver ) ) {
      for( Integer id: idToReceiverMap.keySet() ) {
        if( idToReceiverMap.get( id ) == receiver ) {
          idToReceiverMap.remove( id );
        }
      }
    }
  }

  public boolean onTouch( View v, MotionEvent event ) {
    float x = 0, y = 0;
    int action = event.getActionMasked();
    int pointerId = 0;
    
    boolean down = false;
    if( action == MotionEvent.ACTION_DOWN ) {
      x = event.getX();
      y = event.getY();
      pointerId = event.getPointerId( 0 );
      down = true;
    } else if( action == MotionEvent.ACTION_POINTER_DOWN ) {
      int pointerIndex = event.getActionIndex();
      x = event.getX( pointerIndex );
      y = event.getY( pointerIndex );
      pointerId = event.getPointerId( pointerIndex );
      down = true;
    }

    if( down ) {
      for( FingerReceiver receiver: inactiveReceivers ) {
        if( receiver.onDown( x, y )) {
          inactiveReceivers.remove( receiver );
          idToReceiverMap.put( Integer.valueOf( pointerId ), receiver );
          return true;
        }
      }
      return false;
    }

    if( action == MotionEvent.ACTION_MOVE ) {
      boolean usedThisEvent = false;
      for( int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++ ) {
        pointerId = event.getPointerId( pointerIndex );
        FingerReceiver receiver = idToReceiverMap.get( Integer.valueOf( pointerId ));
        if( receiver != null ) {
          receiver.onMove( event.getX( pointerIndex ), event.getY( pointerIndex ));
          usedThisEvent = true;
        }
      }
      return usedThisEvent;
    }

    if( action == MotionEvent.ACTION_UP ) {
      boolean usedThisEvent = false;
      for( FingerReceiver receiver: idToReceiverMap.values() ) {
        receiver.onUp();
        inactiveReceivers.add( receiver );
        usedThisEvent = true;
      }
      idToReceiverMap.clear();
      return usedThisEvent;
    }

    if( action == MotionEvent.ACTION_CANCEL ) {
      boolean usedThisEvent = false;
      for( FingerReceiver receiver: idToReceiverMap.values() ) {
        inactiveReceivers.add( receiver );
        usedThisEvent = true;
      }
      idToReceiverMap.clear();
      return usedThisEvent;
    }

    if( action == MotionEvent.ACTION_POINTER_UP ) {
      int pointerIndex = event.getActionIndex();
      pointerId = event.getPointerId( pointerIndex );
      FingerReceiver receiver = idToReceiverMap.get( Integer.valueOf( pointerId ));
      if( receiver != null ) {
        receiver.onUp();
        idToReceiverMap.remove( Integer.valueOf( pointerId ));
        inactiveReceivers.add( receiver );
        return true;
      } else {
        return false;
      }
    }

    return false;
  }
}
