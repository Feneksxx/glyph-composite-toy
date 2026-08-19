package com.sergi.glyphcomposite;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.BatteryManager;
import android.content.IntentFilter;
import com.nothing.ketchum.glyph.Glyph;
import com.nothing.ketchum.glyph.GlyphMatrixManager;
import com.nothing.ketchum.glyph.GlyphMatrixUtils;
import com.nothing.ketchum.glyph.GlyphMatrixFrame;
import com.nothing.ketchum.glyph.GlyphMatrixObject;

public class CompositeGlyphToyService extends android.app.Service {
    private GlyphMatrixManager manager; private final Handler handler = new Handler(Looper.getMainLooper()); private float phase;
    private final Runnable loop = new Runnable() { @Override public void run() { draw(); handler.postDelayed(this, 120); } };
    @Override public IBinder onBind(Intent intent) { init(); return null; }
    @Override public boolean onUnbind(Intent intent) { handler.removeCallbacks(loop); if (manager != null) manager.unInit(); return false; }
    private void init() { manager = GlyphMatrixManager.getInstance(getApplicationContext()); manager.init(new GlyphMatrixManager.Callback() { @Override public void onServiceConnected(ComponentName n) { manager.register(Glyph.DEVICE_23112); handler.post(loop); } @Override public void onServiceDisconnected(ComponentName n) {} }); }
    private void draw() {
        phase += 0.22f; int n = 25; Bitmap b = Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.WHITE); p.setStrokeWidth(1); p.setStyle(Paint.Style.STROKE);
        float cx=12.0f, cy=12.0f;
        // Outer music ring: always gently moving; audio input can be wired to phase/amplitude later.
        for (int i=0;i<24;i++) { double a=(Math.PI*2*i/24); float r=11.0f + (float)Math.sin(phase+i*.8f)*.5f; int x=Math.round(cx+(float)Math.cos(a)*r), y=Math.round(cy+(float)Math.sin(a)*r); if(x>=0&&x<n&&y>=0&&y<n) c.drawPoint(x,y,p); }
        // Charging arc from system battery state.
        Intent bat=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); int level=bat==null?0:bat.getIntExtra(BatteryManager.EXTRA_LEVEL,0); boolean charging=bat!=null && bat.getIntExtra(BatteryManager.EXTRA_STATUS,0)==BatteryManager.BATTERY_STATUS_CHARGING;
        if(charging){ p.setStyle(Paint.Style.STROKE); for(int i=0;i<Math.round(level/100f*16);i++){ double a=Math.PI*(1.0+i/16.0); c.drawPoint(Math.round(cx+(float)Math.cos(a)*9),Math.round(cy+(float)Math.sin(a)*9),p); } }
        // Compact HH:MM clock in the center.
        p.setStyle(Paint.Style.FILL); p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)); p.setTextSize(4.8f); String t=new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()); c.drawText(t,2.0f,14.0f,p);
        // Notification pulse on both sides for a short window.
        if(System.currentTimeMillis()-GlyphNotificationListener.lastNotificationAt < 10000 && ((System.currentTimeMillis()/240)%2==0)){ c.drawPoint(1,12,p); c.drawPoint(23,12,p); c.drawPoint(1,13,p); c.drawPoint(23,13,p); }
        GlyphMatrixObject obj=new GlyphMatrixObject.Builder().setImageSource(b).build(); GlyphMatrixFrame frame=new GlyphMatrixFrame.Builder().addTop(obj).build(this); manager.setMatrixFrame(frame.render());
    }
}
