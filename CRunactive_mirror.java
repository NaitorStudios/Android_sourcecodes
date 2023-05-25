package Extensions;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import java.io.*;
import android.graphics.BitmapFactory;

import Actions.CActExtension;
import Application.CRunApp;
import Banks.CImage;
import Conditions.CCndExtension;
import Expressions.CValue;

import RunLoop.CCreateObjectInfo;
import Runtime.SurfaceView;
import Runtime.Log;
import Runtime.MMFRuntime;
import Services.CBinaryFile;
import Services.CServices;
import android.app.Activity;
import android.content.Intent;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.opengl.GLUtils;
import android.graphics.Point;

import Objects.CExtension;
import Objects.CObject;
import Banks.CImageInfo;
import java.lang.Math;
import OpenGL.GLRenderer;

public class CRunactive_mirror extends CRunExtension{
	private int t_address;
	private CObject p_target ;
	
	private int flg_x , flg_y ;
	private int off_x , off_y ;
	private int ac_x , ac_y ;
	
    private CValue expRet;
    public CRunactive_mirror () {
	    expRet = new CValue(0);
    }
	@Override 
	public int getNumberOfConditions(){
		return 0;
	}
    @Override
    public boolean createRunObject(CBinaryFile file, CCreateObjectInfo cob, int version) {
		t_address = 0;
		p_target = null;
		flg_x 	= 0;
		flg_y 	= 0;
		off_x 	= 0;
		off_y 	= 0;
		ac_x	= 0;
		ac_y	= 0;
        return false;
    }
    @Override
    public void destroyRunObject(boolean bFast) {
    }
    @Override
    public void displayRunObject(){
		if( p_target != null ){
			CImage ci = ho.hoAdRunHeader.rhApp.imageBank.getImageFromHandle( p_target.roc.rcImage );
			int w = ci.getWidth();
			int h = ci.getHeight();
			float scale_x = p_target.roc.rcScaleX;
			float scale_y = p_target.roc.rcScaleY;
			int x = ho.hoX - rh.rhWindowX ;
			int y = ho.hoY - rh.rhWindowY ;
			int hx = ci.getXSpot();
			int hy = ci.getYSpot();
			
			float r = p_target.roc.rcAngle;
			if( flg_x > 0 ){
				w *= -1; 
				hx *= -1;
				x += off_x;
			}
			if( flg_y > 0 ){
				h *= -1;
				hy *= -1;
				y += off_y;
			}
			int effect = p_target.ros.rsEffect;
			if((p_target.ros.rsFlags & 0x0008 )>0 || (p_target.ros.rsFlags & 0x0010)>0){
				effect |= 0x01;
			}
			GLRenderer.inst.renderScaledRotatedImage( 
				ci , r , scale_x , scale_y ,
				hx , hy , x , y , w , h ,
				effect , p_target.ros.rsEffectParam);
		}
    }

    @Override
    public void reinitDisplay () {
    }
	@Override
	public int handleRunObject(){
		if( p_target != null ){
			ho.hoX = p_target.hoX;
			ho.hoY = p_target.hoY;
			ho.hoImgWidth = p_target.hoImgWidth;
			ho.hoImgHeight = p_target.hoImgHeight;
			ho.hoImgXSpot = p_target.hoImgXSpot;
			ho.hoImgYSpot = p_target.hoImgYSpot;
			ho.roc.rcAngle = p_target.roc.rcAngle;
		}
		return 0;
	}
    @Override
    public void pauseRunObject() {
    }
   
    @Override
    public void continueRunObject() {
    }
    
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	}
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults, List<Integer> permissionsReturned) {
	}
	
	@Override
    public boolean condition (int num, CCndExtension cnd){
        return false;
    }
    
    @Override
    public void action (int num, CActExtension act){
        switch (num){
        	case 0: // SetFixed
        		SetFixed( act.getParamExpression(rh, 0) );
        		break;
			case 1: // SetMirrorX
				flg_x = act.getParamExpression(rh, 0);
				ho.roc.rcImage = -1;
				break;
			case 2: // SetMirrorY
				flg_y = act.getParamExpression(rh, 0);
				ho.roc.rcImage = -1;
				break;
			case 3: // SetOffset
				off_x = act.getParamExpression(rh, 0);
				off_y = act.getParamExpression(rh, 1);
				ho.roc.rcImage = -1;
				break;
        }
        return;
    }
    @Override
    public CValue expression (int num){
        switch (num){
			case 0: // getFixed
				expRet.forceInt( t_address );
				break;
			case 1: // getMirrorXFlag
				expRet.forceInt( flg_x );
				break;
			case 2:  //	getMirrorYFlag
				expRet.forceInt( flg_y );
				break;
			case 3: // getActionPointX
				UpdateActionPoint();
				expRet.forceInt( ac_x );
				break;
			case 4: // getActionPointY
				UpdateActionPoint();
				expRet.forceInt( ac_y );
				break;
        }
        return expRet;
    }
    public void SetFixed( int fixed ){
    	t_address = fixed;
    	p_target = null;
		if( t_address != 0 ){
			int id = 0x0000FFFF & t_address;
			if( id < 0 || id > ho.hoAdRunHeader.rhMaxObjects ) return ;
			CObject obj = ho.hoAdRunHeader.rhObjectList[id];
			if( obj == null || (obj.hoCreationId<<16)!=(0xFFFF0000 & t_address) ) return;
			p_target = obj;
			ho.hoX = obj.hoX;
			ho.hoX = obj.hoY;
		}
    }
    public void UpdateActionPoint(){
		if( p_target != null ){
			CObject r = p_target;
			if( ho.hoX != r.hoX || ho.hoY != r.hoY || ho.roc.rcAngle != r.roc.rcAngle || ho.roc.rcScaleX != r.roc.rcScaleX || ho.roc.rcScaleY != r.roc.rcScaleY || ho.roc.rcImage != r.roc.rcImage ){
				CImageInfo ci = ho.hoAdRunHeader.rhApp.imageBank.getImageInfoEx( r.roc.rcImage , 0, 1.0f, 1.0f);
				int shx = ci.xSpot;
				int shy = ci.ySpot;
				if( flg_x > 0) shx += off_x;
				if( flg_y > 0) shy += off_y;
				double r_hx = (ci.xAP - shx) * r.roc.rcScaleX;
				double r_hy = (ci.yAP - shy) * r.roc.rcScaleY;
				double r_z = Math.sqrt(r_hx*r_hx + r_hy*r_hy);
				if(flg_x > 0) r_hx *= -1.0 ;
				if(flg_y > 0) r_hy *= -1.0 ;
				double r_r = Math.atan2(r_hy, r_hx) ; 
				double rad = ((r.roc.rcAngle + 90) * (Math.PI / 180)) - r_r ;
				r_hx = Math.sin(rad) * r_z;
				r_hy = Math.cos(rad) * r_z;
				
				ac_x = ho.hoX + (int)r_hx;
				ac_y = ho.hoY + (int)r_hy;
				
				ho.hoX = r.hoX ;
				ho.hoY = r.hoY ;
				ho.roc.rcAngle = r.roc.rcAngle ;
				ho.roc.rcScaleX = r.roc.rcScaleX ;
				ho.roc.rcScaleY = r.roc.rcScaleY ;
				ho.roc.rcImage = r.roc.rcImage ;
			}
		}
    }
    
}
