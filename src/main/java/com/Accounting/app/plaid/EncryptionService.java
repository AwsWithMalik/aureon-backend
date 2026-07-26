package com.Accounting.app.plaid;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {
    private final String secretKey;

    public EncryptionService(@Value("${app.token-encryption-key:}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be set");
        }
        int length = secretKey.getBytes().length;
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be 16, 24, or 32 bytes for AES");
        }
        this.secretKey = secretKey;
    }

    public String encrypt(String plainText) {

        try {
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), "AES");

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] encryptBytes = cipher.doFinal(plainText.getBytes());

            return Base64.getEncoder().encodeToString(encryptBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting token", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), "AES");

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);

            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            return new String(decryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException("Error decrypting token", e);
        }
    }

    public String decryptIfEncrypted(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }

        try {
            return decrypt(token);
        } catch (RuntimeException ex) {
            return token;
        }
    }
}
