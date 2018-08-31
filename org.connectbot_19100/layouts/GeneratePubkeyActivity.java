package org.connectbot;

import android.app.ProgressDialog;
import android.graphics.PorterDuff.Mode;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import com.trilead.ssh2.signature.ECDSASHA2Verify;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import org.connectbot.bean.PubkeyBean;
import org.connectbot.util.Ed25519Provider;
import org.connectbot.util.EntropyDialog;
import org.connectbot.util.EntropyView;
import org.connectbot.util.OnEntropyGatheredListener;
import org.connectbot.util.OnKeyGeneratedListener;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

public class GeneratePubkeyActivity extends AppCompatActivity implements OnEntropyGatheredListener, OnKeyGeneratedListener {
    private static final int[] ECDSA_SIZES = ECDSASHA2Verify.getCurveSizes();
    private int bits;
    private SeekBar bitsSlider;
    private EditText bitsText;
    private CheckBox confirmUse;
    private byte[] entropy;
    private LayoutInflater inflater = null;
    private KeyType keyType;
    private OnKeyGeneratedListener listener;
    private EditText nickname;
    private EditText password1;
    private EditText password2;
    private ProgressDialog progress;
    private Button save;
    private final TextWatcher textChecker = new TextWatcher() {
        public void afterTextChanged(Editable s) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            GeneratePubkeyActivity.this.checkEntries();
        }
    };
    private CheckBox unlockAtStartup;

    private static class KeyGeneratorRunnable implements Runnable {
        private final byte[] entropy;
        private final String keyType;
        private final OnKeyGeneratedListener listener;
        private final int numBits;

        KeyGeneratorRunnable(String keyType, int numBits, byte[] entropy, OnKeyGeneratedListener listener) {
            this.keyType = keyType;
            this.numBits = numBits;
            this.entropy = entropy;
            this.listener = listener;
        }

        public void run() {
            SecureRandom random = new SecureRandom();
            random.nextInt();
            random.setSeed(this.entropy);
            try {
                KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(this.keyType);
                keyPairGen.initialize(this.numBits, random);
                this.listener.onGenerationSuccess(keyPairGen.generateKeyPair());
            } catch (Exception e) {
                this.listener.onGenerationError(e);
            }
        }
    }

    private enum KeyType {
        RSA("RSA", 1024, 16384, 2048),
        DSA("DSA", 1024, 1024, 1024),
        EC("EC", GeneratePubkeyActivity.ECDSA_SIZES[0], GeneratePubkeyActivity.ECDSA_SIZES[GeneratePubkeyActivity.ECDSA_SIZES.length - 1], GeneratePubkeyActivity.ECDSA_SIZES[0]),
        ED25519("ED25519", 256, 256, 256);
        
        public final int defaultBits;
        public final int maximumBits;
        public final int minimumBits;
        public final String name;

        private KeyType(String name, int minimumBits, int maximumBits, int defaultBits) {
            this.name = name;
            this.minimumBits = minimumBits;
            this.maximumBits = maximumBits;
            this.defaultBits = defaultBits;
        }
    }

    static {
        Ed25519Provider.insertIfNeeded();
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView((int) R.layout.act_generatepubkey);
        this.nickname = (EditText) findViewById(R.id.nickname);
        RadioGroup keyTypeGroup = (RadioGroup) findViewById(R.id.key_type);
        this.bitsText = (EditText) findViewById(R.id.bits);
        this.bitsSlider = (SeekBar) findViewById(R.id.bits_slider);
        this.password1 = (EditText) findViewById(R.id.password1);
        this.password2 = (EditText) findViewById(R.id.password2);
        this.unlockAtStartup = (CheckBox) findViewById(R.id.unlock_at_startup);
        this.confirmUse = (CheckBox) findViewById(R.id.confirm_use);
        this.save = (Button) findViewById(R.id.save);
        this.inflater = LayoutInflater.from(this);
        this.nickname.addTextChangedListener(this.textChecker);
        this.password1.addTextChangedListener(this.textChecker);
        this.password2.addTextChangedListener(this.textChecker);
        setKeyType(KeyType.RSA);
        if (Security.getProviders("KeyPairGenerator.EC") == null) {
            ((RadioButton) findViewById(R.id.ec)).setEnabled(false);
        }
        keyTypeGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rsa) {
                    GeneratePubkeyActivity.this.setKeyType(KeyType.RSA);
                } else if (checkedId == R.id.dsa) {
                    GeneratePubkeyActivity.this.setKeyType(KeyType.DSA);
                } else if (checkedId == R.id.ec) {
                    GeneratePubkeyActivity.this.setKeyType(KeyType.EC);
                } else if (checkedId == R.id.ed25519) {
                    GeneratePubkeyActivity.this.setKeyType(KeyType.ED25519);
                }
            }
        });
        this.bitsSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
                GeneratePubkeyActivity.this.setBits(GeneratePubkeyActivity.this.keyType.minimumBits + progress);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                GeneratePubkeyActivity.this.setBits(GeneratePubkeyActivity.this.bits);
            }
        });
        this.bitsText.setOnFocusChangeListener(new OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    int newBits;
                    try {
                        newBits = Integer.parseInt(GeneratePubkeyActivity.this.bitsText.getText().toString());
                    } catch (NumberFormatException e) {
                        newBits = GeneratePubkeyActivity.this.keyType.defaultBits;
                    }
                    GeneratePubkeyActivity.this.setBits(newBits);
                }
            }
        });
        this.save.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                GeneratePubkeyActivity.this.save.setEnabled(false);
                GeneratePubkeyActivity.this.startEntropyGather();
            }
        });
    }

    private void setKeyType(KeyType newKeyType) {
        this.keyType = newKeyType;
        resetBitDefaults();
        switch (newKeyType) {
            case RSA:
            case EC:
                setAllowBitStrengthChange(true);
                return;
            case DSA:
            case ED25519:
                setAllowBitStrengthChange(false);
                return;
            default:
                throw new AssertionError("Impossible key type encountered");
        }
    }

    private void setAllowBitStrengthChange(boolean enabled) {
        this.bitsSlider.setEnabled(enabled);
        this.bitsText.setEnabled(enabled);
    }

    private void resetBitDefaults() {
        this.bitsSlider.setMax(this.keyType.maximumBits - this.keyType.minimumBits);
        setBits(this.keyType.defaultBits);
    }

    private void setBits(int newBits) {
        if (newBits < this.keyType.minimumBits || newBits > this.keyType.maximumBits) {
            newBits = this.keyType.defaultBits;
        }
        if (this.keyType == KeyType.EC) {
            this.bits = getClosestFieldSize(newBits);
        } else {
            this.bits = newBits - (newBits % 8);
        }
        this.bitsSlider.setProgress(newBits - this.keyType.minimumBits);
        this.bitsText.setText(String.valueOf(this.bits));
    }

    private void checkEntries() {
        boolean allowSave = true;
        if (!this.password1.getText().toString().equals(this.password2.getText().toString())) {
            allowSave = false;
        }
        if (this.nickname.getText().length() == 0) {
            allowSave = false;
        }
        if (allowSave) {
            this.save.getBackground().setColorFilter(getResources().getColor(R.color.accent), Mode.SRC_IN);
        } else {
            this.save.getBackground().setColorFilter(null);
        }
        this.save.setEnabled(allowSave);
    }

    private void startEntropyGather() {
        View entropyView = this.inflater.inflate(R.layout.dia_gatherentropy, null, false);
        ((EntropyView) entropyView.findViewById(R.id.entropy)).addOnEntropyGatheredListener(this);
        new EntropyDialog(this, entropyView).show();
    }

    public void onEntropyGathered(byte[] entropy) {
        if (entropy == null) {
            finish();
            return;
        }
        this.entropy = (byte[]) entropy.clone();
        int numSetBits = 0;
        for (int i = 0; i < 20; i++) {
            numSetBits += measureNumberOfSetBits(this.entropy[i]);
        }
        Log.d("CB.GeneratePubkeyAct", "Entropy gathered; population of ones is " + ((int) (100.0d * (((double) numSetBits) / 160.0d))) + "%");
        startKeyGen();
    }

    private void startKeyGen() {
        this.progress = new ProgressDialog(this);
        this.progress.setMessage(getResources().getText(R.string.pubkey_generating));
        this.progress.setIndeterminate(true);
        this.progress.setCancelable(false);
        this.progress.show();
        Log.d("CB.GeneratePubkeyAct", "Starting generation of " + this.keyType + " of strength " + this.bits);
        Thread keyGenThread = new Thread(new KeyGeneratorRunnable(this.keyType.name, this.bits, this.entropy, this));
        keyGenThread.setName("KeyGen " + this.keyType + " " + this.bits);
        keyGenThread.start();
    }

    public void onGenerationSuccess(KeyPair pair) {
        boolean encrypted = false;
        try {
            PrivateKey priv = pair.getPrivate();
            PublicKey pub = pair.getPublic();
            String secret = this.password1.getText().toString();
            if (secret.length() > 0) {
                encrypted = true;
            }
            Log.d("CB.GeneratePubkeyAct", "public: " + PubkeyUtils.formatKey(pub));
            PubkeyBean pubkey = new PubkeyBean();
            pubkey.setNickname(this.nickname.getText().toString());
            pubkey.setType(this.keyType.name);
            pubkey.setPrivateKey(PubkeyUtils.getEncodedPrivate(priv, secret));
            pubkey.setPublicKey(pub.getEncoded());
            pubkey.setEncrypted(encrypted);
            pubkey.setStartup(this.unlockAtStartup.isChecked());
            pubkey.setConfirmUse(this.confirmUse.isChecked());
            PubkeyDatabase.get(this).savePubkey(pubkey);
        } catch (Exception e) {
            Log.e("CB.GeneratePubkeyAct", "Could not generate key pair");
            e.printStackTrace();
        }
        if (this.listener != null) {
            this.listener.onGenerationSuccess(pair);
        }
        dismissActivity();
    }

    public void onGenerationError(Exception e) {
        Log.e("CB.GeneratePubkeyAct", "Could not generate key pair");
        e.printStackTrace();
        if (this.listener != null) {
            this.listener.onGenerationError(e);
        }
        dismissActivity();
    }

    private void dismissActivity() {
        runOnUiThread(new Runnable() {
            public void run() {
                GeneratePubkeyActivity.this.progress.dismiss();
                GeneratePubkeyActivity.this.finish();
            }
        });
    }

    private int measureNumberOfSetBits(byte b) {
        int numSetBits = 0;
        for (int i = 0; i < 8; i++) {
            if ((b & 1) == 1) {
                numSetBits++;
            }
            b = (byte) (b >> 1);
        }
        return numSetBits;
    }

    private int getClosestFieldSize(int bits) {
        int outBits = ECDSA_SIZES[0];
        int distance = Math.abs(bits - outBits);
        for (int i = 1; i < ECDSA_SIZES.length; i++) {
            int thisDistance = Math.abs(bits - ECDSA_SIZES[i]);
            if (thisDistance < distance) {
                distance = thisDistance;
                outBits = ECDSA_SIZES[i];
            }
        }
        return outBits;
    }
}
