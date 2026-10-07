package it.progettogas.confronti;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;

final class PdfLogo {
  private PdfLogo() {}

  static BufferedImage leggi(String data) {
    if (data == null || data.isBlank()) return null;
    if (!data.startsWith("data:image/png;base64,") && !data.startsWith("data:image/jpeg;base64,"))
      throw new IllegalArgumentException("Logo non valido: carica un'immagine PNG o JPEG.");
    try {
      byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
      if (bytes.length > 1048576)
        throw new IllegalArgumentException("Il logo deve essere inferiore a 1 MB.");
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext())
          throw new IllegalArgumentException("Il logo non è un'immagine leggibile.");
        var reader = readers.next();
        try {
          String format = reader.getFormatName();
          if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
            throw new IllegalArgumentException("Logo non valido: usa PNG o JPEG.");
          reader.setInput(input);
          int w = reader.getWidth(0), h = reader.getHeight(0);
          if (w < 1 || h < 1 || w > 4096 || h > 4096 || (long) w * h > 4000000)
            throw new IllegalArgumentException(
                "Logo troppo grande: massimo 4096 px e 4 megapixel.");
          return reader.read(0);
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Logo non valido. Usa PNG o JPEG, massimo 1 MB e 4 megapixel.", e);
    }
  }
}
