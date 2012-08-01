package com.lsg.app;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.lsg.app.TimeTable.TimeTableUpdater;

public class SetupAssistant extends Activity {
	Download down;
	private SharedPreferences prefs;
	private SharedPreferences.Editor edit;

	class CommonData {
		Context context;

		CommonData(Context c) {
			context = c;
		}

		public String[] updateSubjectList() {
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(context);
			String add = "";
			try {
				add = "&"
						+ URLEncoder.encode("date", "UTF-8")
						+ "="
						+ URLEncoder.encode(
								prefs.getString("subject_update_time", ""),
								"UTF-8");
			} catch (UnsupportedEncodingException e) {
				Log.d("encoding", e.getMessage());
			}
			String get = Functions.getData(Functions.SUBJECT_URL, context,
					true, add);
			if (!get.equals("networkerror") && !get.equals("loginerror")
					&& !get.equals("noupdate")) {
				try {
					JSONArray jArray = new JSONArray(get);
					int i = 0;
					SQLiteDatabase myDB = context.openOrCreateDatabase(
							Functions.DB_NAME, Context.MODE_PRIVATE, null);
					myDB.delete(Functions.DB_SUBJECT_TABLE, null, null); // clear
																			// subjectlist
					while (i < jArray.length() - 1) {
						JSONObject jObject = jArray.getJSONObject(i);
						ContentValues values = new ContentValues();
						values.put(Functions.DB_RAW_FACH,
								jObject.getString("kuerzel"));
						values.put(Functions.DB_FACH, jObject.getString("name"));
						myDB.insert(Functions.DB_SUBJECT_TABLE, null, values);
						i++;
					}
					JSONObject jObject = jArray.getJSONObject(i);
					String update_time = jObject.getString("update_time");
					SharedPreferences.Editor edit = prefs.edit();
					edit.putString("subject_update_time", update_time);
					edit.commit();
					myDB.close();
				} catch (JSONException e) {
					Log.d("json", e.getMessage());
					return new String[] { "json",
							context.getString(R.string.jsonerror) };
				}
			} else if (get.equals("networkerror"))
				return new String[] { "networkerror",
						context.getString(R.string.networkerror) };
			else if (get.equals("loginerror"))
				return new String[] { "loginerror",
						context.getString(R.string.loginerror) };
			return new String[] { "success", "" };
		}

		public boolean getClasses() {
			SQLiteDatabase myDB = context.openOrCreateDatabase(
					Functions.DB_NAME, Context.MODE_PRIVATE, null);
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(context);
			try {
				String get = Functions.getData(Functions.CLASS_URL, context,
						true, "");
				Log.d("classes", get);
				if (!get.equals("loginerror")) {
					JSONArray jArray = new JSONArray(get);
					int i = 0;
					myDB.delete(Functions.DB_CLASS_TABLE, null, null);
					while (i < jArray.length() - 1) {
						ContentValues vals = new ContentValues();
						vals.put(Functions.DB_CLASS, jArray.getString(i));
						myDB.insert(Functions.DB_CLASS_TABLE, null, vals);
						i++;
					}
					SharedPreferences.Editor editor = prefs.edit();
					editor.putString("class", jArray.getString(i));
					editor.commit();
					myDB.close();
				} else {
					myDB.close();
					return false;
				}
			} catch (Exception e) {
				myDB.close();
				Log.d("getClass()", e.getMessage());
			}
			return true;
		}
	}

	class CommonDataTask extends AsyncTask<Void, Void, String[]> {
		protected void onPreExecute() {
			super.onPreExecute();
			Functions.lockRotation(SetupAssistant.this);
			loading = ProgressDialog.show(SetupAssistant.this, "",
					SetupAssistant.this.getString(R.string.loading_common_data));
		}

		@Override
		protected String[] doInBackground(Void... params) {
			CommonData cd = new CommonData(SetupAssistant.this);
			boolean login = cd.getClasses();
			String res[] = cd.updateSubjectList();
			if (!login) {
				res[0] = "loginerror";
				res[1] = SetupAssistant.this.getString(R.string.loginerror);
			}
			return res;
		}

		protected void onPostExecute(final String[] res) {
			loading.cancel();
			if (!res[0].equals("success"))
				Toast.makeText(SetupAssistant.this, res[1], Toast.LENGTH_LONG).show();
			if (res[0].equals("loginerror")) {
				Intent intent;
				if (Functions.getSDK() >= 11)
					intent = new Intent(SetupAssistant.this, SettingsAdvanced.class);
				else
					intent = new Intent(SetupAssistant.this, Settings.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
				SetupAssistant.this.startActivity(intent);
			} else if (res[0].equals("success")) {
				if (prefs.getString(Functions.FULL_CLASS, "-1").equals("-1")
						|| !prefs.getString(Functions.FULL_CLASS, "").contains(
								prefs.getString(Functions.class_key, "-1"))) {
					SQLiteDatabase myDB = openOrCreateDatabase(
							Functions.DB_NAME, MODE_PRIVATE, null);
					myDB.delete(Functions.DB_TIME_TABLE, null, null);
					SQLiteStatement num_rows = myDB
							.compileStatement("SELECT COUNT(*) FROM "
									+ Functions.DB_CLASS_TABLE);
					long count = num_rows.simpleQueryForLong();
					num_rows.close();
					Cursor cur = myDB.query(Functions.DB_CLASS_TABLE,
							new String[] { Functions.DB_CLASS },
							Functions.DB_CLASS + " LIKE ?", new String[] { "%"
									+ prefs.getString("class", "")
											.toLowerCase() + "%" }, null, null,
							null);
					cur.moveToFirst();
					if (count == 1) {
						edit.putString(Functions.FULL_CLASS, cur.getString(cur
								.getColumnIndex(Functions.DB_CLASS)));
						edit.commit();
						myDB.close();
						cur.close();
					} else if (count > 1) {
						final CharSequence[] items = new CharSequence[cur
								.getCount()];
						int i = 0;
						while (i < cur.getCount()) {
							items[i] = cur.getString(cur
									.getColumnIndex(Functions.DB_CLASS));
							i++;
							cur.moveToNext();
						}
						AlertDialog.Builder builder = new AlertDialog.Builder(
								SetupAssistant.this);
						builder.setTitle(R.string.your_class);
						builder.setItems(items,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int item) {
										edit.putString(Functions.FULL_CLASS,
												(String) items[item]);
										edit.commit();
									}
								});
						AlertDialog alert = builder.create();
						alert.show();
						myDB.close();
						cur.close();
					}
				}
			}
			Functions.unlockRotation(SetupAssistant.this);
		}
	}

	class UpdateCheckTask extends AsyncTask<Void, Void, String[]> {
		protected void onPreExecute() {
			super.onPreExecute();
		}

		@Override
		protected String[] doInBackground(Void... params) {
			String get = Functions.getData(Functions.UPDATE_CHECK_URL
					+ getString(R.string.versionname), SetupAssistant.this, false, "");
			try {
				JSONObject jObject = new JSONObject(get);
				if (!jObject.getBoolean("act")) {
					return new String[] { "notact",
							jObject.getString("actversion"),
							jObject.getString("changelog") };
				} else
					return new String[] { "act" };
			} catch (JSONException e) {
				Log.d("json", e.getMessage());
				Log.d("asdf", e.getMessage());
			}
			return new String[] { "act" };
		}

		protected void onPostExecute(final String[] data) {
			if (data[0].equals("notact")) {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						SetupAssistant.this);
				builder.setMessage(
						getString(R.string.update_available) + '\n' + data[1]
								+ ": " + '\n' + data[2])
						.setCancelable(false)
						.setPositiveButton(getString(R.string.update),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										Toast.makeText(
												SetupAssistant.this,
												getString(R.string.downloading),
												Toast.LENGTH_LONG).show();
										if (Functions.getSDK() >= 11) { // could
																		// also
																		// be 9,
																		// but
																		// there
																		// are
																		// some
																		// failed
																		// downloads
																		// on
																		// sgs2
											down.download();
										} else {
											Intent intent = new Intent(
													Intent.ACTION_VIEW);
											intent.setData(Uri
													.parse(Functions.UPDATE_URL));
											startActivity(intent);
										}
									}
								})
						.setNegativeButton(getString(R.string.no_update),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.cancel();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}
		}
	}

	class LoginTest extends AsyncTask<Void, Void, String> {
		Context context;

		LoginTest(Context c) {
			context = c;
		}

		protected void onPreExecute() {
			super.onPreExecute();
			loading = ProgressDialog
					.show(context, null, "Teste Login-Daten...");
		}

		@Override
		protected String doInBackground(Void... params) {
			return Functions.getData(Functions.LOGIN_TEST_URL, context, true,
					"");
		}

		@Override
		protected void onPostExecute(String success) {
			super.onPostExecute(success);
			loading.cancel();
			if (success.equals("true")) {
				loginsuccess = true;
				step = 1;
				setup(step, false);
			} else if (success.equals("false"))
				setupUser();
			else {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						SetupAssistant.this);
				builder.setMessage(
						"Internet-Verbindung ist zum Setup erforderlich.")
						.setCancelable(true)
						.setNeutralButton("Ok",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										SetupAssistant.this.finish();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}
		}
	}

	class PersonData extends AsyncTask<Void, Void, String> {
		Context context;

		PersonData(Context c) {
			context = c;
		}

		protected void onPreExecute() {
			super.onPreExecute();
			loading = ProgressDialog.show(context, null, "Lade Daten...");
		}

		@Override
		protected String doInBackground(Void... params) {
			return Functions.getData(Functions.PERSON_DATA_URL, context, true,
					"");
		}

		@Override
		protected void onPostExecute(String data) {
			super.onPostExecute(data);
			loading.cancel();
			try {
				JSONObject jarr = new JSONObject(data);
				gender = jarr.getString("gender").charAt(0);
				religion = jarr.getString("religion");
				usr_class = jarr.getString("class");
				JSONArray json_cls = jarr.getJSONArray("classes");
				classes = new String[json_cls.length()];
				for (int i = 0; i < json_cls.length(); i++)
					classes[i] = json_cls.getString(i);
			} catch (Exception e) {
			}
			dataUser = prefs.getString("username", "");
			setVPlanData();
		}
	}

	class SendData extends AsyncTask<Void, Void, Boolean> {
		Context context;
		SharedPreferences prefs;

		SendData(Context c, SharedPreferences p) {
			context = c;
			prefs = p;
		}

		protected void onPreExecute() {
			super.onPreExecute();
			loading = ProgressDialog.show(context, null, "Lade Daten...");
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			String res = "";
			try {
				res = Functions
						.getData(
								Functions.PERSON_DATA_SEND_URL,
								context,
								true,
								"&"
										+ URLEncoder.encode("full_class",
												"UTF-8")
										+ "="
										+ URLEncoder.encode(prefs.getString(
												Functions.FULL_CLASS, ""),
												"UTF-8")
										+ "&"
										+ URLEncoder.encode("gender", "UTF-8")
										+ "="
										+ URLEncoder.encode(prefs.getString(
												Functions.GENDER, ""), "UTF-8")
										+ "&"
										+ URLEncoder
												.encode("religion", "UTF-8")
										+ "="
										+ URLEncoder.encode(prefs.getString(
												Functions.RELIGION, ""),
												"UTF-8"));
			} catch (Exception e) {
			}
			Log.d("res", res);
			return res.equals("success");
		}

		@Override
		protected void onPostExecute(Boolean parm) {
			super.onPostExecute(parm);
			loading.cancel();
			step += 1;
			setup(step, false);
		}
	}

	class TimeTableData extends AsyncTask<Void, Void, ArrayList<Integer[]>> {
		Context context;

		TimeTableData(Context c) {
			context = c;
		}

		protected void onPreExecute() {
			super.onPreExecute();
			loading = ProgressDialog.show(context, null, "Lade Daten...");
		}

		@Override
		protected ArrayList<Integer[]> doInBackground(Void... params) {
			ArrayList<Integer[]> conflicts = new ArrayList<Integer[]>();
			TimeTableUpdater upd = new TimeTableUpdater(SetupAssistant.this);
			upd.update();
			SQLiteDatabase myDB = context.openOrCreateDatabase(
					Functions.DB_NAME, Context.MODE_PRIVATE, null);
			for (int day = 0; day < 7; day++) {
				for (int hour = 0; hour < 12; hour++) {
					SQLiteStatement stmt = myDB.compileStatement("SELECT COUNT(*) FROM "
							+ Functions.DB_TIME_TABLE + " WHERE "
							+ Functions.DB_DAY + "=? AND " + Functions.DB_HOUR
							+ "=? AND " + Functions.DB_RAW_FACH + "!=? AND "
							+ Functions.DB_RAW_FACH + "!=? AND "
							+ Functions.DB_RAW_FACH + "!=? AND "
							+ Functions.DB_CLASS + " LIKE ?");
					stmt.bindString(1, Integer.valueOf(day).toString());
					stmt.bindString(2, Integer.valueOf(hour).toString());
					stmt.bindString(3, prefs.getString(Functions.GENDER, "m")
							.equals("m") ? "Sw" : "Sm");
					stmt.bindString(6, "%" + prefs.getString(Functions.FULL_CLASS, "").substring(0, 2) + "%" + prefs.getString(Functions.FULL_CLASS, "").substring(2, 3) + "%");
					if (prefs.getString(Functions.RELIGION, "").equals(
							Functions.KATHOLISCH)) {
						stmt.bindString(4, Functions.EVANGELISCH);
						stmt.bindString(5, Functions.ETHIK);
					} else if (prefs.getString(Functions.RELIGION, "").equals(
							Functions.EVANGELISCH)) {
						stmt.bindString(4, Functions.KATHOLISCH);
						stmt.bindString(5, Functions.ETHIK);
					} else {
						stmt.bindString(4, Functions.EVANGELISCH);
						stmt.bindString(5, Functions.KATHOLISCH);
					}
					long count = stmt.simpleQueryForLong();
					if (count > 1) {
						/*
						 * SQLiteStatement stmt =
						 * myDB.compileStatement("UPDATE " +
						 * Functions.DB_TIME_TABLE + " SET " +
						 * Functions.DB_DISABLED + " =?");
						 */
						Cursor c = myDB.query(
								Functions.DB_TIME_TABLE,
								new String[] { Functions.DB_RAW_FACH,
										Functions.DB_FACH,
										Functions.DB_RAW_LEHRER },
								Functions.DB_HOUR + "=? AND "
										+ Functions.DB_DAY + "=? AND "
										+ Functions.DB_CLASS + " LIKE ?",
								new String[] {
										Integer.valueOf(hour).toString(),
										Integer.valueOf(day).toString(),
										"%"
												+ prefs.getString(
														Functions.FULL_CLASS,
														"").substring(0, 2)
												+ "%"
												+ prefs.getString(
														Functions.FULL_CLASS,
														"").substring(2, 3)
												+ "%" }, null, null, null);
						c.moveToFirst();
						while (c.moveToNext()) {
							stmt = myDB.compileStatement("INSERT INTO "
									+ Functions.EXCLUDE_TABLE + " ("
									+ Functions.DB_TEACHER + ", "
									+ Functions.DB_RAW_FACH + ", "
									+ Functions.DB_FACH + ", "
									+ Functions.DB_HOUR + ", "
									+ Functions.DB_DAY
									+ ") VALUES (?, ?, ?, ?, ?)");
							stmt.bindString(1, c.getString(c.getColumnIndex(Functions.DB_RAW_LEHRER)));
							stmt.bindString(2, c.getString(c.getColumnIndex(Functions.DB_RAW_FACH)));
							stmt.bindString(3, c.getString(c.getColumnIndex(Functions.DB_FACH)));
							stmt.bindLong(4, hour);
							stmt.bindLong(5, day);
							stmt.execute();
							Log.d("rawfach", c.getString(c.getColumnIndex(Functions.DB_RAW_FACH)));
						}
						conflicts.add(new Integer[] { day, hour });
						/*SQLiteStatement rmstmt = myDB
								.compileStatement("UPDATE "
										+ Functions.DB_TIME_TABLE + " SET "
										+ Functions.DB_DISABLED + "=? WHERE "
										+ Functions.DB_DAY + "=? AND "
										+ Functions.DB_HOUR + "=? AND "
										+ Functions.DB_CLASS + "=?");
						rmstmt.bindLong(1, 1);
						rmstmt.bindLong(2, day);
						rmstmt.bindLong(3, hour);
						rmstmt.bindString(4, prefs.getString(Functions.FULL_CLASS, ""));
						rmstmt.execute();*/
					}
				}
			}
			myDB.close();
			return conflicts;
		}

		@Override
		protected void onPostExecute(ArrayList<Integer[]> conflicts) {
			loading.cancel();
			super.onPostExecute(conflicts);
			setupTimeTableConflicts(conflicts);
		}
	}

	private ProgressDialog loading;
	private static int step = 0;
	private static boolean loginsuccess = false;
	private static char gender = '0';
	private static String religion = null;
	private static String dataUser = null;
	private static String[] classes;
	private static String usr_class;
	private static ArrayList<Integer[]> timetable_conflicts;
	private static ArrayList<RadioGroup> timetable_conflicts_rg;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Functions.init(this);
		Functions.setTheme(false, false, this);
		Functions.setupDB(this);
		getWindow().setBackgroundDrawableResource(R.layout.background);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		edit = prefs.edit();
		if(!prefs.getBoolean(Functions.IS_LOGGED_IN, false))
			setup(step, false);
		else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Wollen sie den Setup-Assistenten wirklich noch einmal ausführen?")
			       .setCancelable(false)
			       .setPositiveButton("Ja", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			                setup(step, false);
			           }
			       })
			       .setNegativeButton("Nein", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			                finish();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
		}
		/*
		 * setContentView(R.layout.main_nav);
		 * 
		 * UpdateCheckTask upcheck = new UpdateCheckTask(); upcheck.execute();
		 * 
		 * prefs = PreferenceManager.getDefaultSharedPreferences(this); edit =
		 * prefs.edit(); if(prefs.getBoolean("updatevplanonstart", false)) {
		 * UpdateBroadcastReceiver.VPupdate upd = new
		 * UpdateBroadcastReceiver.VPupdate(this); upd.start(); }
		 */
		Functions.setAlarm(this);
		Intent testAC2DM = new Intent("com.google.android.c2dm.intent.REGISTER");
		if (startService(testAC2DM) == null) {
			Log.i(Functions.TAG, "c2dm not available; disabling");
			edit.putBoolean("disableAC2DM", true);
			edit.putBoolean("useac2dm", false);
			edit.commit();
		}/*
		 * 
		 * if(Functions.getSDK() >= 9) down = new Download(lsgapp.this);
		 */
	}

	public void setup(int step, boolean force) {
		if (!loginsuccess)
			step = 0;
		switch (step) {
		case 0:
			if (!force)
				testUser();
			else
				setupUser();
			break;
		case 1:
			setupVPlan();
			break;
		case 2:
			setupTimeTable();
			break;
		case 3:
		}
	}

	public void testUser() {
		setTitle("LSGäpp Setup");
		if (!prefs.getString("username", "").equals("")) {
			LoginTest test = new LoginTest(this);
			test.execute();
		} else
			setupUser();
	}

	public void setupUser() {
		setContentView(R.layout.setup_user);
		((EditText) findViewById(R.id.username)).setText(prefs.getString(
				"username", ""));
		((EditText) findViewById(R.id.password)).setText(prefs.getString(
				"password", ""));
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		((EditText) findViewById(R.id.password)).setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
	                next(null);
	            }
				return false;
			}
			
		});
	}

	public void setupVPlan() {
		setContentView(R.layout.setup_vplan);
		if (dataUser == null
				|| !dataUser.equals(prefs.getString("username", ""))) {
			PersonData pd = new PersonData(this);
			pd.execute();
		} else
			setVPlanData();
	}

	public void setVPlanData() {
		if (prefs.getBoolean("disableAC2DM", false)) {
			CheckBox chk = (CheckBox) findViewById(R.id.push_check);
			chk.setChecked(false);
			chk.setVisibility(View.GONE);
		}
		RadioGroup genderrg = (RadioGroup) findViewById(R.id.gendergroup);
		if (gender == 'm')
			genderrg.check(R.id.male);
		else
			genderrg.check(R.id.female);
		RadioGroup religionrg = (RadioGroup) findViewById(R.id.religiongroup);
		if (religion.equals("K"))
			religionrg.check(R.id.catholic);
		else if (religion.equals("Ev"))
			religionrg.check(R.id.protestant);
		else if (religion.equals("Eth"))
			religionrg.check(R.id.ethics);
		RadioButton[] rb = new RadioButton[classes.length];
		for (int i = 0; i < classes.length; i++) {
			rb[i] = new RadioButton(this);
			rb[i].setText(classes[i]);
			rb[i].setId(i);
			if (classes[i].equals(usr_class))
				rb[i].toggle();
		}
		RadioGroup classrg = (RadioGroup) findViewById(R.id.classgroup);
		for (int i = 0; i < classes.length; i++)
			classrg.addView(rb[i], i);
	}

	public void setupTimeTable() {
		setContentView(R.layout.setup_timetable);
		TimeTableData ttd = new TimeTableData(this);
		ttd.execute();
	}

	public void setupTimeTableConflicts(ArrayList<Integer[]> conflicts) {
		Integer[] conflict = new Integer[2];
		timetable_conflicts = conflicts;
		timetable_conflicts_rg = new ArrayList<RadioGroup>();
		SQLiteDatabase myDB = openOrCreateDatabase(Functions.DB_NAME,
				Context.MODE_PRIVATE, null);
		for (int i = 0; i < conflicts.size(); i++) {
			conflict = conflicts.get(i);

			String[] selectionArgs = new String[6];
			selectionArgs[0] = Integer.valueOf(conflict[0]).toString();
			selectionArgs[1] = Integer.valueOf(conflict[1]).toString();
			selectionArgs[2] = prefs.getString(Functions.GENDER, "m").equals(
					"m") ? "Sw" : "Sm";
			if (prefs.getString(Functions.RELIGION, "").equals(
					Functions.KATHOLISCH)) {
				selectionArgs[3] = Functions.EVANGELISCH;
				selectionArgs[4] = Functions.ETHIK;
			} else if (prefs.getString(Functions.RELIGION, "").equals(
					Functions.EVANGELISCH)) {
				selectionArgs[3] = Functions.KATHOLISCH;
				selectionArgs[4] = Functions.ETHIK;
			} else {
				selectionArgs[3] = Functions.EVANGELISCH;
				selectionArgs[4] = Functions.KATHOLISCH;
			}
			selectionArgs[5] = "%" + prefs.getString(Functions.FULL_CLASS, "").substring(0, 2) + "%" + prefs.getString(Functions.FULL_CLASS, "").substring(2, 3) + "%";
			Cursor c = myDB.query(Functions.DB_TIME_TABLE, new String[] {
					Functions.DB_RAW_FACH, Functions.DB_ROWID,
					Functions.DB_FACH, Functions.DB_LEHRER }, Functions.DB_DAY
					+ "=? AND " + Functions.DB_HOUR + "=? AND "
					+ Functions.DB_RAW_FACH + "!=? AND "
					+ Functions.DB_RAW_FACH + "!=? AND "
					+ Functions.DB_RAW_FACH + "!=? AND " + Functions.DB_CLASS + " LIKE ?", selectionArgs, null, null,
					null);
			c.moveToFirst();
			RadioGroup rg = new RadioGroup(this);
			TextView tv = new TextView(this);
			tv.setText(getResources().getStringArray(R.array.days)[conflict[0]]
					+ ", " + Integer.valueOf(conflict[1] + 1).toString()
					+ ". Stunde");
			tv.setTextAppearance(this, android.R.attr.textAppearanceMedium);
			((LinearLayout) findViewById(R.id.timetable_conflicts_placeholder))
					.addView(tv);
			boolean showNone;
			if (conflict[1] + 1 < 7)
				showNone = false;
			else
				showNone = true;
			for (int ii = 0; ii < c.getCount(); ii++) {
				if (showNone) {
					try {
						if (!c.getString(
								c.getColumnIndex(Functions.DB_RAW_FACH))
								.substring(0, 3).equals("INT"))
							showNone = false;
					} catch (IndexOutOfBoundsException e) {
						showNone = false;
					}
				}
				RadioButton option = new RadioButton(this);
				option.setText(c.getString(c.getColumnIndex(Functions.DB_FACH))
						+ " bei " + c.getString(c.getColumnIndex(Functions.DB_LEHRER)));
				option.setId(c.getInt(c.getColumnIndex(Functions.DB_ROWID)));
				if (ii == 0)
					option.setChecked(true);
				rg.addView(option);
				c.moveToNext();
				if (ii + 1 == c.getCount() && showNone) {
					option = new RadioButton(this);
					option.setText("Kein Unterricht für mich!");
					option.setId(0);
					rg.addView(option);
				}
			}
			c.close();
			((LinearLayout) findViewById(R.id.timetable_conflicts_placeholder))
					.addView(rg);
			timetable_conflicts_rg.add(rg);
		}
		myDB.close();
	}

	public void next(View v) {
		switch (step) {
		case 0:
			edit.putString("username",
					(((EditText) findViewById(R.id.username))).getText()
							.toString());
			edit.putString("password",
					(((EditText) findViewById(R.id.password))).getText()
							.toString());
			edit.commit();
			InputMethodManager imm = (InputMethodManager)getSystemService(
				      Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(((EditText) findViewById(R.id.password)).getWindowToken(), 0);
			testUser();
			break;
		case 1:
			RadioGroup religionrg = (RadioGroup) findViewById(R.id.religiongroup);
			String religion;
			switch (religionrg.getCheckedRadioButtonId()) {
			case R.id.catholic:
				religion = Functions.KATHOLISCH;
				break;
			case R.id.protestant:
				religion = Functions.EVANGELISCH;
				break;
			case R.id.ethics:
			default:
				religion = Functions.ETHIK;
				break;
			}
			edit.putString(Functions.RELIGION, religion);
			RadioGroup genderrg = (RadioGroup) findViewById(R.id.gendergroup);
			edit.putString(Functions.GENDER, (genderrg
					.getCheckedRadioButtonId() == R.id.female) ? "w" : "m");
			RadioGroup classrg = (RadioGroup) findViewById(R.id.classgroup);
			edit.putString(Functions.FULL_CLASS,
					classes[classrg.getCheckedRadioButtonId()]);
			CheckBox chk = (CheckBox) findViewById(R.id.push_check);
			edit.putBoolean("useac2dm", chk.isChecked());
			if (chk.isChecked())
				Functions.registerAC2DM(this);
			edit.commit();
			SendData sd = new SendData(this, prefs);
			sd.execute();
			break;
		case 2:
			SQLiteDatabase myDB = openOrCreateDatabase(Functions.DB_NAME,
					MODE_PRIVATE, null);
			for (int i = 0; i < timetable_conflicts_rg.size(); i++) {
				RadioGroup rg = timetable_conflicts_rg.get(i);
				Log.d("id", Integer.valueOf(rg.getCheckedRadioButtonId())
						.toString());
				if (rg.getCheckedRadioButtonId() != 0) {
					Cursor c = myDB.query(Functions.DB_TIME_TABLE,
							new String[] { Functions.DB_HOUR, Functions.DB_DAY,
									Functions.DB_RAW_FACH,
									Functions.DB_RAW_LEHRER },
							Functions.DB_ROWID + "=?", new String[] { Integer
									.valueOf(rg.getCheckedRadioButtonId())
									.toString() }, null, null, null);
					c.moveToFirst();
					Log.d("count", Integer.valueOf(c.getCount()).toString());
					SQLiteStatement stmt = myDB.compileStatement("DELETE FROM "
							+ Functions.EXCLUDE_TABLE + " WHERE "
							+ Functions.DB_HOUR + "=? AND " + Functions.DB_DAY
							+ "=? AND " + Functions.DB_RAW_FACH + "=? AND "
							+ Functions.DB_TEACHER + "=?");
					stmt.bindString(1,
							c.getString(c.getColumnIndex(Functions.DB_HOUR)));
					stmt.bindString(2,
							c.getString(c.getColumnIndex(Functions.DB_DAY)));
					stmt.bindString(3, c.getString(c
							.getColumnIndex(Functions.DB_RAW_FACH)));
					stmt.bindString(4, c.getString(c
							.getColumnIndex(Functions.DB_RAW_LEHRER)));
					stmt.execute();
					Log.d("hour",
							c.getString(c.getColumnIndex(Functions.DB_HOUR)));
					Log.d("day",
							c.getString(c.getColumnIndex(Functions.DB_DAY)));
					Log.d("subject", c.getString(c
							.getColumnIndex(Functions.DB_RAW_FACH)));
					Log.d("teacher", c.getString(c
							.getColumnIndex(Functions.DB_RAW_LEHRER)));
				}
			}
			myDB.close();
			edit.putBoolean(Functions.IS_LOGGED_IN, true);
			edit.commit();
			startActivity(new Intent(this, TimeTable.class));
			break;
		}
		// step += 1;
		// setup(step);
		// startActivity(new Intent(this, TimeTable.class));
	}

	public void previous(View v) {
		step -= 1;
		setup(step, true);
	}

	public void button_press(View v) {
		Log.d("text", (String) ((Button) v).getText());
		if (((String) ((Button) v).getText())
				.equals(getString(R.string.timetable))) {
			Intent timetable = new Intent(this, TimeTable.class);
			startActivity(timetable);
		}
		if (((String) ((Button) v).getText())
				.equals(getString(R.string.vplan_short))) {
			Intent timetable = new Intent(this, VPlan.class);
			startActivity(timetable);
		}
		if (((String) ((Button) v).getText())
				.equals(getString(R.string.events))) {
			Intent timetable = new Intent(this, Events.class);
			startActivity(timetable);
		}
		if (((String) ((Button) v).getText())
				.equals(getString(R.string.smvblog))) {
			Intent timetable = new Intent(this, SMVBlog.class);
			startActivity(timetable);
		}
		if (((String) ((Button) v).getText())
				.equals(getString(R.string.settings))) {
			Intent settings;
			if (Functions.getSDK() >= 11)
				settings = new Intent(this, SettingsAdvanced.class);
			else
				settings = new Intent(this, Settings.class);
			startActivity(settings);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		// inflater.inflate(R.menu.lsgapp, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.help:
			Intent help = new Intent(this, HelpAbout.class);
			help.putExtra(Functions.helpabout, Functions.help);
			startActivity(help);
			return true;
		case R.id.about:
			Intent about = new Intent(this, HelpAbout.class);
			about.putExtra(Functions.helpabout, Functions.about);
			startActivity(about);
			return true;
		case R.id.refresh:
			CommonDataTask cdt = new CommonDataTask();
			cdt.execute();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		/*
		 * if(Functions.getSDK() >= 9)
		 * unregisterReceiver(down.downloadReceiver);
		 */
	}

	@Override
	public void onResume() {
		super.onResume();/*
						 * if(Functions.getSDK() >= 9) { IntentFilter
						 * intentFilter = new
						 * IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE
						 * ); registerReceiver(down.downloadReceiver,
						 * intentFilter); }
						 * 
						 * if(!prefs.getString("username", "").equals("")) {
						 * if(!prefs.getBoolean("disableAC2DM", false) &&
						 * !prefs.getBoolean("ac2dm_chosen", false)) {
						 * AlertDialog.Builder builder = new
						 * AlertDialog.Builder(lsgapp.this);
						 * builder.setMessage(getString(R.string.enable_AC2DM))
						 * .setCancelable(false)
						 * .setPositiveButton(getString(R.string.ok), new
						 * DialogInterface.OnClickListener() { public void
						 * onClick(DialogInterface dialog, int id) {
						 * edit.putBoolean("useac2dm", true);
						 * edit.putBoolean("ac2dm_chosen", true); edit.commit();
						 * Functions.registerAC2DM(lsgapp.this);
						 * dialog.cancel(); } })
						 * .setNegativeButton(getString(R.string.no), new
						 * DialogInterface.OnClickListener() { public void
						 * onClick(DialogInterface dialog, int id) {
						 * edit.putBoolean("useac2dm", false);
						 * edit.putBoolean("ac2dm_chosen", true); edit.commit();
						 * dialog.cancel(); } }); AlertDialog alert =
						 * builder.create(); builder.setCancelable(false);
						 * alert.show(); }
						 * if(prefs.getString(Functions.RELIGION,
						 * "-1").equals("-1")) { final CharSequence[] items =
						 * getResources().getStringArray(R.array.religion);
						 * AlertDialog.Builder builder = new
						 * AlertDialog.Builder(this);
						 * builder.setTitle(R.string.choose_religion);
						 * builder.setItems(items, new
						 * DialogInterface.OnClickListener() { public void
						 * onClick(DialogInterface dialog, int item) {
						 * switch(item) { case 0:
						 * edit.putString(Functions.RELIGION,
						 * Functions.KATHOLISCH); break; case 1:
						 * edit.putString(Functions.RELIGION,
						 * Functions.EVANGELISCH); break; default:
						 * edit.putString(Functions.RELIGION, Functions.ETHIK);
						 * break; } edit.commit(); dialog.cancel(); } });
						 * AlertDialog alert = builder.create();
						 * builder.setCancelable(false); alert.show(); }
						 * if(prefs.getString(Functions.GENDER,
						 * "-1").equals("-1")) { final CharSequence[] items =
						 * getResources().getStringArray(R.array.gender);
						 * AlertDialog.Builder builder = new
						 * AlertDialog.Builder(this);
						 * builder.setTitle(R.string.your_gender);
						 * builder.setItems(items, new
						 * DialogInterface.OnClickListener() { public void
						 * onClick(DialogInterface dialog, int item) {
						 * switch(item) { case 0:
						 * edit.putString(Functions.GENDER, "m"); break; case 1:
						 * edit.putString(Functions.GENDER, "w"); break;
						 * default: edit.putString(Functions.GENDER, "-1");
						 * break; } edit.commit(); dialog.cancel(); } });
						 * builder.setCancelable(false); AlertDialog alert =
						 * builder.create(); builder.setCancelable(false);
						 * alert.show(); } } SQLiteDatabase myDB =
						 * openOrCreateDatabase(Functions.DB_NAME,
						 * Context.MODE_PRIVATE, null); SQLiteStatement num_rows
						 * = myDB.compileStatement("SELECT COUNT(*) FROM " +
						 * Functions.DB_CLASS_TABLE); long count =
						 * num_rows.simpleQueryForLong(); if(count == 0) {
						 * CommonDataTask cdt = new CommonDataTask();
						 * cdt.execute(); } myDB.close(); num_rows.close();
						 */
	}
}