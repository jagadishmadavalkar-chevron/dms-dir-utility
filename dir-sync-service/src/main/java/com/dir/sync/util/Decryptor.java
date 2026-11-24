package com.dir.sync.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class Decryptor {

    private static final String ALGORITHM = "AES";
    private static final String SALT       = "SECRET";

    private SecretKeySpec buildKey() throws Exception {
        byte[] key = SALT.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        key = sha.digest(key);
        key = Arrays.copyOf(key, 32);
        return new SecretKeySpec(key, ALGORITHM);
    }

    public String encrypt(String value) throws Exception {
        SecretKeySpec secretKey = buildKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    public String decrypt(String encrypted) throws Exception {
        SecretKeySpec secretKey = buildKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
    }

    public static void main(String[] args) throws Exception {
        Decryptor d = new Decryptor();
        if (args.length == 2 && "encrypt".equals(args[0])) {
            System.out.println(d.encrypt(args[1]));
        } else if (args.length == 2 && "decrypt".equals(args[0])) {
            System.out.println(d.decrypt(args[1]));
        } else {
            System.out.println("Usage: Decryptor encrypt|decrypt <value>");
        }
    }
}
