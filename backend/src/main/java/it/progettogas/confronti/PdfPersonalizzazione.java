package it.progettogas.confronti;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Presentation only: economic data always comes from the saved comparison. */
public record PdfPersonalizzazione(
    Stile stile,
    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Usa un colore esadecimale, ad esempio #1E3A8A")
        String colore,
    @Size(max = 1400000, message = "Il logo deve essere inferiore a 1 MB") String logo,
    @Valid Consulente consulente) {
  public PdfPersonalizzazione {
    if (stile == null) stile = Stile.CLASSICO;
    if (colore == null) colore = "#194D3D";
    if (consulente == null) consulente = new Consulente("", "", "", "", "", false);
  }

  public enum Stile {
    CLASSICO,
    ESSENZIALE,
    EDITORIALE,
    SINTESI
  }

  public record Consulente(
      @Size(max = 100) String nome,
      @Size(max = 100) String ruolo,
      @Size(max = 120) String email,
      @Size(max = 60) String telefono,
      @Size(max = 160) String indirizzo,
      boolean dimostrativo) {}

  public static PdfPersonalizzazione predefinita() {
    return new PdfPersonalizzazione(
        Stile.CLASSICO, "#194D3D", null, new Consulente("", "", "", "", "", false));
  }

  public Stile stileEffettivo() {
    return stile == null ? Stile.CLASSICO : stile;
  }

  public String coloreEffettivo() {
    return colore == null ? "#194D3D" : colore;
  }
}
