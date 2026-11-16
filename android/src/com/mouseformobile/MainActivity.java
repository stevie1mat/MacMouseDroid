package com.mouseformobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
  private static final int PAPER=0xfff6f5f1, INK=0xff181a18, MUTED=0xff636761, GREEN=0xff355e3b, LINE=0xffd3d5ce;
  private LinearLayout body;
  private final Handler handler=new Handler();
  private String renderedState="";
  private final Runnable monitor=new Runnable() { public void run() { refresh(); handler.postDelayed(this,750); } };

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(PAPER);
    getWindow().setNavigationBarColor(PAPER);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(PAPER);
    body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(20),dp(24),dp(24));
    scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
    // Target 35 draws edge-to-edge on Android 15; keep every control clear of system bars.
    scroll.setOnApplyWindowInsetsListener((v,insets) -> {
      v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
      return insets.consumeSystemWindowInsets();
    });
    setContentView(scroll); scroll.requestApplyInsets();
  }
  protected void onResume() { super.onResume(); renderedState=""; handler.removeCallbacks(monitor); monitor.run(); }
  protected void onPause() { handler.removeCallbacks(monitor); super.onPause(); }
  private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
  private TextView text(String value,int size,int color,boolean bold) {
    TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
    t.setFontFeatureSettings("kern"); t.setLineSpacing(dp(2),1);
    t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); return t;
  }
  private void space(LinearLayout parent,int height) { View v=new View(this); parent.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
  private void rule(LinearLayout parent) { View v=new View(this); v.setBackgroundColor(LINE); parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1))); }
  private GradientDrawable surface(int fill,int stroke) { GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(12)); if(stroke!=0) d.setStroke(dp(1),stroke); return d; }
  private Button button(String label,boolean primary,View.OnClickListener action) {
    Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    b.setTextColor(primary?0xffffffff:INK); b.setBackground(surface(primary?GREEN:PAPER,primary?0:INK));
    b.setMinHeight(dp(52)); b.setPadding(dp(18),dp(10),dp(18),dp(10)); b.setStateListAnimator(null);
    b.setOnClickListener(action); return b;
  }
  private void label(LinearLayout parent,String value) { TextView t=text(value,11,MUTED,true); t.setLetterSpacing(.14f); parent.addView(t); }
  private void openAccessibility() { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
  private void refresh() {
    MouseService service=MouseService.instance;
    boolean enabled=service!=null, sharing=enabled && service.server!=null;
    String addresses=sharing?addresses():"";
    String state=enabled+":"+sharing+":"+addresses+":"+(sharing?service.token:"");
    if(state.equals(renderedState)) return; renderedState=state;
    body.removeAllViews();
    LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(text("Mouse for Mobile",19,INK,true),new LinearLayout.LayoutParams(0,-2,1));
    Button settings=button("Settings",false,v -> openAccessibility());
    settings.setTextSize(12); settings.setContentDescription("Open accessibility settings");
    header.addView(settings,new LinearLayout.LayoutParams(-2,dp(48))); body.addView(header);
    space(body,36);
    if(sharing) { body.addView(text("Sharing is on",14,GREEN,true)); space(body,18); }
    TextView title=text(sharing?"Ready for\nyour Mac.":"Your mouse.\nOn your phone.",36,INK,true);
    title.setLineSpacing(0,1); body.addView(title); space(body,12);
    body.addView(text(sharing?"Enter these details in the Mac app.":"Control this phone from your Mac over Wi-Fi.",16,MUTED,false));
    space(body,28);
    if(sharing) {
      rule(body); space(body,20);
      detail("PHONE ADDRESS",addresses.isEmpty()?"No network address found":addresses);
      space(body,22); detail("PAIRING CODE",service.token); space(body,24); rule(body); space(body,20);
      label(body,"HOW TO CONTROL"); space(body,10);
      control("Click","Tap"); control("Right click","Back"); control("Scroll","Swipe");
      control("H","Home"); control("Space","Long press"); control("Esc on Mac","Release mouse");
    } else {
      LinearLayout setup=new LinearLayout(this); setup.setOrientation(1); setup.setPadding(dp(18),dp(20),dp(18),dp(20)); setup.setBackground(surface(PAPER,LINE));
      label(setup,"SETUP"); space(setup,22);
      setup.addView(text("01   Enable accessibility",18,INK,true)); space(setup,6);
      setup.addView(text(enabled?"Enabled. Your phone is ready.":"Required to move, tap and scroll.",14,MUTED,false)); space(setup,14);
      setup.addView(button(enabled?"Accessibility enabled":"Open settings",false,v -> openAccessibility()));
      space(setup,22); rule(setup); space(setup,22);
      setup.addView(text("02   Connect your Mac",18,INK,true)); space(setup,6);
      setup.addView(text("Use the same Wi-Fi on both devices.",14,MUTED,false)); body.addView(setup);
      space(body,20);
      Button help=button("Setting blocked?",false,v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
      body.addView(help); space(body,8);
      body.addView(text("In App info, open the menu and choose Allow restricted settings.",13,MUTED,false));
    }
    Space flexible=new Space(this); body.addView(flexible,new LinearLayout.LayoutParams(1,dp(32),1));
    Button main=button(sharing?"Stop sharing":"Start sharing",!sharing,v -> {
      MouseService current=MouseService.instance;
      if(current!=null) { if(current.server!=null) current.stopSharing(); else current.startSharing(); }
      renderedState=""; refresh();
    });
    if(!sharing && !enabled) { main.setEnabled(false); main.setAlpha(.45f); }
    body.addView(main,new LinearLayout.LayoutParams(-1,dp(54))); space(body,10);
    TextView footer=text(!enabled?"Enable accessibility to continue":"Trusted Wi-Fi only. Connection is not encrypted.",12,MUTED,false);
    footer.setGravity(Gravity.CENTER); body.addView(footer);
  }
  private void detail(String name,String value) {
    label(body,name); space(body,8);
    LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
    TextView data=text(value,21,INK,false); data.setTypeface(Typeface.MONOSPACE); data.setTextIsSelectable(true);
    row.addView(data,new LinearLayout.LayoutParams(0,-2,1));
    Button copy=button("Copy",false,v -> {
      ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(name,value));
      Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();
    });
    copy.setTextSize(12); copy.setContentDescription("Copy "+name.toLowerCase(Locale.ROOT));
    row.addView(copy,new LinearLayout.LayoutParams(-2,dp(48))); body.addView(row);
  }
  private void control(String key,String action) {
    LinearLayout row=new LinearLayout(this); row.setPadding(0,dp(10),0,dp(10));
    row.addView(text(key,14,INK,true),new LinearLayout.LayoutParams(0,-2,1));
    row.addView(text(action,14,MUTED,false),new LinearLayout.LayoutParams(0,-2,1)); body.addView(row); rule(body);
  }
  private String addresses() {
    ArrayList<String> result=new ArrayList<>();
    try { for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces())) {
      if(!n.isUp() || n.isLoopback()) continue;
      for(InetAddress a:Collections.list(n.getInetAddresses())) if(a instanceof Inet4Address && !a.isLoopbackAddress()) result.add(a.getHostAddress()+":8765");
    } } catch(Exception ignored) {}
    return android.text.TextUtils.join("\n",result);
  }
}
