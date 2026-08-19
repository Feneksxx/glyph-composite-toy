package com.sergi.glyphcomposite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,48,32,32);
        TextView title = new TextView(this); title.setText("Glyph Composite\nЧасы · музыка · зарядка · уведомления"); title.setTextSize(22); root.addView(title);
        Button permissions = new Button(this); permissions.setText("Разрешить уведомления"); permissions.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))); root.addView(permissions);
        Button manager = new Button(this); manager.setText("Открыть Glyph Toys"); manager.setOnClickListener(v -> { Intent i = new Intent(); i.setComponent(new ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")); startActivity(i); }); root.addView(manager);
        setContentView(root);
    }
}
