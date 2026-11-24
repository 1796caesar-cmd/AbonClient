package ua.abonclient.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-local credential storage. Password is never written in plaintext. */
final class LoginVault {
    private static final String ALIAS = "abonclient.login.aes.v1";
    private final SharedPreferences prefs;
    LoginVault(Context context) {
        prefs = context.getSharedPreferences("login_vault", Context.MODE_PRIVATE);
    }
    boolean rememberEmail() { return prefs.getBoolean("remember_email", true); }
    boolean rememberPassword() { return prefs.getBoolean("remember_password", false); }
    String email() { return rememberEmail() ? prefs.getString("email", "") : ""; }
    void setRememberEmail(boolean enabled) {
        prefs.edit().putBoolean("remember_email", enabled).apply();
        if (!enabled) { prefs.edit().remove("email").apply(); setRememberPassword(false); }
    }
    void setRememberPassword(boolean enabled) {
        prefs.edit().putBoolean("remember_password", enabled).apply();
        if (!enabled) clearPassword();
    }
    private SecretKey key(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        if (!create) throw new IllegalStateException("Missing key");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }
    void save(String email, String password) throws Exception {
        if (!rememberEmail()) { clearPassword(); return; }
        prefs.edit().putString("email", email).apply();
        if (!rememberPassword() || password == null) { clearPassword(); return; }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        cipher.updateAAD(email.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("password_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("password_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply();
    }
    String password() {
        if (!rememberPassword()) return "";
        String iv = prefs.getString("password_iv", null), encoded = prefs.getString("password_cipher", null);
        if (iv == null || encoded == null) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            cipher.updateAAD(email().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) { clearPassword(); return ""; }
    }
    void clearPassword() { prefs.edit().remove("password_iv").remove("password_cipher").apply(); }
    void clear() {
        prefs.edit().clear().putBoolean("remember_email", false).putBoolean("remember_password", false).apply();
        try { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); ks.deleteEntry(ALIAS); } catch (Exception ignored) {}
    }
}
