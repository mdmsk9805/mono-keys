package com.mono.keys;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES/GCM encryption with the key held in the AndroidKeyStore (hardware-
 *  backed where the device supports it). The key never leaves the Keystore;
 *  values at rest in SharedPreferences are ciphertext. Protection is only as
 *  strong as the device lock: anyone past the lock screen can use the app. */
public final class CryptoBox {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "mono_keys_snippets";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private final SecretKey key; // null in JVM tests -> caller injects software key

    public CryptoBox(Context ctx) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            kg.generateKey();
        }
        key = (SecretKey) ks.getKey(ALIAS, null);
    }

    /** Test-only constructor with a caller-supplied key (software JCE). */
    CryptoBox(SecretKey softwareKey) { this.key = softwareKey; }

    public String encrypt(String plain) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
        ByteBuffer bb = ByteBuffer.allocate(IV_LEN + ct.length);
        bb.put(iv).put(ct);
        return Base64.encodeToString(bb.array(), Base64.NO_WRAP);
    }

    public String decrypt(String encoded) throws Exception {
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
        byte[] iv = new byte[IV_LEN];
        System.arraycopy(all, 0, iv, 0, IV_LEN);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(c.doFinal(all, IV_LEN, all.length - IV_LEN), "UTF-8");
    }
}
