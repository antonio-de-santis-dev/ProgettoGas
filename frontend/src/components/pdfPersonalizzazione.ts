import axios from "axios";
import { messaggioErrore } from "../api";
export const stiliPdf = [
  {
    id: "CLASSICO",
    nome: "Classico",
    descrizione:
      "Intestazione piena, dati ordinati e riepilogo in tre riquadri.",
  },
  {
    id: "ESSENZIALE",
    nome: "Essenziale",
    descrizione: "Più spazio bianco, linee sottili e importi in elenco.",
  },
  {
    id: "EDITORIALE",
    nome: "Editoriale",
    descrizione: "Titoli marcati, accenti laterali e lettura guidata.",
  },
  {
    id: "SINTESI",
    nome: "Sintesi cliente",
    descrizione: "Il risparmio prima di tutto, spiegato con parole semplici.",
  },
] as const;
export const coloriPdf = [
  ["#1E3A8A", "Blu Istituzionale"],
  ["#0284C7", "Azzurro Chiaro"],
  ["#15803D", "Verde Sostenibile"],
  ["#D97706", "Ambra Caldo"],
  ["#EA580C", "Arancione Dinamico"],
  ["#374151", "Grigio Antracite"],
  ["#6B21A8", "Viola Premium"],
] as const;
export type PdfOptions = {
  stile: (typeof stiliPdf)[number]["id"];
  colore: string;
  logo: string | null;
  consulente: {
    nome: string;
    ruolo: string;
    email: string;
    telefono: string;
    indirizzo: string;
    dimostrativo: boolean;
  };
};
export const defaultPdf: PdfOptions = {
  stile: "CLASSICO",
  colore: "#194D3D",
  logo: null,
  consulente: {
    nome: "",
    ruolo: "",
    email: "",
    telefono: "",
    indirizzo: "",
    dimostrativo: false,
  },
};
export function downloadPdf(blob: Blob, id: number) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `confronto-${id}.pdf`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export async function errorePdf(e: unknown): Promise<string> {
  if (axios.isAxiosError(e) && e.response?.data instanceof Blob) {
    try {
      const data = JSON.parse(await e.response.data.text());
      if (typeof data.message === "string")
        return (
          data.message +
          (data.fields ? ": " + Object.values(data.fields).join("; ") : "")
        );
    } catch {
      /* Preserve the standard error for non JSON responses. */
    }
  }
  return messaggioErrore(e);
}
export function verificaPdf(blob: Blob) {
  if (!blob.type.includes("application/pdf"))
    throw new Error("Il server non ha restituito un PDF valido.");
}
export async function leggiLogo(file: File): Promise<string> {
  if (!["image/png", "image/jpeg"].includes(file.type))
    throw new Error("Carica un logo PNG o JPEG.");
  if (file.size > 1048576)
    throw new Error("Il logo deve essere inferiore a 1 MB.");
  const data = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () =>
      reject(new Error("Impossibile leggere il logo. Riprova."));
    reader.readAsDataURL(file);
  });
  await new Promise<void>((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      if (
        image.naturalWidth > 4096 ||
        image.naturalHeight > 4096 ||
        image.naturalWidth * image.naturalHeight > 4000000
      )
        reject(new Error("Logo troppo grande: massimo 4096 px e 4 megapixel."));
      else resolve();
    };
    image.onerror = () =>
      reject(new Error("Il logo non è un'immagine leggibile."));
    image.src = data;
  });
  return data;
}

export type PdfAnteprima = { pdfBase64: string; pagine: string[] };
export function decodificaAnteprima(data: PdfAnteprima): Blob {
  if (
    !data ||
    !Array.isArray(data.pagine) ||
    !data.pagine.length ||
    !data.pagine.every((p) => p.startsWith("data:image/png;base64,"))
  )
    throw new Error("Anteprima PDF non valida.");
  const binary = atob(data.pdfBase64);
  if (!binary.startsWith("%PDF-"))
    throw new Error("Il server non ha restituito un PDF valido.");
  return new Blob([Uint8Array.from(binary, (c) => c.charCodeAt(0))], {
    type: "application/pdf",
  });
}
