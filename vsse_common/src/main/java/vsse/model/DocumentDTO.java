package vsse.model;

import com.google.protobuf.ByteString;
import vsse.proto.Filedesc;

public class DocumentDTO {
    private int id;
    private String content;
    private byte[] cipher;
    private String[] keywords;

    public static DocumentDTO parse(Filedesc.Document document) {
        return new DocumentDTO()
                .setId(document.getId())
                .setCipher(document.getCipher().toByteArray());
    }

    public Filedesc.Document build() {
        return Filedesc.Document.newBuilder()
                .setId(this.getId())
                .setCipher(ByteString.copyFrom(this.getCipher()))
                .build();
    }

    public byte[] getCipher() {
        return cipher;
    }

    public DocumentDTO setCipher(byte[] cipher) {
        this.cipher = cipher;
        return this;
    }

    public int getId() {
        return id;
    }

    public DocumentDTO setId(int id) {
        this.id = id;
        return this;
    }

    public String getContent() {
        return content;
    }

    public DocumentDTO setContent(String content) {
        this.content = content;
        return this;
    }

    public String[] getKeywords() {
        if (keywords != null)
            return keywords;
        return new String[0];
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public String getAbstract() {
        if (content == null)
            return "<EMPTY>";
        return content.split("[\r\n]")[0];
    }
}
