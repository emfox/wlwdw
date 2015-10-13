package com.wlwdw.gps1s;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

public class MessageActivity extends ListActivity {
	
	private MsgdbHelper dbHelper = null;
	private SQLiteDatabase msgdb = null; 
	public static boolean is_foreground = false;
	public static ListAdapter adapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We'll define a custom screen layout here (the one shown above), but
        // typically, you could just use the standard ListActivity layout.
        setContentView(R.layout.message);
        //获取数据库  
        dbHelper = new MsgdbHelper(this);  
        msgdb = dbHelper.getWritableDatabase();  
        RefreshMessages();
	}
	
	protected void onResume(){
		super.onResume();
		is_foreground = true;
	}
	
	protected void onPause(){
		super.onPause();
		is_foreground = false;
	}
	
	private void RefreshMessages(){
        Cursor mCursor = msgdb.query(MsgdbHelper.TABLE_NAME, 
        		new String[]{MsgdbHelper.COLUMN_ID,MsgdbHelper.COLUMN_FROM,MsgdbHelper.COLUMN_TIME,MsgdbHelper.COLUMN_CONTENT},
        		null, null, null, null,
        		MsgdbHelper.COLUMN_ID + " desc");

        // Now create a new list adapter bound to the cursor.
        // SimpleListAdapter is designed for binding to a Cursor.

		 adapter = new SimpleCursorAdapter(
                this, // Context.
                R.layout.message_list,  // Specify the row template to use (here, two columns bound to the two retrieved cursor rows).
                mCursor,                                              // Pass in the cursor to bind to.
                new String[]{MsgdbHelper.COLUMN_FROM,MsgdbHelper.COLUMN_TIME,MsgdbHelper.COLUMN_CONTENT},           // Array of cursor columns to bind to.
                new int[] {R.id.textView1, R.id.textView2,R.id.textView3}, 0);  // Parallel array of which template objects to bind to those columns.

        // Bind to our new adapter.
        setListAdapter(adapter);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		final long item_id = id;
		Cursor item = msgdb.query(MsgdbHelper.TABLE_NAME, 
        		new String[]{MsgdbHelper.COLUMN_FROM,MsgdbHelper.COLUMN_TIME,MsgdbHelper.COLUMN_CONTENT},
        		MsgdbHelper.COLUMN_ID + "=?", new String[] { String.valueOf(id) }, null, null,null);
		if (item != null)
	        item.moveToFirst();
		new AlertDialog.Builder(this)
		.setTitle(item.getString(0))
		.setMessage(item.getString(1) + "\n" + item.getString(2))
		.setPositiveButton("删除", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			    msgdb.delete(MsgdbHelper.TABLE_NAME, MsgdbHelper.COLUMN_ID + " = ?",
			            new String[] { String.valueOf(item_id) });
			    RefreshMessages();
			}
		})
		.setNegativeButton("返回", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		})
		.show();
	}
	
	protected void onDestroy(){
		super.onDestroy();
		msgdb.close();
	}

}
