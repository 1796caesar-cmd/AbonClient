package ua.abonclient.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabColorSchemeParams;

public class MainActivity extends Activity {
    private static final String URL = "https://www.abon.com.ua/account/list";
    private static final int GREEN = Color.rgb(40,97,61), BG = Color.rgb(241,246,242), DARK = Color.rgb(28,46,35);
    private SharedPreferences prefs;
    private LinearLayout root;
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(24),dp(24),dp(24));
        scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        label("МОЯ КОМУНАЛКА", 13, GREEN, true);
        label("Особистий кабінет", 32, DARK, true);
        label("Комунальні послуги • Abon.com.ua", 16, DARK, false);
        space(28);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(1); panel.setPadding(dp(20),dp(20),dp(20),dp(20)); panel.setBackground(round(Color.WHITE,20)); root.addView(panel);
        TextView heading = new TextView(this); heading.setText("Мої особові рахунки"); heading.setTextColor(DARK); heading.setTextSize(22); heading.setTypeface(null,Typeface.BOLD); panel.addView(heading);
        TextView note = new TextView(this); note.setText("Переглядай нарахування, передавай показники води та перевіряй історію платежів у кабінеті."); note.setTextColor(DARK); note.setTextSize(16); note.setPadding(0,dp(12),0,dp(16)); panel.addView(note);
        Button open = new Button(this); open.setText("Відкрити кабінет"); open.setAllCaps(false); open.setTextColor(Color.WHITE); open.setTextSize(17); open.setBackground(round(GREEN,14)); panel.addView(open,new LinearLayout.LayoutParams(-1,dp(54))); open.setOnClickListener(v -> openCabinet());
        space(20);
        Switch auto = new Switch(this); auto.setText("Відкривати кабінет при запуску"); auto.setTextSize(16); auto.setTextColor(DARK); auto.setChecked(prefs.getBoolean("auto",false)); root.addView(auto,new LinearLayout.LayoutParams(-1,dp(58))); auto.setOnCheckedChangeListener((b,checked)->prefs.edit().putBoolean("auto",checked).apply());
        label("Авторизація через Google виконується на сайті у браузері. Додаток не отримує ваш пароль або файли cookies.", 14, DARK, false);
        space(24); label("Версія 1.0 • Тест авторизації", 13, GREEN, false);
        if(state == null && prefs.getBoolean("auto",false)) root.post(this::openCabinet);
    }
    private void openCabinet() {
        Uri uri = Uri.parse(URL);
        CustomTabsIntent tab = new CustomTabsIntent.Builder().setShowTitle(true).setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder().setToolbarColor(GREEN).build()).build();
        try { tab.launchUrl(this,uri); }
        catch(ActivityNotFoundException e) { try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch(ActivityNotFoundException ex) { Toast.makeText(this,"Встановіть браузер",Toast.LENGTH_LONG).show(); } }
    }
    private GradientDrawable round(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void label(String text,int size,int color,boolean bold) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); if(bold)t.setTypeface(null,Typeface.BOLD); t.setPadding(0,dp(5),0,dp(5)); root.addView(t); }
    private void space(int size) { root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size))); }
}
