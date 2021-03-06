package com.gzplanet.xposed.xperianavbarbuttons;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import sheetrock.panda.changelog.ChangeLog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.robobunny.SeekBarPreference;

public class XposedSettings extends PreferenceActivity {
	public final static String DELIMITER_LAUNCHKEY = ",";

	int mScreenWidth;
	int mButtonWidth;
	int mButtonsCount = 2;
	boolean mShowMenu;
	boolean mShowSearch;
	boolean mShowRecent;
	String mThemeId;
	String mThemeColor;
	boolean mUseTheme;
	boolean mUseAltMenu;
	String mCacheFolder;
	int mDensityDpi;
	int mLeftMargin;
	int mRightMargin;
	int mNavBarHeight;
	int mExtraPadding;

	String mSearchKeycode;
	String mSearchLongPressKeycode;
	String mSearchFuncApp;
	String mSearchLongPressFuncApp;

	private ArrayList<String> mSearchFuncArray = new ArrayList<String>();
	private ArrayList<String> mSearchKeycodeValues = new ArrayList<String>();
	private ArrayList<String> mAppActivity = new ArrayList<String>();
	private ArrayList<String> mAppPackageName = new ArrayList<String>();
	private ArrayList<String> mAppComponentName = new ArrayList<String>();
	private ArrayList<String> mAppLaunchKey = new ArrayList<String>();
	private ArrayList<Drawable> mAppActivityIcon = new ArrayList<Drawable>();

	CheckBoxPreference mPrefShowRecent;
	CheckBoxPreference mPrefShowMenu;
	CheckBoxPreference mPrefShowSearch;
	Preference mPrefRestartSystemUI;
	Preference mPrefReorder;
	Preference mPrefTheme;
	CheckBoxPreference mPrefUseAltMenu;
	Preference mPrefHints;
	Preference mPrefLeftMargin;
	Preference mPrefRightMargin;
	Preference mPrefNavBarHeight;
	Preference mPrefSearchButtonFunc;
	Preference mPrefSearchButtonLongPressFunc;
	AppListPreference mPrefSearchButtonFuncApps;
	AppListPreference mPrefSearchLongPressButtonFuncApps;

	ButtonSettings mSettings;
	ThemeIcons mThemeIcons = new ThemeIcons();
	CustomButtons mCustomButtons;

	static int[] mIconId = { R.id.iv1, R.id.iv2, R.id.iv3, R.id.iv4, R.id.iv5 };

	// async task to load installed app list
	private class LoadInstalledAppTask extends AsyncTask<Void, Void, Void> {
		final Context mContext;

		public LoadInstalledAppTask(Context context) {
			mContext = context;
		}

		@Override
		protected Void doInBackground(Void... params) {
			final Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			final PackageManager pm = mContext.getPackageManager();

			List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

			Comparator<ResolveInfo> icc = new Comparator<ResolveInfo>() {
				@Override
				public int compare(ResolveInfo lhs, ResolveInfo rhs) {
					final String name1 = lhs.loadLabel(pm).toString();
					final String name2 = rhs.loadLabel(pm).toString();

					return name1.compareToIgnoreCase(name2);
				}

			};
			Collections.sort(apps, icc);

			// retrieve standard icon size from this package
			Drawable appIcon = mContext.getResources().getDrawable(R.drawable.ic_launcher);
			int width = ((BitmapDrawable) appIcon).getIntrinsicWidth();

			for (int i = 0; i < apps.size(); i++) {
				mAppActivity.add(apps.get(i).loadLabel(pm).toString());
				mAppPackageName.add(apps.get(i).activityInfo.packageName);
				mAppComponentName.add(apps.get(i).activityInfo.name);
				mAppLaunchKey.add(apps.get(i).activityInfo.packageName + DELIMITER_LAUNCHKEY + apps.get(i).activityInfo.name);
				// resize app icon because some apps are loaded with over-sized
				// icons
				mAppActivityIcon.add(Utils.resizeDrawable(mContext.getResources(), apps.get(i).activityInfo.applicationInfo.loadIcon(pm), width, width));
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// populate app listpreferences
			mPrefSearchButtonFuncApps.setEntries(mAppActivity.toArray(new CharSequence[mAppActivity.size()]));
			mPrefSearchButtonFuncApps.setEntryValues(mAppLaunchKey.toArray(new CharSequence[mAppLaunchKey.size()]));
			mPrefSearchButtonFuncApps.setEntryIcons(mAppActivityIcon.toArray(new Drawable[mAppActivityIcon.size()]));
			mPrefSearchLongPressButtonFuncApps.setEntries(mAppActivity.toArray(new CharSequence[mAppActivity.size()]));
			mPrefSearchLongPressButtonFuncApps.setEntryValues(mAppLaunchKey.toArray(new CharSequence[mAppLaunchKey.size()]));
			mPrefSearchLongPressButtonFuncApps.setEntryIcons(mAppActivityIcon.toArray(new Drawable[mAppActivityIcon.size()]));

			// display preference summary
			mPrefSearchButtonFunc.setSummary(getSearchFuncSummary(mSearchKeycode, mSearchFuncApp));
			mPrefSearchButtonLongPressFunc.setSummary(getSearchFuncSummary(mSearchLongPressKeycode, mSearchLongPressFuncApp));

			// enable search button action preferences
			mPrefSearchButtonFunc.setEnabled(mShowSearch);
			mPrefSearchButtonLongPressFunc.setEnabled(mShowSearch);

			super.onPostExecute(result);
		}

	}

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// this is important because although the handler classes that read
		// these settings
		// are in the same package, they are executed in the context of the
		// hooked package
		getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
		addPreferencesFromResource(R.xml.preferences);

		// get screen width
		mScreenWidth = Utils.getScreenWidth(this);
		int maxWidth = (int) (mScreenWidth * 0.75);

		// for Lollipop, add extra padding for IME switcher
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) 
			mExtraPadding = Utils.getPackageResDimension(this, "navigation_extra_key_width", "dimen", XperiaNavBarButtons.CLASSNAME_SYSTEMUI);
		else
			mExtraPadding = 0;

		mPrefShowRecent = (CheckBoxPreference) findPreference("pref_show_recent");
		mPrefShowMenu = (CheckBoxPreference) findPreference("pref_show_menu");
		mPrefShowSearch = (CheckBoxPreference) findPreference("pref_show_search");
		mPrefReorder = (Preference) findPreference("pref_reorder");
		mPrefTheme = (Preference) findPreference("pref_theme");
		mPrefRestartSystemUI = (Preference) findPreference("pref_restart_systemui");
		mPrefUseAltMenu = (CheckBoxPreference) findPreference("pref_use_alt_menu");
		mPrefHints = (Preference) findPreference("pref_hints");
		mPrefLeftMargin = (Preference) findPreference("pref_left_margin");
		mPrefRightMargin = (Preference) findPreference("pref_right_margin");
		mPrefNavBarHeight = (Preference) findPreference("pref_navbar_height");
		mPrefSearchButtonFunc = (Preference) findPreference("pref_search_function");
		mPrefSearchButtonLongPressFunc = (Preference) findPreference("pref_search_longpress_function");
		mPrefSearchButtonFuncApps = (AppListPreference) findPreference("pref_search_function_apps");
		mPrefSearchLongPressButtonFuncApps = (AppListPreference) findPreference("pref_search_longpress_function_apps");

		reloadPreferences();

		mShowMenu = mSettings.isShowMenu();
		if (mShowMenu)
			mButtonsCount++;
		mShowSearch = mSettings.isShowSearch();
		if (mShowSearch)
			mButtonsCount++;
		mShowRecent = mSettings.isShowRecent();
		if (mShowRecent)
			mButtonsCount++;

		mCacheFolder = Utils.getSystemUICacheFolder(this);
		mDensityDpi = Utils.getDensityDpi(getResources());
		mCustomButtons = new CustomButtons(mCacheFolder, mDensityDpi, "custom_");

		// load available search button functions
		Collections.addAll(mSearchFuncArray, getResources().getStringArray(R.array.array_search_func_name));
		Collections.addAll(mSearchKeycodeValues, getResources().getStringArray(R.array.array_search_func_keycode));

		storePreferences();

		updatePreviewPanel();

		// this is important because although the handler classes that read
		// these settings
		// are in the same package, they are executed in the context of the
		// hooked package
		getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);

		// shared preference
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		mSearchKeycode = settings.getString("pref_search_function", String.valueOf(KeyEvent.KEYCODE_SEARCH));
		mSearchLongPressKeycode = settings.getString("pref_search_longpress_function", "-1");
		mSearchFuncApp = settings.getString("pref_search_function_apps", null);
		mSearchLongPressFuncApp = settings.getString("pref_search_longpress_function_apps", null);

		mPrefShowRecent.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue)
					mButtonsCount++;
				else
					mButtonsCount--;
				mShowRecent = (Boolean) newValue;
				mSettings.setShowRecent(mShowRecent);
				getPreferenceManager().getSharedPreferences().edit().putString("pref_order", mSettings.getOrderListString()).commit();
				updatePreviewPanel();
				return true;
			}
		});

		mPrefShowMenu.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue)
					mButtonsCount++;
				else
					mButtonsCount--;
				mShowMenu = (Boolean) newValue;
				mSettings.setShowMenu(mShowMenu);
				getPreferenceManager().getSharedPreferences().edit().putString("pref_order", mSettings.getOrderListString()).commit();
				updatePreviewPanel();
				return true;
			}
		});

		mPrefShowSearch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue)
					mButtonsCount++;
				else
					mButtonsCount--;
				mShowSearch = (Boolean) newValue;
				mSettings.setShowSearch(mShowSearch);
				getPreferenceManager().getSharedPreferences().edit().putString("pref_order", mSettings.getOrderListString()).commit();
				updatePreviewPanel();
				mPrefSearchButtonFunc.setEnabled(mShowSearch);
				mPrefSearchButtonLongPressFunc.setEnabled(mShowSearch);
				return true;
			}
		});

		mPrefReorder.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(XposedSettings.this, ReorderActivity.class);
				intent.putExtra("order_list", mSettings.getOrderListString());
				intent.putExtra("left_margin", mLeftMargin);
				intent.putExtra("right_margin", mRightMargin);
				startActivityForResult(intent, 1);
				return true;
			}
		});

		mPrefTheme.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(XposedSettings.this, ThemeActivity.class);
				intent.putExtra("usetheme", mUseTheme);
				intent.putExtra("themeid", mThemeId);
				intent.putExtra("themecolor", mThemeColor);
				startActivityForResult(intent, 2);
				return true;
			}
		});

		mPrefUseAltMenu.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mUseAltMenu = (Boolean) newValue;
				getPreferenceManager().getSharedPreferences().edit().putBoolean("pref_use_alt_menu", mUseAltMenu).commit();
				updatePreviewPanel();
				return true;
			}
		});

		mPrefSearchButtonFunc.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (Integer.valueOf(newValue.toString()) == XperiaNavBarButtons.KEYCODE_LAUNCH_APP) {
					mPrefSearchButtonFuncApps.show();
				} else {
					preference.setSummary(getSearchFuncSummary(newValue.toString(), null));
				}
				return true;
			}
		});
		mPrefSearchButtonFunc.setEnabled(false);

		mPrefSearchButtonFuncApps.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefSearchButtonFunc.setSummary(getSearchFuncSummary(String.valueOf(XperiaNavBarButtons.KEYCODE_LAUNCH_APP), newValue.toString()));
				return true;
			}
		});
		getPreferenceScreen().removePreference(mPrefSearchButtonFuncApps);

		mPrefSearchButtonLongPressFunc.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (Integer.valueOf(newValue.toString()) == XperiaNavBarButtons.KEYCODE_LAUNCH_APP) {
					mPrefSearchLongPressButtonFuncApps.show();
				} else {
					preference.setSummary(getSearchFuncSummary(newValue.toString(), null));
				}
				return true;
			}
		});
		mPrefSearchButtonLongPressFunc.setEnabled(false);

		mPrefSearchLongPressButtonFuncApps.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefSearchButtonLongPressFunc.setSummary(getSearchFuncSummary(String.valueOf(XperiaNavBarButtons.KEYCODE_LAUNCH_APP), newValue.toString()));
				return true;
			}
		});
		getPreferenceScreen().removePreference(mPrefSearchLongPressButtonFuncApps);

		mPrefLeftMargin.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mLeftMargin = (Integer) newValue;
				updatePreviewPanel();
				return true;
			}

		});
		((SeekBarPreference) mPrefLeftMargin).setMaxValue(maxWidth);

		mPrefRightMargin.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mRightMargin = (Integer) newValue;
				updatePreviewPanel();
				return true;
			}

		});
		((SeekBarPreference) mPrefRightMargin).setMaxValue(maxWidth);

		mPrefNavBarHeight.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mNavBarHeight = (Integer) newValue;
				updatePreviewPanel();
				return true;
			}

		});

		mPrefRestartSystemUI.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				try {
					Toast.makeText(XposedSettings.this, "Restarting SystemUI...", Toast.LENGTH_SHORT).show();

					final String pkgName = XposedSettings.this.getPackageName();
					final String pkgFilename = pkgName + "_preferences";
					final File prefFile = new File(Environment.getDataDirectory(), "data/" + pkgName + "/shared_prefs/" + pkgFilename + ".xml");
					Log.d("XposedSettings", prefFile.getAbsolutePath());

					// make shared preference world-readable
					Process sh = Runtime.getRuntime().exec("su", null, null);
					OutputStream os = sh.getOutputStream();
					os.write(("chmod 664 " + prefFile.getAbsolutePath()).getBytes("ASCII"));
					os.flush();
					os.close();
					try {
						sh.waitFor();
					} catch (Exception e) {
						e.printStackTrace();
					}

					// restart SystemUI process
					sh = Runtime.getRuntime().exec("su", null, null);
					os = sh.getOutputStream();
					os.write(("pkill com.android.systemui").getBytes("ASCII"));
					os.flush();
					os.close();
					try {
						sh.waitFor();
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			}
		});

		mPrefHints.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				(new ChangeLog(XposedSettings.this)).getFullLogDialog().show();
				return true;
			}
		});

		// load installed app by async task
		(new LoadInstalledAppTask(this)).execute();

		// display change log
		ChangeLog cl = new ChangeLog(this);
		if (cl.firstRun())
			cl.getFullLogDialog().show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
			case 1:
				String order = data.getStringExtra("order_list");
				getPreferenceManager().getSharedPreferences().edit().putString("pref_order", order).commit();
				mSettings = new ButtonSettings(this, order);
				updatePreviewPanel();
				break;
			case 2:
				mUseTheme = data.getBooleanExtra("usetheme", false);
				mThemeId = data.getStringExtra("themeid");
				mThemeColor = data.getStringExtra("themecolor");
				Editor editor = getPreferenceManager().getSharedPreferences().edit();
				editor.putBoolean("pref_usetheme", mUseTheme);
				editor.putString("pref_themeid", mThemeId);
				editor.putString("pref_themecolor", mThemeColor);
				editor.commit();
				mCustomButtons.refresh();
				updatePreviewPanel();
				break;
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void updatePreviewPanel() {
		int extraPadding = mShowMenu ? 0 : mExtraPadding;

		mButtonWidth = Math.round((float) (mScreenWidth - mLeftMargin - mRightMargin - extraPadding * 2) / (float) mButtonsCount);

		// get default navbar height
		int navBarHeight = (int) Utils.getNavBarHeight(getResources()) * mNavBarHeight / 100;

		LinearLayout panel = (LinearLayout) findViewById(R.id.previewPanel);
		if (navBarHeight > 0)
			panel.getLayoutParams().height = navBarHeight;

		ImageView leftMargin = (ImageView) panel.findViewById(R.id.left_margin);
		leftMargin.setLayoutParams(new LinearLayout.LayoutParams(mLeftMargin + extraPadding, LinearLayout.LayoutParams.MATCH_PARENT, 0.0f));

		ImageView rightMargin = (ImageView) panel.findViewById(R.id.right_margin);
		rightMargin.setLayoutParams(new LinearLayout.LayoutParams(mRightMargin + extraPadding, LinearLayout.LayoutParams.MATCH_PARENT, 0.0f));

		for (int i = 0; i < 5; i++) {
			ImageView iv = (ImageView) panel.findViewById(mIconId[i]);
			if (i < mButtonsCount) {
				boolean useAlt = false;

				iv.setLayoutParams(new LinearLayout.LayoutParams(mButtonWidth, LinearLayout.LayoutParams.MATCH_PARENT, 0.0f));
				if ("Menu".equals(mSettings.getButtonName(i)))
					useAlt = mUseAltMenu;

				Drawable drawable = null;
				if (mUseTheme) {
					int buttonResId = mThemeIcons.getIconResId(mThemeId, mThemeColor, mSettings.getButtonName(i), useAlt, false);
					if (buttonResId != -1)
						drawable = getResources().getDrawable(buttonResId);
					else {
						Bitmap bitmap = mCustomButtons.getBitmap(mSettings.getButtonName(i), useAlt, false);
						if (bitmap == null)
							drawable = mSettings.getButtonDrawable(i);
						else
							drawable = new BitmapDrawable(getResources(), bitmap);
					}
				} else {
					drawable = mSettings.getButtonDrawable(i);
				}
				iv.setImageDrawable(drawable);
				iv.setVisibility(View.VISIBLE);
			} else {
				iv.setVisibility(View.GONE);
			}
		}

		mPrefUseAltMenu.setEnabled(mUseTheme);
	}

	private void reloadPreferences() {
		SharedPreferences pref = getPreferenceManager().getSharedPreferences();

		mThemeId = pref.getString("pref_themeid", XperiaNavBarButtons.DEF_THEMEID);
		mThemeColor = pref.getString("pref_themecolor", XperiaNavBarButtons.DEF_THEMECOLOR);

		mSettings = new ButtonSettings(this, pref.getString("pref_order", null));

		mUseTheme = pref.getBoolean("pref_usetheme", false);
		mUseAltMenu = pref.getBoolean("pref_use_alt_menu", false);

		mLeftMargin = pref.getInt("pref_left_margin", 0);
		mRightMargin = pref.getInt("pref_right_margin", 0);

		mNavBarHeight = pref.getInt("pref_navbar_height", 100);
	}

	// store necesary info for user define theme to work
	private void storePreferences() {
		Editor editor = getPreferenceManager().getSharedPreferences().edit();

		editor.putString("pref_cache_folder", mCacheFolder);
		editor.putInt("pref_density_dpi", mDensityDpi);
		editor.commit();
	}

	private String getSearchFuncSummary(String keyCode, String launchKey) {
		String keys[];
		String component = null;
		if (launchKey != null) {
			keys = launchKey.split(DELIMITER_LAUNCHKEY);
			component = keys[1];
		}

		if (Integer.valueOf(keyCode) == XperiaNavBarButtons.KEYCODE_LAUNCH_APP) {
			int pos = mAppComponentName.indexOf(component);
			String activityName = (pos >= 0) ? mAppActivity.get(pos) : getResources().getString(R.string.text_unknown);
			return String.format(getResources().getString(R.string.summ_search_function2),
					mSearchFuncArray.get(mSearchKeycodeValues.indexOf(String.valueOf(XperiaNavBarButtons.KEYCODE_LAUNCH_APP))), activityName);
		} else
			return String.format(getResources().getString(R.string.summ_search_function), mSearchFuncArray.get(mSearchKeycodeValues.indexOf(keyCode)));
	}
}