/* Copyright (c) 1996-2013 Clickteam
 *
 * This source code is part of the Android exporter for Clickteam Multimedia Fusion 2.
 *
 * Permission is hereby granted to any person obtaining a legal copy
 * of Clickteam Multimedia Fusion 2 to use or modify this source code for
 * debugging, optimizing, or customizing applications created with
 * Clickteam Multimedia Fusion 2.  Any other use of this source code is prohibited.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

// 12/7/2016 partially added feature from iOS to get correct boundingbox for object representation and touch detection.

package Extensions;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.webkit.URLUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import Actions.CActExtension;
import Application.CRunApp;
import Application.CRunApp.HFile;
import Banks.CImage;
import Conditions.CCndExtension;
import Expressions.CValue;
import OpenGL.GLRenderer;
import RunLoop.CCreateObjectInfo;
import Runtime.Log;
import Runtime.MMFRuntime;
import Runtime.PermissionsResultAction;
import Runtime.SurfaceView;
import Services.CBinaryFile;
import Services.CServices;

public class CRunkcpica extends CRunExtension
{
	private static long PICTURE_RESIZE = 0x0001;
	private static long PICTURE_HIDEONSTART = 0x0002;
	private static long OLD_PICTURE_TRANSP_BLACK = 0x0008;
	private static long PICTURE_TRANSP_FIRSTPIXEL = 0x0010;
	private static long PICTURE_FLIPPED_HORZ = 0x0020;
	private static long PICTURE_FLIPPED_VERT = 0x0040;
	private static long PICTURE_RESAMPLE = 0x0080;
	private static long WRAPMODE_OFF = 0x0100;
	private static long PICTURE_LINKDIR = 0x00010000;

	public static final int EFFECTFLAG_TRANSPARENT = 0x10000000;
	public static final int EFFECTFLAG_ANTIALIAS = 0x20000000;
	public static final int EFFECT_MASK = 0xFFFF;
	// From Object Effect
	//private static int OBJ_ANTIALIASED	= 0x20000000;

	private int editorWidth, editorHeight;
	private int screenWidth, screenHeight;
	private int iHotXSpot, iHotYSpot;
	private int iHoX, iHoY;

	private int flags;
	private float fAngle;
	private int color_transp;

	private int scale = 1;
	private boolean loaded;

	private String imageName;
	private static InputStream stream;
	private String galleryPic;
	private boolean imageselect = false;
	private int orientation;

	static final int REQUEST_IMAGE_PICKED = 1098799;

	private CValue expRet;

	private boolean enabled_perms;
	private boolean transparent;
	private boolean first_pixel;

	private short objOi;

	class CAPTexture extends CImage
	{
		public CAPTexture (InputStream stream)
		{
			super (stream, (MMFRuntime.inst.app.hdr2Options & CRunApp.AH2OPT_ANTIALIASED) != 0, transparent, first_pixel, color_transp);
		}

		@Override
		public void onDestroy ()
		{
			/* Clear texture in the APO */

			texture = null;
		}
	}

	private CAPTexture texture;

	private int offsetX, offsetY;

	private boolean appEndOn = false;
	private boolean fromPause;

	public CRunkcpica () {
		expRet = new CValue(0);
	}

	//////////////////////////////////////////////////////////////////////
	//
	//			Control functions
	//
	/////////////////////////////////////////////////////////////////////

	private void RestoreAutoEnd() {
		if(appEndOn) {
			appEndOn = false;
			MMFRuntime.inst.app.hdr2Options |= CRunApp.AH2OPT_AUTOEND;
		}
	}

	private void SuspendAutoEnd() {
		//AH2OPT_AUTOEND
		if (!appEndOn && MMFRuntime.inst.app != null && (MMFRuntime.inst.app.hdr2Options & CRunApp.AH2OPT_AUTOEND) != 0) {
			appEndOn = true;
			MMFRuntime.inst.app.hdr2Options &= ~ CRunApp.AH2OPT_AUTOEND;
		}
	}

	public CImage getImage()
	{
		return (CImage)texture;
	}

	public static int getOrientation(String path) {
		int angle=0;

		try {
			ExifInterface exifReader = new ExifInterface(path);
			if(exifReader == null)
				throw new IOException();
			int result = exifReader.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

			if (result == ExifInterface.ORIENTATION_ROTATE_90)
				angle = -90;
			else if (result == ExifInterface.ORIENTATION_ROTATE_180)
				angle = 180;
			else if (result == ExifInterface.ORIENTATION_ROTATE_270)
				angle = 270;

			while(angle < 0)
				angle += 360;
			while(angle >= 360)
				angle -= 360;

		} catch (IOException | NullPointerException e) {
			Log.Log("error in exif: "+e.getMessage());
			return -1;
		}

		return angle;
	}

	@Override
	public int getNumberOfConditions()
	{
		return 4;
	}

	@Override
	public boolean createRunObject(CBinaryFile file, CCreateObjectInfo cob, int version)
	{
		imageName = "";

		fAngle = 0;

		editorWidth = file.readInt();
		editorHeight = file.readInt();
		screenWidth = editorWidth;
		screenHeight = editorHeight;
		ho.setWidth(editorWidth);
		ho.setHeight(editorHeight);

		flags = file.readInt();

		transparent = ((ho.hoOiList.oilInkEffect & EFFECTFLAG_TRANSPARENT) != 0);
		first_pixel = ((flags & PICTURE_TRANSP_FIRSTPIXEL) != 0);
		color_transp = CServices.swapRGB(file.readInt()); /* transparent color */
		imageName = file.readString(260);

		String hex_value = Integer.toHexString(color_transp);
		loaded = false;
		iHotXSpot = 0;
		iHotYSpot = 0;
		iHoX = cob.cobX;
		iHoY = cob.cobY;

		enabled_perms = false;

		objOi = -1;

		if(Build.VERSION.SDK_INT  > 22)
			enabled_perms = MMFRuntime.inst.hasPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);
		else
			enabled_perms = true;

		offsetX = 0;
		offsetY = 0;
		fromPause = false;

		if(!enabled_perms && imageName.length() > 0 && !imageName.contains("/"))
			enabled_perms = true;

		loadFromPermission(imageName);
		setHotSpot(0, 0);
		return false;
	}

	private void loadFromPermission(final String filename)
	{
		if(filename == null || filename.length() == 0)
			return;

		if(!enabled_perms
				&& MMFRuntime.inst.hasManifestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
				&& !filename.contains(MMFRuntime.packageName)) {
			MMFRuntime.inst.askForPermission(
					new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
					new PermissionsResultAction() {
						@Override
						public void onGranted() {
							enabled_perms = true;
							load(filename);
						}

						@Override
						public void onDenied(String permission) {
							enabled_perms = false;
						}
					}
			);
		}
		else
		{
			load(filename);
		}

	}
	@Override
	public void destroyRunObject(boolean bFast)
	{
		if (texture != null)
			texture.destroy ();

		if(this.stream != null)
		{
			InputStream s =  this.stream;
			try {
				if(s != null)
					s.close();
			}
			catch(Exception e)
			{

			}
			this.stream = null;
		}
	}

	public void load(String filename)
	{
		if(filename != null && filename.length() > 0) {
			//Log.Log("kcpica name: "+filename);
			try
			{
				new URL(filename);
			}
			catch(MalformedURLException e)
			{
				HFile file = ho.openHFile(filename);

				if(file == null)
					return;

				load(file.stream);
				//Log.Log("kcpica loaded: "+filename);
				file.close();
				return;
			}

			ho.retrieveHFile(filename, new CRunApp.FileRetrievedHandler()
			{
				@Override
				public void onRetrieved(HFile file, InputStream stream)
				{
					try
					{
						Log.Log("kcpica: Image retrieved, " + stream.available() + " bytes available");
					}
					catch (IOException e)
					{
					}

					load(stream);
				}

				@Override
				public void onFailure()
				{
					Log.Log("kcpica: Failure w/ async image download");
				}
			});

		}
	}

	public boolean load(InputStream stream)
	{
		try
		{
			if (texture != null)
			{
				texture.destroy ();
				Log.Log("Destroyed APO texture ...");
			}

			if(this.stream != null)
			{
				this.stream.close();
				this.stream = null;
			}

			flags &= ~ (PICTURE_FLIPPED_HORZ | PICTURE_FLIPPED_VERT);

			//offsetX = 0;
			//offsetY = 0;

			stream.mark(Integer.MAX_VALUE);

			try
			{
				texture = new CAPTexture (stream);
			}
			catch (Throwable e)
			{
				Log.Log ("kcpica: failed to create texture");

				stream.close();

				return false;
			}

			this.stream = stream;

			int iWidth = texture.getWidth ();
			int iHeight = texture.getHeight ();

			if ((flags & PICTURE_RESIZE) == 0 && !fromPause)
			{
				editorWidth = iWidth;
				editorHeight = iHeight;
				screenWidth = editorWidth;
				screenHeight = editorHeight;
				ho.setWidth(editorWidth);
				ho.setHeight(editorHeight);
			}

			loaded = true;
			fromPause = false;
			int angle = getOrientation(imageName);
			if(loaded)
			{
				if(angle != -1)
				{
					int lHotXSpot = iHotXSpot, lHotYSpot = iHotYSpot;
					setHotSpot(iWidth / 2, iHeight / 2);
					rotateRect(angle);
					fAngle = angle;
					setHotSpot(lHotXSpot, lHotYSpot);
				}
			}

		}
		catch(IOException e)
		{
			Log.Log ("kcpica: IOException loading image");
			return false;
		}

		return true;
	}

	@Override
	public void displayRunObject()
	{
		if (texture == null)
			return;

		int drawX = ho.hoX;
		int drawY = ho.hoY;

		drawX -= rh.rhWindowX;
		drawY -= rh.rhWindowY;

		//if(rh.rhApp.parentApp != null)
		//{
		//  	drawX += rh.rhApp.absoluteX;
		//    drawY += rh.rhApp.absoluteY;
		//}

		GLRenderer renderer = GLRenderer.inst;

		int texWidth  = texture.getWidth();
		int texHeight = texture.getHeight();

		float scaleX = ((float) screenWidth) / texWidth;
		float scaleY = ((float) screenHeight) / texHeight;

		boolean wrap = (flags & WRAPMODE_OFF)==0;
		boolean flipH = (flags & PICTURE_FLIPPED_HORZ)!=0;
		boolean flipV = (flags & PICTURE_FLIPPED_VERT)!=0;
		boolean resample = (flags & PICTURE_RESAMPLE)!=0;

		renderer.renderScaledRotatedImageWrapAndFlip
				(texture, fAngle,
						scaleX,
						scaleY,
						iHotXSpot,
						iHotYSpot,
						drawX,		// offsetX/Y should be removed and added when wrap mode is done
						drawY,
						texWidth,
						texHeight,
						Math.max(ho.ros.rsEffect , transparent ? GLRenderer.BOP_BLEND : GLRenderer.BOP_COPY),
						ho.ros.rsEffectParam,
						offsetX,
						offsetY,
						wrap  ? 1: 0,
						flipH ? 1: 0,
						flipV ? 1: 0,
						(MMFRuntime.inst.app.hdr2Options & CRunApp.AH2OPT_ANTIALIASED) != 0 || resample ? 1: 0,
						color_transp);

	}

	private void setEnabledPerm()
	{
		enabled_perms = true;
	}

	@Override
	public void reinitDisplay ()
	{

		Log.Log("About to reinit display ...");

		if (texture != null)
			return;

		if(SurfaceView.inst != null) {
			fromPause = true;
			loaded = false;
			if(texture == null) {
				loadFromPermission(imageName);
			}
		}

		//Log.Log("Active Picture reinitDisplay(), but no inputStream to load from?");

	}


	@Override
	public int handleRunObject()
	{
		if ((flags & PICTURE_LINKDIR) != 0)
		{
			float angle = (rh.getDir(ho) * 360) / 32;
			while (angle < 0)
				angle += 360;
			while (angle >= 360)
				angle -= 360;
			if (this.fAngle != angle) {
				this.fAngle = angle;
				onChange();
			}
		}
		return 0;
	}

	@Override
	public void pauseRunObject() {
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		imageselect = true;
		String filename=null;
		RestoreAutoEnd();
		if (requestCode == REQUEST_IMAGE_PICKED && resultCode == Activity.RESULT_OK) {

			if(data == null)
			{
				objOi = -1;
				return;
			}

			Uri selectedImage = data.getData();
			MMFRuntime.resolver.takePersistableUriPermission(selectedImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);
			filename = CServices.getPath(MMFRuntime.inst, selectedImage);
			if(filename == null)
				filename = selectedImage.toString();

			if(filename != null && objOi == ho.hoOi)
			{
				imageselect = true;
				imageName = filename;
				loaded = false;
				fromPause = false;
				if(texture != null)
				{
					texture.destroy();
					texture = null;
				}

			}
		}
		else {
			imageselect = false;
		}
		objOi = -1;
	}

	private void updateImgSpot()
	{
		int xs = iHotXSpot;
		int ys = iHotYSpot;

		if(texture != null)
		{
			// Scaling
			if (screenWidth != texture.getWidth() && texture.getWidth() != 0)
				xs = ((xs * screenWidth) / texture.getWidth());
			if (screenHeight != texture.getHeight() && texture.getHeight() != 0)
				ys = ((ys * screenHeight) / texture.getHeight());
		}
		// Rotation
		if (fAngle != 0) {
			float cosa;
			float sina;
			if (fAngle == 90) {
				cosa = 0.0f;
				sina = 1.0f;
			}
			else if (fAngle == 270) {
				cosa = 0.0f;
				sina = -1.0f;
			}
			else {
				cosa = (float) Math.cos(fAngle * Math.PI / 180.0f);
				sina = (float) Math.sin(fAngle * Math.PI / 180.0f);
			}

			// Rotation / center
			int xaxis = screenWidth / 2;
			int yaxis = screenHeight / 2;

			int x2 = (int) ((xs - xaxis) * cosa + (ys - yaxis) * sina);
			int y2 = (int) ((ys - yaxis) * cosa - (xs - xaxis) * sina);

			// Translation
			xs = x2 + ho.hoImgWidth / 2;
			ys = y2 + ho.hoImgHeight / 2;
		}

		ho.hoImgXSpot = Math.round(xs);
		ho.hoImgYSpot = Math.round(ys);
	}

	private void setHotSpot(int xs, int ys)
	{
		if (iHotXSpot != xs || iHotYSpot != ys)
		{
			ho.hoX -= ho.hoImgXSpot;
			ho.hoY -= ho.hoImgYSpot;
			iHotXSpot = xs;
			iHotYSpot = ys;
			updateImgSpot();
			ho.hoX += ho.hoImgXSpot;
			ho.hoY += ho.hoImgYSpot;
		}

	}

	private void rotateRect(float fAngle)
	{
		int x, y;
		double cosa, sina;

		if (fAngle == 90.0)
		{
			cosa = 0.0f;
			sina = 1.0f;
		}
		else if (fAngle == 180.0)
		{
			cosa = -1.0f;
			sina = 0.0f;
		}
		else if (fAngle == 270.0)
		{
			cosa = 0.0f;
			sina = -1.0f;
		}
		else
		{
			double arad = fAngle * Math.PI / 180.0f;
			cosa = Math.cos(arad);
			sina = Math.sin(arad);
		}

		double nhxcos;
		double nhxsin;
		double nhycos;
		double nhysin;
		nhxcos = nhxsin = nhycos = nhysin = 0.0;
		ImgMask cMask = new ImgMask();
		cMask.topLeft.x = cMask.topLeft.y = 0;
		x = ho.hoImgWidth;
		nhxcos = x * cosa;
		nhxsin = x * sina;
		cMask.topRight.x = (int) Math.floor(nhxcos + nhysin);
		cMask.topRight.y = (int) Math.floor(nhycos - nhxsin);

		y = ho.hoImgHeight;
		nhycos = y * cosa;
		nhysin = y * sina;
		cMask.bottomRight.x = (int) Math.floor(nhxcos + nhysin);
		cMask.bottomRight.y = (int) Math.floor(nhycos - nhxsin);

		cMask.bottomLeft.x = cMask.topLeft.x + cMask.bottomRight.x - cMask.topRight.x;
		cMask.bottomLeft.y = cMask.topLeft.y + cMask.bottomRight.y - cMask.topRight.y;

		int xmin = Math.min(cMask.topLeft.x, Math.min(cMask.topRight.x, Math.min(cMask.bottomRight.x, cMask.bottomLeft.x)));
		int ymin = Math.min(cMask.topLeft.y, Math.min(cMask.topRight.y, Math.min(cMask.bottomRight.y, cMask.bottomLeft.y)));
		int xmax = Math.max(cMask.topLeft.x, Math.max(cMask.topRight.x, Math.max(cMask.bottomRight.x, cMask.bottomLeft.x)));
		int ymax = Math.max(cMask.topLeft.y, Math.max(cMask.topRight.y, Math.max(cMask.bottomRight.y, cMask.bottomLeft.y)));

		ho.hoImgWidth = Math.round(xmax - xmin);
		ho.hoImgHeight = Math.round(ymax - ymin);
	}

	private void onChange()
	{
		ho.hoImgWidth = screenWidth;
		ho.hoImgHeight = screenHeight;
		if (fAngle != 0.0f )
			rotateRect(fAngle);
		updateImgSpot();
		ho.modif();
		getZoneInfos();		//Using getZoneInfos() to get initial size.
	}

	class ImgMask
	{
		public Point topLeft;
		public Point topRight;
		public Point bottomLeft;
		public Point bottomRight;

		ImgMask()
		{
			topLeft = new Point(0,0);
			topRight = new Point(0,0);
			bottomLeft = new Point(0,0);
			bottomRight = new Point(0,0);

		}
	}

	@Override
	public void action (int num, CActExtension act)
	{
		switch (num)
		{
			case 0: /* Load image */
			{
				imageName = act.getParamFilename(rh, 0);

				loaded = false;
				fromPause = false;
				Log.Log("kcpica filename: "+imageName);

				if (imageName.length() == 0)
					return;

				if(Build.VERSION.SDK_INT  > 22)
					enabled_perms = MMFRuntime.inst.hasPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);

				if(!enabled_perms)
				{
					if (!imageName.contains("/")	// possible embedded file
							|| imageName.contains(MMFRuntime.packageName) //scope or data storage
							|| URLUtil.isAssetUrl(imageName)
							|| URLUtil.isContentUrl(imageName)
							|| URLUtil.isNetworkUrl(imageName))
						enabled_perms = true;
				}
				loadFromPermission (imageName);
				return;
			}

			case 1: /* Load image from selector */
			{
				if(Build.VERSION.SDK_INT  > 22)
					enabled_perms = MMFRuntime.inst.hasPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);

				if(!enabled_perms
						&& MMFRuntime.inst.hasManifestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
					MMFRuntime.inst.askForPermission(
							new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
							new PermissionsResultAction() {
								@Override
								public void onGranted() {
									enabled_perms = true;
									try {
										imageselect = false;
										objOi = ho.hoOi;
										SuspendAutoEnd();
										Intent pickPhoto = new Intent(Intent.ACTION_OPEN_DOCUMENT,
												android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
										pickPhoto.addCategory(Intent.CATEGORY_OPENABLE);
										pickPhoto.setType("image/*");
										pickPhoto.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
										MMFRuntime.inst.startActivityForResult(pickPhoto, REQUEST_IMAGE_PICKED);
									}catch (ActivityNotFoundException e) {
										Log.Log("Error:"+e.getMessage());
									}								}

								@Override
								public void onDenied(String permission) {
									enabled_perms = false;
								}
							}
					);
				}
				else
				{
					try {
						imageselect = false;
						objOi = ho.hoOi;
						SuspendAutoEnd();
						Intent pickPhoto = new Intent(Intent.ACTION_OPEN_DOCUMENT,
								android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
						pickPhoto.addCategory(Intent.CATEGORY_OPENABLE);
						pickPhoto.setType("image/*");
						pickPhoto.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						MMFRuntime.inst.startActivityForResult(pickPhoto, REQUEST_IMAGE_PICKED);
					}catch (ActivityNotFoundException e) {
						Log.Log("Error:"+e.getMessage());
					}
				}

				return;
			}
			case 2: /* Set hotspot */
			{
				setHotSpot(act.getParamExpression (rh, 0), act.getParamExpression (rh, 1));
				return;
			}

			case 3: /* Set dimensions */
			{
				screenWidth = Math.max(act.getParamExpression(this.rh, 0), 0);
				screenHeight = Math.max(act.getParamExpression(this.rh, 1), 10);
				onChange();
				return;
			}

			case 4: /* Set angle */
			{
				int angle = act.getParamExpression (rh, 0);

				while (angle < 0)
					angle += 360;

				while (angle >= 360)
					angle -= 360;

				if (this.fAngle != angle)
				{
					ho.roc.rcAngle = angle;
					fAngle = angle;
					onChange();
				}
				return;
			}

			case 5: /* Set semi-transparency ratio */
				int ratio = (int) act.getParamExpression(rh, 0);
				// Build 283.2: simple copy from CEvent / actSetSemiTransparency
				if((ho.ros.rsEffect & GLRenderer.BOP_RGBAFILTER) != 0)
				{
					ratio = Math.min(Math.max(255-(int)(ratio*2.55f), 0), 255);
					ho.ros.rsEffect = (ho.ros.rsEffect & GLRenderer.BOP_MASK) | GLRenderer.BOP_RGBAFILTER;

					int rgbaCoeff = ho.ros.rsEffectParam;
					int alphaPart = (int)ratio << 24;
					int rgbPart = (rgbaCoeff & 0x00FFFFFF);
					ho.ros.rsEffectParam = alphaPart | rgbPart;
				}
				else
				{
					ratio = Math.min(Math.max((int)((float)ratio*128/100), 0), 128);
					ho.ros.rsEffect&=~EFFECT_MASK;
					ho.ros.rsEffect|= GLRenderer.BOP_BLEND;   // EFFECT_SEMITRANSP SAME VALUE as BOP_BLEND = 1;
					ho.ros.rsEffectParam=(int)ratio;
				}
				ho.modif();
				return;

			case 6: /* Set hotspot to top-left */
			{
				setHotSpot(0, 0);
				return;
			}

			case 7: /* Set hotspot to top-center */
			{
				if(texture != null)
					setHotSpot(texture.getWidth() / 2, 0);
				return;
			}

			case 8: /* Set hotspot to top-right */
			{
				if(texture != null)
					setHotSpot(texture.getWidth() - 1, 0);
				return;
			}

			case 9: /* Set hotspot to center-left */
			{
				if(texture != null)
					setHotSpot(0, texture.getHeight() / 2);
				return;
			}

			case 10: /* Set hotspot to center */
			{
				if(texture != null)
					setHotSpot(texture.getWidth() / 2, texture.getHeight() / 2);
				return;
			}

			case 11: /* Set hotspot to center-right */
			{
				if(texture != null)
					setHotSpot(texture.getWidth() - 1, texture.getHeight() / 2);
				return;
			}

			case 12: /* Set hotspot to bottom-left */
			{
				if(texture != null)
					setHotSpot(0, texture.getHeight() - 1);
				return;
			}

			case 13: /* Set hotspot to bottom-center */
			{
				if(texture != null)
					setHotSpot(texture.getWidth() / 2, texture.getHeight() - 1);
				return;
			}

			case 14: /* Set hotspot to bottom-right */
			{
				if(texture != null)
					setHotSpot(texture.getWidth() - 1, texture.getHeight() - 1);
				return;
			}

			case 15: /* Flip horizontally */
			{
				flags ^= PICTURE_FLIPPED_HORZ;
				return;
			}

			case 16: /* Flip vertically */
			{
				flags ^= PICTURE_FLIPPED_VERT;
				return;
			}

			case 17: /* Link direction */
			{
				flags |= PICTURE_LINKDIR;
				return;
			}

			case 18: /* Unlink direction */
			{
				flags &= ~PICTURE_LINKDIR;
				return;
			}
			case 19: /* Look at */
			{
				int tgtx = act.getParamExpression (rh, 0);
				int tgty = act.getParamExpression (rh, 1);
				int srcx = ho.getX() - ho.hoImgXSpot + ho.getWidth()/2;
				int srcy = ho.getY() - ho.hoImgYSpot + ho.getHeight()/2;
				int angle;
				// Calcul de l'angle (entre le centre de l'image et le point destination)
				if ( srcx == tgtx )
				{
					if ( tgty < srcy )
						angle = 90;
					else
						angle = 270;
				}
				else
				{
					angle = (int)( Math.atan2(Math.abs(tgty-srcy),Math.abs(tgtx-srcx)) * 180 / Math.PI);
					// Trouver le bon cadran
					if ( tgtx > srcx )
					{
						if ( tgty > srcy )
							angle = 360 - angle;
					}
					else
					{
						if ( tgty > srcy )
							angle = 180 + angle;
						else
							angle = 180 - angle;
					}
				}
				if (fAngle != angle)
				{
					fAngle = angle;
					this.onChange();
				}
				return;
			}

			case 20: /* Set offset X */

				offsetX = act.getParamExpression (rh, 0);
				return;

			case 21: /* Set offset Y */

				offsetY = act.getParamExpression (rh, 0);
				return;

			case 24: /* Set wrap mode on */
			{
				flags &= ~WRAPMODE_OFF;
				return;
			}

			case 25: /* Set wrap mode off */
			{
				flags |= WRAPMODE_OFF;
				return;
			}

			case 26: /* Add backdrop */

				/* TODO */

				return;

			case 27: /* Set auto-resize on */
			{
				flags |= PICTURE_RESIZE;

				screenWidth = editorWidth;
				screenHeight = editorHeight;
				ho.setWidth (editorWidth);
				ho.setHeight (editorHeight);
				onChange();
				return;
			}

			case 28: /* Set auto-resize off */
			{
				flags |= ~ PICTURE_RESIZE;

				if(texture.getWidth() != 0)
				{
					screenWidth = texture.getWidth();
					screenHeight = texture.getHeight();
				}
				else
				{
					screenWidth = editorWidth;
					screenHeight = editorHeight;
				}
				ho.setWidth (screenWidth);
				ho.setHeight (screenHeight);
				onChange();

				return;
			}

			case 29: /* Set zoom percentage */
			{
				float zoom = (act.getParamExpression (rh, 0));
				if(texture != null)
				{
					screenWidth = (int)Math.ceil(texture.getWidth() * zoom / 100.0f);
					screenHeight = (int)Math.ceil(texture.getHeight() * zoom / 100.0f);
					onChange();
				}

				return;
			}

			case 30: /* Set zoom width */
			{
				screenWidth = act.getParamExpression(this.rh, 0);
				if (texture != null && texture.getWidth() != 0 )
					screenHeight = (texture.getHeight() * screenWidth) / texture.getWidth();
				onChange();

				return;
			}

			case 31: /* Set zoom height */
			{
				screenHeight = act.getParamExpression(this.rh, 0);
				if (texture != null && texture.getHeight() != 0)
					screenWidth = (texture.getWidth() * screenHeight) / texture.getHeight();
				onChange();
				return;
			}

			case 32: /* Set zoom rect */
			{
				if (texture == null)
					return;

				int w = act.getParamExpression (rh, 0);
				int h = act.getParamExpression (rh, 1);
				boolean evenIfSmaller = act.getParamExpression (rh, 2) != 0;

				int iw = texture.getWidth ();
				int ih = texture.getHeight ();
				int nw = 0;
				int nh = 0;
				if ( w != 0 && h != 0 )
				{
					if ( evenIfSmaller || iw > w || ih > h )
					{
						if ( iw/w > ih/h )
						{
							nw = w;
							if ( iw != 0 )
							{
								nh = Math.round((ih * w) / iw);
							}
						}
						else
						{
							nh = h;
							if ( ih != 0 )
							{
								nw = Math.round((iw * h) / ih);
							}
						}
					}
					else
					{
						nw = iw;
						nh = ih;
					}
				}

				screenWidth = nw;
				screenHeight = nh;
				onChange();

				return;
			}

			case 22: /* Set resize mode -> fast */
				flags &= ~PICTURE_RESAMPLE;
				return;
			case 23: /* Set resize mode -> resample */
				flags |= PICTURE_RESAMPLE;
				return;
		}
	}

	@Override
	public boolean condition (int num, CCndExtension cnd)
	{
		switch (num)
		{
			case 0: /* Picture is loaded? */
				return (loaded);

			case 1: /* Picture is flipped horizontally? */
				return (flags & PICTURE_FLIPPED_HORZ) != 0;

			case 2: /* Picture is flipped vertically? */
				return (flags & PICTURE_FLIPPED_VERT) != 0;

			case 3: /* Wrap mode is on? */
				return (flags & WRAPMODE_OFF) == 0;
		}

		return false;
	}

	@Override
	public CValue expression (int num)
	{
		switch (num)
		{
			case 0: /* Get picture name */
				expRet.forceString(imageName);
				return expRet;

			case 1: /* Get picture width */
				expRet.forceInt((texture != null ? texture.getWidth () : 0));
				return expRet;

			case 2: /* Get picture height */
				expRet.forceInt((texture != null ? texture.getHeight () : 0));
				return expRet;

			case 3: /* Get picture resized width */
				expRet.forceInt(screenWidth);
				return expRet;

			case 4: /* Get picture resized height */
				expRet.forceInt(screenHeight);
				return expRet;

			case 5: /* Get picture display width */
				expRet.forceInt(ho.hoImgWidth);
				return expRet;

			case 6: /* Get picture display height */
				expRet.forceInt(ho.hoImgHeight);
				return expRet;

			case 7: /* Get hotspot X */
				if(texture != null)
					expRet.forceInt(iHotXSpot);
				return expRet;

			case 8: /* Get hotspot Y */
				if(texture != null)
					expRet.forceInt(iHotYSpot);
				return expRet;

			case 9: /* Get angle */
				expRet.forceDouble(fAngle);
				return expRet;

			case 10: /* Get semi-transparency ratio */
				float alpha = 1.0f;
				if ((this.ho.ros.rsEffect & GLRenderer.BOP_RGBAFILTER) != 0)
					alpha =  (((ho.ros.rsEffectParam >>> 24) & 0xFF) / 255.0f);
				else if ((ho.ros.rsEffect & GLRenderer.BOP_MASK ) == GLRenderer.BOP_BLEND)
				{
					expRet.forceInt(Math.round(ho.ros.rsEffectParam * 100) / 128);
					return expRet;
				}
				expRet.forceInt( Math.round( (1 - alpha) * 255.0f ));
				return expRet;

			case 11: /* Get offset X */
				expRet.forceInt(offsetX);
				return expRet;

			case 12: /* Get offset Y */
				expRet.forceInt(offsetY);
				return expRet;

			case 13: /* Get zoom factor X */
				expRet.forceDouble(0);

				if (texture == null)
					return expRet;

				expRet.forceDouble(100.0*screenWidth / texture.getWidth());
				return expRet;

			case 14: /* Get zoom factor Y */
				expRet.forceDouble(0);

				if (texture == null)
					return expRet;

				expRet.forceDouble(100.0*screenHeight / texture.getHeight());
				return expRet;
		}

		return expRet;
	}


}
