package org.connectbot;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.List;
import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.service.TerminalManager.TerminalBinder;
import org.connectbot.util.HostDatabase;

public class PortForwardListActivity extends AppCompatListActivity {
    private ServiceConnection connection = null;
    private HostBean host;
    protected TerminalBridge hostBridge = null;
    protected HostDatabase hostdb;
    protected LayoutInflater inflater = null;
    protected Handler updateHandler = new Handler(new WeakReference(this));

    private static class Handler extends android.os.Handler {
        private WeakReference<PortForwardListActivity> mActivity;

        Handler(WeakReference<PortForwardListActivity> activity) {
            this.mActivity = activity;
        }

        public void handleMessage(Message msg) {
            ((PortForwardListActivity) this.mActivity.get()).updateList();
        }
    }

    private class PortForwardAdapter extends ItemAdapter {
        private final List<PortForwardBean> portForwards;

        public PortForwardAdapter(Context context, List<PortForwardBean> portForwards) {
            super(context);
            this.portForwards = portForwards;
        }

        public PortForwardViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PortForwardViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_portforward, parent, false));
        }

        public void onBindViewHolder(ItemViewHolder holder, int position) {
            PortForwardViewHolder portForwardHolder = (PortForwardViewHolder) holder;
            PortForwardBean portForward = (PortForwardBean) this.portForwards.get(position);
            portForwardHolder.portForward = portForward;
            portForwardHolder.nickname.setText(portForward.getNickname());
            portForwardHolder.caption.setText(portForward.getDescription());
            if (PortForwardListActivity.this.hostBridge != null && !portForward.isEnabled()) {
                portForwardHolder.nickname.setPaintFlags(portForwardHolder.nickname.getPaintFlags() | 16);
                portForwardHolder.caption.setPaintFlags(portForwardHolder.caption.getPaintFlags() | 16);
            }
        }

        public long getItemId(int position) {
            return ((PortForwardBean) this.portForwards.get(position)).getId();
        }

        public int getItemCount() {
            return this.portForwards.size();
        }
    }

    private class PortForwardViewHolder extends ItemViewHolder {
        public final TextView caption;
        public final TextView nickname;
        public PortForwardBean portForward;

        public PortForwardViewHolder(View v) {
            super(v);
            this.nickname = (TextView) v.findViewById(16908308);
            this.caption = (TextView) v.findViewById(16908309);
        }

        public void onClick(View v) {
            if (PortForwardListActivity.this.hostBridge != null) {
                if (this.portForward.isEnabled()) {
                    PortForwardListActivity.this.hostBridge.disablePortForward(this.portForward);
                } else if (!PortForwardListActivity.this.hostBridge.enablePortForward(this.portForward)) {
                    Toast.makeText(PortForwardListActivity.this, PortForwardListActivity.this.getString(R.string.portforward_problem), 1).show();
                }
                PortForwardListActivity.this.updateHandler.sendEmptyMessage(-1);
            }
        }

        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(this.portForward.getNickname());
            menu.add(R.string.portforward_edit).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    View editTunnelView = View.inflate(PortForwardListActivity.this, R.layout.dia_portforward, null);
                    final Spinner typeSpinner = (Spinner) editTunnelView.findViewById(R.id.portforward_type);
                    if ("local".equals(PortForwardViewHolder.this.portForward.getType())) {
                        typeSpinner.setSelection(0);
                    } else if ("remote".equals(PortForwardViewHolder.this.portForward.getType())) {
                        typeSpinner.setSelection(1);
                    } else {
                        typeSpinner.setSelection(2);
                    }
                    final EditText nicknameEdit = (EditText) editTunnelView.findViewById(R.id.nickname);
                    nicknameEdit.setText(PortForwardViewHolder.this.portForward.getNickname());
                    final EditText sourcePortEdit = (EditText) editTunnelView.findViewById(R.id.portforward_source);
                    sourcePortEdit.setText(String.valueOf(PortForwardViewHolder.this.portForward.getSourcePort()));
                    final EditText destEdit = (EditText) editTunnelView.findViewById(R.id.portforward_destination);
                    if ("dynamic5".equals(PortForwardViewHolder.this.portForward.getType())) {
                        destEdit.setEnabled(false);
                    } else {
                        destEdit.setText(String.format("%s:%d", new Object[]{PortForwardViewHolder.this.portForward.getDestAddr(), Integer.valueOf(PortForwardViewHolder.this.portForward.getDestPort())}));
                    }
                    typeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                        public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                            destEdit.setEnabled(position != 2);
                        }

                        public void onNothingSelected(AdapterView<?> adapterView) {
                        }
                    });
                    new Builder(PortForwardListActivity.this, R.style.AlertDialogTheme).setView(editTunnelView).setPositiveButton(R.string.button_change, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                if (PortForwardListActivity.this.hostBridge != null) {
                                    PortForwardListActivity.this.hostBridge.disablePortForward(PortForwardViewHolder.this.portForward);
                                }
                                PortForwardViewHolder.this.portForward.setNickname(nicknameEdit.getText().toString());
                                switch (typeSpinner.getSelectedItemPosition()) {
                                    case 0:
                                        PortForwardViewHolder.this.portForward.setType("local");
                                        break;
                                    case 1:
                                        PortForwardViewHolder.this.portForward.setType("remote");
                                        break;
                                    case 2:
                                        PortForwardViewHolder.this.portForward.setType("dynamic5");
                                        break;
                                }
                                PortForwardViewHolder.this.portForward.setSourcePort(Integer.parseInt(sourcePortEdit.getText().toString()));
                                PortForwardViewHolder.this.portForward.setDest(destEdit.getText().toString());
                                if (PortForwardListActivity.this.hostBridge != null) {
                                    PortForwardListActivity.this.updateHandler.postDelayed(new Runnable() {
                                        public void run() {
                                            PortForwardListActivity.this.hostBridge.enablePortForward(PortForwardViewHolder.this.portForward);
                                            PortForwardListActivity.this.updateHandler.sendEmptyMessage(-1);
                                        }
                                    }, 500);
                                }
                                if (PortForwardListActivity.this.hostdb.savePortForward(PortForwardViewHolder.this.portForward)) {
                                    PortForwardListActivity.this.updateHandler.sendEmptyMessage(-1);
                                    return;
                                }
                                throw new SQLException("Could not save port forward");
                            } catch (Exception e) {
                                Log.e("CB.PortForwardListAct", "Could not update port forward", e);
                            }
                        }
                    }).setNegativeButton(17039360, null).create().show();
                    return true;
                }
            });
            menu.add(R.string.portforward_delete).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    new Builder(PortForwardListActivity.this, R.style.AlertDialogTheme).setMessage(PortForwardListActivity.this.getString(R.string.delete_message, new Object[]{PortForwardViewHolder.this.portForward.getNickname()})).setPositiveButton(R.string.delete_pos, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                if (PortForwardListActivity.this.hostBridge != null) {
                                    PortForwardListActivity.this.hostBridge.removePortForward(PortForwardViewHolder.this.portForward);
                                }
                                PortForwardListActivity.this.hostdb.deletePortForward(PortForwardViewHolder.this.portForward);
                            } catch (Exception e) {
                                Log.e("CB.PortForwardListAct", "Could not delete port forward", e);
                            }
                            PortForwardListActivity.this.updateHandler.sendEmptyMessage(-1);
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
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        long hostId = getIntent().getLongExtra("android.intent.extra.TITLE", -1);
        setContentView((int) R.layout.act_portforwardlist);
        this.mListView = (RecyclerView) findViewById(R.id.list);
        this.mListView.setHasFixedSize(true);
        this.mListView.setLayoutManager(new LinearLayoutManager(this));
        this.mListView.addItemDecoration(new ListItemDecoration(this));
        this.mEmptyView = findViewById(R.id.empty);
        this.hostdb = HostDatabase.get(this);
        this.host = this.hostdb.findHostById(hostId);
        String nickname = this.host != null ? this.host.getNickname() : null;
        Resources resources = getResources();
        if (nickname != null) {
            setTitle(String.format("%s (%s)", new Object[]{resources.getText(R.string.title_port_forwards_list), nickname}));
        }
        this.connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                TerminalManager bound = ((TerminalBinder) service).getService();
                PortForwardListActivity.this.hostBridge = bound.getConnectedBridge(PortForwardListActivity.this.host);
                PortForwardListActivity.this.updateHandler.sendEmptyMessage(-1);
            }

            public void onServiceDisconnected(ComponentName name) {
                PortForwardListActivity.this.hostBridge = null;
            }
        };
        updateList();
        registerForContextMenu(this.mListView);
        this.inflater = LayoutInflater.from(this);
        ((FloatingActionButton) findViewById(R.id.add_port_forward_button)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final View portForwardView = View.inflate(PortForwardListActivity.this, R.layout.dia_portforward, null);
                final EditText destEdit = (EditText) portForwardView.findViewById(R.id.portforward_destination);
                final Spinner typeSpinner = (Spinner) portForwardView.findViewById(R.id.portforward_type);
                typeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                        destEdit.setEnabled(position != 2);
                    }

                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                new Builder(PortForwardListActivity.this, R.style.AlertDialogTheme).setView(portForwardView).setPositiveButton(R.string.portforward_pos, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            EditText nicknameEdit = (EditText) portForwardView.findViewById(R.id.nickname);
                            EditText sourcePortEdit = (EditText) portForwardView.findViewById(R.id.portforward_source);
                            String type = "local";
                            switch (typeSpinner.getSelectedItemPosition()) {
                                case 0:
                                    type = "local";
                                    break;
                                case 1:
                                    type = "remote";
                                    break;
                                case 2:
                                    type = "dynamic5";
                                    break;
                            }
                            String sourcePort = sourcePortEdit.getText().toString();
                            if (sourcePort.length() == 0) {
                                sourcePort = sourcePortEdit.getHint().toString();
                            }
                            String destination = destEdit.getText().toString();
                            if (destination.length() == 0) {
                                destination = destEdit.getHint().toString();
                            }
                            PortForwardBean portForward = new PortForwardBean(PortForwardListActivity.this.host != null ? PortForwardListActivity.this.host.getId() : -1, nicknameEdit.getText().toString(), type, sourcePort, destination);
                            if (PortForwardListActivity.this.hostBridge != null) {
                                PortForwardListActivity.this.hostBridge.addPortForward(portForward);
                                PortForwardListActivity.this.hostBridge.enablePortForward(portForward);
                            }
                            if (PortForwardListActivity.this.host == null || PortForwardListActivity.this.hostdb.savePortForward(portForward)) {
                                PortForwardListActivity.this.updateHandler.sendEmptyMessage(-1);
                                return;
                            }
                            throw new SQLException("Could not save port forward");
                        } catch (Exception e) {
                            Log.e("CB.PortForwardListAct", "Could not update port forward", e);
                        }
                    }
                }).setNegativeButton(R.string.delete_neg, null).create().show();
            }
        });
    }

    protected void updateList() {
        List<PortForwardBean> portForwards;
        if (this.hostBridge != null) {
            portForwards = this.hostBridge.getPortForwards();
        } else if (this.hostdb != null) {
            portForwards = this.hostdb.getPortForwardsForHost(this.host);
        } else {
            return;
        }
        this.mAdapter = new PortForwardAdapter(this, portForwards);
        this.mListView.setAdapter(this.mAdapter);
        adjustViewVisibility();
    }
}
