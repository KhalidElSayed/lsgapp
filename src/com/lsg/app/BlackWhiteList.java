package com.lsg.app;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class BlackWhiteList extends ListActivity {
	private SQLiteDatabase myDB;
	private String table;
	private SimpleCursorAdapter adap;
	private Cursor c;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Functions.setTheme(false, true, this);
		
		myDB = openOrCreateDatabase(Functions.DB_NAME, MODE_PRIVATE, null);
		
		Bundle data = getIntent().getExtras();
		String type = data.getString(Functions.BLACKWHITELIST);
		if(type.equals(Functions.BLACKLIST)) {
			setTitle(getString(R.string.blacklist));
			table = new String(Functions.EXCLUDE_TABLE);
		}
		else {
			setTitle(getString(R.string.whitelist));
			table = new String(Functions.INCLUDE_TABLE);
		}
		
		c = myDB.query(table, new String[] {Functions.DB_ROWID, Functions.DB_FACH, Functions.DB_NEEDS_SYNC},
				null, null, null, null, null);
		
		adap = new SimpleCursorAdapter(this, R.layout.main_listitem, c, new String[] {Functions.DB_FACH},
				new int[] {R.id.main_textview});
		setListAdapter(adap);
		
		registerForContextMenu(getListView());
	}
	public void updateList() {
		c = myDB.query(table, new String[] {Functions.DB_ROWID, Functions.DB_FACH, Functions.DB_NEEDS_SYNC},
				null, null, null, null, null);
		adap.changeCursor(c);
	}
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		TextView title_text_view = (TextView) info.targetView.findViewById(R.id.main_textview);
		String title = new StringBuffer(title_text_view.getText()).toString();
		menu.setHeaderTitle(title);
		menu.add(0, 0, Menu.NONE, R.string.list_remove);
	}
	@Override
	public boolean onContextItemSelected(final MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		final CharSequence title = ((TextView) info.targetView.findViewById(R.id.main_textview)).getText();
		int menuItemIndex = item.getItemId();
		if(menuItemIndex == 0) {
			myDB.delete(table, Functions.DB_FACH + " LIKE ?", new String[] {(String) title});
			updateList();
		}
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
        case android.R.id.home:
            // app icon in action bar clicked; go home
            Intent intent = new Intent(this, Settings.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}
}