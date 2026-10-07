package it.progettogas.confronti;

import static it.progettogas.gas.Model.*;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.springframework.stereotype.Component;

@Component
public class GasPdfRenderer {
  public byte[] generate(long id, Comparison s, PdfPersonalizzazione options) throws IOException {
    var logo = PdfLogo.leggi(options.logo());
    try (var document = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      document.getDocumentInformation().setTitle("Progetto Gas - Confronto #" + id);
      document.getDocumentInformation().setAuthor("Progetto Gas");
      try (var l = new Layout(document, id, options, logo)) {
        var b = s.bill();
        var r = s.result();
        l.consulente();
        if (options.stileEffettivo() == PdfPersonalizzazione.Stile.SINTESI)
          l.metrics(b.previousTotal(), r.total(), r.difference());
        l.section("Cliente e proposta gas naturale");
        l.field("Cliente / ragione sociale", b.client());
        l.field("PDR", b.pdr());
        if (b.partitaIva() != null && !b.partitaIva().isBlank())
          l.field("Partita IVA", b.partitaIva());
        l.field("Fornitore attuale", b.supplier());
        l.field("Offerta proposta", s.offer().name() + " / " + s.offer().provider());
        l.field(
            "Periodo e consumi",
            b.periods().get(0).month()
                + " → "
                + b.periods().get(b.periods().size() - 1).month()
                + " · "
                + number(r.consumptionSmc())
                + " Smc");
        l.field("Riferimento", b.reference() == null ? "" : b.reference());
        if (options.stileEffettivo() != PdfPersonalizzazione.Stile.SINTESI)
          l.metrics(b.previousTotal(), r.total(), r.difference());
        if (r.unapproved())
          l.paragraph(
              "Profilo tariffario da validare: simulazione dimostrativa, tariffe non approvate.",
              true);
        l.row(
            "Variazione sul costo attuale",
            r.percentage() == null
                ? "Non disponibile"
                : number(r.percentage().abs())
                    + "% "
                    + (r.differenceRaw().signum() >= 0 ? "in meno" : "in più"));
        l.row(
            r.annualRaw().signum() >= 0
                ? "Risparmio annuale indicativo"
                : "Maggior costo annuale indicativo",
            money(r.annual().abs()));
        l.paragraph(
            "Proiezione del periodo × 12 / mesi. Non considera stagionalità o variazioni future del"
                + " PSV.",
            false);
        l.section("Come si forma il costo");
        for (var c : r.categories().entrySet()) l.row(category(c.getKey()), money(c.getValue()));
        l.row("Imponibile", money(r.taxable()));
        l.row("IVA", money(r.vat()));
        l.row("Altre partite non soggette IVA", money(r.nonTaxable()));
        l.row("Totale proposta gas", money(r.total()));
        l.field("Totale raw", r.totalRaw().toPlainString() + " EUR");
        for (var p : b.periods()) {
          l.section("Dettaglio " + p.month() + " · " + number(p.consumptionSmc()) + " Smc");
          l.tableHeader(new String[] {"Mese", "Voce", "Quantità", "Tariffa", "Importo"});
          int i = 0;
          for (var line : r.rows())
            if (line.month().equals(p.month()))
              l.tableRow(
                  new String[] {
                    line.month(),
                    line.label(),
                    number(line.quantity()),
                    number(line.rate()) + " " + line.unit(),
                    money(line.amount())
                  },
                  new String[] {"Mese", "Voce", "Quantità", "Tariffa", "Importo"},
                  i++);
          l.section("Origine delle tariffe · " + p.month());
          for (var line : r.rows())
            if (line.month().equals(p.month()))
              l.paragraph(
                  line.label()
                      + " · "
                      + (line.band() == null ? "" : line.band() + " · ")
                      + line.origin()
                      + " · IVA raw "
                      + number(line.vat())
                      + " EUR",
                  false);
        }
        l.section("Profili e regole conservati");
        for (var e : s.profiles().entrySet())
          l.paragraph(
              e.getKey()
                  + " · "
                  + e.getValue().name()
                  + " · "
                  + e.getValue().mode()
                  + " · "
                  + e.getValue().status()
                  + " · fonte: "
                  + e.getValue().source()
                  + " · validità "
                  + e.getValue().validFrom()
                  + " / "
                  + e.getValue().validTo(),
              false);
        for (var a : r.audit())
          l.paragraph(
              "Override "
                  + a.month()
                  + " · "
                  + a.code()
                  + ": "
                  + number(a.original())
                  + " → "
                  + number(a.updated())
                  + " · "
                  + a.reason()
                  + " · "
                  + a.operator()
                  + " · "
                  + a.timestamp(),
              false);
        l.paragraph(
            "Il report conserva lo snapshot del confronto. Le modifiche successive non ne cambiano"
                + " gli importi. Gli scaglioni sono scelte esplicite, non dedotti automaticamente."
                + " Simulazione parametrica, non fattura o preventivo contrattuale.",
            false);
      }
      document.save(out);
      return out.toByteArray();
    }
  }

  private static String category(Category c) {
    return switch (c) {
      case COMMODITY -> "Materia gas";
      case TRANSPORT -> "Trasporto e contatore";
      case SYSTEM -> "Oneri di sistema";
      case TAX -> "Accisa e addizionali";
      case OTHER -> "Altre partite";
    };
  }

  private static String money(BigDecimal value) {
    var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ITALY));
    format.setRoundingMode(java.math.RoundingMode.HALF_UP);
    return format.format(value) + " €";
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString().replace('.', ',');
  }

  private static final class Layout implements AutoCloseable {
    private static final float LEFT = 42, WIDTH = PDRectangle.A4.getWidth() - 84;
    private static final float[] COLUMNS = {55, 163, 83, 105, WIDTH - 406};
    private static final Color INK = new Color(32, 51, 43), MUTED = new Color(99, 115, 107);
    private final Color GREEN, PALE, ACCENT_INK, ON_PRIMARY;
    private final PdfPersonalizzazione options;
    private final PDImageXObject logo;
    private final PDDocument document;
    private final PDType0Font regular, bold;
    private final Long id;
    private PDPageContentStream stream;
    private float y;
    private boolean substitutions;

    Layout(
        PDDocument document,
        Long id,
        PdfPersonalizzazione options,
        java.awt.image.BufferedImage image)
        throws IOException {
      this.options = options;
      GREEN = Color.decode(options.coloreEffettivo());
      PALE = mix(GREEN, .91);
      ACCENT_INK = luminance(GREEN) < .183 ? GREEN : INK;
      ON_PRIMARY = luminance(GREEN) < .179 ? Color.WHITE : Color.BLACK;
      logo = image == null ? null : LosslessFactory.createFromImage(document, image);
      this.document = document;
      this.id = id;
      regular = font(document, "DejaVuSans.ttf");
      bold = font(document, "DejaVuSans-Bold.ttf");
      newPage();
    }

    private static Color mix(Color color, double white) {
      return new Color(
          (int) (color.getRed() * (1 - white) + 255 * white),
          (int) (color.getGreen() * (1 - white) + 255 * white),
          (int) (color.getBlue() * (1 - white) + 255 * white));
    }

    private static double luminance(Color color) {
      double[] channels = {color.getRed() / 255d, color.getGreen() / 255d, color.getBlue() / 255d};
      for (int i = 0; i < 3; i++)
        channels[i] =
            channels[i] <= .04045
                ? channels[i] / 12.92
                : Math.pow((channels[i] + .055) / 1.055, 2.4);
      return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
    }

    void consulente() throws IOException {
      var c = options.consulente();
      if (c == null) return;
      var lines = new ArrayList<String>();
      for (String value :
          new String[] {c.nome(), c.ruolo(), c.email(), c.telefono(), c.indirizzo()})
        if (value != null && !value.isBlank()) lines.addAll(wrap(value, regular, 9, WIDTH - 24));
      if (lines.isEmpty()) return;
      float height = 29 + lines.size() * 12;
      ensure(height + 12);
      box(LEFT, y - height + 12, WIDTH, height, PALE);
      text(
          c.dimostrativo() ? "CONSULENTE · DATI DIMOSTRATIVI" : "IL TUO CONSULENTE",
          LEFT + 12,
          y - 4,
          bold,
          8,
          ACCENT_INK);
      float baseline = y - 19;
      for (String line : lines) {
        text(line, LEFT + 12, baseline, regular, 9, INK);
        baseline -= 12;
      }
      y -= height + 12;
    }

    private static PDType0Font font(PDDocument document, String name) throws IOException {
      try (var input = GasPdfRenderer.class.getResourceAsStream("/fonts/" + name)) {
        if (input == null) throw new IOException("Font PDF mancante: " + name);
        return PDType0Font.load(document, input, true);
      }
    }

    void newPage() throws IOException {
      if (stream != null) stream.close();
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      var style = options.stileEffettivo();
      boolean filled = style == PdfPersonalizzazione.Stile.CLASSICO;
      if (filled) box(0, 747, PDRectangle.A4.getWidth(), 95, GREEN);
      else if (style == PdfPersonalizzazione.Stile.ESSENZIALE) box(LEFT, 747, WIDTH, 2, GREEN);
      else if (style == PdfPersonalizzazione.Stile.EDITORIALE) {
        box(0, 747, 12, 95, GREEN);
        box(LEFT, 750, WIDTH, 1, PALE);
      } else {
        box(0, 747, PDRectangle.A4.getWidth(), 95, PALE);
        box(0, 747, PDRectangle.A4.getWidth(), 4, GREEN);
      }
      Color ink = filled ? ON_PRIMARY : ACCENT_INK;
      float titleX = LEFT;
      if (logo != null) {
        box(LEFT, 771, 60, 52, Color.WHITE);
        float scale = Math.min(52f / logo.getWidth(), 44f / logo.getHeight());
        float w = logo.getWidth() * scale, h = logo.getHeight() * scale;
        stream.drawImage(logo, LEFT + (60 - w) / 2, 775 + (44 - h) / 2, w, h);
        titleX += 72;
      }
      text(
          style == PdfPersonalizzazione.Stile.EDITORIALE
              ? "La tua energia, in chiaro."
              : "Confronto gas naturale",
          titleX,
          803,
          bold,
          logo == null ? 20 : 16,
          ink);
      text("PROGETTO GAS · REPORT #" + id, titleX, 777, regular, 9, ink);
      y = 721;
    }

    void ensure(float height) throws IOException {
      if (y - height < 65) newPage();
    }

    void section(String value) throws IOException {
      ensure(90);
      y -= 8;
      if (options.stileEffettivo() == PdfPersonalizzazione.Stile.EDITORIALE) {
        box(LEFT, y - 3, 4, 15, GREEN);
        text(value, LEFT + 12, y, bold, 13, ACCENT_INK);
      } else text(value, LEFT, y, bold, 13, ACCENT_INK);
      y -= 12;
      box(LEFT, y, WIDTH, 1, PALE);
      y -= 11;
    }

    void field(String label, String value) throws IOException {
      var lines = wrap(value, regular, 10, WIDTH);
      ensure(15 + lines.size() * 12);
      text(label.toUpperCase(Locale.ITALY), LEFT, y, bold, 8, MUTED);
      y -= 12;
      for (String line : lines) {
        ensure(12);
        text(line, LEFT, y, regular, 10, INK);
        y -= 12;
      }
      y -= 3;
    }

    void paragraph(String value, boolean strong) throws IOException {
      var font = strong ? bold : regular;
      var lines = wrap(value, font, 9, WIDTH);
      ensure(Math.min(lines.size() * 13 + 6, 170));
      for (String line : lines) {
        ensure(13);
        text(line, LEFT, y, font, 9, INK);
        y -= 13;
      }
      y -= 6;
    }

    void row(String label, String value) throws IOException {
      var labels = wrap(label, regular, 9, WIDTH * .60f);
      var values = wrap(value, bold, 9, WIDTH * .37f);
      float height = Math.max(labels.size(), values.size()) * 13 + 7;
      ensure(height);
      for (int i = 0; i < labels.size(); i++)
        text(labels.get(i), LEFT, y - i * 13, regular, 9, INK);
      for (int i = 0; i < values.size(); i++) {
        String line = values.get(i);
        text(line, LEFT + WIDTH - bold.getStringWidth(line) / 1000 * 9, y - i * 13, bold, 9, INK);
      }
      y -= height;
    }

    void metrics(BigDecimal current, BigDecimal proposed, BigDecimal saving) throws IOException {
      if (options.stileEffettivo() == PdfPersonalizzazione.Stile.ESSENZIALE) {
        row("Bolletta attuale (IVA inclusa)", money(current));
        row("Offerta proposta (IVA inclusa)", money(proposed));
        row(
            saving.signum() >= 0 ? "Risparmio nel periodo" : "Maggior costo nel periodo",
            money(saving.abs()));
        return;
      }
      ensure(90);
      String[] labels = {
        "Bolletta attuale",
        "Offerta proposta",
        saving.signum() >= 0 ? "Risparmio periodo" : "Maggior costo"
      };
      String[] values = {money(current), money(proposed), money(saving.abs())};
      float w = (WIDTH - 20) / 3;
      for (int i = 0; i < 3; i++) {
        float x = LEFT + i * (w + 10);
        box(x, y - 68, w, 72, PALE);
        if (options.stileEffettivo() == PdfPersonalizzazione.Stile.EDITORIALE)
          box(x, y - 68, 3, 72, GREEN);
        if (options.stileEffettivo() == PdfPersonalizzazione.Stile.SINTESI && i == 2)
          box(x, y - 68, w, 4, GREEN);
        text(labels[i], x + 10, y - 15, regular, 8, MUTED);
        float size = 18;
        while (bold.getStringWidth(values[i]) / 1000 * size > w - 20) size--;
        text(values[i], x + 10, y - 44, bold, size, ACCENT_INK);
      }
      y -= 87;
    }

    void tableHeader(String[] headings) throws IOException {
      ensure(30);
      box(LEFT, y - 21, WIDTH, 26, GREEN);
      float x = LEFT;
      for (int i = 0; i < headings.length; i++) {
        text(headings[i], x + 5, y - 12, bold, 8, ON_PRIMARY);
        x += COLUMNS[i];
      }
      y -= 32;
    }

    void tableRow(String[] cells, String[] headings, int index) throws IOException {
      var lines = new ArrayList<List<String>>();
      int count = 1;
      for (int i = 0; i < cells.length; i++) {
        var wrapped = wrap(cells[i], regular, 7.5f, COLUMNS[i] - 10);
        lines.add(wrapped);
        count = Math.max(count, wrapped.size());
      }
      float height = count * 11 + 8;
      if (y - height < 65) {
        newPage();
        tableHeader(headings);
      }
      if (index % 2 == 0) box(LEFT, y - height + 5, WIDTH, height, PALE);
      float x = LEFT;
      for (int i = 0; i < cells.length; i++) {
        for (int j = 0; j < lines.get(i).size(); j++)
          text(lines.get(i).get(j), x + 5, y - 8 - j * 11, regular, 7.5f, INK);
        x += COLUMNS[i];
      }
      y -= height;
    }

    private List<String> wrap(String raw, PDFont font, float size, float width) throws IOException {
      String value = safe(raw, font).replaceAll("\\s+", " ").trim();
      var lines = new ArrayList<String>();
      StringBuilder line = new StringBuilder();
      for (int offset = 0; offset < value.length(); ) {
        int cp = value.codePointAt(offset);
        offset += Character.charCount(cp);
        String c = new String(Character.toChars(cp));
        if (font.getStringWidth(line + c) / 1000 * size > width && !line.isEmpty()) {
          int space = line.lastIndexOf(" ");
          if (space > 0) {
            lines.add(line.substring(0, space));
            line = new StringBuilder(line.substring(space + 1));
          } else {
            lines.add(line.toString());
            line.setLength(0);
          }
        }
        line.append(c);
      }
      if (!line.isEmpty()) lines.add(line.toString().trim());
      if (lines.isEmpty()) lines.add("");
      return lines;
    }

    private String safe(String value, PDFont font) throws IOException {
      var result = new StringBuilder();
      for (int offset = 0; offset < value.length(); ) {
        int cp = value.codePointAt(offset);
        offset += Character.charCount(cp);
        if (Character.isWhitespace(cp) || Character.isISOControl(cp)) {
          result.append(' ');
          continue;
        }
        String c = new String(Character.toChars(cp));
        try {
          font.encode(c);
          result.append(c);
        } catch (IllegalArgumentException e) {
          result.append('?');
          substitutions = true;
        }
      }
      return result.toString();
    }

    private void box(float x, float bottom, float width, float height, Color color)
        throws IOException {
      stream.setNonStrokingColor(color);
      stream.addRect(x, bottom, width, height);
      stream.fill();
    }

    private void text(String value, float x, float baseline, PDFont font, float size, Color color)
        throws IOException {
      stream.beginText();
      stream.setFont(font, size);
      stream.setNonStrokingColor(color);
      stream.newLineAtOffset(x, baseline);
      stream.showText(safe(value, font));
      stream.endText();
    }

    @java.lang.Override
    public void close() throws IOException {
      stream.close();
      for (int i = 0; i < document.getNumberOfPages(); i++) {
        try (var footer =
            new PDPageContentStream(
                document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
          footer.beginText();
          footer.setFont(regular, 8);
          footer.setNonStrokingColor(MUTED);
          footer.newLineAtOffset(LEFT, 35);
          footer.showText("Progetto Gas · Confronto #" + id + " · Simulazione parametrica");
          footer.endText();
          footer.beginText();
          footer.setFont(regular, 8);
          footer.newLineAtOffset(LEFT + WIDTH - 62, 35);
          footer.showText((i + 1) + " / " + document.getNumberOfPages());
          footer.endText();
        }
      }
    }
  }
}
