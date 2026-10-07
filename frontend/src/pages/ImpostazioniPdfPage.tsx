import { useEffect, useState } from "react";
import { api, messaggioErrore } from "../api";
import { Caricamento, Errore, Titolo } from "../components/ui";
import PdfEditor from "../components/PdfEditor";
import type { PdfOptions } from "../components/pdfPersonalizzazione";
type Configurazione = { opzioni: PdfOptions; versione: number };
export default function ImpostazioniPdfPage() {
  const [config, setConfig] = useState<Configurazione | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setConfig(null);
    api
      .get<Configurazione>("/impostazioni-pdf", { signal: controller.signal })
      .then(({ data }) => {
        if (!controller.signal.aborted) setConfig(data);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(messaggioErrore(e));
      });
    return () => controller.abort();
  }, [retry]);
  async function salva(opzioni: PdfOptions) {
    if (!config) throw new Error("Carica le impostazioni prima di salvarle.");
    const { data } = await api.put<Configurazione>("/impostazioni-pdf", {
      opzioni,
      versione: config.versione,
    });
    setConfig(data);
    return data.opzioni;
  }
  return (
    <>
      <Titolo
        eyebrow="I tuoi documenti"
        title="Impostazioni PDF"
        description="Scegli lo stile dei tuoi report e salva la configurazione da applicare a ogni PDF."
      />
      {error ? (
        <Errore message={error} onRetry={() => setRetry((v) => v + 1)} />
      ) : !config ? (
        <Caricamento />
      ) : (
        <PdfEditor initialOptions={config.opzioni} onSave={salva} />
      )}
    </>
  );
}
