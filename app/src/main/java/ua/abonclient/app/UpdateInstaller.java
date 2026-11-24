package ua.abonclient.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

final class UpdateInstaller {
    private static final int ALLOW_INSTALL = 1301, INSTALL_APK = 1302;
    private static final long MAX_BYTES = 100L * 1024 * 1024;
    private final Activity activity;
    private final File folder, apk, partial;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private boolean busy;
    private ProgressDialog progress;
    UpdateInstaller(Activity activity) {
        this.activity = activity;
        folder = new File(activity.getCacheDir(), "updates");
        apk = new File(folder, "AbonClient.apk");
        partial = new File(folder, "AbonClient.part");
        partial.delete();
        if (apk.exists()) {
            try { PackageInfo p = activity.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
                if (p == null || p.versionCode <= installed().versionCode) apk.delete();
            } catch (Exception e) { apk.delete(); }
        }
    }
    private PackageInfo installed() throws Exception {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
    }
    private boolean alive() { return !activity.isFinishing() && !activity.isDestroyed(); }
    private void notice(String s) { if (alive()) Toast.makeText(activity,s,Toast.LENGTH_LONG).show(); }
    void download(String link, int expectedCode) {
        if (busy) { notice("Оновлення вже завантажується"); return; }
        if (!link.startsWith("https://github.com/1796caesar-cmd/AbonClient/releases/download/")) return;
        if (!folder.isDirectory() && !folder.mkdirs()) { notice("Не вдалося створити папку оновлення"); return; }
        busy=true; cancelled.set(false); apk.delete(); partial.delete();
        progress=new ProgressDialog(activity);
        progress.setTitle("Завантаження оновлення");
        progress.setMessage("Завантажуємо APK з GitHub…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100); progress.setIndeterminate(true); progress.setCancelable(false);
        progress.setButton(ProgressDialog.BUTTON_NEGATIVE,"Скасувати",(d,w)->cancelled.set(true));
        progress.show();
        new Thread(()-> {
            HttpURLConnection c=null;
            String error=null;
            try {
                URL url=new URL(link);
                for(int redirects=0;;redirects++) {
                    if(!"https".equals(url.getProtocol())||redirects>8) throw new IOException("Небезпечне перенаправлення");
                    c=(HttpURLConnection)url.openConnection();
                    c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000);c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent","AbonClient");
                    int status=c.getResponseCode();
                    if(status>=300&&status<400){String next=c.getHeaderField("Location");c.disconnect();c=null;if(next==null)throw new IOException("Немає адреси файлу");url=new URL(url,next);continue;}
                    if(status!=200)throw new IOException("HTTP "+status);break;
                }
                long total=-1;try{total=Long.parseLong(c.getHeaderField("Content-Length"));}catch(Exception ignored){}
                if(total>MAX_BYTES)throw new IOException("Файл завеликий");
                long count=0;int last=-1;
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(partial)){
                    byte[] b=new byte[32768];int n;
                    while((n=in.read(b))!=-1){if(cancelled.get())throw new InterruptedIOException();count+=n;if(count>MAX_BYTES)throw new IOException("Файл завеликий");out.write(b,0,n);
                        if(total>0){int pct=(int)Math.min(100,count*100/total);if(pct!=last){last=pct;activity.runOnUiThread(()->{if(alive()&&progress!=null){progress.setIndeterminate(false);progress.setProgress(pct);}});}}
                    }
                }
                if(cancelled.get())throw new InterruptedIOException();
                if(count==0||(total>0&&total!=count))throw new IOException("Неповний файл");
                if(!partial.renameTo(apk))throw new IOException("Не вдалося зберегти APK");
                PackageInfo current=installed();PackageInfo incoming=activity.getPackageManager().getPackageArchiveInfo(apk.getPath(),PackageManager.GET_SIGNATURES);
                if(incoming==null||!current.packageName.equals(incoming.packageName)||incoming.versionCode!=expectedCode||incoming.versionCode<=current.versionCode||incoming.signatures==null||!Arrays.equals(current.signatures,incoming.signatures))throw new IOException("Версія або підпис APK не відповідають додатку");
            } catch(Exception e){error=cancelled.get()?"Завантаження скасовано":"Не вдалося завантажити оновлення: "+(e.getMessage()==null?"помилка":e.getMessage());apk.delete();}
            finally{if(c!=null)c.disconnect();partial.delete();}
            final String result=error;
            activity.runOnUiThread(()->{busy=false;if(progress!=null){progress.dismiss();progress=null;}if(!alive())return;if(result!=null){notice(result);return;}if(cancelled.get()){apk.delete();return;}requestInstall();});
        },"abon-update").start();
    }
    private void requestInstall(){
        if(!apk.isFile()){notice("Файл оновлення відсутній");return;}
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Дозвіл на встановлення").setMessage("Дозвольте встановлення програм із цього джерела. Після повернення відкриється вікно встановлення оновлення.").setNegativeButton("Скасувати",(d,w)->apk.delete()).setPositiveButton("Налаштування",(d,w)->{try{activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),ALLOW_INSTALL);}catch(Exception e){notice("Не вдалося відкрити налаштування");}}).show();return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",apk);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);intent.setData(uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.putExtra(Intent.EXTRA_RETURN_RESULT,true);activity.startActivityForResult(intent,INSTALL_APK);
        }catch(Exception e){notice("Не вдалося відкрити встановлення APK");}
    }
    void onActivityResult(int request,int result){
        if(request==ALLOW_INSTALL){if(Build.VERSION.SDK_INT<26||activity.getPackageManager().canRequestPackageInstalls())requestInstall();else notice("Встановлення не дозволено");}
        else if(request==INSTALL_APK){apk.delete();}
    }
    void destroy(){cancelled.set(true);if(progress!=null){progress.dismiss();progress=null;}}
}
