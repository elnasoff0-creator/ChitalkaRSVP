package ru.reader.rsvp;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class WebTransferAuth {
    private static final String ALIAS="webtransfer_password_195";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("webtransfer_auth_195",0);}
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();
    }
    static synchronized String password(Context c)throws Exception {
        String saved=prefs(c).getString("password","");
        if(saved.isEmpty()){String p=random(9);setPassword(c,p);return p;}
        String[] parts=saved.split(":",-1);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    static boolean valid(String p){return p!=null&&p.length()>=8&&p.length()<=64&&p.codePoints().noneMatch(Character::isISOControl);}
    static synchronized void setPassword(Context c,String p)throws Exception {
        if(!valid(p))throw new IllegalArgumentException("Пароль: 8–64 символа без управляющих символов");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String encrypted=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(p.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!prefs(c).edit().clear().putString("password",encrypted).commit())throw new java.io.IOException("Не удалось сохранить пароль");
    }
    static String random(int n){byte[] b=new byte[n];RANDOM.nextBytes(b);return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    static boolean equal(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static String hash(String token)throws Exception{return "token_"+Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    static synchronized void remember(Context c,String token)throws Exception {
        SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit();int count=0;long now=System.currentTimeMillis();
        for(String k:p.getAll().keySet())if(k.startsWith("token_")){if(p.getLong(k,0)<now)e.remove(k);else count++;}
        if(count>=32)for(String k:p.getAll().keySet())if(k.startsWith("token_"))e.remove(k);
        e.putLong(hash(token),now+30L*86400000L).commit();
    }
    static synchronized boolean trusted(Context c,String token){try{return token.matches("[A-Za-z0-9_-]{43}")&&prefs(c).getLong(hash(token),0)>System.currentTimeMillis();}catch(Exception e){return false;}}
    static synchronized void reset(Context c){SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit();for(String k:p.getAll().keySet())if(k.startsWith("token_"))e.remove(k);e.commit();}
}
