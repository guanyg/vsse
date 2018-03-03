package vsse.model;

import com.google.protobuf.ByteString;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import vsse.proto.Filedesc.Document;
import vsse.proto.Filedesc.Keyword;
import vsse.proto.Radix.RadixTreeEl;
import vsse.proto.Radix.TreeNodeEl;
import vsse.security.SecurityUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class RadixTree {
    private static final char[] LABELS =
            new char[]{
                    '$', '#', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
                    'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
            };
    //  private static final Logger log = Logger.getLogger(RadixTree.class.getSimpleName());
    private final SecurityUtil securityUtil;
    private Node root;
    private int fileNumber;

    public Collection<String> getKeywords() {
        return keywords;
    }

    private Collection<String> keywords;
    private List<Document> documents;
    private Map<Integer, Document> docmap;

    public RadixTree(SecurityUtil securityUtil) {
        this.securityUtil = securityUtil;
        root = new Node();
        root.label = '$';
        root.ancestors = "";
    }

    public static int getLabelIdx(byte label) {
        int idx;
        switch (label) {
            case '$':
                throw new UnsupportedOperationException();
            case '#':
                idx = 1;
                break;
            default:
                idx = Character.toLowerCase(label) - 'a' + 2;
        }
        return idx;
    }

    private void update() {
        if (securityUtil == null) return;
        root.updateHash();
    }

    public void putKeywords(Collection<String> keywords) {
        if (securityUtil == null) return;
        keywords
                .stream()
                .parallel()
                .forEach(
                        k -> {
                            Node cur = root;
                            for (byte c : k.getBytes()) {
                                synchronized (cur) {
                                    Node n = cur.children[getLabelIdx(c)];
                                    if (n == null) n = new Node(cur, (char) c);
                                    cur = n;
                                }
                            }
                            new LeafNode(cur);
                        });
        this.keywords = keywords;
    }

    public void putDocuments(List<Document> documents) {
        if (securityUtil == null) return;
        documents
                .stream()
                .sorted(
                        (o1, o2) -> o1.getId() - o2.getId())
                .forEach(
                        d -> {
                            //              log.info(String.format("%d, %s", d.getId(), Arrays.toString(lnode.bitmap)));
                            d.getKeywordsList()
                                    .parallelStream()
                                    .map(Keyword::getKeyword)
                                    .filter(keywords::contains)
                                    .forEach(
                                            w -> {
                                                GetNodeResp resp = getNodeByKeyword(w);
                                                if (!resp.isSuccess()) {
                                                    throw new UnsupportedOperationException();
                                                }
                                                synchronized (resp.getNode()) {
                                                    ((LeafNode) resp.getNode()).addDocument(d);
                                                }
                                            });
                        });
        this.documents = documents;
        this.docmap = documents.stream().collect(Collectors.toMap(Document::getId, i -> i));
        this.update();
    }

    public GetNodeResp getNodesByPrefix(String prefix) {
        GetNodeResp ret = new GetNodeResp();

        ret.setKeyword(prefix);
        int i = 0;
        Node cur = root;
        for (byte c : prefix.getBytes()) {

            Node n = cur.children[getLabelIdx(c)];
            if (n == null) {
                break;
            }
            cur = n;
            i++;
        }

        ret.setNode(cur);
        ret.setL_star(i);
        ret.setSuccess(i >= prefix.length());

        return ret;
    }

    /**
     * Get Node by Keyword
     *
     * @param keyword
     * @return
     */
    public GetNodeResp getNodeByKeyword(String keyword) {
        GetNodeResp ret = getNodesByPrefix(keyword);
        LeafNode lNode = (LeafNode) ret.getNode().children[getLabelIdx((byte) '#')];
        ret.setSuccess(ret.success && lNode != null);
        if (ret.isSuccess()) {
            ret.setNode(lNode);
        }
        return ret;
    }

    public int getFileNumber() {
        return fileNumber;
    }

    public void setFileNumber(int fileNumber) {
        if (securityUtil == null) return;
        this.fileNumber = fileNumber;
    }

    public List<Node> getMiddleNodes() {
        List<Node> ret = new LinkedList<>();
        LinkedList<Node> workQ = new LinkedList<>();
        workQ.add(root);
        while (!workQ.isEmpty()) {
            Node n = workQ.removeFirst();
            if (n instanceof LeafNode) continue;
            ret.add(n);
            workQ.addAll(
                    Arrays.stream(n.children).filter(Objects::nonNull).collect(Collectors.toList()));
        }

        return ret;
    }

    public List<LeafNode> getLeafNodes() {
        List<LeafNode> ret = new LinkedList<>();
        LinkedList<Node> workQ = new LinkedList<>();
        workQ.add(root);
        while (!workQ.isEmpty()) {
            Arrays.stream(workQ.removeFirst().getChildren())
                    .filter(Objects::nonNull)
                    .forEach(
                            n -> {
                                if (n instanceof LeafNode) {
                                    ret.add((LeafNode) n);
                                } else {
                                    workQ.addLast(n);
                                }
                            });
        }

        return ret;
    }

    public RadixTreeEl serialize() {
        return serialize(false);
    }

    public RadixTreeEl serialize(boolean simplify) {
        List<TreeNodeEl> lst = new ArrayList<>();
        LinkedList<NodeSerializeHelper> worklst = new LinkedList<>();

        worklst.add(new NodeSerializeHelper(0, -1, root));
        while (!worklst.isEmpty()) {
            NodeSerializeHelper p = worklst.remove();
            TreeNodeEl.Builder b = TreeNodeEl.newBuilder();

            b.setParentId(p.parentIdx);
            b.setLabel(p.label);
            b.setHCp(ByteString.copyFrom(p.node.hcp));

            if (p.node instanceof LeafNode) {
                LeafNode leafNode = (LeafNode) p.node;
                if (!simplify) {
                    //          b.setDes(leafNode.getDes());
                    b.setBitmap(ByteString.copyFrom(leafNode._bitmap));
                    b.setHBitmap(ByteString.copyFrom(leafNode.getHbw()));
                    b.addAllHCws(
                            leafNode
                                    .hashset
                                    .parallelStream()
                                    .map(ByteString::copyFrom)
                                    .collect(Collectors.toSet()));
                }
                b.setHCwt(ByteString.copyFrom(leafNode.hcw));
            }

            lst.add(b.build());

            Arrays.stream(p.node.children)
                    .filter(Objects::nonNull)
                    .forEach(
                            i -> {
                                synchronized (worklst) {
                                    worklst.add(new NodeSerializeHelper(lst.size() + worklst.size(), p.idx, i));
                                }
                            });
        }

        RadixTreeEl.Builder ret = RadixTreeEl.newBuilder().addAllNodes(lst);
        return ret.build();
    }

    public RadixTree load(RadixTreeEl el) {

        return load(el, null);
    }

    public RadixTree load(RadixTreeEl el, String prefix) {
        List<Node> lst = new ArrayList<>();
        for (TreeNodeEl nodeEl : el.getNodesList()) {
            Node node;
            int parentId = nodeEl.getParentId();
            Node parent = parentId >= 0 ? lst.get(parentId) : null;
            if (nodeEl.getBitmap() != null && !nodeEl.getBitmap().isEmpty()) {
                LeafNode leafNode = new LeafNode(parent);
                if (prefix == null) {
                    //          b.setDes(leafNode.getDes());
                    leafNode.setBitmap(nodeEl.getBitmap().toByteArray());
                    leafNode.setHbw(nodeEl.getHBitmap().toByteArray());
                    leafNode.setHashset(
                            nodeEl
                                    .getHCwsList()
                                    .parallelStream()
                                    .map(ByteString::toByteArray)
                                    .collect(Collectors.toList()));
                }
                leafNode.setHcw(nodeEl.getHCwt().toByteArray());
                //        leafNode.setDes(nodeEl.getDes());
                node = leafNode;
            } else if (parent != null) {
                node = new Node(parent, (char) nodeEl.getLabel());
            } else {
                node = root;
                if (prefix != null) {
                    root.setAncestors("$" + prefix);
                    root.setLabel((char) nodeEl.getLabel());
                }
            }
            node.setHcp(nodeEl.getHCp().toByteArray());

            lst.add(node);
        }

        return this;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public Map<Integer, Document> getDocmap() {
        return docmap;
    }

    public static class GetNodeResp {
        private boolean success;
        private int l_star;
        private Node node;
        private String keyword;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getL_star() {
            return l_star;
        }

        public void setL_star(int l_star) {
            this.l_star = l_star;
        }

        public Node getNode() {
            return node;
        }

        public void setNode(Node node) {
            this.node = node;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }

    private static class NodeSerializeHelper {
        int parentIdx;
        char label;
        Node node;
        private int idx;

        public NodeSerializeHelper(int idx, int parentIdx, char label, Node node) {
            this.idx = idx;
            this.parentIdx = parentIdx;
            this.label = label;
            this.node = node;
        }

        public NodeSerializeHelper(int idx, int parentIdx, Node node) {
            this(idx, parentIdx, node.getLabel(), node);
        }
    }

    public class Node {
        private char label;
        private byte[] hcp;
        private String ancestors;
        private Node[] children;
        private String Lc;

        public Node() {
            this.children = new Node[LABELS.length];
            this.ancestors = "";
        }

        public Node(Node parent, char label) {
            this();
            int idx = getLabelIdx((byte) label);
            parent.children[idx] = this;
            this.ancestors = parent.ancestors + parent.label;
            this.label = label;
        }

        public void updateHash() {
            StringBuilder childrenSet = new StringBuilder();
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    childrenSet.append(LABELS[i]);
                    children[i].updateHash();
                }
            }
            hcp = securityUtil.HMAC(ancestors + label, childrenSet.toString());
            this.Lc = childrenSet.toString();
        }

        public String getLc() {
            if (Lc == null) {
                StringBuilder childrenSet = new StringBuilder();
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        childrenSet.append(LABELS[i]);
                    }
                }
                Lc = childrenSet.toString();
            }
            return Lc;
        }

        public char getLabel() {
            return label;
        }

        public void setLabel(char label) {
            this.label = label;
        }

        public byte[] getHcp() {
            return hcp;
        }

        public void setHcp(byte[] hcp) {
            this.hcp = hcp;
        }

        public String getAncestors() {
            return ancestors;
        }

        public void setAncestors(String ancestors) {
            this.ancestors = ancestors;
        }

        public Node[] getChildren() {
            return children;
        }

        public void setChildren(Node[] children) {
            this.children = children;
        }

        public RadixTree getSubtree() {
            RadixTree t = new RadixTree(securityUtil);
            t.root = this;
            return t;
        }
    }

    public class LeafNode extends Node {
        private String w;
        private byte[] hcw;
        private RoaringBitmap bitmap;
        private byte[] hbw;
        private List<byte[]> hashset;
        private List<byte[]> files;
        private String des = "";
        private byte[] _bitmap;

        public LeafNode(Node parent) {
            super(parent, '#');
            //      this.bitmap = new byte[(int) Math.ceil((fileNumber) / 8.0)];
            this.bitmap = new RoaringBitmap();
            this.hashset = new ArrayList<>();
            this.files = new ArrayList<>();
            this.w = this.getAncestors().substring(1);
        }

        public String getDes() {
            return des;
        }

        public void setDes(String des) {
            this.des = des;
        }

        public void addDocument(Document d) {
            this.bitmap.add(d.getId());
            // lnode.bitmap[d.getId() / 8] |= (0x01 << (d.getId() % 8));

            this.files.add(d.getCipher().toByteArray());
            //      this.fileHashes.add(securityUtil.hash(d.getCipher().toByteArray()));
            this.hashset.add(securityUtil.HMAC(d.getCipher().toByteArray(), this.w.getBytes()));
            this.setDes(this.getDes() + "," + d.getId());
            this._bitmap = null;
        }

        public String getW() {
            return w;
        }

        public byte[] getHcw() {
            return hcw;
        }

        public void setHcw(byte[] hcw) {
            this.hcw = hcw;
        }

        public RoaringBitmap getBitmapObject() {
            return bitmap;
        }

        public void setBitmapObject(RoaringBitmap bitmap) {
            this.bitmap = bitmap;
        }

        public byte[] getBitmap() {
            if (_bitmap == null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                try {
                    bitmap.serialize(dos);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                _bitmap = bos.toByteArray();
            }
            return _bitmap;
        }

        public void setBitmap(byte[] bitmap) {
            this.bitmap = new ImmutableRoaringBitmap(ByteBuffer.wrap(bitmap)).toRoaringBitmap();
        }

        public byte[] getHbw() {
            return hbw;
        }

        public void setHbw(byte[] hbw) {
            this.hbw = hbw;
        }

        public List<byte[]> getHashset() {
            return hashset;
        }

        public void setHashset(List<byte[]> hashset) {
            this.hashset = hashset;
        }

        @Override
        public void updateHash() {
            super.updateHash();
            hcw = securityUtil.HMAC(files, w.getBytes());
            hbw = securityUtil.HMAC(getBitmap(), w.getBytes());
        }

        //    public void appendDocument(Document doc) {
        //      fileHashes.append(new String(doc.getHash().toByteArray()));
        //      hashset.add(securityUtil.HMAC(doc.getCipher().toByteArray(), w.getBytes()));
        //      bitmap[doc.getId() / 8] |= 0x1 << (doc.getId() % 8);
        //    }
    }
}
