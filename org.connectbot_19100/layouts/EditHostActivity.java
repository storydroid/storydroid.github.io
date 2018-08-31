package org.connectbot;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.connectbot.HostEditorFragment.Listener;
import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.service.TerminalManager.TerminalBinder;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;

public class EditHostActivity extends AppCompatActivity implements Listener {
    private TerminalBridge mBridge;
    private HostBean mHost;
    private HostDatabase mHostDb;
    private boolean mIsCreating;
    private PubkeyDatabase mPubkeyDb;
    private MenuItem mSaveHostButton;
    private ServiceConnection mTerminalConnection;

    private static class CharsetHolder {
        private static Map<String, String> mData;
        private static boolean mInitialized = false;

        private CharsetHolder() {
        }

        public static Map<String, String> getCharsetData() {
            if (mData == null) {
                initialize();
            }
            return mData;
        }

        private static synchronized void initialize() {
            synchronized (CharsetHolder.class) {
                if (!mInitialized) {
                    mData = new HashMap();
                    for (Entry<String, Charset> entry : Charset.availableCharsets().entrySet()) {
                        Charset c = (Charset) entry.getValue();
                        if (c.canEncode() && c.isRegistered()) {
                            if (((String) entry.getKey()).startsWith("cp")) {
                                mData.put("CP437", "CP437");
                            }
                            mData.put(c.displayName(), entry.getKey());
                        }
                    }
                    mInitialized = true;
                }
            }
        }

        public static boolean isInitialized() {
            return mInitialized;
        }
    }

    public static Intent createIntentForExistingHost(Context context, long existingHostId) {
        Intent i = new Intent(context, EditHostActivity.class);
        i.putExtra("org.connectbot.existing_host_id", existingHostId);
        return i;
    }

    public static Intent createIntentForNewHost(Context context) {
        return createIntentForExistingHost(context, -1);
    }

    protected void onCreate(Bundle savedInstanceState) {
        HostBean hostBean;
        int i;
        super.onCreate(savedInstanceState);
        this.mHostDb = HostDatabase.get(this);
        this.mPubkeyDb = PubkeyDatabase.get(this);
        this.mTerminalConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                EditHostActivity.this.mBridge = ((TerminalBinder) service).getService().getConnectedBridge(EditHostActivity.this.mHost);
            }

            public void onServiceDisconnected(ComponentName name) {
                EditHostActivity.this.mBridge = null;
            }
        };
        long hostId = getIntent().getLongExtra("org.connectbot.existing_host_id", -1);
        this.mIsCreating = hostId == -1;
        if (this.mIsCreating) {
            hostBean = null;
        } else {
            hostBean = this.mHostDb.findHostById(hostId);
        }
        this.mHost = hostBean;
        ArrayList<String> pubkeyNames = new ArrayList();
        ArrayList<String> pubkeyValues = new ArrayList();
        TypedArray defaultPubkeyNames = getResources().obtainTypedArray(R.array.list_pubkeyids);
        for (i = 0; i < defaultPubkeyNames.length(); i++) {
            pubkeyNames.add(defaultPubkeyNames.getString(i));
        }
        TypedArray defaultPubkeyValues = getResources().obtainTypedArray(R.array.list_pubkeyids_value);
        for (i = 0; i < defaultPubkeyValues.length(); i++) {
            pubkeyValues.add(defaultPubkeyValues.getString(i));
        }
        for (CharSequence cs : this.mPubkeyDb.allValues("nickname")) {
            pubkeyNames.add(cs.toString());
        }
        for (CharSequence cs2 : this.mPubkeyDb.allValues("_id")) {
            pubkeyValues.add(cs2.toString());
        }
        setContentView((int) R.layout.activity_edit_host);
        if (((HostEditorFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container)) == null) {
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, HostEditorFragment.newInstance(this.mHost, pubkeyNames, pubkeyValues)).commit();
        }
        defaultPubkeyNames.recycle();
        defaultPubkeyValues.recycle();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        boolean z;
        getMenuInflater().inflate(this.mIsCreating ? R.menu.edit_host_activity_add_menu : R.menu.edit_host_activity_edit_menu, menu);
        this.mSaveHostButton = menu.getItem(0);
        if (this.mIsCreating) {
            z = false;
        } else {
            z = true;
        }
        setAddSaveButtonEnabled(z);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 16908332:
            case R.id.save:
                attemptSaveAndExit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onStart() {
        super.onStart();
        bindService(new Intent(this, TerminalManager.class), this.mTerminalConnection, 1);
        final HostEditorFragment fragment = (HostEditorFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (CharsetHolder.isInitialized()) {
            fragment.setCharsetData(CharsetHolder.getCharsetData());
        } else {
            new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... unused) {
                    CharsetHolder.initialize();
                    return null;
                }

                protected void onPostExecute(Void unused) {
                    fragment.setCharsetData(CharsetHolder.getCharsetData());
                }
            }.execute(new Void[0]);
        }
    }

    public void onStop() {
        super.onStop();
        unbindService(this.mTerminalConnection);
    }

    public void onValidHostConfigured(HostBean host) {
        this.mHost = host;
        if (this.mSaveHostButton != null) {
            setAddSaveButtonEnabled(true);
        }
    }

    public void onHostInvalidated() {
        this.mHost = null;
        if (this.mSaveHostButton != null) {
            setAddSaveButtonEnabled(false);
        }
    }

    public void onBackPressed() {
        attemptSaveAndExit();
    }

    private void attemptSaveAndExit() {
        if (this.mHost == null) {
            showDiscardDialog();
            return;
        }
        this.mHostDb.saveHost(this.mHost);
        if (this.mBridge != null) {
            this.mBridge.setCharset(this.mHost.getEncoding());
        }
        finish();
    }

    private void showDiscardDialog() {
        Builder builder = new Builder(this, R.style.AlertDialogTheme);
        builder.setMessage((int) R.string.discard_host_changes_message).setPositiveButton(R.string.discard_host_button, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                EditHostActivity.this.finish();
            }
        }).setNegativeButton(R.string.discard_host_cancel_button, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.show();
    }

    private void setAddSaveButtonEnabled(boolean enabled) {
        this.mSaveHostButton.setEnabled(enabled);
        this.mSaveHostButton.getIcon().setAlpha(enabled ? 255 : 130);
    }
}
