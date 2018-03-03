package vsse.util;

import vsse.proto.Filedesc.Credential;
import vsse.security.RandomString;

import java.util.function.Function;

public class Util {

    public static Credential createCredential() {
        Credential.Builder c = Credential.newBuilder();
        RandomString r = new RandomString();
        c.setK(r.nextString());
        c.setK0(r.nextString());
        c.setK1(r.nextString());
        return c.build();
    }

    public static String getJarName(Class cls) {
        return cls.getProtectionDomain().getCodeSource().getLocation().getFile();
    }

}
