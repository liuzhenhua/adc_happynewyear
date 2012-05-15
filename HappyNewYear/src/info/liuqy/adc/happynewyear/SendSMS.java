package info.liuqy.adc.happynewyear;

import java.util.List;
import java.util.Map;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;

public class SendSMS implements Runnable
{
    static final String KEY_TO = "TO";
    static final String KEY_SMS = "SMS";

    static final String SENT_ACTION = "SMS_SENT_ACTION";
    static final String DELIVERED_ACTION = "SMS_DELIVERED_ACTION";
    static final String EXTRA_IDX = "contact_adapter_idx";
    static final String EXTRA_TONUMBER = "sms_to_number";
    static final String EXTRA_SMS = "sms_content";
    
    private List<Map<String, String>> smslist = null;
    private Context context = null;
    private final String SMS_INDEX_ACTION = "sms_index_action";
    private final String INDEX_KEY = "index_key";
    public SendSMS(Context context , List<Map<String, String>> smslist)
    {
        this.smslist = smslist;
        this.context = context;
    }

    @Override
    public void run()
    {
        SmsManager sender = SmsManager.getDefault();
        if (sender == null) {
            // TODO toast error msg
        }
        else
        {

        for (int idx = 0; idx < smslist.size(); idx++) {
            
            Intent intent = new Intent(SMS_INDEX_ACTION);
            intent.putExtra(INDEX_KEY, idx);
            context.sendBroadcast(intent);
            
            Map<String, String> rec = smslist.get(idx);
            String toNumber = rec.get(KEY_TO);
            String sms = rec.get(KEY_SMS);
            
            Log.i("betterman", "toNumber:" + toNumber + " SMS:" + sms);

            // SMS sent pending intent
            Intent sentActionIntent = new Intent(SENT_ACTION);
            sentActionIntent.putExtra(EXTRA_IDX, idx);
            sentActionIntent.putExtra(EXTRA_TONUMBER, toNumber);
            sentActionIntent.putExtra(EXTRA_SMS, sms);
            PendingIntent sentPendingIntent = PendingIntent.getBroadcast(
                    context, 0, sentActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            // SMS delivered pending intent
            Intent deliveredActionIntent = new Intent(DELIVERED_ACTION);
            deliveredActionIntent.putExtra(EXTRA_IDX, idx);
            deliveredActionIntent.putExtra(EXTRA_TONUMBER, toNumber);
            deliveredActionIntent.putExtra(EXTRA_SMS, sms);
            PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
                    context, 0, deliveredActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            //send
            sender.sendTextMessage(toNumber, null, sms, sentPendingIntent,deliveredPendingIntent);
            
            //每隔5秒发送一次短信
            try
            {
                Thread.sleep(5000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            }
        }

    }

}
