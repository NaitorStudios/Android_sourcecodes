/*
 * Copyright 2021 Gigatron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Copyright (c) 1996-2014 Clickteam
 *
 * This source code is part of the HTML5 exporter for Clickteam Fusion 2.5
 *
 * Permission is hereby granted to any person obtaining a legal copy
 * of Clickteam Fusion 2.5 to use or modify this source code for
 * debugging, optimizing, or customizing applications created with
 * Clickteam Fusion 2.5.
 * Any other use of this source code is prohibited.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package Extensions;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.EmbossMaskFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;
import android.widget.ImageView;

import Actions.CActExtension;
import Banks.CImage;
import Conditions.CCndExtension;
import Expressions.CValue;
import Objects.CObject;
import OpenGL.GLRenderer;
import RunLoop.CCreateObjectInfo;
import Runtime.MMFRuntime;
import Services.CBinaryFile;


public class CRunAcanvas extends CRunExtension {

    static final int CND_CONDITION = 0;
    static final int CND_LAST = 1;

    static final int ACT_SETFRAGMENT = 0;
    static final int ACT_SETUNIFORM   = 1;
    static final int ACT_CIRCLE       = 2;
    static final int ACT_PAINT_STROKE = 3;
    static final int ACT_PAINT_COLOR  = 4;
    static final int ACT_LINE         = 5;
    static final int ACT_POINT        = 6;
    static final int ACT_RECTANGLE    = 7 ;
    static final int ACT_SKEW         = 8 ; // skew
    static final int ACT_SETROTATE    = 9 ;
    static final int ACT_SETCONTRAST  = 10 ;
    static final int ACT_CLEAR        = 11 ;
    static final int ACT_EMBOSS        = 12 ;
    static final int ACT_BLURSOLID     = 13 ;
    static final int ACT_BLUROUTER     = 14 ;
    static final int ACT_BLURINNER     = 15 ;
    static final int ACT_BLURNORMAL    = 16 ;
    static final int ACT_PAINT_SHADOW   = 17 ;

    static final int ACT_BLEND_ADD        = 18 ;
    static final int ACT_BLEND_CLEAR      = 19 ;
    static final int ACT_BLEND_DARKEN     = 20 ;
    static final int ACT_BLEND_DST        = 21 ;
    static final int ACT_BLEND_DST_ATOP   = 22 ;
    static final int ACT_BLEND_DST_IN     = 23 ;
    static final int ACT_BLEND_DST_OUT    = 24 ;
    static final int ACT_BLEND_DST_OVER   = 25 ;
    static final int ACT_BLEND_LIGHTEN    = 26 ;
    static final int ACT_BLEND_MULTIPLY   = 27 ;
    static final int ACT_BLEND_OVERLAY    = 28 ;
    static final int ACT_BLEND_SCREEN     = 29 ;
    static final int ACT_BLEND_SOURCE     = 30 ;
    static final int ACT_BLEND_SOURCE_ATOP = 31 ;
    static final int ACT_BLEND_SOURCE_IN = 32 ;
    static final int ACT_BLEND_SOURCE_OUT   = 33 ;
    static final int ACT_BLEND_SOURCE_OVER  = 34 ;
    static final int ACT_PAINT_STYLE_FILL   = 35 ;
    static final int ACT_PAINT_STYLE_STROKE = 36 ;
    static final int ACT_PAINT_ARGB         = 37 ;


    static final int ACT_LAST               = 38 ;

    static final int EXP_GETTEXT = 0;
    static final int EXP_GETINT = 1;
    static final int EXP_GETFRAGMENT = 2;
    static final int EXP_LAST = 3;

    public static GLRenderer  my_renderer;

    public static float  offset_val = 0.0f;
    public static float  wave_val   = 0.0f;

    public static String uniform_wave   = "Wave";
    public static String uniform_offset = "OffSet";

    public static CImage image;
    public static short canvas_screen = 0;

    public static Context context;

    public  int              width;
    public  int              height;
    private Bitmap           mBitmap;
    private Canvas           mCanvas;
    private ImageView        mImageView;
    private Paint            mPaint;
    private EmbossMaskFilter mEmboss;
    private BlurMaskFilter   mBlur;

    private Path             mPath;

    //*****************
    public static float paint_stroke_width = 0f;
    public static int paint_color = 0;


    public CRunAcanvas() {
    }

    @Override
    public int getNumberOfConditions() {
        return 2;
    }

    @Override
    public boolean createRunObject(CBinaryFile file, CCreateObjectInfo cob, int version) {
        // Set up params
       ho.setX(cob.cobX);
       ho.setY(cob.cobY);
       ho.setWidth(file.readShort());
       file.readShort();
       ho.setHeight(file.readShort());

       int xx = ho.getX();
       int yy = ho.getY();
       int ww = ho.getWidth();
       int hh = ho.getHeight();
       my_renderer = GLRenderer.inst;

       int w = (int)((MMFRuntime.inst.currentWidth - 2*MMFRuntime.inst.viewportX)/MMFRuntime.inst.scaleX);
       int h = (int)((MMFRuntime.inst.currentHeight -2*MMFRuntime.inst.viewportY)/MMFRuntime.inst.scaleY);
      // ViewGroup.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
       // ViewGroup.LayoutParams.MATCH_PARENT
       mImageView = new ImageView(MMFRuntime.inst.getApplicationContext());
       mImageView.setX( xx);
       mImageView.setY( yy-240);

       mPaint = new Paint();
       mBitmap = Bitmap.createBitmap(ww, hh, Bitmap.Config.ARGB_8888);
       mImageView.setImageBitmap(mBitmap);
       mCanvas = new Canvas(mBitmap);
     //  MMFRuntime.inst.setContentView(mImageView);
       MMFRuntime.inst.mainView.addView(mImageView, ww +160,  hh+740);
      // target_sprite = this.rh.rhApp.imageBank.addImage(mBitmap, (short) 0, (short) 0, (short) 0, (short) 0, false);
        return true;
    }

    public void destroyRunObject(boolean bFast) {}
    @Override
    public int handleRunObject() {
        int ww = ho.getWidth();
        int hh = ho.getHeight();

        return 0;
    }

    @Override
    public void displayRunObject() {

        int xx = ho.getX();
        int yy = ho.getY();
        int ww = ho.getWidth();
        int hh = ho.getHeight();

       // my_renderer.renderImage(image,false,0,300,300,300,0,0);

    }

    @Override
    public boolean condition(int num, CCndExtension cnd) {
        switch (num) {
            case CND_CONDITION:
                return true;
        }
        return false;
    }
    // Actions
    // -------------------------------------------------
    @Override
    public void action(int num, CActExtension act) {
        switch (num) {
            case ACT_SETFRAGMENT: actSetFragment(act); break;
            case ACT_SETUNIFORM:  actSet_Wave(act);     break;
            case ACT_CIRCLE:      actDrawCircle(act);   break;
            case ACT_PAINT_STROKE:actSet_Paint_Stroke(act);break;
            case ACT_PAINT_COLOR:actSetPaint_Color(act);  break;
            case ACT_LINE:actDrawLine(act);break;
            case ACT_POINT:actDrawPoint(act);break;
            case ACT_RECTANGLE:
                actDrawRectangle(act);
                break;
            case ACT_SKEW:
                actCanvasSkew(act);
                break;
            case ACT_SETROTATE:
                actSetRotate(act);
                break;
            case ACT_SETCONTRAST:
                actSetContrast(act);
                break;
            case ACT_CLEAR:
                actClear(act);
                break;
            case ACT_EMBOSS:
                actEmboss(act);
                break;
            case ACT_BLURSOLID:
                actBlurSolid(act);
                break;
            case ACT_BLUROUTER:
                actBlurOuter(act);
                break;
            case ACT_BLURINNER:
                actBlurInner(act);
                break;
            case ACT_BLURNORMAL:
                actBlurNormal(act);
                break;
            case ACT_PAINT_SHADOW:
                actPaintShadow(act);
                break;
            case ACT_BLEND_ADD        : actBlend_Add(act); break;
            case ACT_BLEND_CLEAR      : actBlend_Clear(act);break;
            case ACT_BLEND_DARKEN     : actBlend_Darken(act);break;
            case ACT_BLEND_DST        : actBlend_Dst(act);break;
            case ACT_BLEND_DST_ATOP   : actBlend_Dst_Atop(act);break;
            case ACT_BLEND_DST_IN     : actBlend_Dst_In(act);break;
            case ACT_BLEND_DST_OUT    : actBlend_Dst_Out(act);break;
            case ACT_BLEND_DST_OVER   : actBlend_Dst_Over(act);break;
            case ACT_BLEND_LIGHTEN    : actBlend_Lighten(act);break;
            case ACT_BLEND_MULTIPLY   : actBlend_Multiply(act);break;
            case ACT_BLEND_OVERLAY    : actBlend_Overlay(act);break;
            case ACT_BLEND_SCREEN     : actBlend_Screen(act);break;
            case ACT_BLEND_SOURCE     : actBlend_Source(act);break;
            case ACT_BLEND_SOURCE_ATOP: actBlend_Source_Atop(act);break;
            case ACT_BLEND_SOURCE_IN  : actBlend_Source_In(act);break;
            case ACT_BLEND_SOURCE_OUT : actBlend_Source_Out(act);break;
            case ACT_BLEND_SOURCE_OVER: actBlend_Source_Over(act);break;

            case ACT_PAINT_STYLE_FILL:   actStyleFill(act);break;
            case ACT_PAINT_STYLE_STROKE: actStyleStroke(act);break;
            case ACT_PAINT_ARGB:         actSetArgb(act);break;

        }
    }
// return always 1 ;
    @Override
    public CValue expression(int num) {
        switch (num) {
            case EXP_GETFRAGMENT:
                return new CValue(1);
            case EXP_GETINT:
                return new CValue(1);
            case EXP_GETTEXT:
                return new CValue(1);
        }
        return new CValue();
    }
    public void actSetFragment(CActExtension act) {
         CObject obj = act.getParamObject(rh, 0);
         canvas_screen = obj.roc.rcImage;
    }
    public void actSet_Wave(CActExtension act) {
          wave_val     = act.getParamExpFloat(rh, 0);
          offset_val   = act.getParamExpFloat(rh, 1);
          my_renderer.updateVariable1f(uniform_wave,(float) wave_val);
          my_renderer.updateVariable1f(uniform_offset,(float) offset_val);
    }
    public void draw_circle(){
        //mCanvas.drawColor(Color.GREEN);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setARGB(50,255,255,0);
        mCanvas.drawRect(100,100,600,600,mPaint);
        mPaint.setColor(Color.parseColor("#ffee00"));
        mCanvas.drawPoint(300,550,mPaint);
        mImageView.invalidate();
    }
    public void set_paint_color(){
        mPaint.setColor(paint_color);
        mImageView.invalidate();
    }
    public void set_paint_stroke_w(){
        mPaint.setStrokeWidth(paint_stroke_width);
        mImageView.invalidate();
    }
    public void actDrawCircle(CActExtension act) {
         int xx = act.getParamExpression(rh, 0);
         int yy = act.getParamExpression(rh, 1);
         int rd = act.getParamExpression(rh, 2);

         mCanvas.drawCircle(xx,yy,rd,mPaint);
         mImageView.invalidate();
        // draw_circle();  // debug
    }

    public void actSet_Paint_Stroke(CActExtension act) {
        float paint_str = act.getParamExpFloat(rh, 0);
        paint_stroke_width = paint_str;
        set_paint_stroke_w();
    }

    public void actSetPaint_Color(CActExtension act) {
        String paint_col = act.getParamExpString(rh, 0);
        paint_color = Color.parseColor("#"+paint_col);
        set_paint_color();
    }
    public void actDrawLine(CActExtension act) {
        int x =  act.getParamExpression(rh, 0);
        int y =  act.getParamExpression(rh, 1);
        int xx = act.getParamExpression(rh, 2);
        int yy = act.getParamExpression(rh, 3);
        mCanvas.drawLine(x,y,xx,yy,mPaint);
        mImageView.invalidate();
    }
    public void actDrawPoint(CActExtension act) {
        int x =  act.getParamExpression(rh, 0);
        int y =  act.getParamExpression(rh, 1);
        mCanvas.drawPoint(x,y,mPaint);
        mImageView.invalidate();
    }
    public void actDrawRectangle(CActExtension act) {
       int left  = act.getParamExpression(rh, 0);
       int top   = act.getParamExpression(rh, 1);
       int right = act.getParamExpression(rh, 2);
       int bot   = act.getParamExpression(rh, 3);
       mCanvas.drawRect(left,top,right,bot,mPaint);
       mImageView.invalidate();
    }
    public void actCanvasSkew(CActExtension act) {
        float sk_x = act.getParamExpFloat(rh, 0);
        float sk_y = act.getParamExpFloat(rh, 1);
        mCanvas.skew(sk_x,sk_y);
        mImageView.invalidate();
    }
    public void actSetRotate(CActExtension act) {
        float rot = act.getParamExpFloat(rh, 0);
        mCanvas.rotate(rot);
        mImageView.invalidate();
    }
    public void actSetContrast(CActExtension act) {
        float dx = act.getParamExpFloat(rh, 0);
        float dy = act.getParamExpFloat(rh, 1);
        mCanvas.translate(dx,dy);
        mImageView.invalidate();
    }
    public void actClear(CActExtension act) {
        String paint_col = act.getParamExpString(rh, 0);
        mCanvas.drawColor(Color.parseColor("#"+paint_col));
        mImageView.invalidate();
    }
    public void actEmboss(CActExtension act) {
        float ambient = act.getParamExpFloat(rh, 0);
        int   spec    = act.getParamExpression(rh, 1);
        float blur    = act.getParamExpFloat(rh, 2);
        mEmboss = new EmbossMaskFilter(new float[] { 1, 1, 1 },ambient, spec, blur);
        mPaint.setMaskFilter(mEmboss);
        mImageView.invalidate();
    }
    public void actBlurSolid(CActExtension act) {
        float radius = act.getParamExpFloat(rh, 0);
        mBlur = new BlurMaskFilter(radius, BlurMaskFilter.Blur.SOLID);
        mPaint.setMaskFilter(mBlur);
        mImageView.invalidate();
    }
    public void actBlurOuter(CActExtension act) {
        float radius = act.getParamExpFloat(rh, 0);
        mBlur = new BlurMaskFilter(radius, BlurMaskFilter.Blur.OUTER);
        mPaint.setMaskFilter(mBlur);
        mImageView.invalidate();
    }
    public void actBlurInner(CActExtension act) {
        float radius = act.getParamExpFloat(rh, 0);
        mBlur = new BlurMaskFilter(radius, BlurMaskFilter.Blur.INNER);
        mPaint.setMaskFilter(mBlur);
        mImageView.invalidate();
    }
    public void actBlurNormal(CActExtension act) {
        float radius = act.getParamExpFloat(rh, 0);
        mBlur = new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
        mPaint.setMaskFilter(mBlur);
        mImageView.invalidate();
    }
    public void actStyleFill(CActExtension act) {
        mPaint.setStyle(Paint.Style.FILL);
        mImageView.invalidate();
    }
    public void actStyleStroke(CActExtension act) {
        mPaint.setStyle(Paint.Style.STROKE);
        mImageView.invalidate();
    }

    public void actPaintShadow(CActExtension act) {
        int rad =  act.getParamExpression(rh, 0);
        int dx =   act.getParamExpression(rh, 1);
        int dy =   act.getParamExpression(rh, 2);
        String sh_color = act.getParamExpString(rh, 3);
        mPaint.setShadowLayer(rad, dx, dy, Color.parseColor("#"+sh_color));
        mImageView.invalidate();
    }

    public void actBlend_Add(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));mImageView.invalidate();}
    public void actBlend_Clear(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));mImageView.invalidate();}
    public void actBlend_Darken(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));mImageView.invalidate();}
    public void actBlend_Dst(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST));mImageView.invalidate();}
    public void actBlend_Dst_Atop(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));mImageView.invalidate();}
    public void actBlend_Dst_In(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));mImageView.invalidate();}
    public void actBlend_Dst_Out(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));mImageView.invalidate();}
    public void actBlend_Dst_Over(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));mImageView.invalidate();}
    public void actBlend_Lighten(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.LIGHTEN));mImageView.invalidate();}
    public void actBlend_Multiply(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));mImageView.invalidate();}
    public void actBlend_Overlay(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));mImageView.invalidate();}
    public void actBlend_Screen(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));mImageView.invalidate();}
    public void actBlend_Source(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));mImageView.invalidate();}
    public void actBlend_Source_Atop(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));mImageView.invalidate();}
    public void actBlend_Source_In(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));mImageView.invalidate();}
    public void actBlend_Source_Out(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));mImageView.invalidate();}
    public void actBlend_Source_Over(CActExtension act) {mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));mImageView.invalidate();}
    // Canvas Blend Mode ;

    public void actSetArgb(CActExtension act) {
        int alpha = act.getParamExpression(rh, 0);
        int red =   act.getParamExpression(rh, 1);
        int green = act.getParamExpression(rh, 2);
        int blue =  act.getParamExpression(rh, 3);
        mPaint.setARGB(alpha,red,green,blue);
        mImageView.invalidate();
    }

    public class Utils {
        public   float convertDpToPx(Context context, float dp) {
            return dp * context.getResources().getDisplayMetrics().density;
        }
    }

}