package jnm.engineer.demo.services;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the first sheet of an .xlsx file, or a .csv file, into rows of text (no extra library).
 * Safe against hostile files: XML external entities/DTDs are refused, and the unzipped size is capped.
 * Numbers Excel stored as numbers come back as plain text (712345678, not 7.12345678E8; 1234, not 1234.0).
 */
public final class SheetReader {
    private static final long MAX_UNZIPPED = 30L * 1024 * 1024;   // 30 MB of XML is far more than any school list
    private static final int MAX_ROWS = 10_000;
    private SheetReader() {}

    public static List<List<String>> read(String fileName, byte[] bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv") || name.endsWith(".txt")) return csv(new String(stripBom(bytes), StandardCharsets.UTF_8));
        if (name.endsWith(".xlsx")) return xlsx(bytes);
        if (name.endsWith(".xls")) throw new IllegalArgumentException("That is an old Excel file (.xls). In Excel choose File → Save As → Excel Workbook (.xlsx), or CSV, and upload again.");
        throw new IllegalArgumentException("Upload an Excel file (.xlsx) or a CSV file.");
    }

    // ── CSV (commas or semicolons; "quoted, values" and "" inside quotes) ──
    static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        String first = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
        char sep = count(first, ';') > count(first, ',') ? ';' : ',';
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == sep) { row.add(cell.toString().trim()); cell.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString().trim()); cell.setLength(0);
                rows.add(row); row = new ArrayList<>();
                if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("The file has more than " + MAX_ROWS + " rows.");
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString().trim()); rows.add(row); }
        return rows;
    }

    // ── XLSX: shared strings + the first worksheet ──
    static List<List<String>> xlsx(byte[] bytes) {
        Map<String, byte[]> parts = unzip(bytes);
        try {
            List<String> shared = new ArrayList<>();
            if (parts.containsKey("xl/sharedStrings.xml")) {
                NodeList sis = parse(parts.get("xl/sharedStrings.xml")).getElementsByTagNameNS("*", "si");
                for (int i = 0; i < sis.getLength(); i++) {
                    StringBuilder b = new StringBuilder();
                    NodeList ts = ((Element) sis.item(i)).getElementsByTagNameNS("*", "t");     // plain or rich text runs
                    for (int k = 0; k < ts.getLength(); k++) b.append(ts.item(k).getTextContent());
                    shared.add(b.toString());
                }
            }
            String sheet = firstSheetPath(parts);
            if (!parts.containsKey(sheet)) throw new IllegalArgumentException("No worksheet was found in the file.");
            NodeList rowNodes = parse(parts.get(sheet)).getElementsByTagNameNS("*", "row");
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < rowNodes.getLength() && i <= MAX_ROWS; i++) {
                Element r = (Element) rowNodes.item(i);
                int rowNo = r.hasAttribute("r") ? Integer.parseInt(r.getAttribute("r")) : rows.size() + 1;
                while (rows.size() < rowNo - 1) rows.add(new ArrayList<>());                      // keep empty rows in place
                List<String> row = new ArrayList<>();
                NodeList cells = r.getElementsByTagNameNS("*", "c");
                for (int k = 0; k < cells.getLength(); k++) {
                    Element c = (Element) cells.item(k);
                    int col = c.hasAttribute("r") ? column(c.getAttribute("r")) : row.size();
                    while (row.size() < col) row.add("");
                    row.add(cellText(c, shared));
                }
                rows.add(row);
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("The Excel file could not be read. Save it again as .xlsx (or CSV) and upload it.");
        }
    }

    private static String cellText(Element c, List<String> shared) {
        String type = c.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList ts = c.getElementsByTagNameNS("*", "t");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < ts.getLength(); i++) b.append(ts.item(i).getTextContent());
            return b.toString().trim();
        }
        NodeList vs = c.getElementsByTagNameNS("*", "v");
        if (vs.getLength() == 0) return "";
        String v = vs.item(0).getTextContent().trim();
        if ("s".equals(type)) { int idx = Integer.parseInt(v); return idx >= 0 && idx < shared.size() ? shared.get(idx).trim() : ""; }
        if ("b".equals(type)) return "1".equals(v) ? "TRUE" : "FALSE";
        if (type.isEmpty() || "n".equals(type)) {                       // a number: plain digits, no ".0" or exponent
            try { return new BigDecimal(v).stripTrailingZeros().toPlainString(); } catch (NumberFormatException e) { return v; }
        }
        return v;
    }

    /** The first sheet in the workbook's order (not necessarily sheet1.xml). */
    private static String firstSheetPath(Map<String, byte[]> parts) throws Exception {
        if (!parts.containsKey("xl/workbook.xml") || !parts.containsKey("xl/_rels/workbook.xml.rels")) return "xl/worksheets/sheet1.xml";
        NodeList sheets = parse(parts.get("xl/workbook.xml")).getElementsByTagNameNS("*", "sheet");
        if (sheets.getLength() == 0) return "xl/worksheets/sheet1.xml";
        Element s = (Element) sheets.item(0);
        String rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
        NodeList rels = parse(parts.get("xl/_rels/workbook.xml.rels")).getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            if (r.getAttribute("Id").equals(rid)) {
                String t = r.getAttribute("Target");
                return t.startsWith("/") ? t.substring(1) : "xl/" + t;
            }
        }
        return "xl/worksheets/sheet1.xml";
    }

    static int column(String ref) {                                     // "C7" → 2
        int n = 0;
        for (char ch : ref.toCharArray()) { if (!Character.isLetter(ch)) break; n = n * 26 + (Character.toUpperCase(ch) - 'A' + 1); }
        return Math.max(0, n - 1);
    }

    private static Map<String, byte[]> unzip(byte[] bytes) {
        Map<String, byte[]> out = new HashMap<>();
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                boolean wanted = n.equals("xl/sharedStrings.xml") || n.equals("xl/workbook.xml") || n.equals("xl/_rels/workbook.xml.rels") || n.startsWith("xl/worksheets/sheet");
                if (!wanted) continue;
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                int k;
                while ((k = z.read(buf)) > 0) {
                    total += k;
                    if (total > MAX_UNZIPPED) throw new IllegalArgumentException("The file is too large to import.");
                    o.write(buf, 0, k);
                }
                out.put(n, o.toByteArray());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("That doesn't look like an Excel (.xlsx) file.");
        }
        if (out.isEmpty()) throw new IllegalArgumentException("That doesn't look like an Excel (.xlsx) file.");
        return out;
    }

    /** XML parser with DTDs and external entities switched off (no XXE). */
    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {   // report problems by exception only (no noise in the log)
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void warning(org.xml.sax.SAXParseException e) { }
        });
        try (InputStream in = new ByteArrayInputStream(xml)) { return b.parse(in); }
    }

    private static int count(String s, char c) { int n = 0; for (char x : s.toCharArray()) if (x == c) n++; return n; }
    private static byte[] stripBom(byte[] b) { return b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF ? Arrays.copyOfRange(b, 3, b.length) : b; }
    @SuppressWarnings("unused") private static String text(Node n) { return n == null ? "" : n.getTextContent(); }
}
