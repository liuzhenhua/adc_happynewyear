package info.liuqy.adc.happynewyear;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class SendListActivity extends ListActivity {
	
    static final String KEY_TO = "TO";
    static final String KEY_SMS = "SMS";

    static final String SENT_ACTION = "SMS_SENT_ACTION";
    static final String DELIVERED_ACTION = "SMS_DELIVERED_ACTION";
    static final String EXTRA_IDX = "contact_adapter_idx";
    static final String EXTRA_TONUMBER = "sms_to_number";
    static final String EXTRA_SMS = "sms_content";
    
    static final String SMS_INDEX_ACTION = "sms_index_action";
    static final String INDEX_KEY = "index_key";
    
    private static final int HAPPYNEWYEAR_ID = 1;

    private static final String DB_NAME = "data";
    private static final int DB_VERSION = 2;
    
    private static final String TBL_NAME = "sms";
    static final String FIELD_TO = "to_number";
    static final String FIELD_SMS = "sms";
    static final String KEY_ROWID = "_id";
    static final String FIELD_SEND_STATE = "send_state";
    
    private static final String UNSEND = "UNSEND";
//    private static final int SENDING = 0x2;
//    private static final int HASSEND = 0x3;
//    private static final int DEVLIVER = 0x4;
//    private static final int UNDEVLIVER = 0x5;
    private static final String SEDNSUCCESSED = "Send successed!";
    
    //[<TO, number>,<SMS, sms>]
    List<Map<String, String>> smslist = new LinkedList<Map<String, String>>();
    SimpleAdapter adapter;

    static BroadcastReceiver smsSentReceiver = null;
	static BroadcastReceiver smsDeliveredReceiver = null;
	static BroadcastReceiver smsIndexReceiver = null;
    
    SQLiteOpenHelper dbHelper = null;
    SQLiteDatabase db = null;
    
    private int maxSms = 0;
    private int deliveredSms = 0;
    private int sendSms_count = 0;
    
    private boolean isSendingSms = false;
    
    ListView smsListView = null;
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sendlist);
        
        smsListView = getListView();
        initdb();
        createReceivers();
        
        adapter = new SimpleAdapter(this, smslist,
                android.R.layout.simple_list_item_2,
                new String[]{KEY_TO, KEY_SMS},
                new int[]{android.R.id.text1, android.R.id.text2});
        this.setListAdapter(adapter);
        handleIntent();
        
        if (smslist.size() == 0)  //FIXME need a better judge if from notification
            loadFromDatabase();
    }
	
	public void handleIntent() {
        Bundle data = this.getIntent().getExtras();
        if (data != null) {
            Bundle sendlist = data.getParcelable(HappyNewYearActivity.SENDLIST);
            
            String cc = data.getString(HappyNewYearActivity.CUSTOMER_CARER);
            String tmpl = data.getString(HappyNewYearActivity.SMS_TEMPLATE);
            
            tmpl = tmpl.replaceAll("\\{FROM\\}", cc);
            
            for (String n : sendlist.keySet()) {
                String sms = tmpl.replaceAll("\\{TO\\}", sendlist.getString(n));
                Map<String, String> rec = new Hashtable<String, String>();
                rec.put(KEY_TO, n);
                rec.put(KEY_SMS, sms);
                //´æÈëÊý¾Ý¿â
                saveToDatabase(n, sms , UNSEND);
                smslist.add(rec);
                adapter.notifyDataSetChanged();
            }
            maxSms = sendlist.size();
        }

	}

	public void sendSms(View v) {
	    
	    if(!isSendingSms)
	    {
	        String[] columns = {FIELD_TO}; 
	        String selection = "send_state=?";        
	        String[] selectionArgs={SEDNSUCCESSED};
	        Cursor phoneNums = db.query(TBL_NAME,columns, selection,selectionArgs,null,null,null);
	        
	        while (phoneNums.moveToNext() && !phoneNums.isNull(0))
	        {
	              String phoneNum = phoneNums.getString(0);
	              smslist.remove(phoneNum);
	        }
	        isSendingSms = true;
	        Thread sendSmsThread = new Thread(new SendSMS(this,smslist));
	        sendSmsThread.start();
	    }
        
    }

	@Override
	protected void onStart() {
		super.onStart();
		// Question for you: where is the right place to register receivers?
		registerReceivers();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		// Question for you: where is the right place to unregister receivers?
		unregisterReceivers();
	}
	
	protected void createReceivers() {
		if (smsSentReceiver == null)
			smsSentReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
				    sendSms_count ++;
				    if(sendSms_count == maxSms)
				    {
				        isSendingSms = false;
				    }
					int idx = intent.getIntExtra(EXTRA_IDX, -1);
					String toNum = intent.getStringExtra(EXTRA_TONUMBER);
					String sms = intent.getStringExtra(EXTRA_SMS);
					
					int succ = getResultCode();
					if (succ == Activity.RESULT_OK) {
					    smsListView.getChildAt(idx).setBackgroundColor(getResources().getColor(R.color.yellow));
					    adapter.notifyDataSetChanged();
						// TODO better notification
						Toast.makeText(SendListActivity.this,
								"Sent to " + toNum + " OK!", Toast.LENGTH_SHORT)
								.show();
					} else {
					    smsListView.getChildAt(idx).setBackgroundColor(getResources().getColor(R.color.red));
					    adapter.notifyDataSetChanged();
					}
				}
			};

		if (smsDeliveredReceiver == null)
			smsDeliveredReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
				    
					int idx = intent.getIntExtra(EXTRA_IDX, -1);
					String toNum = intent.getStringExtra(EXTRA_TONUMBER);
					String sms = intent.getStringExtra(EXTRA_SMS);
					int succ = getResultCode();
					if (succ == Activity.RESULT_OK) {
					    smsListView.getChildAt(idx).setBackgroundColor(getResources().getColor(R.color.green));
					    adapter.notifyDataSetChanged();
					    deliveredSms ++;
						// TODO better notification
						//Toast.makeText(SendListActivity.this, "Delivered to " + toNum + " OK!", Toast.LENGTH_SHORT).show();
						updateDataBase(toNum, sms, SEDNSUCCESSED);
						notifySuccessfulDelivery("Delivered to " + toNum + " OK!", sms);
					} else {
						// TODO
					}
				}
			};
			if (smsIndexReceiver == null)
			    smsIndexReceiver = new BroadcastReceiver() {
	                @Override
	                public void onReceive(Context context, Intent intent) {
	                    
	                    int idx = intent.getIntExtra(INDEX_KEY, -1);
	                    smsListView.getChildAt(idx).setBackgroundColor(getResources().getColor(R.color.blue));
	                    adapter.notifyDataSetChanged();
	                }
	            };
	}

	protected void registerReceivers() {
		this.registerReceiver(smsSentReceiver, new IntentFilter(SENT_ACTION));
		this.registerReceiver(smsDeliveredReceiver, new IntentFilter(DELIVERED_ACTION));
		this.registerReceiver(smsIndexReceiver, new IntentFilter(SMS_INDEX_ACTION));
	}
	
	protected void unregisterReceivers() {
		this.unregisterReceiver(smsSentReceiver);
		this.unregisterReceiver(smsDeliveredReceiver);
		this.unregisterReceiver(smsIndexReceiver);
	}
	
    public void notifySuccessfulDelivery(String title, String text) {
        
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        
        int icon = R.drawable.ic_launcher;
        CharSequence tickerText = "HappyNewYear";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon,tickerText,when);
        
        
        if(maxSms > deliveredSms)
        {
            notification.flags = Notification.FLAG_ONGOING_EVENT;
        }
        else if(maxSms == deliveredSms)
        {
            
            notification.flags = Notification.FLAG_AUTO_CANCEL;
            
        }
        
        RemoteViews contentView = new RemoteViews(this.getPackageName(), R.layout.custom_notification);
        contentView.setTextViewText(R.id.rate,  deliveredSms+ "/" + maxSms);  
        contentView.setProgressBar(R.id.progress, maxSms, deliveredSms, false); 
        notification.contentView = contentView;
        
//        Context context = getApplicationContext();
//        CharSequence contentTitle = title;
//        CharSequence contentText = text;
        
        Intent notificationIntent = new Intent(this, SendListActivity.class); //if click, then open SendListActivity
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.contentIntent = contentIntent;
        
        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.defaults |= Notification.DEFAULT_VIBRATE;
        notification.defaults |= Notification.DEFAULT_LIGHTS;
        
        mNotificationManager.notify(HAPPYNEWYEAR_ID, notification);
        
    }

    protected void initdb() {
        dbHelper = new SQLiteOpenHelper(this, DB_NAME, null, DB_VERSION) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("create table sms (_id integer primary key autoincrement, " +
                        "to_number text not null, sms text not null ,send_state text)");
            }
            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
                //TODO on DB upgrade
            }
            
        };
        
        db = dbHelper.getWritableDatabase();
    }
    
    protected void loadFromDatabase() {
        Cursor cur = db.query(TBL_NAME, new String[]{KEY_ROWID, FIELD_TO, FIELD_SMS},
                null, null, null, null, null);

        while (cur.moveToNext()) {
            String toNumber = cur.getString(cur.getColumnIndex(FIELD_TO));
            String sms = cur.getString(cur.getColumnIndex(FIELD_SMS));
            Map<String, String> rec = new Hashtable<String, String>();
            rec.put(KEY_TO, toNumber);
            rec.put(KEY_SMS, sms);
            smslist.add(rec);
        }
        cur.close();
        
        adapter.notifyDataSetChanged();
    }
    protected void updateDataBase(String toNum, String sms, String sendState)
    {
        ContentValues values = new ContentValues();
        values.put(FIELD_TO, toNum); //FIXME string constant
        values.put(FIELD_SMS, sms);
        values.put(FIELD_SEND_STATE, sendState);
        
        String whereClause = "to_number=?";        
        String[] whereArgs={toNum};
        db.update(TBL_NAME,values,whereClause,whereArgs);
    }
    protected void saveToDatabase(String toNum, String sms, String sendState) {
        ContentValues values = new ContentValues();
        values.put(FIELD_TO, toNum); //FIXME string constant
        values.put(FIELD_SMS, sms);
        values.put(FIELD_SEND_STATE,sendState);
        db.insert(TBL_NAME, null, values);
    }
    
}
