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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.view.View;
import android.util.AttributeSet;

import ros.android.activity.R;

public class BatteryLevelView extends View {
  private Bitmap silhouette;
  private Bitmap plug;
  private Paint green;
  private Paint yellow;
  private Paint red;
  private Paint gray;

  private float levelPercent;
  private boolean pluggedIn;

  public BatteryLevelView(Context ctx) {
    super(ctx);
    init(ctx);
  }

  public BatteryLevelView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  public BatteryLevelView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }
  
  private Paint makePaint( int color ) {
    Paint paint = new Paint();
    paint.setColorFilter( new PorterDuffColorFilter( 0xff000000 | color, PorterDuff.Mode.SRC_ATOP ));
    return paint;
  }

  private void init(Context context) {
    silhouette = BitmapFactory.decodeResource(context.getResources(), R.drawable.battery_silhouette);
    plug = BitmapFactory.decodeResource(context.getResources(), R.drawable.power_plug);
    green = makePaint( 0x00ff00 );
    yellow = makePaint( 0xffff00 );
    red = makePaint( 0xff0000 );
    gray = makePaint( 0x808080 );

    levelPercent = 0;
    pluggedIn = false;
  }

  public void setBatteryPercent(float percent) {
    levelPercent = percent;
    invalidate();
  }

  public void setPluggedIn(boolean plugged) {
    pluggedIn = plugged;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    // draw the entire background battery image in gray
    Rect srcRect = new Rect(0, 0, silhouette.getWidth(), silhouette.getHeight());
    Rect destRect = new Rect(0, 0, getWidth(), getHeight());

    canvas.drawBitmap(silhouette, srcRect, destRect, gray);

    Paint fillPaint;
    if( levelPercent < 20 ) {
      fillPaint = red;
    } else if( levelPercent < 50 ) {
      fillPaint = yellow;
    } else {
      fillPaint = green;
    }
    
    // draw a portion of the foreground battery image with the width coming from levelPercent.
    srcRect.set(0, 0, (int)(silhouette.getWidth() * levelPercent / 100f), silhouette.getHeight());
    destRect.set(0, 0, (int)(getWidth() * levelPercent / 100f), getHeight());

    canvas.drawBitmap(silhouette, srcRect, destRect, fillPaint);

    if( pluggedIn ) {
      srcRect.set(0, 0, plug.getWidth(), plug.getHeight());
      destRect.set(getWidth() / 5, getHeight() / 5, (getWidth() * 4) / 5, (getHeight() * 4) / 5);

      canvas.drawBitmap(plug, srcRect, destRect, new Paint());
    }
  }
}
