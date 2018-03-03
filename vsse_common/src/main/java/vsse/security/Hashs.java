package vsse.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Hashs {
    private static Class<? extends Hash> cls = SHA256.class;

    public static Hash getHash() {
        try {
            return cls.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface Hash {
        void update(byte[] b);

        byte[] digest();
    }

    public static class Debug implements Hash {
        List<byte[]> lst = new ArrayList<>();

        @Override
        public void update(byte[] b) {
            lst.add(b);
        }

        @Override
        public byte[] digest() {
            return Arrays.toString(lst.stream().map(Arrays::toString).toArray()).getBytes();
        }
    }

    public static class SHA256 implements Hash {
        private MessageDigest digest;

        public SHA256() {
            digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void update(byte[] b) {
            digest.update(b);
        }

        @Override
        public byte[] digest() {
            return digest.digest();
        }
    }
}
