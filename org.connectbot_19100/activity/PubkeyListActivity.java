package org.connectbot;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.crypto.PEMStructure;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.List;
import org.connectbot.bean.PubkeyBean;
import org.connectbot.service.TerminalManager;
import org.connectbot.service.TerminalManager.TerminalBinder;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

public class PubkeyListActivity extends AppCompatListActivity implements EventListener {
    private TerminalManager bound = null;
    protected ClipboardManager clipboard;
    private MenuItem confirmUse = null;
    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            PubkeyListActivity.this.bound = ((TerminalBinder) service).getService();
            PubkeyListActivity.this.updateList();
        }

        public void onServiceDisconnected(ComponentName className) {
            PubkeyListActivity.this.bound = null;
            PubkeyListActivity.this.updateList();
        }
    };
    private LayoutInflater inflater = null;
    private MenuItem onstartToggle = null;
    private List<PubkeyBean> pubkeys;

    private class PubkeyAdapter extends ItemAdapter {
        private final List<PubkeyBean> pubkeys;

        public PubkeyAdapter(Context context, List<PubkeyBean> pubkeys) {
            super(context);
            this.pubkeys = pubkeys;
        }

        public PubkeyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PubkeyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pubkey, parent, false));
        }

        public void onBindViewHolder(ItemViewHolder holder, int position) {
            PubkeyViewHolder pubkeyHolder = (PubkeyViewHolder) holder;
            PubkeyBean pubkey = (PubkeyBean) this.pubkeys.get(position);
            pubkeyHolder.pubkey = pubkey;
            if (pubkey == null) {
                Log.e("PubkeyAdapter", "Pubkey bean is null!");
                pubkeyHolder.nickname.setText("Error during lookup");
            } else {
                pubkeyHolder.nickname.setText(pubkey.getNickname());
            }
            if ("IMPORTED".equals(pubkey.getType())) {
                try {
                    String type;
                    PEMStructure struct = PEMDecoder.parsePEM(new String(pubkey.getPrivateKey()).toCharArray());
                    if (struct.pemType == 1) {
                        type = "RSA";
                    } else if (struct.pemType == 2) {
                        type = "DSA";
                    } else if (struct.pemType == 3) {
                        type = "EC";
                    } else if (struct.pemType == 4) {
                        type = "OpenSSH";
                    } else {
                        throw new RuntimeException("Unexpected key type: " + struct.pemType);
                    }
                    pubkeyHolder.caption.setText(String.format("%s unknown-bit", new Object[]{type}));
                } catch (IOException e) {
                    Log.e("CB.PubkeyListActivity", "Error decoding IMPORTED public key at " + pubkey.getId(), e);
                }
            } else {
                try {
                    pubkeyHolder.caption.setText(pubkey.getDescription(PubkeyListActivity.this.getApplicationContext()));
                } catch (Exception e2) {
                    Log.e("CB.PubkeyListActivity", "Error decoding public key at " + pubkey.getId(), e2);
                    pubkeyHolder.caption.setText(R.string.pubkey_unknown_format);
                }
            }
            if (PubkeyListActivity.this.bound == null) {
                pubkeyHolder.icon.setVisibility(8);
                return;
            }
            pubkeyHolder.icon.setVisibility(0);
            if (PubkeyListActivity.this.bound.isKeyLoaded(pubkey.getNickname())) {
                pubkeyHolder.icon.setImageState(new int[]{16842912}, true);
                return;
            }
            pubkeyHolder.icon.setImageState(new int[0], true);
        }

        public int getItemCount() {
            return this.pubkeys.size();
        }

        public long getItemId(int position) {
            return ((PubkeyBean) this.pubkeys.get(position)).getId();
        }
    }

    public class PubkeyViewHolder extends ItemViewHolder {
        public final TextView caption;
        public final ImageView icon;
        public final TextView nickname;
        public PubkeyBean pubkey;

        public PubkeyViewHolder(View v) {
            super(v);
            this.icon = (ImageView) v.findViewById(16908294);
            this.nickname = (TextView) v.findViewById(16908308);
            this.caption = (TextView) v.findViewById(16908309);
        }

        public void onClick(View v) {
            boolean loaded = PubkeyListActivity.this.bound != null && PubkeyListActivity.this.bound.isKeyLoaded(this.pubkey.getNickname());
            if (loaded) {
                PubkeyListActivity.this.bound.removeKey(this.pubkey.getNickname());
                PubkeyListActivity.this.updateList();
                return;
            }
            PubkeyListActivity.this.handleAddKey(this.pubkey);
        }

        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            boolean loaded;
            boolean z;
            boolean z2 = false;
            menu.setHeaderTitle(this.pubkey.getNickname());
            final boolean imported = "IMPORTED".equals(this.pubkey.getType());
            if (PubkeyListActivity.this.bound == null || !PubkeyListActivity.this.bound.isKeyLoaded(this.pubkey.getNickname())) {
                loaded = false;
            } else {
                loaded = true;
            }
            menu.add(loaded ? R.string.pubkey_memory_unload : R.string.pubkey_memory_load).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    if (loaded) {
                        PubkeyListActivity.this.bound.removeKey(PubkeyViewHolder.this.pubkey.getNickname());
                        PubkeyListActivity.this.updateList();
                    } else {
                        PubkeyListActivity.this.handleAddKey(PubkeyViewHolder.this.pubkey);
                    }
                    return true;
                }
            });
            PubkeyListActivity.this.onstartToggle = menu.add(R.string.pubkey_load_on_start);
            MenuItem access$200 = PubkeyListActivity.this.onstartToggle;
            if (this.pubkey.isEncrypted()) {
                z = false;
            } else {
                z = true;
            }
            access$200.setEnabled(z);
            PubkeyListActivity.this.onstartToggle.setCheckable(true);
            PubkeyListActivity.this.onstartToggle.setChecked(this.pubkey.isStartup());
            PubkeyListActivity.this.onstartToggle.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    PubkeyViewHolder.this.pubkey.setStartup(!PubkeyViewHolder.this.pubkey.isStartup());
                    PubkeyDatabase.get(PubkeyListActivity.this).savePubkey(PubkeyViewHolder.this.pubkey);
                    PubkeyListActivity.this.updateList();
                    return true;
                }
            });
            MenuItem copyPublicToClipboard = menu.add(R.string.pubkey_copy_public);
            if (imported) {
                z = false;
            } else {
                z = true;
            }
            copyPublicToClipboard.setEnabled(z);
            copyPublicToClipboard.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    try {
                        PubkeyListActivity.this.clipboard.setText(PubkeyUtils.convertToOpenSSHFormat(PubkeyUtils.decodePublic(PubkeyViewHolder.this.pubkey.getPublicKey(), PubkeyViewHolder.this.pubkey.getType()), PubkeyViewHolder.this.pubkey.getNickname()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            });
            MenuItem copyPrivateToClipboard = menu.add(R.string.pubkey_copy_private);
            if (!this.pubkey.isEncrypted() || imported) {
                z = true;
            } else {
                z = false;
            }
            copyPrivateToClipboard.setEnabled(z);
            copyPrivateToClipboard.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    try {
                        String data;
                        if (imported) {
                            data = new String(PubkeyViewHolder.this.pubkey.getPrivateKey());
                        } else {
                            data = PubkeyUtils.exportPEM(PubkeyUtils.decodePrivate(PubkeyViewHolder.this.pubkey.getPrivateKey(), PubkeyViewHolder.this.pubkey.getType()), null);
                        }
                        PubkeyListActivity.this.clipboard.setText(data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            });
            MenuItem changePassword = menu.add(R.string.pubkey_change_password);
            if (!imported) {
                z2 = true;
            }
            changePassword.setEnabled(z2);
            changePassword.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    final View changePasswordView = View.inflate(PubkeyListActivity.this, R.layout.dia_changepassword, null);
                    ((TableRow) changePasswordView.findViewById(R.id.old_password_prompt)).setVisibility(PubkeyViewHolder.this.pubkey.isEncrypted() ? 0 : 8);
                    new Builder(PubkeyListActivity.this, R.style.AlertDialogTheme).setView(changePasswordView).setPositiveButton(R.string.button_change, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String oldPassword = ((EditText) changePasswordView.findViewById(R.id.old_password)).getText().toString();
                            String password1 = ((EditText) changePasswordView.findViewById(R.id.password1)).getText().toString();
                            if (password1.equals(((EditText) changePasswordView.findViewById(R.id.password2)).getText().toString())) {
                                try {
                                    if (PubkeyViewHolder.this.pubkey.changePassword(oldPassword, password1)) {
                                        PubkeyDatabase.get(PubkeyListActivity.this).savePubkey(PubkeyViewHolder.this.pubkey);
                                        PubkeyListActivity.this.updateList();
                                        return;
                                    }
                                    new Builder(PubkeyListActivity.this, R.style.AlertDialogTheme).setMessage((int) R.string.alert_wrong_password_msg).setPositiveButton(17039370, null).create().show();
                                    return;
                                } catch (Exception e) {
                                    Log.e("CB.PubkeyListActivity", "Could not change private key password", e);
                                    new Builder(PubkeyListActivity.this, R.style.AlertDialogTheme).setMessage((int) R.string.alert_key_corrupted_msg).setPositiveButton(17039370, null).create().show();
                                    return;
                                }
                            }
                            new Builder(PubkeyListActivity.this, R.style.AlertDialogTheme).setMessage((int) R.string.alert_passwords_do_not_match_msg).setPositiveButton(17039370, null).create().show();
                        }
                    }).setNegativeButton(17039360, null).create().show();
                    return true;
                }
            });
            PubkeyListActivity.this.confirmUse = menu.add(R.string.pubkey_confirm_use);
            PubkeyListActivity.this.confirmUse.setCheckable(true);
            PubkeyListActivity.this.confirmUse.setChecked(this.pubkey.isConfirmUse());
            PubkeyListActivity.this.confirmUse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    PubkeyViewHolder.this.pubkey.setConfirmUse(!PubkeyViewHolder.this.pubkey.isConfirmUse());
                    PubkeyDatabase.get(PubkeyListActivity.this).savePubkey(PubkeyViewHolder.this.pubkey);
                    PubkeyListActivity.this.updateList();
                    return true;
                }
            });
            menu.add(R.string.pubkey_delete).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    new Builder(PubkeyListActivity.this, R.style.AlertDialogTheme).setMessage(PubkeyListActivity.this.getString(R.string.delete_message, new Object[]{PubkeyViewHolder.this.pubkey.getNickname()})).setPositiveButton(R.string.delete_pos, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (loaded) {
                                PubkeyListActivity.this.bound.removeKey(PubkeyViewHolder.this.pubkey.getNickname());
                            }
                            PubkeyDatabase.get(PubkeyListActivity.this).deletePubkey(PubkeyViewHolder.this.pubkey);
                            PubkeyListActivity.this.updateList();
                        }
                    }).setNegativeButton(R.string.delete_neg, null).create().show();
                    return true;
                }
            });
        }
    }

    public void onStart() {
        super.onStart();
        bindService(new Intent(this, TerminalManager.class), this.connection, 1);
        updateList();
    }

    public void onStop() {
        super.onStop();
        unbindService(this.connection);
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView((int) R.layout.act_pubkeylist);
        this.mListView = (RecyclerView) findViewById(R.id.list);
        this.mListView.setHasFixedSize(true);
        this.mListView.setLayoutManager(new LinearLayoutManager(this));
        this.mListView.addItemDecoration(new ListItemDecoration(this));
        this.mEmptyView = findViewById(R.id.empty);
        registerForContextMenu(this.mListView);
        this.clipboard = (ClipboardManager) getSystemService("clipboard");
        this.inflater = LayoutInflater.from(this);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pubkey_list_activity_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_new_key_icon:
                startActivity(new Intent(this, GeneratePubkeyActivity.class));
                return true;
            case R.id.import_existing_key_icon:
                importExistingKey();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean importExistingKey() {
        Uri sdcard = Uri.fromFile(Environment.getExternalStorageDirectory());
        String pickerTitle = getString(R.string.pubkey_list_pick);
        if ((VERSION.SDK_INT >= 19 && importExistingKeyKitKat()) || importExistingKeyOpenIntents(sdcard, pickerTitle) || importExistingKeyAndExplorer(sdcard, pickerTitle) || pickFileSimple()) {
            return true;
        }
        return false;
    }

    @TargetApi(19)
    public boolean importExistingKeyKitKat() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        try {
            startActivityForResult(intent, 1);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private boolean importExistingKeyOpenIntents(Uri sdcard, String pickerTitle) {
        Intent intent = new Intent("org.openintents.action.PICK_FILE");
        intent.setData(sdcard);
        intent.putExtra("org.openintents.extra.TITLE", pickerTitle);
        intent.putExtra("org.openintents.extra.BUTTON_TEXT", getString(17039370));
        try {
            startActivityForResult(intent, 1);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private boolean importExistingKeyAndExplorer(Uri sdcard, String pickerTitle) {
        Intent intent = new Intent("android.intent.action.PICK");
        intent.setDataAndType(sdcard, "vnd.android.cursor.dir/lysesoft.andexplorer.file");
        intent.putExtra("explorer_title", pickerTitle);
        try {
            startActivityForResult(intent, 1);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private boolean pickFileSimple() {
        final File sdcard = Environment.getExternalStorageDirectory();
        Log.d("CB.PubkeyListActivity", sdcard.toString());
        String state = Environment.getExternalStorageState();
        if ("mounted_ro".equals(state) || "mounted".equals(state)) {
            List<String> names = new LinkedList();
            if (sdcard.listFiles() != null) {
                for (File file : sdcard.listFiles()) {
                    if (!file.isDirectory()) {
                        names.add(file.getName());
                    }
                }
            }
            Collections.sort(names);
            final String[] namesList = (String[]) names.toArray(new String[0]);
            Log.d("CB.PubkeyListActivity", names.toString());
            new Builder(this, R.style.AlertDialogTheme).setTitle((int) R.string.pubkey_list_pick).setItems(namesList, new OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    PubkeyListActivity.this.readKeyFromFile(Uri.fromFile(new File(sdcard, namesList[arg1])));
                }
            }).setNegativeButton(17039360, null).create().show();
        } else {
            new Builder(this, R.style.AlertDialogTheme).setMessage((int) R.string.alert_sdcard_absent).setNegativeButton(17039360, null).create().show();
        }
        return true;
    }

    protected void handleAddKey(final PubkeyBean pubkey) {
        if (pubkey.isEncrypted()) {
            View view = View.inflate(this, R.layout.dia_password, null);
            final EditText passwordField = (EditText) view.findViewById(16908308);
            new Builder(this, R.style.AlertDialogTheme).setView(view).setPositiveButton(R.string.pubkey_unlock, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    PubkeyListActivity.this.handleAddKey(pubkey, passwordField.getText().toString());
                }
            }).setNegativeButton(17039360, null).create().show();
            return;
        }
        handleAddKey(pubkey, null);
    }

    protected void handleAddKey(PubkeyBean keybean, String password) {
        String message;
        KeyPair pair = null;
        if ("IMPORTED".equals(keybean.getType())) {
            try {
                pair = PEMDecoder.decode(new String(keybean.getPrivateKey()).toCharArray(), password);
            } catch (Exception e) {
                message = getResources().getString(R.string.pubkey_failed_add, new Object[]{keybean.getNickname()});
                Log.e("CB.PubkeyListActivity", message, e);
                Toast.makeText(this, message, 1).show();
            }
        } else {
            try {
                PrivateKey privKey = PubkeyUtils.decodePrivate(keybean.getPrivateKey(), keybean.getType(), password);
                PublicKey pubKey = PubkeyUtils.decodePublic(keybean.getPublicKey(), keybean.getType());
                Log.d("CB.PubkeyListActivity", "Unlocked key " + PubkeyUtils.formatKey(pubKey));
                pair = new KeyPair(pubKey, privKey);
            } catch (Exception e2) {
                message = getResources().getString(R.string.pubkey_failed_add, new Object[]{keybean.getNickname()});
                Log.e("CB.PubkeyListActivity", message, e2);
                Toast.makeText(this, message, 1).show();
                return;
            }
        }
        if (pair != null) {
            Log.d("CB.PubkeyListActivity", String.format("Unlocked key '%s'", new Object[]{keybean.getNickname()}));
            this.bound.addKey(keybean, pair, true);
            updateList();
        }
    }

    protected void updateList() {
        this.pubkeys = PubkeyDatabase.get(this).allPubkeys();
        this.mAdapter = new PubkeyAdapter(this, this.pubkeys);
        this.mListView.setAdapter(this.mAdapter);
        adjustViewVisibility();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        switch (requestCode) {
            case 1:
                if (resultCode == -1 && resultData != null) {
                    Uri uri = resultData.getData();
                    if (uri != null) {
                        try {
                            readKeyFromFile(uri);
                            return;
                        } catch (IllegalArgumentException e) {
                            Log.e("CB.PubkeyListActivity", "Couldn't read from picked file", e);
                            return;
                        }
                    }
                    String filename = resultData.getDataString();
                    if (filename != null) {
                        readKeyFromFile(Uri.parse(filename));
                        return;
                    }
                    return;
                }
                return;
            default:
                return;
        }
    }

    public static byte[] getBytesFromInputStream(InputStream is, int maxSize) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[65535];
        while (true) {
            int len = is.read(buffer);
            if (len != -1 && os.size() < maxSize) {
                os.write(buffer, 0, len);
            }
        }
        if (os.size() >= maxSize) {
            throw new IOException("File was too big");
        }
        os.flush();
        return os.toByteArray();
    }

    private KeyPair readPKCS8Key(byte[] keyData) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(keyData)));
        try {
            ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
            boolean inKey = false;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                } else if (line.equals("-----BEGIN PRIVATE KEY-----")) {
                    inKey = true;
                } else if (line.equals("-----END PRIVATE KEY-----")) {
                    break;
                } else if (inKey) {
                    keyBytes.write(line.getBytes("US-ASCII"));
                }
            }
            if (keyBytes.size() > 0) {
                return PubkeyUtils.recoverKeyPair(Base64.decode(keyBytes.toString().toCharArray()));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void readKeyFromFile(Uri uri) {
        PubkeyBean pubkey = new PubkeyBean();
        pubkey.setNickname(uri.getLastPathSegment());
        try {
            byte[] keyData = getBytesFromInputStream(getContentResolver().openInputStream(uri), 32768);
            KeyPair kp = readPKCS8Key(keyData);
            if (kp != null) {
                pubkey.setType(convertAlgorithmName(kp.getPrivate().getAlgorithm()));
                pubkey.setPrivateKey(kp.getPrivate().getEncoded());
                pubkey.setPublicKey(kp.getPublic().getEncoded());
            } else {
                try {
                    PEMStructure struct = PEMDecoder.parsePEM(new String(keyData).toCharArray());
                    boolean encrypted = PEMDecoder.isPEMEncrypted(struct);
                    pubkey.setEncrypted(encrypted);
                    if (encrypted) {
                        pubkey.setType("IMPORTED");
                        pubkey.setPrivateKey(keyData);
                    } else {
                        kp = PEMDecoder.decode(struct, null);
                        pubkey.setType(convertAlgorithmName(kp.getPrivate().getAlgorithm()));
                        pubkey.setPrivateKey(kp.getPrivate().getEncoded());
                        pubkey.setPublicKey(kp.getPublic().getEncoded());
                    }
                } catch (IOException e) {
                    Log.e("CB.PubkeyListActivity", "Problem parsing imported private key", e);
                    Toast.makeText(this, R.string.pubkey_import_parse_problem, 1).show();
                }
            }
            PubkeyDatabase.get(this).savePubkey(pubkey);
            updateList();
        } catch (IOException e2) {
            Toast.makeText(this, R.string.pubkey_import_parse_problem, 1).show();
        }
    }

    private String convertAlgorithmName(String algorithm) {
        if ("EdDSA".equals(algorithm)) {
            return "ED25519";
        }
        return algorithm;
    }
}
