package com.mouseformobile;

import android.accessibilityservice.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.graphics.*;
import android.os.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class MouseService extends AccessibilityService {
  public static MouseService instance;
  volatile ServerSocket server;
  volatile String token = "";
  Handler main = new Handler(Looper.getMainLooper());
  WindowManager wm; View cursor; WindowManager.LayoutParams params;
  float x=200, y=300; boolean gesturing;
  protected void onServiceConnected() { instance=this; wm=(WindowManager)getSystemService(WINDOW_SERVICE); }
  public void onAccessibilityEvent(AccessibilityEvent e) {}
  public void onInterrupt() { stopSharing(); }
  public void onDestroy() { stopSharing(); instance=null; super.onDestroy(); }
  void startSharing() {
    if(server != null) return;
    byte[] bytes=new byte[6]; new SecureRandom().nextBytes(bytes); StringBuilder b=new StringBuilder(); for(byte value:bytes) b.append(String.format("%02x",value)); token=b.toString();
    try { server=new ServerSocket(8765); } catch(IOException e) { android.widget.Toast.makeText(this,"Could not open port 8765",1).show(); return; }
    cursor=new View(this) { protected void onDraw(Canvas c) { Paint p=new Paint(3); p.setColor(Color.WHITE); c.drawCircle(14,14,10,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0xff087f8c); c.drawCircle(14,14,10,p); }};
    params=new WindowManager.LayoutParams(28,28,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
    params.gravity=Gravity.TOP|Gravity.LEFT; updateCursor(); wm.addView(cursor,params);
    final ServerSocket active=server;
    new Thread(() -> { while(!active.isClosed()) { try(Socket client=active.accept()) { client.setSoTimeout(1500); handle(client,active); } catch(IOException ignored) {} } },"mouse-server").start();
  }
  void stopSharing() { ServerSocket old=server; server=null; token=""; if(old!=null) try { old.close(); } catch(IOException ignored) {} if(cursor!=null) { wm.removeView(cursor); cursor=null; } }
  void handle(Socket client,ServerSocket active) throws IOException {
    BufferedReader r=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
    StringBuilder line=new StringBuilder(); int ch;
    while((ch=r.read())!=-1 && ch!='\n') { if(line.length()>=2048) return; line.append((char)ch); }
    String request=line.toString(); if(request.isEmpty()) return;
    // A tiny one-request protocol: token command arg arg. No screen data is collected.
    String[] parts=request.trim().split(" ");
    boolean ok=parts.length>=2 && !token.isEmpty() && java.security.MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.US_ASCII),token.getBytes(StandardCharsets.US_ASCII));
    if(ok) main.post(() -> { if(server==active) command(parts); });
    client.getOutputStream().write((ok?"OK\n":"DENIED\n").getBytes(StandardCharsets.US_ASCII));
  }
  void updateCursor() { params.x=Math.round(x)-14; params.y=Math.round(y)-14; if(cursor!=null && cursor.isAttachedToWindow()) wm.updateViewLayout(cursor,params); }
  void command(String[] p) {
    android.util.DisplayMetrics dm=new android.util.DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(dm);
    try {
      if(p[1].equals("move") && p.length==4) { float dx=Float.parseFloat(p[2]),dy=Float.parseFloat(p[3]); if(!Float.isFinite(dx)||!Float.isFinite(dy)) return; x=Math.max(1,Math.min(dm.widthPixels-2,x+dx)); y=Math.max(1,Math.min(dm.heightPixels-2,y+dy)); updateCursor(); }
      if(p[1].equals("tap")) gesture(x,y,x,y,60);
      if(p[1].equals("hold")) gesture(x,y,x,y,650);
      if(p[1].equals("scroll") && p.length==3) { float delta=Float.parseFloat(p[2]); if(!Float.isFinite(delta)) return; gesture(x,y,x,Math.max(1,Math.min(dm.heightPixels-2,y+Math.max(-500,Math.min(500,delta)))),220); }
      if(p[1].equals("back")) performGlobalAction(GLOBAL_ACTION_BACK);
      if(p[1].equals("home")) performGlobalAction(GLOBAL_ACTION_HOME);
    } catch(NumberFormatException ignored) {}
  }
  void gesture(float a,float b,float c,float d,long duration) {
    if(gesturing) return;
    Path path=new Path(); path.moveTo(a,b); if(a!=c||b!=d) path.lineTo(c,d);
    gesturing=true;
    boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build(),new GestureResultCallback() { public void onCompleted(GestureDescription g) { gesturing=false; } public void onCancelled(GestureDescription g) { gesturing=false; } },null);
    if(!accepted) gesturing=false;
  }
}
