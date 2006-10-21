package syndie.data;

import gnu.crypto.hash.Sha256Standalone;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.i2p.I2PAppContext;
import net.i2p.crypto.AESInputStream;
import net.i2p.data.*;
import net.i2p.util.Log;

/**
 *
 */
public class EnclosureBody {
    private I2PAppContext _context;
    private Log _log;
    /** filename to byte[] */
    private Map _entries;
    /** key to value */
    private Properties _headers;
    /** list of config settings (Properties) for each page */
    private List _pageConfig;
    /** list of config settings (Properties) for each attachment */
    private List _attachConfig;
    private int _pages;
    private int _attachments;
    private List _references;
    
    public static final String ENTRY_AVATAR = "avatar32.png";
    public static final String ENTRY_HEADERS = "headers.dat";
    public static final String ENTRY_PAGE_PREFIX = "page";
    public static final String ENTRY_PAGE_DATA_SUFFIX = ".dat";
    public static final String ENTRY_PAGE_CONFIG_SUFFIX = ".cfg";
    public static final String ENTRY_ATTACHMENT_PREFIX = "attachment";
    public static final String ENTRY_ATTACHMENT_DATA_SUFFIX = ".dat";
    public static final String ENTRY_ATTACHMENT_CONFIG_SUFFIX = ".cfg";
    public static final String ENTRY_REFERENCES = "references.cfg";
    
    protected EnclosureBody(I2PAppContext ctx) {
        _context = ctx;
        _log = ctx.logManager().getLog(getClass());
        _entries = new HashMap();
        _pageConfig = new ArrayList();
        _attachConfig = new ArrayList();
        _references = new ArrayList();
        _headers = new Properties();
        _pages = 0;
        _attachments = 0;
    }
    
    /**
     * Decrypt and parse up the enclosure body with the given read key, throwing a DFE if
     * the decryption or parsing fails.
     * format: IV + E(rand(nonzero) padding + 0 + internalSize + totalSize + data + rand, IV, key)+HMAC(bodySection, H(bodyKey+IV))
     */
    public EnclosureBody(I2PAppContext ctx, InputStream data, int size, SessionKey key) throws IOException, DataFormatException {
        this(ctx);
        byte iv[] = new byte[16];
        if (DataHelper.read(data, iv) != 16) throw new IOException("Not enough data for the IV");
        byte enc[] = new byte[size-16];
        int read = DataHelper.read(data, enc);
        if (read != size-16) throw new IOException("Not enough data for the payload (size=" + (size-16) + ", read=" + read);
        byte dec[] = new byte[size-16];
        ctx.aes().decrypt(enc, 0, dec, 0, key, iv, enc.length-32);
        
        int start = 0;
        int pad = 0;
        while (start < size && dec[start] != 0x0) {
            start++;
            pad++;
        }
        start++;
        int off = start;
        int internalSize = (int)DataHelper.fromLong(dec, off, 4);
        off += 4;
        int totalSize = (int)DataHelper.fromLong(dec, off, 4);
        off += 4;
        if (totalSize != (size-16)) {
            if (_log.shouldLog(Log.DEBUG)) {
                Sha256Standalone dbg = new Sha256Standalone();
                dbg.update(enc);
                byte h[] = dbg.digest();
                _log.debug("borked: off=" + off);
                _log.debug("borked: Encrypted body hashes to " + Base64.encode(h));
                _log.debug("borked: key used: " + Base64.encode(key.getData()));
                _log.debug("borked: IV used: " + Base64.encode(iv));
                _log.debug("borked: pad: " + pad);
                _log.debug("borked: totalSize: " + totalSize);
                _log.debug("borked: size: " + size);
                _log.debug("borked: internalSize: " + internalSize);
            }
            throw new DataFormatException("Invalid total size (" + totalSize + "/" + size + ")");
        }
        if (internalSize + start + 8 > totalSize) throw new DataFormatException("Invalid internal size (" + internalSize + "), start (" + start + " iv=" + Base64.encode(iv) + " / pad=" + pad + ")");
        
        byte hmacPreKey[] = new byte[SessionKey.KEYSIZE_BYTES+iv.length];
        System.arraycopy(key.getData(), 0, hmacPreKey, 0, SessionKey.KEYSIZE_BYTES);
        System.arraycopy(iv, 0, hmacPreKey, SessionKey.KEYSIZE_BYTES, iv.length);
        byte hmacKey[] = ctx.sha().calculateHash(hmacPreKey).getData();
        boolean hmacOK = ctx.hmac256().verify(new SessionKey(hmacKey), enc, 0, enc.length-32, enc, enc.length-32, 32);
        if (!hmacOK) {
            if (_log.shouldLog(Log.DEBUG)) {
                _log.debug("borked hmac: hmacKey: " + Base64.encode(hmacKey));
                _log.debug("borked hmac: readMAC: " + Base64.encode(enc, enc.length-32, 32));
            }
            throw new DataFormatException("Invalid HMAC, but valid sizes");
        }
        
        parse(new ByteArrayInputStream(dec, off, internalSize));
    }
    
    /**
     * Decrypt and parse up the enclosure body with the given reply key, throwing a DFE if
     * the decryption or parsing fails
     */
    public EnclosureBody(I2PAppContext ctx, InputStream data, int size, PrivateKey key) throws IOException, DataFormatException {
        this(ctx);
        //if (true) throw new RuntimeException("Not yet implemented");
        byte asym[] = new byte[514];
        int read = DataHelper.read(data, asym);
        if (read != asym.length) throw new IOException("Not enough data for the asym block (" + read + ")");
        //System.out.println("Asym block[" + asym.length + "]:\n" + Base64.encode(asym) + "\npubKey:\n" + Base64.encode(ctx.keyGenerator().getPublicKey(key).getData()));
        byte decrypted[] = ctx.elGamalEngine().decrypt(asym, key);
        if (decrypted == null) throw new DataFormatException("Decrypt failed");
        
        Hash ivCalc = ctx.sha().calculateHash(decrypted, 0, 16);
        byte bodyKeyData[] = new byte[SessionKey.KEYSIZE_BYTES];
        System.arraycopy(decrypted, 16, bodyKeyData, 0, bodyKeyData.length);
        SessionKey bodyKey = new SessionKey(bodyKeyData);
        
        byte enc[] = new byte[size-asym.length-32];
        read = DataHelper.read(data, enc);
        if (read != size-asym.length-32) throw new IOException("Not enough data for the payload (size=" + (size-asym.length) + ", read=" + read);
        byte macRead[] = new byte[32];
        read = DataHelper.read(data, macRead);
        if (read != macRead.length) throw new IOException("Not enough data for the mac");
        byte dec[] = new byte[enc.length];
        ctx.aes().decrypt(enc, 0, dec, 0, bodyKey, ivCalc.getData(), 0, enc.length);
        
        int start = 0;
        while (start < size && dec[start] != 0x0)
            start++;
        start++;
        int off = start;
        int internalSize = (int)DataHelper.fromLong(dec, off, 4);
        off += 4;
        int totalSize = (int)DataHelper.fromLong(dec, off, 4);
        off += 4;
        if (totalSize != (size-asym.length)) throw new DataFormatException("Invalid total size (" + totalSize + "/" + size + ")");
        if (internalSize + start + 8 > totalSize) throw new DataFormatException("Invalid internal size (" + internalSize + "), start (" + start + ")");

        // check the hmac
        byte hmacPreKey[] = new byte[SessionKey.KEYSIZE_BYTES+16];
        System.arraycopy(bodyKeyData, 0, hmacPreKey, 0, SessionKey.KEYSIZE_BYTES);
        System.arraycopy(ivCalc.getData(), 0, hmacPreKey, SessionKey.KEYSIZE_BYTES, 16);
        byte hmacKey[] = ctx.sha().calculateHash(hmacPreKey).getData();
        boolean hmacOK = ctx.hmac256().verify(new SessionKey(hmacKey), enc, 0, enc.length, macRead, 0, macRead.length);
        if (!hmacOK) {
            if (_log.shouldLog(Log.DEBUG)) {
                _log.debug("borked hmac: hmacKey: " + Base64.encode(hmacKey));
                _log.debug("borked hmac: readMAC: " + Base64.encode(macRead));
            }
            throw new DataFormatException("Invalid HMAC, but valid sizes");
        }
        
        parse(new ByteArrayInputStream(dec, off, internalSize));
    }
    
    public int getPages() { return _pages; }
    public int getAttachments() { return _attachments; }
    public InputStream getAvatar() {
        if (_entries.containsKey(ENTRY_AVATAR))
            return new ByteArrayInputStream((byte[])_entries.get(ENTRY_AVATAR));
        else
            return null;
    }
    public Set getPageConfigKeys(int pageNum) { return ((Properties)_pageConfig.get(pageNum)).keySet(); }
    public Set getAttachmentConfigKeys(int attachNum) { return ((Properties)_attachConfig.get(attachNum)).keySet(); }
    public Set getHeaderKeys() { return _headers.keySet(); }
    public int getReferenceRootCount() { return _references.size(); }
    public ReferenceNode getReferenceRoot(int index) { return (ReferenceNode)_references.get(index); }
    public Properties getHeaders() { return _headers; }
    
    public String getHeaderString(String key) { return _headers.getProperty(key); }
    public byte[] getHeaderBytes(String key) { return Enclosure.toBytes(_headers.getProperty(key)); }
    public SyndieURI getHeaderURI(String key) { return Enclosure.toURI(_headers.getProperty(key)); }
    public SyndieURI[] getHeaderURIs(String key) { return Enclosure.toURIs(_headers.getProperty(key)); }
    public String[] getHeaderStrings(String key) { return Enclosure.toStrings(_headers.getProperty(key)); }
    public Boolean getHeaderBoolean(String key) { return Enclosure.toBoolean(_headers.getProperty(key)); }
    public Long getHeaderLong(String key) { return Enclosure.toLong(_headers.getProperty(key)); }
    public SessionKey getHeaderSessionKey(String key) { return Enclosure.toSessionKey(_headers.getProperty(key)); }
    public SessionKey[] getHeaderSessionKeys(String key) { return Enclosure.toSessionKeys(_headers.getProperty(key)); }
    public SigningPublicKey getHeaderSigningKey(String key) { return Enclosure.toSigningKey(_headers.getProperty(key)); }
    public SigningPublicKey[] getHeaderSigningKeys(String key) { return Enclosure.toSigningKeys(Enclosure.toStrings(_headers.getProperty(key))); }
    public PublicKey getHeaderEncryptKey(String key) { return Enclosure.toEncryptKey(_headers.getProperty(key)); }
    public Date getHeaderDate(String key) { return Enclosure.toDate(_headers.getProperty(key)); }

    public String getPageConfigString(int page, String key) { return getPageConfig(page).getProperty(key); }
    public byte[] getPageConfigBytes(int page, String key) { return Enclosure.toBytes(getPageConfig(page).getProperty(key)); }
    public SyndieURI getPageConfigURI(int page, String key) { return Enclosure.toURI(getPageConfig(page).getProperty(key)); }
    public String[] getPageConfigStrings(int page, String key) { return Enclosure.toStrings(getPageConfig(page).getProperty(key)); }
    public Boolean getPageConfigBoolean(int page, String key) { return Enclosure.toBoolean(getPageConfig(page).getProperty(key)); }
    public Long getPageConfigLong(int page, String key) { return Enclosure.toLong(getPageConfig(page).getProperty(key)); }
    public SessionKey getPageConfigSessionKey(int page, String key) { return Enclosure.toSessionKey(getPageConfig(page).getProperty(key)); }
    public SigningPublicKey getPageConfigSigningKey(int page, String key) { return Enclosure.toSigningKey(getPageConfig(page).getProperty(key)); }
    public SigningPublicKey[] getPageConfigSigningKeys(int page, String key) { return Enclosure.toSigningKeys(Enclosure.toStrings(getPageConfig(page).getProperty(key))); }
    public PublicKey getPageConfigEncryptKey(int page, String key) { return Enclosure.toEncryptKey(getPageConfig(page).getProperty(key)); }
    public Date getPageConfigDate(int page, String key) { return Enclosure.toDate(getPageConfig(page).getProperty(key)); }

    public String getAttachmentConfigString(int attach, String key) { return getAttachmentConfig(attach).getProperty(key); }
    public byte[] getAttachmentConfigBytes(int attach, String key) { return Enclosure.toBytes(getAttachmentConfig(attach).getProperty(key)); }
    public SyndieURI getAttachmentConfigURI(int attach, String key) { return Enclosure.toURI(getAttachmentConfig(attach).getProperty(key)); }
    public String[] getAttachmentConfigStrings(int attach, String key) { return Enclosure.toStrings(getAttachmentConfig(attach).getProperty(key)); }
    public Boolean getAttachmentConfigBoolean(int attach, String key) { return Enclosure.toBoolean(getAttachmentConfig(attach).getProperty(key)); }
    public Long getAttachmentConfigLong(int attach, String key) { return Enclosure.toLong(getAttachmentConfig(attach).getProperty(key)); }
    public SessionKey getAttachmentConfigSessionKey(int attach, String key) { return Enclosure.toSessionKey(getAttachmentConfig(attach).getProperty(key)); }
    public SigningPublicKey getAttachmentConfigSigningKey(int attach, String key) { return Enclosure.toSigningKey(getAttachmentConfig(attach).getProperty(key)); }
    public SigningPublicKey[] getAttachmentConfigSigningKeys(int attach, String key) { return Enclosure.toSigningKeys(Enclosure.toStrings(getAttachmentConfig(attach).getProperty(key))); }
    public PublicKey getAttachmentConfigEncryptKey(int attach, String key) { return Enclosure.toEncryptKey(getAttachmentConfig(attach).getProperty(key)); }
    public Date getAttachmentConfigDate(int attach, String key) { return Enclosure.toDate(getAttachmentConfig(attach).getProperty(key)); }
    
    public byte[] getPage(int page) { return (byte[])_entries.get(ENTRY_PAGE_PREFIX + page + ENTRY_PAGE_DATA_SUFFIX); }
    public byte[] getAttachment(int attachment) { return (byte[])_entries.get(ENTRY_ATTACHMENT_PREFIX + attachment + ENTRY_ATTACHMENT_DATA_SUFFIX); }

    public String toString() {
        StringBuffer rv = new StringBuffer();
        rv.append("EnclosureBody with ").append(_pages).append(" pages, ").append(_attachments).append(" attachments, and private headers of {");
        for (Iterator iter = _headers.keySet().iterator(); iter.hasNext(); ) {
            String key = (String)iter.next();
            String val = _headers.getProperty(key);
            rv.append('\'').append(key).append("' => '").append(val).append("\'");
            if (iter.hasNext())
                rv.append(", ");
        }
        rv.append("}");
        return rv.toString();
    }
    
    
    public Properties getPageConfig(int pageNum) { return (Properties)_pageConfig.get(pageNum); }
    public Properties getAttachmentConfig(int attachNum) { return (Properties)_attachConfig.get(attachNum); }
    
    private void parse(InputStream zipData) throws IOException {
        unzip(zipData);
        _headers = parseProps(ENTRY_HEADERS);
        for (int i = 0; i < _pages; i++)
            _pageConfig.add(parseProps(ENTRY_PAGE_PREFIX + i + ENTRY_PAGE_CONFIG_SUFFIX));
        for (int i = 0; i < _attachments; i++)
            _attachConfig.add(parseProps(ENTRY_ATTACHMENT_PREFIX + i + ENTRY_ATTACHMENT_CONFIG_SUFFIX));
        // parse the references
        byte refs[] = (byte[])_entries.get(ENTRY_REFERENCES);
        if (refs != null) {
            //System.out.println("References entry found, size: " + refs.length);
            _references.addAll(ReferenceNode.buildTree(new ByteArrayInputStream(refs)));
        } else {
            //System.out.println("No references entry found");
        }
    }
    private void unzip(InputStream zipData) throws IOException {
        ZipInputStream in = new ZipInputStream(zipData);
        ZipEntry entry = null;
        while ( (entry = in.getNextEntry()) != null) {
            String name = entry.getName();
            byte data[] = null;
            long sz = entry.getSize();
            // spec & sun sayeth --1 implies unknown size, but kaffe [1.1.7] uses 0 too
            if ( (sz == -1) || (sz == 0) ) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte buf[] = new byte[4096];
                int read = -1;
                while ( (read = in.read(buf)) != -1)
                    baos.write(buf, 0, read);
                data = baos.toByteArray();
            } else {
                data = new byte[(int)sz];
                if (DataHelper.read(in, data) != sz)
                    throw new IOException("Not enough data for " + name);
            }
            if (name.startsWith(ENTRY_ATTACHMENT_PREFIX) && name.endsWith(ENTRY_ATTACHMENT_DATA_SUFFIX))
                _attachments++;
            else if (name.startsWith(ENTRY_PAGE_PREFIX) && name.endsWith(ENTRY_PAGE_DATA_SUFFIX))
                _pages++;
            _entries.put(name, data);
        }
    }
    private Properties parseProps(String entry) {
        Properties rv = new Properties();
        byte data[] = (byte[])_entries.get(entry);
        if (data == null) {
            //System.out.println("Entry " + entry + " does not exist");
            return new Properties();
        }
        parseProps(data, rv);
        return rv;
    }
    private static void parseProps(byte data[], Properties rv) {
        //System.out.println("parsing props: " + new String(data));
        int off = 0;
        int dataStart = off;
        int valStart = -1;
        while (off < data.length) {
            if (data[off] == '\n') {
                try {
                    String key = new String(data, dataStart, valStart-1-dataStart, "UTF-8");
                    String val = new String(data, valStart, off-valStart, "UTF-8");
                    //System.out.println("Prop parsed: [" + key + "] = [" + val + "] (dataStart=" + dataStart + " valStart " + valStart + " off " + off + ")");
                    rv.setProperty(key, val);
                } catch (UnsupportedEncodingException uee) {
                    //
                } catch (RuntimeException re) {
                    //re.printStackTrace();
                }
                dataStart = off+1;
                valStart = -1;
            } else if ( (data[off] == '=') && (valStart == -1) ) {
                valStart = off+1;
            } else if (off + 1 >= data.length) {
                if ( ( (valStart-1-dataStart) > 0) && ( (off+1-valStart) > 0) ) {
                    try {
                        String key = new String(data, dataStart, valStart-1-dataStart, "UTF-8");
                        String val = new String(data, valStart, off+1-valStart, "UTF-8");
                        //System.out.println("End prop parsed: [" + key + "] = [" + val + "] (dataStart=" + dataStart + " valStart " + valStart + " off " + off + ")");
                        rv.setProperty(key, val);
                    } catch (UnsupportedEncodingException uee) {
                        //
                    } catch (RuntimeException re) {
                        //re.printStackTrace();
                    }
                }
            }
            off++;
        }
    }
    
    public static void main(String args[]) {
        Properties props = new Properties();
        parseProps("a=b\nc=d".getBytes(), props);
        System.out.println("props: " + props);
    }
}