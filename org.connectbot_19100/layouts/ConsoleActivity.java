package org.connectbot;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TabLayout.TabLayoutOnPageChangeListener;
import android.support.design.widget.TabLayout.ViewPagerOnTabSelectedListener;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager.SimpleOnPageChangeListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.OnMenuVisibilityListener;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.connectbot.bean.HostBean;
import org.connectbot.service.BridgeDisconnectedListener;
import org.connectbot.service.PromptHelper;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalKeyListener;
import org.connectbot.service.TerminalManager;
import org.connectbot.service.TerminalManager.TerminalBinder;
import org.connectbot.util.TerminalViewPager;

public class ConsoleActivity extends AppCompatActivity implements BridgeDisconnectedListener {
    private ActionBar actionBar;
    protected TerminalPagerAdapter adapter = null;
    private Button booleanNo;
    private TextView booleanPrompt;
    private RelativeLayout booleanPromptGroup;
    private Button booleanYes;
    protected TerminalManager bound = null;
    protected ClipboardManager clipboard;
    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            ConsoleActivity.this.bound = ((TerminalBinder) service).getService();
            ConsoleActivity.this.bound.disconnectListener = ConsoleActivity.this;
            ConsoleActivity.this.bound.setResizeAllowed(true);
            String requestedNickname = ConsoleActivity.this.requested != null ? ConsoleActivity.this.requested.getFragment() : null;
            TerminalBridge requestedBridge = ConsoleActivity.this.bound.getConnectedBridge(requestedNickname);
            if (requestedNickname != null && requestedBridge == null) {
                try {
                    Log.d("CB.ConsoleActivity", String.format("We couldnt find an existing bridge with URI=%s (nickname=%s), so creating one now", new Object[]{ConsoleActivity.this.requested.toString(), requestedNickname}));
                    requestedBridge = ConsoleActivity.this.bound.openConnection(ConsoleActivity.this.requested);
                } catch (Exception e) {
                    Log.e("CB.ConsoleActivity", "Problem while trying to create new requested bridge from URI", e);
                }
            }
            ConsoleActivity.this.adapter.notifyDataSetChanged();
            final int requestedIndex = ConsoleActivity.this.bound.getBridges().indexOf(requestedBridge);
            if (requestedBridge != null) {
                requestedBridge.promptHelper.setHandler(ConsoleActivity.this.promptHandler);
            }
            if (requestedIndex != -1) {
                ConsoleActivity.this.pager.post(new Runnable() {
                    public void run() {
                        ConsoleActivity.this.setDisplayedTerminal(requestedIndex);
                    }
                });
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            ConsoleActivity.this.bound = null;
            ConsoleActivity.this.adapter.notifyDataSetChanged();
            ConsoleActivity.this.updateEmptyVisible();
        }
    };
    private View contentView;
    private MenuItem copy;
    private MenuItem disconnect;
    private TextView empty;
    protected OnClickListener emulatedKeysListener = new OnClickListener() {
        public void onClick(View v) {
            ConsoleActivity.this.onEmulatedKeyClicked(v);
        }
    };
    private Animation fade_out_delayed;
    private boolean forcedOrientation;
    private Handler handler = new Handler();
    private boolean hardKeyboard = false;
    private boolean inActionBarMenu = false;
    protected LayoutInflater inflater = null;
    private InputMethodManager inputManager;
    protected Handler keyRepeatHandler = new Handler();
    private boolean keyboardAlwaysVisible = false;
    private LinearLayout keyboardGroup;
    private Runnable keyboardGroupHider;
    private Animation keyboard_fade_in;
    private Animation keyboard_fade_out;
    private ImageView mKeyboardButton;
    protected TerminalViewPager pager = null;
    private MenuItem paste;
    private MenuItem portForward;
    private SharedPreferences prefs = null;
    protected Handler promptHandler = new Handler() {
        public void handleMessage(Message msg) {
            ConsoleActivity.this.updatePromptVisible();
        }
    };
    protected Uri requested;
    private MenuItem resize;
    protected EditText stringPrompt;
    private RelativeLayout stringPromptGroup;
    private TextView stringPromptInstructions;
    protected TabLayout tabs = null;
    private boolean titleBarHide;
    protected Toolbar toolbar = null;
    private MenuItem urlscan;

    public class KeyRepeater implements OnClickListener, OnTouchListener, Runnable {
        private boolean mDown = false;
        private Handler mHandler;
        private View mView;

        public KeyRepeater(Handler handler, View view) {
            this.mView = view;
            this.mHandler = handler;
        }

        public void run() {
            this.mDown = true;
            this.mHandler.removeCallbacks(this);
            this.mHandler.postDelayed(this, 100);
            this.mView.performClick();
        }

        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case 0:
                    this.mDown = false;
                    this.mHandler.postDelayed(this, 500);
                    this.mView.setPressed(true);
                    return true;
                case 1:
                    this.mHandler.removeCallbacks(this);
                    this.mView.setPressed(false);
                    if (this.mDown) {
                        return true;
                    }
                    this.mView.performClick();
                    return true;
                case 3:
                    this.mHandler.removeCallbacks(this);
                    this.mView.setPressed(false);
                    return true;
                default:
                    return false;
            }
        }

        public void onClick(View view) {
            ConsoleActivity.this.onEmulatedKeyClicked(view);
        }
    }

    public class TerminalPagerAdapter extends PagerAdapter {
        public int getCount() {
            if (ConsoleActivity.this.bound != null) {
                return ConsoleActivity.this.bound.getBridges().size();
            }
            return 0;
        }

        public Object instantiateItem(ViewGroup container, int position) {
            if (ConsoleActivity.this.bound == null || ConsoleActivity.this.bound.getBridges().size() <= position) {
                Log.w("CB.ConsoleActivity", "Activity not bound when creating TerminalView.");
            }
            TerminalBridge bridge = (TerminalBridge) ConsoleActivity.this.bound.getBridges().get(position);
            bridge.promptHelper.setHandler(ConsoleActivity.this.promptHandler);
            RelativeLayout view = (RelativeLayout) ConsoleActivity.this.inflater.inflate(R.layout.item_terminal, container, false);
            TextView terminalNameOverlay = (TextView) view.findViewById(R.id.terminal_name_overlay);
            terminalNameOverlay.setText(bridge.host.getNickname());
            TerminalView terminal = new TerminalView(container.getContext(), bridge, ConsoleActivity.this.pager);
            terminal.setId(R.id.terminal_view);
            view.addView(terminal, 0);
            view.setTag(bridge);
            container.addView(view);
            terminalNameOverlay.startAnimation(ConsoleActivity.this.fade_out_delayed);
            return view;
        }

        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        public int getItemPosition(Object object) {
            if (ConsoleActivity.this.bound == null) {
                return -2;
            }
            HostBean host = ((TerminalView) ((View) object).findViewById(R.id.terminal_view)).bridge.host;
            int i = 0;
            Iterator it = ConsoleActivity.this.bound.getBridges().iterator();
            while (it.hasNext()) {
                if (((TerminalBridge) it.next()).host.equals(host)) {
                    return i;
                }
                i++;
            }
            return -2;
        }

        public TerminalBridge getBridgeAtPosition(int position) {
            if (ConsoleActivity.this.bound == null) {
                return null;
            }
            ArrayList<TerminalBridge> bridges = ConsoleActivity.this.bound.getBridges();
            if (position < 0 || position >= bridges.size()) {
                return null;
            }
            return (TerminalBridge) bridges.get(position);
        }

        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if (ConsoleActivity.this.tabs != null) {
                ConsoleActivity.this.toolbar.setVisibility(getCount() > 1 ? 0 : 8);
                ConsoleActivity.this.tabs.setTabsFromPagerAdapter(this);
            }
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        public CharSequence getPageTitle(int position) {
            TerminalBridge bridge = getBridgeAtPosition(position);
            if (bridge == null) {
                return "???";
            }
            return bridge.host.getNickname();
        }

        public TerminalView getCurrentTerminalView() {
            View currentView = ConsoleActivity.this.pager.findViewWithTag(getBridgeAtPosition(ConsoleActivity.this.pager.getCurrentItem()));
            if (currentView == null) {
                return null;
            }
            return (TerminalView) currentView.findViewById(R.id.terminal_view);
        }
    }

    private class URLItemListener implements OnItemClickListener {
        private WeakReference<Context> contextRef;

        URLItemListener(Context context) {
            this.contextRef = new WeakReference(context);
        }

        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            Context context = (Context) this.contextRef.get();
            if (context != null) {
                try {
                    String url = ((TextView) view).getText().toString();
                    if (url.indexOf("://") < 0) {
                        url = "http://" + url;
                    }
                    context.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(url)));
                } catch (Exception e) {
                    Log.e("CB.ConsoleActivity", "couldn't open URL", e);
                }
            }
        }
    }

    public void onDisconnected(TerminalBridge bridge) {
        synchronized (this.adapter) {
            this.adapter.notifyDataSetChanged();
            Log.d("CB.ConsoleActivity", "Someone sending HANDLE_DISCONNECT to parentHandler");
            if (bridge.isAwaitingClose()) {
                closeBridge(bridge);
            }
        }
    }

    private void onEmulatedKeyClicked(View v) {
        TerminalView terminal = this.adapter.getCurrentTerminalView();
        if (terminal != null) {
            TerminalKeyListener handler = terminal.bridge.getKeyHandler();
            boolean hideKeys = false;
            switch (v.getId()) {
                case R.id.button_ctrl:
                    handler.metaPress(1, true);
                    hideKeys = true;
                    break;
                case R.id.button_esc:
                    handler.sendEscape();
                    hideKeys = true;
                    break;
                case R.id.button_tab:
                    handler.sendTab();
                    hideKeys = true;
                    break;
                case R.id.button_up:
                    handler.sendPressedKey(14);
                    break;
                case R.id.button_down:
                    handler.sendPressedKey(15);
                    break;
                case R.id.button_left:
                    handler.sendPressedKey(16);
                    break;
                case R.id.button_right:
                    handler.sendPressedKey(17);
                    break;
                case R.id.button_home:
                    handler.sendPressedKey(23);
                    break;
                case R.id.button_end:
                    handler.sendPressedKey(24);
                    break;
                case R.id.button_pgup:
                    handler.sendPressedKey(19);
                    break;
                case R.id.button_pgdn:
                    handler.sendPressedKey(18);
                    break;
                case R.id.button_f1:
                    handler.sendPressedKey(2);
                    break;
                case R.id.button_f2:
                    handler.sendPressedKey(3);
                    break;
                case R.id.button_f3:
                    handler.sendPressedKey(4);
                    break;
                case R.id.button_f4:
                    handler.sendPressedKey(5);
                    break;
                case R.id.button_f5:
                    handler.sendPressedKey(6);
                    break;
                case R.id.button_f6:
                    handler.sendPressedKey(7);
                    break;
                case R.id.button_f7:
                    handler.sendPressedKey(8);
                    break;
                case R.id.button_f8:
                    handler.sendPressedKey(9);
                    break;
                case R.id.button_f9:
                    handler.sendPressedKey(10);
                    break;
                case R.id.button_f10:
                    handler.sendPressedKey(11);
                    break;
                case R.id.button_f11:
                    handler.sendPressedKey(12);
                    break;
                case R.id.button_f12:
                    handler.sendPressedKey(13);
                    break;
                default:
                    Log.e("CB.ConsoleActivity", "Unknown emulated key clicked: " + v.getId());
                    break;
            }
            if (hideKeys) {
                hideEmulatedKeys();
            } else {
                autoHideEmulatedKeys();
            }
            terminal.bridge.tryKeyVibrate();
            hideActionBarIfRequested();
        }
    }

    private void hideActionBarIfRequested() {
        if (this.titleBarHide && this.actionBar != null) {
            this.actionBar.hide();
        }
    }

    private void closeBridge(TerminalBridge bridge) {
        updateEmptyVisible();
        updatePromptVisible();
        if (this.pager.getChildCount() == 0) {
            finish();
        }
    }

    protected View findCurrentView(int id) {
        View view = this.pager.findViewWithTag(this.adapter.getBridgeAtPosition(this.pager.getCurrentItem()));
        if (view == null) {
            return null;
        }
        return view.findViewById(id);
    }

    protected PromptHelper getCurrentPromptHelper() {
        TerminalView view = this.adapter.getCurrentTerminalView();
        if (view == null) {
            return null;
        }
        return view.bridge.promptHelper;
    }

    protected void hideAllPrompts() {
        this.stringPromptGroup.setVisibility(8);
        this.booleanPromptGroup.setVisibility(8);
    }

    private void showEmulatedKeys(boolean showActionBar) {
        if (this.keyboardGroup.getVisibility() == 8) {
            this.keyboardGroup.startAnimation(this.keyboard_fade_in);
            this.keyboardGroup.setVisibility(0);
        }
        if (showActionBar) {
            this.actionBar.show();
        }
        autoHideEmulatedKeys();
    }

    private void autoHideEmulatedKeys() {
        if (this.keyboardGroupHider != null) {
            this.handler.removeCallbacks(this.keyboardGroupHider);
        }
        this.keyboardGroupHider = new Runnable() {
            public void run() {
                if (ConsoleActivity.this.keyboardGroup.getVisibility() != 8 && !ConsoleActivity.this.inActionBarMenu) {
                    if (!ConsoleActivity.this.keyboardAlwaysVisible) {
                        ConsoleActivity.this.keyboardGroup.startAnimation(ConsoleActivity.this.keyboard_fade_out);
                        ConsoleActivity.this.keyboardGroup.setVisibility(8);
                    }
                    ConsoleActivity.this.hideActionBarIfRequested();
                    ConsoleActivity.this.keyboardGroupHider = null;
                }
            }
        };
        this.handler.postDelayed(this.keyboardGroupHider, 3000);
    }

    private void hideEmulatedKeys() {
        if (!this.keyboardAlwaysVisible) {
            if (this.keyboardGroupHider != null) {
                this.handler.removeCallbacks(this.keyboardGroupHider);
            }
            this.keyboardGroup.setVisibility(8);
        }
        hideActionBarIfRequested();
    }

    @TargetApi(11)
    private void requestActionBar() {
        supportRequestWindowFeature(9);
    }

    public void onCreate(Bundle icicle) {
        boolean z;
        super.onCreate(icicle);
        if (VERSION.SDK_INT >= 9) {
            StrictModeSetup.run();
        }
        if (getResources().getConfiguration().keyboard == 2) {
            z = true;
        } else {
            z = false;
        }
        this.hardKeyboard = z;
        this.clipboard = (ClipboardManager) getSystemService("clipboard");
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.titleBarHide = this.prefs.getBoolean("titlebarhide", false);
        if (this.titleBarHide && VERSION.SDK_INT >= 11) {
            requestActionBar();
        }
        setContentView((int) R.layout.act_console);
        if (this.prefs.getBoolean("fullscreen", false)) {
            getWindow().setFlags(1024, 1024);
        }
        setVolumeControlStream(3);
        if (icicle == null) {
            this.requested = getIntent().getData();
        } else {
            String uri = icicle.getString("selectedUri");
            if (uri != null) {
                this.requested = Uri.parse(uri);
            }
        }
        this.inflater = LayoutInflater.from(this);
        this.toolbar = (Toolbar) findViewById(R.id.toolbar);
        this.pager = (TerminalViewPager) findViewById(R.id.console_flip);
        this.pager.addOnPageChangeListener(new SimpleOnPageChangeListener() {
            public void onPageSelected(int position) {
                ConsoleActivity.this.setTitle(ConsoleActivity.this.adapter.getPageTitle(position));
                ConsoleActivity.this.onTerminalChanged();
            }
        });
        this.adapter = new TerminalPagerAdapter();
        this.pager.setAdapter(this.adapter);
        this.empty = (TextView) findViewById(16908292);
        this.stringPromptGroup = (RelativeLayout) findViewById(R.id.console_password_group);
        this.stringPromptInstructions = (TextView) findViewById(R.id.console_password_instructions);
        this.stringPrompt = (EditText) findViewById(R.id.console_password);
        this.stringPrompt.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == 1 || keyCode != 66) {
                    return false;
                }
                String value = ConsoleActivity.this.stringPrompt.getText().toString();
                PromptHelper helper = ConsoleActivity.this.getCurrentPromptHelper();
                if (helper == null) {
                    return false;
                }
                helper.setResponse(value);
                ConsoleActivity.this.stringPrompt.setText("");
                ConsoleActivity.this.updatePromptVisible();
                return true;
            }
        });
        this.booleanPromptGroup = (RelativeLayout) findViewById(R.id.console_boolean_group);
        this.booleanPrompt = (TextView) findViewById(R.id.console_prompt);
        this.booleanYes = (Button) findViewById(R.id.console_prompt_yes);
        this.booleanYes.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                PromptHelper helper = ConsoleActivity.this.getCurrentPromptHelper();
                if (helper != null) {
                    helper.setResponse(Boolean.TRUE);
                    ConsoleActivity.this.updatePromptVisible();
                }
            }
        });
        this.booleanNo = (Button) findViewById(R.id.console_prompt_no);
        this.booleanNo.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                PromptHelper helper = ConsoleActivity.this.getCurrentPromptHelper();
                if (helper != null) {
                    helper.setResponse(Boolean.FALSE);
                    ConsoleActivity.this.updatePromptVisible();
                }
            }
        });
        this.fade_out_delayed = AnimationUtils.loadAnimation(this, R.anim.fade_out_delayed);
        this.keyboard_fade_in = AnimationUtils.loadAnimation(this, R.anim.keyboard_fade_in);
        this.keyboard_fade_out = AnimationUtils.loadAnimation(this, R.anim.keyboard_fade_out);
        this.inputManager = (InputMethodManager) getSystemService("input_method");
        this.keyboardGroup = (LinearLayout) findViewById(R.id.keyboard_group);
        this.keyboardAlwaysVisible = this.prefs.getBoolean("alwaysvisible", false);
        if (this.keyboardAlwaysVisible) {
            LayoutParams layoutParams = new LayoutParams(-1, -1);
            layoutParams.addRule(2, R.id.keyboard_group);
            this.pager.setLayoutParams(layoutParams);
            this.keyboardGroup.setVisibility(0);
        }
        this.mKeyboardButton = (ImageView) findViewById(R.id.button_keyboard);
        this.mKeyboardButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                View terminal = ConsoleActivity.this.adapter.getCurrentTerminalView();
                if (terminal != null) {
                    ((InputMethodManager) ConsoleActivity.this.getSystemService("input_method")).toggleSoftInputFromWindow(terminal.getApplicationWindowToken(), 2, 0);
                    terminal.requestFocus();
                    ConsoleActivity.this.hideEmulatedKeys();
                }
            }
        });
        findViewById(R.id.button_ctrl).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_esc).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_tab).setOnClickListener(this.emulatedKeysListener);
        addKeyRepeater(findViewById(R.id.button_up));
        addKeyRepeater(findViewById(R.id.button_up));
        addKeyRepeater(findViewById(R.id.button_down));
        addKeyRepeater(findViewById(R.id.button_left));
        addKeyRepeater(findViewById(R.id.button_right));
        findViewById(R.id.button_home).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_end).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_pgup).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_pgdn).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f1).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f2).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f3).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f4).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f5).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f6).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f7).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f8).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f9).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f10).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f11).setOnClickListener(this.emulatedKeysListener);
        findViewById(R.id.button_f12).setOnClickListener(this.emulatedKeysListener);
        this.actionBar = getSupportActionBar();
        if (this.actionBar != null) {
            this.actionBar.setDisplayHomeAsUpEnabled(true);
            if (this.titleBarHide) {
                this.actionBar.hide();
            }
            this.actionBar.addOnMenuVisibilityListener(new OnMenuVisibilityListener() {
                public void onMenuVisibilityChanged(boolean isVisible) {
                    ConsoleActivity.this.inActionBarMenu = isVisible;
                    if (!isVisible) {
                        ConsoleActivity.this.hideEmulatedKeys();
                    }
                }
            });
        }
        final HorizontalScrollView keyboardScroll = (HorizontalScrollView) findViewById(R.id.keyboard_hscroll);
        if (!this.hardKeyboard) {
            showEmulatedKeys(false);
            keyboardScroll.postDelayed(new Runnable() {
                public void run() {
                    final int xscroll = ConsoleActivity.this.findViewById(R.id.button_f12).getRight();
                    keyboardScroll.smoothScrollBy(xscroll, 0);
                    keyboardScroll.postDelayed(new Runnable() {
                        public void run() {
                            keyboardScroll.smoothScrollBy(-xscroll, 0);
                        }
                    }, 500);
                }
            }, 500);
        }
        keyboardScroll.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case 1:
                        v.performClick();
                        return true;
                    case 2:
                        ConsoleActivity.this.autoHideEmulatedKeys();
                        break;
                }
                return false;
            }
        });
        this.tabs = (TabLayout) findViewById(R.id.tabs);
        if (this.tabs != null) {
            setupTabLayoutWithViewPager();
        }
        this.pager.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                ConsoleActivity.this.showEmulatedKeys(true);
            }
        });
        this.contentView = findViewById(16908290);
        this.contentView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                Rect r = new Rect();
                ConsoleActivity.this.contentView.getWindowVisibleDisplayFrame(r);
                int screenHeight = ConsoleActivity.this.contentView.getRootView().getHeight();
                if (((double) (screenHeight - r.bottom)) > ((double) screenHeight) * 0.15d) {
                    ConsoleActivity.this.mKeyboardButton.setImageResource(R.drawable.ic_keyboard_hide);
                    ConsoleActivity.this.mKeyboardButton.setContentDescription(ConsoleActivity.this.getResources().getText(R.string.image_description_hide_keyboard));
                    return;
                }
                ConsoleActivity.this.mKeyboardButton.setImageResource(R.drawable.ic_keyboard);
                ConsoleActivity.this.mKeyboardButton.setContentDescription(ConsoleActivity.this.getResources().getText(R.string.image_description_show_keyboard));
            }
        });
    }

    private void addKeyRepeater(View view) {
        KeyRepeater keyRepeater = new KeyRepeater(this.keyRepeatHandler, view);
        view.setOnClickListener(keyRepeater);
        view.setOnTouchListener(keyRepeater);
    }

    public void setupTabLayoutWithViewPager() {
        this.tabs.setTabsFromPagerAdapter(this.adapter);
        this.pager.addOnPageChangeListener(new TabLayoutOnPageChangeListener(this.tabs));
        this.tabs.setOnTabSelectedListener(new ViewPagerOnTabSelectedListener(this.pager));
        if (this.adapter.getCount() > 0) {
            int curItem = this.pager.getCurrentItem();
            if (this.tabs.getSelectedTabPosition() != curItem) {
                this.tabs.getTabAt(curItem).select();
            }
        }
    }

    private void configureOrientation() {
        String rotateDefault;
        if (getResources().getConfiguration().keyboard == 1) {
            rotateDefault = "Force portrait";
        } else {
            rotateDefault = "Force landscape";
        }
        String rotate = this.prefs.getString("rotation", rotateDefault);
        if ("Default".equals(rotate)) {
            rotate = rotateDefault;
        }
        if ("Force landscape".equals(rotate)) {
            setRequestedOrientation(0);
            this.forcedOrientation = true;
        } else if ("Force portrait".equals(rotate)) {
            setRequestedOrientation(1);
            this.forcedOrientation = true;
        } else {
            setRequestedOrientation(-1);
            this.forcedOrientation = false;
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        boolean activeTerminal;
        boolean z = false;
        super.onCreateOptionsMenu(menu);
        TerminalView view = this.adapter.getCurrentTerminalView();
        if (view != null) {
            activeTerminal = true;
        } else {
            activeTerminal = false;
        }
        boolean sessionOpen = false;
        boolean disconnected = false;
        boolean canForwardPorts = false;
        if (activeTerminal) {
            TerminalBridge bridge = view.bridge;
            sessionOpen = bridge.isSessionOpen();
            disconnected = bridge.isDisconnected();
            canForwardPorts = bridge.canFowardPorts();
        }
        menu.setQwertyMode(true);
        this.disconnect = menu.add(R.string.list_host_disconnect);
        if (this.hardKeyboard) {
            this.disconnect.setAlphabeticShortcut('w');
        }
        if (!sessionOpen && disconnected) {
            this.disconnect.setTitle(R.string.console_menu_close);
        }
        this.disconnect.setEnabled(activeTerminal);
        this.disconnect.setIcon(17301560);
        this.disconnect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                ConsoleActivity.this.adapter.getCurrentTerminalView().bridge.dispatchDisconnect(true);
                return true;
            }
        });
        if (VERSION.SDK_INT < 11) {
            this.copy = menu.add(R.string.console_menu_copy);
            if (this.hardKeyboard) {
                this.copy.setAlphabeticShortcut('c');
            }
            MenuItemCompat.setShowAsAction(this.copy, 1);
            this.copy.setIcon(R.drawable.ic_action_copy);
            this.copy.setEnabled(activeTerminal);
            this.copy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    ConsoleActivity.this.adapter.getCurrentTerminalView().startPreHoneycombCopyMode();
                    Toast.makeText(ConsoleActivity.this, ConsoleActivity.this.getString(R.string.console_copy_start), 1).show();
                    return true;
                }
            });
        }
        this.paste = menu.add(R.string.console_menu_paste);
        if (this.hardKeyboard) {
            this.paste.setAlphabeticShortcut('v');
        }
        MenuItemCompat.setShowAsAction(this.paste, 1);
        this.paste.setIcon(R.drawable.ic_action_paste);
        this.paste.setEnabled(activeTerminal);
        this.paste.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                ConsoleActivity.this.pasteIntoTerminal();
                return true;
            }
        });
        this.portForward = menu.add(R.string.console_menu_portforwards);
        if (this.hardKeyboard) {
            this.portForward.setAlphabeticShortcut('f');
        }
        this.portForward.setIcon(17301570);
        MenuItem menuItem = this.portForward;
        if (sessionOpen && canForwardPorts) {
            z = true;
        }
        menuItem.setEnabled(z);
        this.portForward.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                TerminalBridge bridge = ConsoleActivity.this.adapter.getCurrentTerminalView().bridge;
                Intent intent = new Intent(ConsoleActivity.this, PortForwardListActivity.class);
                intent.putExtra("android.intent.extra.TITLE", bridge.host.getId());
                ConsoleActivity.this.startActivityForResult(intent, 1);
                return true;
            }
        });
        this.urlscan = menu.add(R.string.console_menu_urlscan);
        if (this.hardKeyboard) {
            this.urlscan.setAlphabeticShortcut('u');
        }
        this.urlscan.setIcon(17301583);
        this.urlscan.setEnabled(activeTerminal);
        this.urlscan.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                List<String> urls = ConsoleActivity.this.adapter.getCurrentTerminalView().bridge.scanForURLs();
                Dialog urlDialog = new Dialog(ConsoleActivity.this);
                urlDialog.setTitle(R.string.console_menu_urlscan);
                ListView urlListView = new ListView(ConsoleActivity.this);
                urlListView.setOnItemClickListener(new URLItemListener(ConsoleActivity.this));
                urlListView.setAdapter(new ArrayAdapter(ConsoleActivity.this, 17367043, urls));
                urlDialog.setContentView(urlListView);
                urlDialog.show();
                return true;
            }
        });
        this.resize = menu.add(R.string.console_menu_resize);
        if (this.hardKeyboard) {
            this.resize.setAlphabeticShortcut('s');
        }
        this.resize.setIcon(17301562);
        this.resize.setEnabled(sessionOpen);
        this.resize.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                final TerminalView terminalView = ConsoleActivity.this.adapter.getCurrentTerminalView();
                final View resizeView = ConsoleActivity.this.inflater.inflate(R.layout.dia_resize, null, false);
                new Builder(ConsoleActivity.this, R.style.AlertDialogTheme).setView(resizeView).setPositiveButton(R.string.button_resize, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            terminalView.forceSize(Integer.parseInt(((EditText) resizeView.findViewById(R.id.width)).getText().toString()), Integer.parseInt(((EditText) resizeView.findViewById(R.id.height)).getText().toString()));
                        } catch (NumberFormatException e) {
                        }
                    }
                }).setNegativeButton(17039360, null).create().show();
                return true;
            }
        });
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean activeTerminal;
        boolean z = false;
        super.onPrepareOptionsMenu(menu);
        setVolumeControlStream(5);
        TerminalView view = this.adapter.getCurrentTerminalView();
        if (view != null) {
            activeTerminal = true;
        } else {
            activeTerminal = false;
        }
        boolean sessionOpen = false;
        boolean disconnected = false;
        boolean canForwardPorts = false;
        if (activeTerminal) {
            TerminalBridge bridge = view.bridge;
            sessionOpen = bridge.isSessionOpen();
            disconnected = bridge.isDisconnected();
            canForwardPorts = bridge.canFowardPorts();
        }
        this.disconnect.setEnabled(activeTerminal);
        if (sessionOpen || !disconnected) {
            this.disconnect.setTitle(R.string.list_host_disconnect);
        } else {
            this.disconnect.setTitle(R.string.console_menu_close);
        }
        if (VERSION.SDK_INT < 11) {
            this.copy.setEnabled(activeTerminal);
        }
        this.paste.setEnabled(activeTerminal);
        MenuItem menuItem = this.portForward;
        if (sessionOpen && canForwardPorts) {
            z = true;
        }
        menuItem.setEnabled(z);
        this.urlscan.setEnabled(activeTerminal);
        this.resize.setEnabled(sessionOpen);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 16908332:
                Intent intent = new Intent(this, HostListActivity.class);
                intent.addFlags(67108864);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        setVolumeControlStream(3);
    }

    public void onStart() {
        super.onStart();
        bindService(new Intent(this, TerminalManager.class), this.connection, 1);
    }

    public void onPause() {
        super.onPause();
        Log.d("CB.ConsoleActivity", "onPause called");
        if (this.forcedOrientation && this.bound != null) {
            this.bound.setResizeAllowed(false);
        }
    }

    public void onResume() {
        super.onResume();
        Log.d("CB.ConsoleActivity", "onResume called");
        if (this.prefs.getBoolean("keepalive", true)) {
            getWindow().addFlags(128);
        } else {
            getWindow().clearFlags(128);
        }
        configureOrientation();
        if (this.forcedOrientation && this.bound != null) {
            this.bound.setResizeAllowed(true);
        }
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("CB.ConsoleActivity", "onNewIntent called");
        this.requested = intent.getData();
        if (this.requested == null) {
            Log.e("CB.ConsoleActivity", "Got null intent data in onNewIntent()");
        } else if (this.bound == null) {
            Log.e("CB.ConsoleActivity", "We're not bound in onNewIntent()");
        } else {
            TerminalBridge requestedBridge = this.bound.getConnectedBridge(this.requested.getFragment());
            int requestedIndex = 0;
            synchronized (this.pager) {
                if (requestedBridge == null) {
                    try {
                        Log.d("CB.ConsoleActivity", String.format("We couldnt find an existing bridge with URI=%s (nickname=%s),so creating one now", new Object[]{this.requested.toString(), this.requested.getFragment()}));
                        this.bound.openConnection(this.requested);
                        this.adapter.notifyDataSetChanged();
                        requestedIndex = this.adapter.getCount();
                    } catch (Exception e) {
                        Log.e("CB.ConsoleActivity", "Problem while trying to create new requested bridge from URI", e);
                        return;
                    }
                }
                int flipIndex = this.bound.getBridges().indexOf(requestedBridge);
                if (flipIndex > 0) {
                    requestedIndex = flipIndex;
                }
                setDisplayedTerminal(requestedIndex);
            }
        }
    }

    public void onStop() {
        super.onStop();
        unbindService(this.connection);
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        TerminalView currentTerminalView = this.adapter.getCurrentTerminalView();
        if (!(currentTerminalView == null || currentTerminalView.bridge.isDisconnected())) {
            this.requested = currentTerminalView.bridge.host.getUri();
            savedInstanceState.putString("selectedUri", this.requested.toString());
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    private void updateDefault() {
        TerminalView view = this.adapter.getCurrentTerminalView();
        if (view != null && this.bound != null) {
            this.bound.defaultBridge = view.bridge;
        }
    }

    protected void updateEmptyVisible() {
        this.empty.setVisibility(this.pager.getChildCount() == 0 ? 0 : 8);
    }

    protected void updatePromptVisible() {
        TerminalView view = this.adapter.getCurrentTerminalView();
        hideAllPrompts();
        if (view != null) {
            PromptHelper prompt = view.bridge.promptHelper;
            if (String.class.equals(prompt.promptRequested)) {
                this.stringPromptGroup.setVisibility(0);
                String instructions = prompt.promptInstructions;
                if (instructions == null || instructions.length() <= 0) {
                    this.stringPromptInstructions.setVisibility(8);
                } else {
                    this.stringPromptInstructions.setVisibility(0);
                    this.stringPromptInstructions.setText(instructions);
                }
                this.stringPrompt.setText("");
                this.stringPrompt.setHint(prompt.promptHint);
                this.stringPrompt.requestFocus();
            } else if (Boolean.class.equals(prompt.promptRequested)) {
                this.booleanPromptGroup.setVisibility(0);
                this.booleanPrompt.setText(prompt.promptHint);
                this.booleanYes.requestFocus();
            } else {
                hideAllPrompts();
                view.requestFocus();
            }
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        int i = 0;
        boolean z = true;
        super.onConfigurationChanged(newConfig);
        Log.d("CB.ConsoleActivity", String.format("onConfigurationChanged; requestedOrientation=%d, newConfig.orientation=%d", new Object[]{Integer.valueOf(getRequestedOrientation()), Integer.valueOf(newConfig.orientation)}));
        if (this.bound != null) {
            if (!(this.forcedOrientation && newConfig.orientation != 2 && getRequestedOrientation() == 0) && (newConfig.orientation == 1 || getRequestedOrientation() != 1)) {
                this.bound.setResizeAllowed(true);
            } else {
                this.bound.setResizeAllowed(false);
            }
            TerminalManager terminalManager = this.bound;
            if (newConfig.hardKeyboardHidden != 2) {
                z = false;
            }
            terminalManager.hardKeyboardHidden = z;
            ImageView imageView = this.mKeyboardButton;
            if (!this.bound.hardKeyboardHidden) {
                i = 8;
            }
            imageView.setVisibility(i);
        }
    }

    private void onTerminalChanged() {
        View terminalNameOverlay = findCurrentView(R.id.terminal_name_overlay);
        if (terminalNameOverlay != null) {
            terminalNameOverlay.startAnimation(this.fade_out_delayed);
        }
        updateDefault();
        updatePromptVisible();
        ActivityCompat.invalidateOptionsMenu(this);
    }

    private void setDisplayedTerminal(int requestedIndex) {
        this.pager.setCurrentItem(requestedIndex);
        setTitle(this.adapter.getPageTitle(requestedIndex));
        onTerminalChanged();
    }

    private void pasteIntoTerminal() {
        TerminalBridge bridge = this.adapter.getCurrentTerminalView().bridge;
        String clip = "";
        if (this.clipboard.hasText()) {
            clip = this.clipboard.getText().toString();
        }
        bridge.injectString(clip);
    }
}
