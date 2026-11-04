package com.coolguy1353.cglogin;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    static PasswordRecord hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt);
        return new PasswordRecord(Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash));
    }

    static boolean verify(String password, String encodedSalt, String encodedHash) {
        if (encodedSalt == null || encodedHash == null) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(encodedSalt);
            byte[] expected = Base64.getDecoder().decode(encodedHash);
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }

    record PasswordRecord(String salt, String hash) {
    }
}