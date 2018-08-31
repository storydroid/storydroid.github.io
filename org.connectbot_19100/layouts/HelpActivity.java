package org.connectbot;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class HelpActivity extends AppCompatActivity {
    private LayoutInflater inflater = null;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView((int) R.layout.act_help);
        ((Button) findViewById(R.id.hints_button)).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                HelpActivity.this.startActivity(new Intent(HelpActivity.this, HintsActivity.class));
            }
        });
        this.inflater = LayoutInflater.from(this);
        ((Button) findViewById(R.id.shortcuts_button)).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                new Builder(HelpActivity.this, R.style.AlertDialogTheme).setView(HelpActivity.this.inflater.inflate(R.layout.dia_keyboard_shortcuts, null, false)).setTitle((int) R.string.keyboard_shortcuts).show();
            }
        });
        ((Button) findViewById(R.id.eula_button)).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                HelpActivity.this.startActivity(new Intent(HelpActivity.this, EulaActivity.class));
            }
        });
    }
}
