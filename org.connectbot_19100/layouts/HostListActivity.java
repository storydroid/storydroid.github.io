package org.connectbot;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Iterator;
import java.util.List;
import org.connectbot.bean.HostBean;
import org.connectbot.data.HostStorage;
import org.connectbot.service.OnHostStatusChangedListener;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.service.TerminalManager.TerminalBinder;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;

public class HostListActivity extends AppCompatListActivity implements OnHostStatusChangedListener {
    protected TerminalManager bound = null;
    private boolean closeOnDisconnectAll = true;
    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            HostListActivity.this.bound = ((TerminalBinder) service).getService();
            HostListActivity.this.updateList();
            HostListActivity.this.bound.registerOnHostStatusChangedListener(HostListActivity.this);
            if (HostListActivity.this.waitingForDisconnectAll) {
                HostListActivity.this.disconnectAll();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            HostListActivity.this.bound.unregisterOnHostStatusChangedListener(HostListActivity.this);
            HostListActivity.this.bound = null;
            HostListActivity.this.updateList();
        }
    };
    private MenuItem disconnectall;
    private HostStorage hostdb;
    private List<HostBean> hosts;
    protected LayoutInflater inflater = null;
    protected boolean makingShortcut = false;
    private SharedPreferences prefs = null;
    private MenuItem sortcolor;
    protected boolean sortedByColor = false;
    private MenuItem sortlast;
    private boolean waitingForDisconnectAll = false;

    private class HostAdapter extends ItemAdapter {
        private final List<HostBean> hosts;
        private final TerminalManager manager;

        public HostAdapter(Context context, List<HostBean> hosts, TerminalManager manager) {
            super(context);
            this.hosts = hosts;
            this.manager = manager;
        }

        private int getConnectedState(HostBean host) {
            if (this.manager == null || host == null) {
                return 1;
            }
            if (this.manager.getConnectedBridge(host) != null) {
                return 2;
            }
            if (this.manager.disconnected.contains(host)) {
                return 3;
            }
            return 1;
        }

        public HostViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new HostViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_host, parent, false));
        }

        @TargetApi(16)
        private void hideFromAccessibility(View view, boolean hide) {
            view.setImportantForAccessibility(hide ? 2 : 1);
        }

        public void onBindViewHolder(ItemViewHolder holder, int position) {
            int chosenStyleFirstLine;
            int chosenStyleSecondLine;
            HostViewHolder hostHolder = (HostViewHolder) holder;
            HostBean host = (HostBean) this.hosts.get(position);
            hostHolder.host = host;
            if (host == null) {
                Log.e("HostAdapter", "Host bean is null!");
                hostHolder.nickname.setText("Error during lookup");
            } else {
                hostHolder.nickname.setText(host.getNickname());
            }
            switch (getConnectedState(host)) {
                case 1:
                    hostHolder.icon.setImageState(new int[0], true);
                    hostHolder.icon.setContentDescription(null);
                    if (VERSION.SDK_INT >= 16) {
                        hideFromAccessibility(hostHolder.icon, true);
                        break;
                    }
                    break;
                case 2:
                    hostHolder.icon.setImageState(new int[]{16842912}, true);
                    hostHolder.icon.setContentDescription(HostListActivity.this.getString(R.string.image_description_connected));
                    if (VERSION.SDK_INT >= 16) {
                        hideFromAccessibility(hostHolder.icon, false);
                        break;
                    }
                    break;
                case 3:
                    hostHolder.icon.setImageState(new int[]{16842920}, true);
                    hostHolder.icon.setContentDescription(HostListActivity.this.getString(R.string.image_description_disconnected));
                    if (VERSION.SDK_INT >= 16) {
                        hideFromAccessibility(hostHolder.icon, false);
                        break;
                    }
                    break;
                default:
                    Log.e("HostAdapter", "Unknown host state encountered: " + getConnectedState(host));
                    break;
            }
            if ("red".equals(host.getColor())) {
                chosenStyleFirstLine = R.style.ListItemFirstLineText.Red;
                chosenStyleSecondLine = R.style.ListItemSecondLineText.Red;
            } else if ("green".equals(host.getColor())) {
                chosenStyleFirstLine = R.style.ListItemFirstLineText.Green;
                chosenStyleSecondLine = R.style.ListItemSecondLineText.Green;
            } else if ("blue".equals(host.getColor())) {
                chosenStyleFirstLine = R.style.ListItemFirstLineText.Blue;
                chosenStyleSecondLine = R.style.ListItemSecondLineText.Blue;
            } else {
                chosenStyleFirstLine = R.style.ListItemFirstLineText;
                chosenStyleSecondLine = R.style.ListItemSecondLineText;
            }
            hostHolder.nickname.setTextAppearance(this.context, chosenStyleFirstLine);
            hostHolder.caption.setTextAppearance(this.context, chosenStyleSecondLine);
            CharSequence nice = this.context.getString(R.string.bind_never);
            if (host.getLastConnect() > 0) {
                nice = DateUtils.getRelativeTimeSpanString(host.getLastConnect() * 1000);
            }
            hostHolder.caption.setText(nice);
        }

        public long getItemId(int position) {
            return ((HostBean) this.hosts.get(position)).getId();
        }

        public int getItemCount() {
            return this.hosts.size();
        }
    }

    public class HostViewHolder extends ItemViewHolder {
        public final TextView caption;
        public HostBean host;
        public final ImageView icon;
        public final TextView nickname;

        public HostViewHolder(View v) {
            super(v);
            this.icon = (ImageView) v.findViewById(16908294);
            this.nickname = (TextView) v.findViewById(16908308);
            this.caption = (TextView) v.findViewById(16908309);
        }

        public void onClick(View v) {
            Intent contents = new Intent("android.intent.action.VIEW", this.host.getUri());
            contents.setFlags(67108864);
            if (HostListActivity.this.makingShortcut) {
                ShortcutIconResource icon = ShortcutIconResource.fromContext(HostListActivity.this, R.drawable.icon);
                Intent intent = new Intent();
                intent.putExtra("android.intent.extra.shortcut.INTENT", contents);
                intent.putExtra("android.intent.extra.shortcut.NAME", this.host.getNickname());
                intent.putExtra("android.intent.extra.shortcut.ICON_RESOURCE", icon);
                HostListActivity.this.setResult(-1, intent);
                HostListActivity.this.finish();
                return;
            }
            contents.setClass(HostListActivity.this, ConsoleActivity.class);
            HostListActivity.this.startActivity(contents);
        }

        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            boolean z;
            menu.setHeaderTitle(this.host.getNickname());
            MenuItem connect = menu.add(R.string.list_host_disconnect);
            final TerminalBridge bridge = HostListActivity.this.bound == null ? null : HostListActivity.this.bound.getConnectedBridge(this.host);
            if (bridge != null) {
                z = true;
            } else {
                z = false;
            }
            connect.setEnabled(z);
            connect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    bridge.dispatchDisconnect(true);
                    return true;
                }
            });
            menu.add(R.string.list_host_edit).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    HostListActivity.this.startActivityForResult(EditHostActivity.createIntentForExistingHost(HostListActivity.this, HostViewHolder.this.host.getId()), 1);
                    return true;
                }
            });
            MenuItem portForwards = menu.add(R.string.list_host_portforwards);
            portForwards.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    Intent intent = new Intent(HostListActivity.this, PortForwardListActivity.class);
                    intent.putExtra("android.intent.extra.TITLE", HostViewHolder.this.host.getId());
                    HostListActivity.this.startActivityForResult(intent, 1);
                    return true;
                }
            });
            if (!TransportFactory.canForwardPorts(this.host.getProtocol())) {
                portForwards.setEnabled(false);
            }
            menu.add(R.string.list_host_delete).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    new Builder(HostListActivity.this, R.style.AlertDialogTheme).setMessage(HostListActivity.this.getString(R.string.delete_message, new Object[]{HostViewHolder.this.host.getNickname()})).setPositiveButton(R.string.delete_pos, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (bridge != null) {
                                bridge.dispatchDisconnect(true);
                            }
                            HostListActivity.this.hostdb.deleteHost(HostViewHolder.this.host);
                            HostListActivity.this.updateList();
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
        this.hostdb = HostDatabase.get(this);
    }

    public void onStop() {
        super.onStop();
        unbindService(this.connection);
        this.hostdb = null;
        this.closeOnDisconnectAll = true;
    }

    public void onResume() {
        super.onResume();
        if ((getIntent().getFlags() & 1048576) == 0 && "org.connectbot.action.DISCONNECT".equals(getIntent().getAction())) {
            Log.d("CB.HostListActivity", "Got disconnect all request");
            disconnectAll();
        }
        boolean z = this.waitingForDisconnectAll && this.closeOnDisconnectAll;
        this.closeOnDisconnectAll = z;
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            updateList();
        }
    }

    public void onCreate(Bundle icicle) {
        boolean z;
        int i = 0;
        super.onCreate(icicle);
        setContentView((int) R.layout.act_hostlist);
        setTitle(R.string.title_hosts_list);
        this.mListView = (RecyclerView) findViewById(R.id.list);
        this.mListView.setHasFixedSize(true);
        this.mListView.setLayoutManager(new LinearLayoutManager(this));
        this.mListView.addItemDecoration(new ListItemDecoration(this));
        this.mEmptyView = findViewById(R.id.empty);
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (Build.MANUFACTURER.equals("HTC") && Build.DEVICE.equals("dream")) {
            Editor editor = this.prefs.edit();
            boolean doCommit = false;
            if (!(this.prefs.contains("shiftfkeys") || this.prefs.contains("ctrlfkeys"))) {
                editor.putBoolean("shiftfkeys", true);
                editor.putBoolean("ctrlfkeys", true);
                doCommit = true;
            }
            if (!this.prefs.contains("stickymodifiers")) {
                editor.putString("stickymodifiers", "yes");
                doCommit = true;
            }
            if (!this.prefs.contains("keymode")) {
                editor.putString("keymode", "Use right-side keys");
                doCommit = true;
            }
            if (doCommit) {
                editor.commit();
            }
        }
        if ("android.intent.action.CREATE_SHORTCUT".equals(getIntent().getAction()) || "android.intent.action.PICK".equals(getIntent().getAction())) {
            z = true;
        } else {
            z = false;
        }
        this.makingShortcut = z;
        this.hostdb = HostDatabase.get(this);
        this.sortedByColor = this.prefs.getBoolean("sortByColor", false);
        registerForContextMenu(this.mListView);
        FloatingActionButton addHostButton = (FloatingActionButton) findViewById(R.id.add_host_button);
        if (this.makingShortcut) {
            i = 8;
        }
        addHostButton.setVisibility(i);
        addHostButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                HostListActivity.this.startActivityForResult(EditHostActivity.createIntentForNewHost(HostListActivity.this), 1);
            }
        });
        this.inflater = LayoutInflater.from(this);
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean z = false;
        super.onPrepareOptionsMenu(menu);
        if (!this.makingShortcut) {
            boolean z2;
            MenuItem menuItem = this.sortcolor;
            if (this.sortedByColor) {
                z2 = false;
            } else {
                z2 = true;
            }
            menuItem.setVisible(z2);
            this.sortlast.setVisible(this.sortedByColor);
            MenuItem menuItem2 = this.disconnectall;
            if (this.bound != null && this.bound.getBridges().size() > 0) {
                z = true;
            }
            menuItem2.setEnabled(z);
        }
        return true;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (!this.makingShortcut) {
            this.sortcolor = menu.add(R.string.list_menu_sortcolor);
            this.sortcolor.setIcon(17301586);
            this.sortcolor.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    HostListActivity.this.sortedByColor = true;
                    HostListActivity.this.updateList();
                    return true;
                }
            });
            this.sortlast = menu.add(R.string.list_menu_sortname);
            this.sortlast.setIcon(17301586);
            this.sortlast.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    HostListActivity.this.sortedByColor = false;
                    HostListActivity.this.updateList();
                    return true;
                }
            });
            MenuItem keys = menu.add(R.string.list_menu_pubkeys);
            keys.setIcon(17301551);
            keys.setIntent(new Intent(this, PubkeyListActivity.class));
            MenuItem colors = menu.add(R.string.title_colors);
            colors.setIcon(17301587);
            colors.setIntent(new Intent(this, ColorsActivity.class));
            this.disconnectall = menu.add(R.string.list_menu_disconnect);
            this.disconnectall.setIcon(17301564);
            this.disconnectall.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem menuItem) {
                    HostListActivity.this.disconnectAll();
                    return false;
                }
            });
            MenuItem settings = menu.add(R.string.list_menu_settings);
            settings.setIcon(17301577);
            settings.setIntent(new Intent(this, SettingsActivity.class));
            MenuItem help = menu.add(R.string.title_help);
            help.setIcon(17301568);
            help.setIntent(new Intent(this, HelpActivity.class));
        }
        return true;
    }

    private void disconnectAll() {
        if (this.bound == null) {
            this.waitingForDisconnectAll = true;
        } else {
            new Builder(this, R.style.AlertDialogTheme).setMessage(getString(R.string.disconnect_all_message)).setPositiveButton(R.string.disconnect_all_pos, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    HostListActivity.this.bound.disconnectAll(true, false);
                    HostListActivity.this.waitingForDisconnectAll = false;
                    HostListActivity.this.setIntent(new Intent());
                    if (HostListActivity.this.closeOnDisconnectAll) {
                        HostListActivity.this.finish();
                    }
                }
            }).setNegativeButton(R.string.disconnect_all_neg, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    HostListActivity.this.waitingForDisconnectAll = false;
                    HostListActivity.this.setIntent(new Intent());
                }
            }).create().show();
        }
    }

    protected void updateList() {
        if (this.prefs.getBoolean("sortByColor", false) != this.sortedByColor) {
            Editor edit = this.prefs.edit();
            edit.putBoolean("sortByColor", this.sortedByColor);
            edit.commit();
        }
        if (this.hostdb == null) {
            this.hostdb = HostDatabase.get(this);
        }
        this.hosts = this.hostdb.getHosts(this.sortedByColor);
        if (this.bound != null) {
            Iterator it = this.bound.getBridges().iterator();
            while (it.hasNext()) {
                TerminalBridge bridge = (TerminalBridge) it.next();
                if (!this.hosts.contains(bridge.host)) {
                    this.hosts.add(0, bridge.host);
                }
            }
        }
        this.mAdapter = new HostAdapter(this, this.hosts, this.bound);
        this.mListView.setAdapter(this.mAdapter);
        adjustViewVisibility();
    }

    public void onHostStatusChanged() {
        updateList();
    }
}
