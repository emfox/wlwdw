package com.wlwdw.gps1s;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MsgdbHelper extends SQLiteOpenHelper {  
	  
    /** 
     * 数据库名称 
     */  
    private static final String DATABASE_NAME = "msg.db";    
      
    /** 
     * 数据库版本 
     */  
    private static final int DATABASE_VERSION = 1;    
  
    /** 
     * 表格名称 
     */  
    public static final String TABLE_NAME = "message";  
      
    /** 
     * 列表一，_ID，自动增加 
     */  
    public static final String COLUMN_ID = "_id";  
      
    /** 
     * 列表二，名称 
     */  
    public static final String COLUMN_TIME = "time";  
    public static final String COLUMN_FROM = "sender";  
    public static final String COLUMN_CONTENT = "content";  
      
    public MsgdbHelper(Context context) {  
        super(context, DATABASE_NAME, null, DATABASE_VERSION);  
    }  
  
  
    @Override  
    public void onCreate(SQLiteDatabase db)  throws SQLException {  
        //创建表格  
        db.execSQL("CREATE TABLE IF NOT EXISTS "+ TABLE_NAME
        		+ "("+ COLUMN_ID +" INTEGER PRIMARY KEY AUTOINCREMENT,"
        		+ COLUMN_TIME +" TEXT,"
        		+ COLUMN_FROM +" TEXT,"
        		+ COLUMN_CONTENT +" TEXT NOT NULL);");  
    }  
  
    @Override  
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)  throws SQLException {  
        //删除并创建表格  
        db.execSQL("DROP TABLE IF EXISTS "+ TABLE_NAME+";");  
        onCreate(db);  
    }  
}  