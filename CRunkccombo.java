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

package Extensions;

import android.Manifest;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import Actions.CActExtension;
import Application.CRunApp;
import Conditions.CCndExtension;
import Expressions.CValue;
import OI.CObjectCommon;
import Params.CPositionInfo;
import RunLoop.CCreateObjectInfo;
import Runtime.MMFRuntime;
import Runtime.PermissionsResultAction;
import Services.CBinaryFile;
import Services.CFontInfo;
import Services.CRect;
import Services.CServices;
import Services.UnicodeReader;

public class CRunkccombo extends CRunViewExtension
{
	List<String> list;
	@SuppressWarnings("rawtypes")
	ArrayAdapter adapter;
	Spinner field;

	boolean modified, bItemClick = false;
	boolean oneBased, sort, scrollToNewLine, UseSystem_color, b3dlook;

	int fColor = 0xff000000;
	int bColor = 0xffffffff;
	int lColor = 0xff333333;

	int brightColor = 0xFFFFFFFF;

	int nHeight;
	float scale = 1;

	boolean firstTime;
	boolean bVisible;
	int selectedIndex =-1;
	CValue expRet;

	private static int COMBO_SIMPLE = 0x0001;
	private static int COMBO_DROPDOWN = 0x0002;
	private static int COMBO_DROPDOWNLIST = 0x0004;

	private static int PERMISSIONS_COMBO_REQUEST = 12377859;
	private boolean enabled_perms;


	public CRunkccombo()
	{
		expRet = new CValue(0);
	}

	@Override
	public int getNumberOfConditions()
	{
		return 6;
	}

	public static final Comparator<String> comparator = new Comparator<String>() {
		@Override
		public int compare(String a, String b) {
			return a.compareTo(b);
		}
	};

	private void addLine(String text)
	{
		list.add(text);

		if (sort)
			Collections.sort (list, comparator);

		adapter.notifyDataSetChanged();
	}

	private CFontInfo font;

	@Override
	public void createRunView(CBinaryFile file, CCreateObjectInfo cob, int version)
	{
		this.ho.hoOEFlags |= CObjectCommon.OEFLAG_NEVERSLEEP;
		this.ho.hoOEFlags |= CObjectCommon.OEFLAG_NEVERKILL;

		ho.hoImgWidth = file.readShort();
		nHeight = file.readShort();

		ho.hoImgHeight = -1;

		if(rh.rhApp.bUnicode)
			font = file.readLogFont();
		else
			font = file.readLogFont16();

		//file.skipBytes(4); // Foreground color
		fColor = file.readColor(); // Foreground color

		if(rh.rhApp.bUnicode)
			file.skipBytes(80);
		else
			file.skipBytes(40);

		int flags = file.readInt();
		int lineCount = file.readShort();

		int mode = (flags & COMBO_SIMPLE) != 0 ? Spinner.MODE_DIALOG : Spinner.MODE_DROPDOWN;
		Context context = ho.getControlsContext();
		field = new Spinner (context, mode);

		field.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
			{
				// This avoid false triggering when creating
				selectedIndex = position;
				if(view != null) {
					ho.generateEvent(3, 0);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent)
			{
				bItemClick = false;
				ho.generateEvent(3, 0);

			}
		});

		field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if(!hasFocus)
				{
					if(!MMFRuntime.inst.bHardKey)
						focusNext();
				}
				else
				{
					if (MMFRuntime.inst.bHardKey && v.getWindowToken() != null && field == v) {
						v.performClick();
					}
				}
			}
		});

		field.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
					focusNext();
					return true;
				}
				if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
					focusNext();
					return true;
				}
				if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
					focusNext();
					return true;
				}

				return false;
			}
		});

		TextPaint textPaint = new TextPaint();

		textPaint.setTypeface(font.font);
		textPaint.setTextSize(font.lfHeight);
		textPaint.setAntiAlias(true);

		Paint.FontMetrics fm = textPaint.getFontMetrics();

		float calcHeight = (fm.bottom - fm.top) + 13;
		calcHeight *= Math.sqrt(MMFRuntime.inst.scaleX*MMFRuntime.inst.scaleY);
		nHeight = (int)calcHeight;
		//file.skipBytes(4); // Background color
		bColor = file.readColor(); // Background color

		file.skipBytes(12);

		oneBased = (flags & 0x0100) != 0;

		UseSystem_color = (flags & 0x0040) != 0;

		b3dlook = true;

		field.setVerticalScrollBarEnabled((flags & 0x0008) != 0);

		sort = ((flags & 0x0010) != 0);
		scrollToNewLine = ((flags & 0x0080) != 0);

		//int comboType = (flags & 0x0007);

		list = new ArrayList <String> ();

		while (lineCount > 0)
		{
			String text = file.readString();
			list.add (text);
			--lineCount;
		}

		if (sort)
			Collections.sort(list);

		if ((rh.rhApp.hdr2Options & CRunApp.AH2OPT_SYSTEMFONT) != 0) {
			font.font = Typeface.DEFAULT;
		}

		if((rh.rhApp.hdr2Options & CRunApp.AH2OPT_SYSTEMFONT) != 0) {
			adapter = new ArrayAdapter<String> (context, android.R.layout.simple_spinner_item, list);
			if((flags & COMBO_SIMPLE) == 0)
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		}
		else {

			if((flags & COMBO_SIMPLE) == 0)
				field.setPopupBackgroundResource(MMFRuntime.inst.getResourceID("drawable/fusion_spinner_popup"));

			adapter = new ArrayAdapter<String> (context, MMFRuntime.inst.getResourceID("layout/fusion_spinner_item"), list) {

				@Override
				public View getView(int position, View convertView, ViewGroup parent) {

					View view =super.getView(position, convertView, parent);

					TextView text=(TextView) view.findViewById(android.R.id.text1);

					text.setTypeface(font.font);
					text.setTextSize(TypedValue.COMPLEX_UNIT_PX, font.lfHeight*(float) Math.sqrt(MMFRuntime.inst.scaleX*MMFRuntime.inst.scaleY));
					text.setGravity(Gravity.CENTER_VERTICAL);
					text.setPadding(5, 5, 5, 5);
					if(!UseSystem_color)
						text.setTextColor((0xff << 24) | fColor);
					else
						text.setTextColor(MMFRuntime.inst.getResources().getColor(android.R.color.primary_text_dark));

					text.setText(text.getText(),TextView.BufferType.SPANNABLE);
					return view;
				}

				@Override
				public View getDropDownView(int position, View convertView, ViewGroup parent)
				{
					View view = super.getView(position, convertView, parent);

					TextView text = (TextView)view.findViewById(android.R.id.text1);

					if(!UseSystem_color) {
						//choose your color
						if(position != selectedIndex) {
							GradientDrawable gd = new GradientDrawable();
							gd.setColor((0xFF << 24) | (bColor));
							gd.setStroke(1, Color.BLACK);
							text.setBackground(gd);
						}
						else {
							GradientDrawable gd = new GradientDrawable();
							gd.setColor(Color.CYAN);
							gd.setStroke(1, Color.BLACK);
							text.setBackground(gd);
						}
						text.setTextColor(new ColorStateList(
								new int[][] {
										new int[] { android.R.attr.state_pressed},
										new int[] { -android.R.attr.state_focused},
										new int[0]},
								new int[] {
										(0xff << 24) | ~fColor,
										(0xff << 24) | fColor,
										(0xff << 24) | ~fColor,
								}
						));

					}

					text.setTypeface(font.font);
					text.setTextSize(TypedValue.COMPLEX_UNIT_PX, font.lfHeight*(float) Math.sqrt(MMFRuntime.inst.scaleX*MMFRuntime.inst.scaleY));
					text.setGravity(Gravity.CENTER_VERTICAL);
					text.setMinHeight(54);
					text.setPadding(8, 5, 5, 8);
					text.setText(text.getText(),TextView.BufferType.SPANNABLE);

					return view;
				}

			};

			if(!UseSystem_color) {
				Resources res = MMFRuntime.inst.getResources();
				try {
					Drawable dd = Drawable.createFromXml(res, res.getXml(MMFRuntime.inst.getResourceID("drawable/fusion_spinner")));
					field.setBackground(dd);
					StateListDrawable stateDrawable = (StateListDrawable) field.getBackground();
					DrawableContainer.DrawableContainerState drawableContainerState = (DrawableContainer.DrawableContainerState) stateDrawable.getConstantState();
					Drawable[] children = drawableContainerState.getChildren();
					LayerDrawable spinnerItem = (LayerDrawable) children[0];
					GradientDrawable gDrawable = (GradientDrawable) spinnerItem.getDrawable(0);
					gDrawable.setColor((0xFF << 24 | bColor));
					field.setBackground(dd);
				} catch (Exception ex) {
					ex.printStackTrace();
				}

			}
		}

		field.setDropDownVerticalOffset(nHeight+3);
		field.setAdapter(adapter);

		setView (field);
		setViewHeight((int)(nHeight));

		adapter.notifyDataSetChanged();

		//if ((flags & 0x0020) != 0) // Hide on start
		bVisible = (flags & 0x0020) == 0; // Show on start

		firstTime = true;

		field.setVisibility(View.INVISIBLE);

		enabled_perms = false;

		if(Build.VERSION.SDK_INT  > 22)
			enabled_perms = MMFRuntime.inst.hasAllPermissionsGranted(
					new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE});
		else
			enabled_perms = true;
		return ;
	}

	@Override
	public int handleRunObject()
	{
		super.handleRunObject ();

		if (view != null) {
			if(bVisible && firstTime) {
				view.setVisibility(View.VISIBLE);
				this.setViewHeight(nHeight);
				firstTime = false;
			}
		}
		return 0;
	}

	@Override
	public void destroyRunObject(boolean bFast) {
		adapter = null;
		view.setVisibility(View.GONE);
		setView (null);
	}

	@Override
	public CFontInfo getRunObjectFont()
	{
		return font;
	}

	@Override
	public void setRunObjectFont(CFontInfo font, CRect rc)
	{
		if ((rh.rhApp.hdr2Options & CRunApp.AH2OPT_SYSTEMFONT) != 0) {
			return;
		}

		this.font = font;

		if(rc != null) {
			setViewWidth(rc.right - rc.left);
			setViewHeight(rc.bottom - rc.top);
		}
		else {
			TextPaint textPaint = new TextPaint();

			textPaint.setTypeface(font.font);
			textPaint.setTextSize(font.lfHeight);
			textPaint.setAntiAlias(true);
			Paint.FontMetrics fm = textPaint.getFontMetrics();

			float calcHeight = (fm.bottom - fm.top) + 3;
			calcHeight *= Math.sqrt(MMFRuntime.inst.scaleX*MMFRuntime.inst.scaleY);
			nHeight = (int)calcHeight+13;

			setViewHeight(nHeight);
		}

		updateLayout();
		if(adapter != null)
			adapter.notifyDataSetChanged();

	}

	@Override
	public void setRunObjectTextColor(int rgb)
	{
		this.fColor = rgb;
		if(adapter != null)
			adapter.notifyDataSetChanged();
	}

	private int getIndexParameter(CActExtension act, int index)
	{
		if (act == null)
			return ho.getExpParam().getInt() - (oneBased ? 1 : 0);;

		return act.getParamExpression(rh, index) - (oneBased ? 1 : 0);
	}

	private int fixIndexBase(int index)
	{
		return index + (oneBased ? 1 : 0);
	}

	@Override
	public boolean condition(int num, CCndExtension cnd)
	{
		switch (num)
		{
			case 0: // Is visible?

				return view.getVisibility() == View.VISIBLE;

			case 1: // Is enabled?

				return view.isEnabled();

			case 2: // Double clicked

				return true;

			case 3: // Selection changed

				return true;

			case 4: // Has focus

				if(view != null)
					return view.isFocused();
				return false;

			case 5: // Is dropped

				return false;
		}
		;

		return false;
	}

	private void fillLoadList(String filename)
	{
		list.clear();
		try
		{

			CRunApp.HFile file = null;
			file = ho.openHFile(filename);
			if(file != null) {
				UnicodeReader ur = new UnicodeReader(file.stream, MMFRuntime.inst.charSet);
				BufferedReader reader = new BufferedReader(ur);

				String s;
				while ((s = reader.readLine()) != null) {
					addLine(s);
				}
				reader.close();
				file.close();
			}
		}
		catch(Exception e)
		{
		}
		if(adapter != null)
			adapter.notifyDataSetChanged();


	}

	private void fillFolderList(String startfolder, boolean mode)
	{
		list.clear();
		enabled_perms = true;

		try
		{

			for(File files : CServices.getFiles(startfolder)) {
				if(mode) {
					if (files.isDirectory())
						addLine(files.getAbsolutePath());
				}
				else {
					if (!files.isDirectory())
						addLine(files.getAbsolutePath());
				}
			}
		}
		catch(Exception e)
		{
		}
		if(adapter != null)
			adapter.notifyDataSetChanged();


	}

	private void saveList(String path)
	{
		try
		{
			String packageName = MMFRuntime.packageName;

			if(CServices.allowWriteFileMode(path))
			{
				FileOutputStream file = new FileOutputStream(path, false);
				if(file != null) {
					for(String s : list)
					{
						file.write(s.getBytes(MMFRuntime.inst.charSet));
						file.write("\n".getBytes());
					}
					file.flush();
					file.close();
				}
			}
			else
			{
				OutputStream fos;
				File f = new File(path);
				fos = CServices.saveFile(MMFRuntime.inst, f);
				if (fos != null)
				{
					for(String s : list)
					{
						fos.write(s.getBytes(MMFRuntime.inst.charSet));
						fos.write("\n".getBytes());
					}
					fos.flush();
					fos.close();
				}
			}
			File file = new File(path);
			if(file.exists())
				CServices.makeFileVisible(MMFRuntime.inst, file);

		}
		catch(Exception e)
		{
		}
	}

	@Override
	public void action(int num, CActExtension act)
	{
		switch (num)
		{
			case 0: // Load list file

				final String filename = act.getParamFilename(rh, 0);
//				if(Build.VERSION.SDK_INT  > 18)
//					enabled_perms = MMFRuntime.inst.hasPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);
//
//				if(!enabled_perms)
//				{
//					if (!filename.contains("/")
//							|| filename.contains(MMFRuntime.packageName)
//							|| URLUtil.isNetworkUrl(filename))
//						enabled_perms = true;
//				}


				if (CServices.canFusionRead(filename))
					fillLoadList(filename);
				else
				{
					MMFRuntime.inst.askForPermission(
							new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
							new PermissionsResultAction() {
								@Override
								public void onGranted() {
									fillLoadList(filename);
								}

								@Override
								public void onDenied(String permission) {

								}
							}
					);
				}
				break;

			case 1: // Load drives list

				break;

			case 2: // Load directory list
				final String startFolder = act.getParamExpString(rh, 0);
				if(Build.VERSION.SDK_INT  > 18)
					enabled_perms = MMFRuntime.inst.hasPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);

				if(enabled_perms)
					fillFolderList(startFolder, true);
				else
					MMFRuntime.inst.askForPermission(
							new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
							new PermissionsResultAction() {
								@Override
								public void onGranted() {
									fillFolderList(startFolder, true);
								}

								@Override
								public void onDenied(String permission) {

								}
							}
					);

				break;

			case 3: // Load files list

				final String startFolder1 = act.getParamExpString(rh, 0);
				if(Build.VERSION.SDK_INT  > 18)
					enabled_perms = MMFRuntime.inst.hasPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);

				if(enabled_perms)
					fillFolderList(startFolder1, false);
				else
					MMFRuntime.inst.askForPermission(
							new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
							new PermissionsResultAction() {
								@Override
								public void onGranted() {
									fillFolderList(startFolder1, false);
								}

								@Override
								public void onDenied(String permission) {

								}
							}
					);

				break;

			case 4: // Save list

				final String savefile = act.getParamFilename(rh, 0);
				if(Build.VERSION.SDK_INT  > 22)
					enabled_perms = MMFRuntime.inst.hasAllPermissionsGranted(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE});

				if(!savefile.contains(MMFRuntime.packageName)) {
					if (enabled_perms)
						saveList(savefile);
					else
						MMFRuntime.inst.askForPermission(
								new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
								new PermissionsResultAction() {
									@Override
									public void onGranted() {
										saveList(savefile);
									}

									@Override
									public void onDenied(String permission) {

									}
								}
						);
				}
				else
				{
					saveList(savefile);
				}
				break;

			case 5: // Reset

				list.clear();
				if(adapter != null)
					adapter.notifyDataSetChanged();

				break;

			case 6: // Add line

				addLine(act.getParamExpString(rh, 0));
				break;

			case 7: // Insert line
			{
				final int position = getIndexParameter(act, 0);
				final String line = act.getParamExpString(rh, 1);

				if (position < 0 || position >= list.size())
					list.add (line);
				else
					list.add(position, line);
				if(adapter != null)
					adapter.notifyDataSetChanged();

				break;
			}

			case 8: // Delete line
			{
				int index = getIndexParameter(act, 0);
				if ( index >= 0 && index < list.size() )
					list.remove(index);
				if(adapter != null)
					adapter.notifyDataSetChanged();
				break;
			}

			case 9: // Set current line
			{
				((Spinner) view).setSelection(getIndexParameter(act, 0));
				break;
			}

			case 10: // Show

				if(view != null)
					view.setVisibility (View.VISIBLE);
				firstTime = false;
				break;

			case 11: // Hide

				if(view != null)
					view.setVisibility (View.INVISIBLE);
				break;

			case 12: // Activate

				MMFRuntime.inst.runOnRuntimeThread( new Runnable() {

					@Override
					public void run() {
						if(view != null)
							view.requestFocus();
					}
				});

				break;

			case 13: // Enable

				if(view != null)
					view.setEnabled (true);
				break;

			case 14: // Disable

				if(view != null)
					view.setEnabled (false);
				break;

			case 15: // Set position

				CPositionInfo position = act.getParamPosition(rh, 0);

				ho.hoX = position.x;
				ho.hoY = position.y;

				break;

			case 16: // Set X position

				ho.hoX = act.getParamExpression(rh, 0);
				break;

			case 17: // Set Y position

				ho.hoY = act.getParamExpression(rh, 0);
				break;

			case 18: // Set size

				ho.setSize (act.getParamExpression(rh, 0),
						act.getParamExpression(rh, 1));

				break;

			case 19: // Set X size

				ho.setWidth (act.getParamExpression(rh, 0));
				break;

			case 20: // Set Y size

				ho.setHeight (act.getParamExpression(rh, 0));
				break;

			case 21: // Deactivate
				if(view != null)
					view.clearFocus();

				break;

			case 22: // Set edit text

				break;

			case 23: // Scroll to top

				break;

			case 24: // Scroll to line

				break;

			case 25: // Scroll to end

				break;

			case 26: // Set color

				break;

			case 27: // Set background color

				break;

			case 28:

				break;

			case 29:

				break;

			case 30:

				break;

			case 31: // Change line

				final int index = getIndexParameter(act, 0);
				final String text = act.getParamExpString(rh, 1);

				list.set (index, text);
				if(adapter != null)
					adapter.notifyDataSetChanged();

				break;
		}
		;
	}

	@Override
	public CValue expression(int num)
	{
		switch (num)
		{
			case 0: // Get selection index

				expRet.forceInt(fixIndexBase(((Spinner) view).getSelectedItemPosition()));
				return expRet;

			case 1: // Get selection text

				int selectionIndex = ((Spinner) view).getSelectedItemPosition();

				try
				{
					expRet.forceString(list.get (selectionIndex));
					return expRet;
				}
				catch (Throwable e)
				{
					expRet.forceString("");
					return expRet;
				}

			case 2: // Get selection directory

				expRet.forceString("");
				return expRet;

			case 3: // Get selection drive

				expRet.forceString("");
				return expRet;

			case 4: // Get line text

				// return new CValue(getIndexParameter(null, 0));
				try
				{
					return new CValue(list.get (getIndexParameter(null, 0)));
				}
				catch (Throwable t)
				{
					return new CValue ("");
				}

			case 5: // Get line directory

				ho.getExpParam();

				expRet.forceString("");
				return expRet;

			case 6: // Get line drive

				ho.getExpParam();

				expRet.forceString("");
				return expRet;

			case 7: // Get number of lines

				expRet.forceInt(list.size());
				return expRet;

			case 8: // Get X

				expRet.forceInt(ho.hoX);
				return expRet;

			case 9: // Get Y

				expRet.forceInt(ho.hoY);
				return expRet;

			case 10: // Get X size

				expRet.forceInt(ho.hoImgWidth);
				return expRet;

			case 11: // Get Y size

				expRet.forceInt(ho.hoImgHeight != -1 ? ho.hoImgHeight : nHeight);
				return expRet;

			case 12: // Get edit text

				expRet.forceString("");
				return expRet;

			case 13: // Get color

				expRet.forceInt(0);
				return expRet;

			case 14: // Get background color

				expRet.forceInt(0);
				return expRet;

			case 15: // Find string
			{
				expRet.forceInt(-1);
				String string = ho.getExpParam().getString();
				int startIndex = getIndexParameter(null, 0);

				if (startIndex >= list.size())
					return expRet;

				if (startIndex < 0)
					startIndex = 0;

				for (int i = startIndex; i < list.size(); ++i)
					if (list.get(i).contains(string)) {
						expRet.forceInt(fixIndexBase (i));
						return expRet;
					}
				return expRet;
			}

			case 16: // Find string exact
			{
				expRet.forceInt(-1);
				String string = ho.getExpParam().getString();
				int startIndex = getIndexParameter(null, 0);

				if (startIndex >= list.size())
					return expRet;

				if (startIndex < 0)
					startIndex = 0;

				int list_size = list.size();
				for (int i = startIndex; i < list_size; ++i)
					if (list.get(i).compareToIgnoreCase(string) == 0) {
						expRet.forceInt(fixIndexBase (i));
						return expRet;
					}
				return expRet;
			}

			case 17: // Get last index
				expRet.forceInt(list.size() - (oneBased ? 0 : 1));
				return expRet;

			case 18: // Get line data
				expRet.forceString("");
				return expRet;

		}

		expRet.forceInt(0);
		return expRet;
	}
}
