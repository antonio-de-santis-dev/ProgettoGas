package it.progettogas.confronti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Raster previews and download bytes come from the exact same PDF. */
public record PdfAnteprima(String pdfBase64, List<String> pagine) {
  public static PdfAnteprima da(byte[] bytes) throws IOException {
    var pages = new ArrayList<String>();
    try (var document = Loader.loadPDF(bytes)) {
      var renderer = new PDFRenderer(document);
      for (int i = 0; i < document.getNumberOfPages(); i++) {
        var image = renderer.renderImageWithDPI(i, 108);
        try (var output = new ByteArrayOutputStream()) {
          ImageIO.write(image, "png", output);
          pages.add(
              "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()));
        } finally {
          image.flush();
        }
      }
    }
    return new PdfAnteprima(Base64.getEncoder().encodeToString(bytes), List.copyOf(pages));
  }
}
