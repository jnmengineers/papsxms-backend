package jnm.engineer.demo.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a simple one-sheet .xlsx (no extra library). Every cell is TEXT, like the bank's
 * template (account numbers keep leading zeros; amounts as the bank expects them).
 * Row 1 is bold (the header). Column widths are given in characters.
 */
public final class XlsxWriter {
    private XlsxWriter() {}

    public static byte[] write(String sheetName, List<List<String>> rows, double[] widths) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>""");
            put(zip, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""");
            put(zip, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets></workbook>""".formatted(esc(sheetName)));
            put(zip, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""");
            // style 1 = text (@), style 2 = text + bold (header)
            put(zip, "xl/styles.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                <cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="49" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/></cellXfs>
                <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>""");
            StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            if (widths != null && widths.length > 0) {
                sb.append("<cols>");
                for (int i = 0; i < widths.length; i++) sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
                sb.append("</cols>");
            }
            sb.append("<sheetData>");
            for (int r = 0; r < rows.size(); r++) {
                sb.append("<row r=\"").append(r + 1).append("\">");
                List<String> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    String v = row.get(c);
                    if (v == null || v.isEmpty()) continue;
                    sb.append("<c r=\"").append(col(c)).append(r + 1).append("\" s=\"").append(r == 0 ? 2 : 1).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                      .append(esc(v)).append("</t></is></c>");
                }
                sb.append("</row>");
            }
            sb.append("</sheetData></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sb.toString());
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    static String col(int i) { StringBuilder s = new StringBuilder(); i++; while (i > 0) { int m = (i - 1) % 26; s.insert(0, (char) ('A' + m)); i = (i - 1) / 26; } return s.toString(); }
    private static String esc(String v) {
        StringBuilder b = new StringBuilder();
        for (char ch : v.toCharArray()) {
            if (ch == '&') b.append("&amp;"); else if (ch == '<') b.append("&lt;"); else if (ch == '>') b.append("&gt;"); else if (ch == '"') b.append("&quot;");
            else if (ch < 0x20 && ch != '\t' && ch != '\n') continue; else b.append(ch);
        }
        return b.toString();
    }
}
