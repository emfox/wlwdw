package com.wlwdw.gps1s;

import android.app.ListActivity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.widget.SimpleCursorAdapter;
import android.widget.ListAdapter;

public class MessageActivity extends ListActivity {
	
	private MsgdbHelper dbHelper = null;
	private SQLiteDatabase msgdb = null; 
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We'll define a custom screen layout here (the one shown above), but
        // typically, you could just use the standard ListActivity layout.
        //setContentView(R.layout.message);
        //获取数据库  
        dbHelper = new MsgdbHelper(this);  
        msgdb = dbHelper.getReadableDatabase();  
        
        Cursor mCursor = msgdb.query(MsgdbHelper.TABLE_NAME, 
        		new String[]{MsgdbHelper.COLUMN_ID,MsgdbHelper.COLUMN_FROM,MsgdbHelper.COLUMN_TIME,MsgdbHelper.COLUMN_CONTENT},
        		null, null, null, null,
        		MsgdbHelper.COLUMN_ID + " desc");

        // Now create a new list adapter bound to the cursor.
        // SimpleListAdapter is designed for binding to a Cursor.

		ListAdapter adapter = new SimpleCursorAdapter(
                this, // Context.
                R.layout.message_list,  // Specify the row template to use (here, two columns bound to the two retrieved cursor rows).
                mCursor,                                              // Pass in the cursor to bind to.
                new String[]{MsgdbHelper.COLUMN_FROM,MsgdbHelper.COLUMN_TIME,MsgdbHelper.COLUMN_CONTENT},           // Array of cursor columns to bind to.
                new int[] {R.id.textView1, R.id.textView2,R.id.textView3}, 0);  // Parallel array of which template objects to bind to those columns.

        // Bind to our new adapter.
        setListAdapter(adapter);
	}
	
	protected void onDestroy(){
		super.onDestroy();
		msgdb.close();
	}

}
