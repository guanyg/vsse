package vsse.security;

import vsse.proto.Filedesc.Credential;
import vsse.security.Hashs.Hash;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

public class SecurityUtil {
    private Credential credential;
    private IvParameterSpec iv;

    public SecurityUtil(Credential c) {
        this.credential = c;
        iv = new IvParameterSpec("VSSEVSSEVSSEVSSE".getBytes());
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public char FPE(char i) {
        return i;
    }

    public String FPE(String s) {
        char[] ret = s.toCharArray();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = FPE(ret[i]);
        }
        return new String(ret);
    }

    public byte[] hash(byte[] b) {
        Hash digest = Hashs.getHash();
        digest.update(b);
        return digest.digest();
//    return ("<hash " + new String(b) + ">").getBytes();
    }

    public byte[] HMAC(String i1, String i2) {
        return HMAC(i1.getBytes(), i2.getBytes());
    }

    public byte[] HMAC(byte[] i1, byte[] i2) {
        Hash digest = Hashs.getHash();
        digest.update(credential.getK0().getBytes());
        digest.update("#1234567890-".getBytes());
        digest.update(i1);
        digest.update("#1234567890-".getBytes());
        digest.update(i2);

        return digest.digest();
//
//    return ("<HMAC " + credential.getK0() + ", " + new String(i1) + "," + new String(i2) + ">")
//        .getBytes();
    }

    // import org.apache.commons.codec.binary.Base64;

    public byte[] encrypt(byte[] value) {
        String key = credential.getK1();
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            //			System.out.println("encrypted string: " + DatatypeConverter.printBase64Binary(encrypted));

            return cipher.doFinal(value);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public byte[] decrypt(byte[] encrypted) {
        String key = credential.getK1();
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            return cipher.doFinal(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

//  	public static void main(String[] args) {
//  		String key = "Bar12345Bar12345"; // 128 bit key
//  		String initVector = "RandomInitVector"; // 16 bytes IV
//
//  		System.out.println(decrypt(key, initVector, encrypt(key, initVector, "Hello World")));
//  	}


    public byte[] HMAC(List<byte[]> i1, byte[] i2) {
        Hash digest = Hashs.getHash();
        digest.update(credential.getK0().getBytes());
        digest.update("#1234567890-".getBytes());
        i1.forEach(digest::update);
        digest.update("#1234567890-".getBytes());
        digest.update(i2);

        return digest.digest();

//    return ("<HMAC " + credential.getK0() + ", " + Arrays.toString(i1.stream().map(Arrays::toString).toArray()) + "," + new String(i2) + ">")
//        .getBytes();
//    return null;
    }
}
