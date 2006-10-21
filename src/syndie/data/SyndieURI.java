package syndie.data;

import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.util.*;
import net.i2p.data.*;
import syndie.Constants;

/**
 * Maintain a reference within syndie per the syndie URN spec, including canonical
 * encoding and decoding
 *
 */
public class SyndieURI {
    private TreeMap _attributes;
    private String _type;
    private transient String _stringified;
    
    public SyndieURI(String encoded) throws URISyntaxException {
        fromString(encoded);
    }
    public SyndieURI(String type, TreeMap attributes) {
        if ( (type == null) || (type.trim().length() <= 0) || (attributes == null) ) 
            throw new IllegalArgumentException("Invalid attributes or type");
        _type = type;
        _attributes = attributes;
    }
    public SyndieURI(String type, Map attributes) {
        this(type, new TreeMap(attributes));
    }
    
    public static SyndieURI createSearch(String searchString) {
        String searchURI = "urn:syndie:search:d7:keyword" + searchString.length() + ":" + searchString + "e";
        try {
            return new SyndieURI(searchURI);
        } catch (URISyntaxException use) {
            throw new RuntimeException("Hmm, encoded search URI is not valid: " + use.getMessage() + " [" + searchURI + "]");
        }
    }

    public static SyndieURI createURL(String url) {
        StringBuffer buf = new StringBuffer();
        buf.append("urn:syndie:url:d");
        if (url != null)
            buf.append("3:url").append(url.length()).append(":").append(url);
        buf.append("e");
        try {
            return new SyndieURI(buf.toString());
        } catch (URISyntaxException use) {
            System.err.println("attempted: " + buf.toString());
            use.printStackTrace();
            return null;
        }
    }
    public static SyndieURI createArchive(String url, String pass) {
        StringBuffer buf = new StringBuffer();
        buf.append("urn:syndie:archive:d");
        if (url != null)
            buf.append("3:url").append(url.length()).append(':').append(url);
        if (pass != null) {
            buf.append("11:postKeyType4:pass11:postKeyData");
            String base64Pass = Base64.encode(DataHelper.getUTF8(pass));
            buf.append(base64Pass.length()).append(':').append(base64Pass);
        }
        buf.append("e");
        try {
            return new SyndieURI(buf.toString());
        } catch (URISyntaxException use) {
            System.err.println("attempted: " + buf.toString());
            use.printStackTrace();
            return null;
        }
    }
    public static SyndieURI createScope(Hash scope) { return createMessage(scope, -1, -1); }
    public static SyndieURI createMessage(Hash scope, long msgId) { return createMessage(scope, msgId, -1); }
    public static SyndieURI createMessage(Hash scope, long msgId, int pageNum) {
        StringBuffer buf = new StringBuffer();
        buf.append("urn:syndie:channel:d");
        if (scope != null) {
            buf.append("7:channel");
            String ch = scope.toBase64();
            buf.append(ch.length()).append(':').append(ch);
            if (msgId >= 0) {
                buf.append("9:messageIdi").append(msgId).append("e");
                if (pageNum >= 0)
                    buf.append("4:pagei").append(pageNum).append("e");
            }
        }
        buf.append('e');
        try {
            return new SyndieURI(buf.toString());
        } catch (URISyntaxException use) {
            System.err.println("attempted: " + buf.toString());
            use.printStackTrace();
            return null;
        }
    }
    

    /**
     * Create a URI that includes the given read key for the specified channel
     */
    public static SyndieURI createKey(Hash scope, SessionKey sessionKey) {
        StringBuffer buf = new StringBuffer();
        buf.append("urn:syndie:channel:d");
        if (scope != null) {
            buf.append("7:channel");
            String ch = scope.toBase64();
            buf.append(ch.length()).append(':').append(ch);
            buf.append("7:readKey");
            ch = Base64.encode(sessionKey.getData());
            buf.append(ch.length()).append(':').append(ch);
        }
        buf.append('e');
        try {
            return new SyndieURI(buf.toString());
        } catch (URISyntaxException use) {
            System.err.println("attempted: " + buf.toString());
            use.printStackTrace();
            return null;
        }
    }

    /**
     * Create a URI that includes the given post or manage key for the specified channel
     */    
    public static SyndieURI createKey(Hash scope, String function, SigningPrivateKey priv) {
        StringBuffer buf = new StringBuffer();
        buf.append("urn:syndie:channel:d");
        if (scope != null) {
            buf.append("7:channel");
            String ch = scope.toBase64();
            buf.append(ch.length()).append(':').append(ch);
            if (function.equalsIgnoreCase(Constants.KEY_FUNCTION_POST))
                buf.append("7:postKey");
            else if (function.equalsIgnoreCase(Constants.KEY_FUNCTION_MANAGE))
                buf.append("9:manageKey");
            ch = Base64.encode(priv.getData());
            buf.append(ch.length()).append(':').append(ch);
        }
        buf.append('e');
        try {
            return new SyndieURI(buf.toString());
        } catch (URISyntaxException use) {
            System.err.println("attempted: " + buf.toString());
            use.printStackTrace();
            return null;
        }
    }
    
    /**
     * Create a URI that includes the private key to decrypt replies for the channel
     */
    public static SyndieURI createKey(Hash scope, PrivateKey priv) {
        StringBuffer buf = new StringBuffer();
        buf.append("urn:syndie:channel:d");
        if (scope != null) {
            buf.append("7:channel");
            String ch = scope.toBase64();
            buf.append(ch.length()).append(':').append(ch);
            buf.append("8:replyKey");
            ch = Base64.encode(priv.getData());
            buf.append(ch.length()).append(':').append(ch);
        }
        buf.append('e');
        try {
            return new SyndieURI(buf.toString());
        } catch (URISyntaxException use) {
            System.err.println("attempted: " + buf.toString());
            use.printStackTrace();
            return null;
        }
    }
    
    private static final String TYPE_URL = "url";
    private static final String TYPE_CHANNEL = "channel";
    private static final String TYPE_ARCHIVE = "archive";
    private static final String TYPE_TEXT = "text";
    
    /** does this this URI maintain a reference to a URL? */
    public boolean isURL() { return TYPE_URL.equals(_type); }
    /** does this this URI maintain a reference to a syndie channel/message/page/attachment? */
    public boolean isChannel() { return TYPE_CHANNEL.equals(_type); }
    /** does this this URI maintain a reference to a syndie archive? */
    public boolean isArchive() { return TYPE_ARCHIVE.equals(_type); }
    /** does this this URI maintain a reference to a URL? */
    public boolean isText() { return TYPE_TEXT.equals(_type); }
    
    public String getType() { return _type; }
    public Map getAttributes() { return _attributes; }
    
    public String getString(String key) { return (String)_attributes.get(key); }
    public Long getLong(String key) { return (Long)_attributes.get(key); }
    public String[] getStringArray(String key) { return (String[])_attributes.get(key); }
    public boolean getBoolean(String key, boolean defaultVal) {
        Object o = _attributes.get(key);
        if (o == null) return defaultVal;
        if (o instanceof Boolean)
            return ((Boolean)o).booleanValue();
        String str = o.toString();
        if (str == null)
            return defaultVal;
        else
            return Boolean.valueOf(str).booleanValue();
    }
    public Hash getScope() { return getHash("channel"); }
    private Hash getHash(String key) {
        String val = (String)_attributes.get(key);
        if (val != null) {
            byte b[] = Base64.decode(val);
            if ( (b != null) && (b.length == Hash.HASH_LENGTH) )
                return new Hash(b);
        }
        return null;
    }
    public SessionKey getReadKey() {
        byte val[] = getBytes("readKey");
        if ( (val != null) && (val.length == SessionKey.KEYSIZE_BYTES) )
            return new SessionKey(val);
        else
            return null;
    }
    public SigningPrivateKey getPostKey() {
        byte val[] = getBytes("postKey");
        if ( (val != null) && (val.length == SigningPrivateKey.KEYSIZE_BYTES) )
            return new SigningPrivateKey(val);
        else
            return null;
    }
    public SigningPrivateKey getManageKey() {
        byte val[] = getBytes("manageKey");
        if ( (val != null) && (val.length == SigningPrivateKey.KEYSIZE_BYTES) )
            return new SigningPrivateKey(val);
        else
            return null;
    }
    public PrivateKey getReplyKey() {
        byte val[] = getBytes("replyKey");
        if ( (val != null) && (val.length == PrivateKey.KEYSIZE_BYTES) )
            return new PrivateKey(val);
        else
            return null;
    }
    private byte[] getBytes(String key) {
        String val = (String)_attributes.get(key);
        if (val != null)
            return Base64.decode(val);
        else
            return null;
    }
    public Long getMessageId() { return getLong("messageId"); }
    
    public void fromString(String bencodedURI) throws URISyntaxException {
        if (bencodedURI == null) throw new URISyntaxException("null URI", "no uri");
        if (bencodedURI.startsWith("urn:syndie:"))
            bencodedURI = bencodedURI.substring("urn:syndie:".length());
        int endType = bencodedURI.indexOf(':');
        if (endType <= 0)
            throw new URISyntaxException(bencodedURI, "Missing type");
        if (endType >= bencodedURI.length())
            throw new URISyntaxException(bencodedURI, "No bencoded attributes");
        _type = bencodedURI.substring(0, endType);
        bencodedURI = bencodedURI.substring(endType+1);
        _attributes = bdecode(bencodedURI);
        if (_attributes == null) {
            throw new URISyntaxException(bencodedURI, "Invalid bencoded attributes");
        }
    }
    public String toString() {
        if (_stringified == null)
            _stringified = "urn:syndie:" + _type + ":" + bencode(_attributes);
        return _stringified;
    }
    
    public boolean equals(Object obj) { return toString().equals(obj.toString()); }
    public int hashCode() { return toString().hashCode(); }
    
    public static void main(String args[]) { test(); }
    private static void test() {
        try {
            new SyndieURI("urn:syndie:channel:d7:channel40:12345678901234567890123456789012345678908:showRefs4:truee");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (!test(new TreeMap()))
            throw new RuntimeException("failed on empty");
        if (!test(createStrings()))
            throw new RuntimeException("failed on strings");
        if (!test(createList()))
            throw new RuntimeException("failed on list");
        if (!test(createMixed()))
            throw new RuntimeException("failed on mixed");
        if (!test(createMultiMixed()))
            throw new RuntimeException("failed on multimixed");
        System.out.println("Passed all tests");
    }
    private static TreeMap createStrings() {
        TreeMap m = new TreeMap();
        for (int i = 0; i < 64; i++)
            m.put("key" + i, "val" + i);
        return m;
    }
    private static TreeMap createList() {
        TreeMap m = new TreeMap();
        for (int i = 0; i < 8; i++)
            m.put("key" + i, "val" + i);
        String str[] = new String[] { "stringElement1", "stringElement2", "stringElement3" };
        m.put("stringList", str);
        return m;
    }
    private static TreeMap createMixed() {
        TreeMap m = new TreeMap();
        for (int i = 0; i < 8; i++)
            m.put("key" + i, "val" + i);
        String str[] = new String[] { "stringElement1", "stringElement2", "stringElement3" };
        m.put("stringList", str);
        for (int i = 8; i < 16; i++)
            m.put("intKey" + i, (i%2==0?(Number)(new Long(i)):(Number)(new Integer(i))));
        return m;
    }
    private static TreeMap createMultiMixed() {
        TreeMap m = new TreeMap();
        for (int i = 0; i < 8; i++)
            m.put("key" + i, "val" + i);
        for (int i = 0; i < 10; i++) {
            String str[] = new String[] { "stringElement1", "stringElement2", "stringElement3" };
            m.put("stringList" + i, str);
        }
        for (int i = 8; i < 16; i++)
            m.put("intKey" + i, (i%2==0?(Number)(new Long(i)):(Number)(new Integer(i))));
        return m;
    }
    private static boolean test(TreeMap orig) {
        String enc = bencode(orig);
        System.out.println("bencoded: " + enc);
        TreeMap decoded = null;
        try {
            decoded = bdecode(enc);
        } catch (URISyntaxException use) {
            use.printStackTrace();
        }
        if (decoded == null) return false;
        Set origKeys = new HashSet(orig.keySet());
        Set decKeys = new HashSet(decoded.keySet());
        if (origKeys.equals(decKeys)) {
            for (Iterator iter = origKeys.iterator(); iter.hasNext(); ) {
                String k = (String)iter.next();
                Object origVal = orig.get(k);
                Object decVal = decoded.get(k);
                if (origVal.getClass().isArray()) {
                    boolean ok = Arrays.equals((String[])origVal, (String[])decVal);
                    if (!ok) {
                        System.out.println("key " + k + " is an unequal array");
                        return false;
                    }
                } else if (origVal instanceof Number) {
                    long o = ((Number)origVal).longValue();
                    long d = ((Number)decVal).longValue();
                    if (d != o) {
                        System.out.println("key " + k + " is an unequal number: " + d + ", " + o);
                    }
                } else if (!origVal.equals(decVal)) {
                    System.out.println("key " + k + " does not match (" + origVal + ", " + decVal + ")/(" + origVal.getClass().getName() + ", " + decVal.getClass().getName() + ")");
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
    
    /////
    // remaining is a trivial bencode/bdecode impl, capable only of handling
    // what the SyndieURI needs
    /////
    
    private static final String bencode(TreeMap attributes) {
        StringBuffer buf = new StringBuffer(64);
        buf.append('d');
        for (Iterator iter = attributes.keySet().iterator(); iter.hasNext(); ) {
            String key = (String)iter.next();
            buf.append(key.length()).append(':').append(key);
            buf.append(bencode(attributes.get(key)));
        }
        buf.append('e');
        return buf.toString();
    }
    
    private static final String bencode(Object val) {
        if ( (val instanceof Integer) || (val instanceof Long) ) {
            return "i" + val.toString() + "e";
        } else if (val.getClass().isArray()) {
            StringBuffer buf = new StringBuffer();
            buf.append("l");
            Object vals[] = (Object[])val;
            for (int i = 0; i < vals.length; i++)
                buf.append(bencode(vals[i]));
            buf.append("e");
            return buf.toString();
        } else {
            String str = val.toString();
            return String.valueOf(str.length()) + ":" + val;
        }
    }
    
    private static final void bdecodeNext(StringBuffer remaining, TreeMap target) throws URISyntaxException {
        String key = null;
        while (true) {
            switch (remaining.charAt(0)) {
                case 'l':
                    List l = new ArrayList();
                    boolean ok = true;
                    remaining.deleteCharAt(0);
                    while (bdecodeNext(remaining, l)) {
                        if (remaining.charAt(0) == 'e') {
                            String str[] = new String[l.size()];
                            for (int i = 0; i < str.length; i++)
                                str[i] = (String)l.get(i);
                            target.put(key, str);
                            key = null;
                            remaining.deleteCharAt(0);
                            return;
                        }
                    }
                    // decode failed
                    throw new URISyntaxException(remaining.toString(), "Unterminated list");
                case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                    String str = bdecodeNext(remaining);
                    if (str == null) {
                        throw new URISyntaxException(remaining.toString(), "Undecoded string");
                    } else if (key == null) {
                        key = str;
                    } else {
                        target.put(key, str);
                        key = null;
                        return;
                    }
                    break;
                case 'i':
                    remaining.deleteCharAt(0);
                    int idx = remaining.indexOf("e");
                    if (idx < 0)
                        throw new URISyntaxException(remaining.toString(), "No remaining 'e'");
                    try {
                        String lstr = remaining.substring(0, idx);
                        long val = Long.parseLong(lstr);
                        if (key == null)
                            throw new URISyntaxException(remaining.toString(), "Numbers cannot be syndie uri keys");
                        target.put(key, new Long(val));
                        key = null;
                        remaining.delete(0, idx+1);
                        return;
                    } catch (NumberFormatException nfe) {
                        throw new URISyntaxException(remaining.toString(), "Invalid number format: " + nfe.getMessage());
                    }
                default:
                    throw new URISyntaxException(remaining.toString(), "Unsupported bencoding type");
            }
        }
    }
    private static final boolean bdecodeNext(StringBuffer remaining, List target) {
        String str = bdecodeNext(remaining);
        if (str == null) return false;
        target.add(str);
        return true;
    }
    private static final String bdecodeNext(StringBuffer remaining) {
        int br = remaining.indexOf(":");
        if (br <= 0)
            return null;
        String len = remaining.substring(0, br);
        try {
            int sz = Integer.parseInt(len);
            remaining.delete(0, br+1);
            String val = remaining.substring(0, sz);
            remaining.delete(0, sz);
            return val;
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
    /**
     * bdecode the subset of bencoded data we require.  The bencoded string must
     * be a single dictionary and contain either strings, integers, or lists of
     * strings.
     */
    private static final TreeMap bdecode(String bencoded) throws URISyntaxException {
        if ( (bencoded.charAt(0) != 'd') || (bencoded.charAt(bencoded.length()-1) != 'e') )
            throw new URISyntaxException(bencoded, "Not bencoded properly");
        StringBuffer buf = new StringBuffer(bencoded);
        buf.deleteCharAt(0);
        buf.deleteCharAt(buf.length()-1);
        TreeMap rv = new TreeMap();
        while (buf.length() > 0)
            bdecodeNext(buf, rv);
        return rv;
    }
}