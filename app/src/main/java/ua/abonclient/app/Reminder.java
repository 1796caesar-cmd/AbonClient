package ua.abonclient.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.Calendar;
import java.util.Locale;

/** Local monthly reminder; no account data or background site access. */
public class Reminder extends BroadcastReceiver {
    private static final String ACTION="ua.abonclient.app.METER_REMINDER", CHANNEL="meter_reminders";
    private static final int ID=7191;
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("meter_reminder",Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static void setEnabled(Context c,boolean enabled){prefs(c).edit().putBoolean("enabled",enabled).apply();schedule(c);if(!enabled){NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);n.cancel(ID);}}
    static int hour(Context c){return prefs(c).getInt("hour",9);}
    static int minute(Context c){return prefs(c).getInt("minute",0);}
    static String time(Context c){return String.format(Locale.ROOT,"%02d:%02d",hour(c),minute(c));}
    static void setTime(Context c,int h,int m){prefs(c).edit().putInt("hour",h).putInt("minute",m).apply();schedule(c);}
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,ID,new Intent(c,Reminder.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static Calendar target(Context c,Calendar now){Calendar t=(Calendar)now.clone();t.set(Calendar.DAY_OF_MONTH,t.getActualMaximum(Calendar.DAY_OF_MONTH)-2);t.set(Calendar.HOUR_OF_DAY,hour(c));t.set(Calendar.MINUTE,minute(c));t.set(Calendar.SECOND,0);t.set(Calendar.MILLISECOND,0);return t;}
    private static String month(Calendar t){return String.format(Locale.ROOT,"%04d-%02d",t.get(Calendar.YEAR),t.get(Calendar.MONTH)+1);}
    static void schedule(Context c){AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(a==null)return;PendingIntent pi=alarm(c);a.cancel(pi);if(!enabled(c))return;Calendar now=Calendar.getInstance(),t=target(c,now);if(!t.after(now)){if(now.get(Calendar.DAY_OF_MONTH)==t.get(Calendar.DAY_OF_MONTH)&&!month(now).equals(prefs(c).getString("sent","")))t.setTimeInMillis(now.getTimeInMillis()+1000);else{now.add(Calendar.MONTH,1);t=target(c,now);}}a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.getTimeInMillis(),pi);}
    @Override public void onReceive(Context c,Intent i){if(!enabled(c))return;if(ACTION.equals(i.getAction())){Calendar now=Calendar.getInstance();if(now.get(Calendar.DAY_OF_MONTH)>=now.getActualMaximum(Calendar.DAY_OF_MONTH)-2&&!month(now).equals(prefs(c).getString("sent",""))){if(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED){NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel(CHANNEL,"Нагадування про показники",NotificationManager.IMPORTANCE_DEFAULT));PendingIntent open=PendingIntent.getActivity(c,ID,new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Моя комуналка").setContentText("Не забудьте подати показники лічильника!").setAutoCancel(true).setContentIntent(open);if(Build.VERSION.SDK_INT<26)b.setDefaults(Notification.DEFAULT_ALL);n.notify(ID,b.build());prefs(c).edit().putString("sent",month(now)).apply();}}}schedule(c);}
}
