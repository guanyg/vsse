package vsse.util;

import vsse.proto.Filedesc.Credential;
import vsse.security.RandomString;

public class Util {

    public static Credential createCredential() {
        Credential.Builder c = Credential.newBuilder();
        RandomString r = new RandomString();
        c.setK(RandomString.randomAZ());
        c.setK0(r.nextString());
        c.setK1(r.nextString());
        return c.build();
    }

    public static String getJarName(Class cls) {
        return cls.getProtectionDomain().getCodeSource().getLocation().getFile();
    }

}
