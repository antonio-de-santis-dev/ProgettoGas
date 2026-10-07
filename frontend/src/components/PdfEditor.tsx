import { useEffect, useId, useRef, useState, type CSSProperties } from "react";
import {
  Download,
  FileText,
  LoaderCircle,
  RotateCcw,
  Upload,
  Save,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { api } from "../api";
import { Campo, Errore } from "./ui";
import {
  coloriPdf,
  defaultPdf,
  downloadPdf,
  errorePdf,
  leggiLogo,
  stiliPdf,
  decodificaAnteprima,
  type PdfAnteprima,
  type PdfOptions,
} from "./pdfPersonalizzazione";

export default function PdfEditor({
  initialOptions,
  onSave,
}: {
  initialOptions: PdfOptions;
  onSave: (options: PdfOptions) => Promise<PdfOptions>;
}) {
  const [options, setOptions] = useState<PdfOptions>(() =>
    structuredClone(initialOptions),
  );
  const [savedSettings, setSavedSettings] = useState(
    JSON.stringify(initialOptions),
  );
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [page, setPage] = useState(0);
  const [hex, setHex] = useState(options.colore);
  const [preview, setPreview] = useState<{
    blob: Blob;
    url: string;
    settings: string;
    pagine: string[];
  } | null>(null);
  const [error, setError] = useState("");
  const [logoError, setLogoError] = useState("");
  const [logoBusy, setLogoBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [retry, setRetry] = useState(0);
  const logoVersion = useRef(0);
  const input = useRef<HTMLInputElement>(null);
  const colorId = useId();
  const settings = JSON.stringify(options);
  const hexValid = /^#[\da-f]{6}$/i.test(hex);
  const ready =
    !!preview &&
    preview.settings === settings &&
    hexValid &&
    !logoBusy &&
    !error;
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setNotice("");
    const timer = window.setTimeout(async () => {
      try {
        const { data } = await api.post<PdfAnteprima>(
          "/impostazioni-pdf/anteprima",
          JSON.parse(settings),
          { signal: controller.signal },
        );
        const blob = decodificaAnteprima(data);
        if (!controller.signal.aborted)
          setPreview({
            blob,
            url: URL.createObjectURL(blob),
            settings,
            pagine: data.pagine,
          });
      } catch (e) {
        if (!controller.signal.aborted) {
          const message = await errorePdf(e);
          if (!controller.signal.aborted) setError(message);
        }
      }
    }, 400);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [settings, retry]);
  useEffect(
    () => () => {
      if (preview) URL.revokeObjectURL(preview.url);
    },
    [preview],
  );
  useEffect(
    () => () => {
      logoVersion.current++;
    },
    [],
  );
  const dirty = settings !== savedSettings || !hexValid;
  const currentPage = preview ? Math.min(page, preview.pagine.length - 1) : 0;
  async function salva() {
    if (!hexValid || logoBusy || saving) {
      setSaveError(
        "Controlla il colore e attendi il caricamento del logo prima di salvare.",
      );
      return;
    }
    setSaving(true);
    setSaveError("");
    setNotice("");
    try {
      const saved = await onSave(structuredClone(options));
      setSavedSettings(JSON.stringify(saved));
      setNotice(
        "Impostazioni PDF salvate. I prossimi report useranno questa configurazione.",
      );
    } catch (e) {
      setSaveError(await errorePdf(e));
    } finally {
      setSaving(false);
    }
  }
  function colore(value: string) {
    setHex(value);
    if (/^#[\da-f]{6}$/i.test(value))
      setOptions((o) => ({ ...o, colore: value.toUpperCase() }));
  }
  function consulente(
    field: keyof PdfOptions["consulente"],
    value: string | boolean,
  ) {
    setOptions((o) => ({
      ...o,
      consulente: { ...o.consulente, [field]: value },
    }));
  }
  async function caricaLogo(file?: File) {
    if (!file) return;
    const version = ++logoVersion.current;
    setLogoBusy(true);
    setLogoError("");
    try {
      const logo = await leggiLogo(file);
      if (version === logoVersion.current) setOptions((o) => ({ ...o, logo }));
    } catch (e) {
      if (version === logoVersion.current)
        setLogoError(e instanceof Error ? e.message : "Logo non valido.");
    } finally {
      if (version === logoVersion.current) setLogoBusy(false);
      if (input.current) input.current.value = "";
    }
  }
  function reset() {
    logoVersion.current++;
    setLogoBusy(false);
    setLogoError("");
    setOptions(structuredClone(defaultPdf));
    setHex(defaultPdf.colore);
  }
  return (
    <section
      className="pdf-settings-editor"
      aria-label="Editor impostazioni PDF"
    >
      <div className="pdf-editor-intro">
        <FileText size={20} />
        <p>
          Documento dimostrativo. Salva le impostazioni per applicarle ai
          prossimi report, anche dallo storico.
        </p>
      </div>
      <div className="pdf-editor-grid">
        <div className="pdf-editor-controls">
          <fieldset className="pdf-control-section">
            <legend>01 · Scegli lo stile</legend>
            <p className="help">
              Quattro modi di presentare gli stessi risultati, senza cambiare il
              calcolo.
            </p>
            <div className="pdf-style-grid">
              {stiliPdf.map((style) => (
                <label
                  key={style.id}
                  className={`pdf-style ${options.stile === style.id ? "selected" : ""}`}
                >
                  <input
                    type="radio"
                    name="pdf-stile"
                    value={style.id}
                    checked={options.stile === style.id}
                    onChange={() =>
                      setOptions((o) => ({ ...o, stile: style.id }))
                    }
                  />
                  <span
                    className={`pdf-style-mini mini-${style.id.toLowerCase()}`}
                    aria-hidden="true"
                    style={{ "--pdf-color": options.colore } as CSSProperties}
                  >
                    <i />
                    <b />
                    <b />
                    <em />
                    <em />
                    <em />
                  </span>
                  <strong>{style.nome}</strong>
                  <small>{style.descrizione}</small>
                </label>
              ))}
            </div>
          </fieldset>
          <fieldset className="pdf-control-section">
            <legend>02 · Colore del report</legend>
            <div className="pdf-swatches">
              {coloriPdf.map(([value, name]) => (
                <button
                  key={value}
                  type="button"
                  aria-label={name}
                  aria-pressed={options.colore === value}
                  title={`${name} · ${value}`}
                  onClick={() => colore(value)}
                >
                  <span style={{ background: value }} />
                  {name}
                  {options.colore === value && <b aria-hidden="true">✓</b>}
                </button>
              ))}
            </div>
            <div className="pdf-color-custom">
              <label htmlFor={colorId}>
                Tavolozza colori
                <input
                  id={colorId}
                  type="color"
                  value={options.colore}
                  onChange={(e) => colore(e.target.value)}
                />
              </label>
              <Campo
                label="Codice colore"
                value={hex}
                maxLength={7}
                onChange={(e) => colore(e.target.value)}
                aria-invalid={!hexValid}
                hint={
                  hexValid
                    ? "Formato #RRGGBB"
                    : "Inserisci # seguito da 6 cifre esadecimali."
                }
              />
            </div>
            <button
              type="button"
              className="text-button"
              onClick={() => colore(defaultPdf.colore)}
            >
              Usa il verde originale
            </button>
            <p className="help">
              Il colore secondario è una tinta più chiara del principale. Il
              testo si adatta per restare leggibile.
            </p>
          </fieldset>
          <fieldset className="pdf-control-section">
            <legend>03 · Logo</legend>
            <label className="pdf-logo-upload">
              <Upload size={20} />
              <span>{logoBusy ? "Lettura logo…" : "Carica il tuo logo"}</span>
              <input
                ref={input}
                type="file"
                accept="image/png,image/jpeg"
                onChange={(e) => void caricaLogo(e.target.files?.[0])}
                disabled={logoBusy}
              />
            </label>
            <p className="help">
              PNG o JPEG · massimo 1 MB e 4 megapixel. Il logo mantiene le sue
              proporzioni.
            </p>
            {options.logo && (
              <div className="pdf-logo-preview">
                <img src={options.logo} alt="Logo scelto per il report" />
                <button
                  className="text-button"
                  type="button"
                  onClick={() => {
                    logoVersion.current++;
                    setLogoBusy(false);
                    setOptions((o) => ({ ...o, logo: null }));
                  }}
                >
                  Rimuovi logo
                </button>
              </div>
            )}
            {logoError && <Errore message={logoError} />}
          </fieldset>
          <fieldset className="pdf-control-section">
            <legend>04 · Chi prepara il report</legend>
            <p className="help">
              Dati di esempio modificabili. Il profilo utente definitivo verrà
              collegato più avanti.
            </p>
            <Campo
              label="Nome o studio"
              value={options.consulente.nome}
              maxLength={100}
              onChange={(e) => consulente("nome", e.target.value)}
            />
            <Campo
              label="Ruolo"
              value={options.consulente.ruolo}
              maxLength={100}
              onChange={(e) => consulente("ruolo", e.target.value)}
            />
            <Campo
              label="Email"
              type="email"
              value={options.consulente.email}
              maxLength={120}
              onChange={(e) => consulente("email", e.target.value)}
            />
            <Campo
              label="Telefono"
              type="tel"
              value={options.consulente.telefono}
              maxLength={60}
              onChange={(e) => consulente("telefono", e.target.value)}
            />
            <Campo
              label="Indirizzo"
              value={options.consulente.indirizzo}
              maxLength={160}
              onChange={(e) => consulente("indirizzo", e.target.value)}
            />
            <label className="pdf-demo-check">
              <input
                type="checkbox"
                checked={options.consulente.dimostrativo}
                onChange={(e) => consulente("dimostrativo", e.target.checked)}
              />
              Indica nel PDF che sono dati dimostrativi
            </label>
          </fieldset>
          <button type="button" className="button secondary" onClick={reset}>
            <RotateCcw size={16} />
            Ripristina impostazioni
          </button>
          <p className="help">
            Stile, colori, logo e recapiti vengono salvati nel sistema solo
            quando premi Salva impostazioni PDF.
          </p>
        </div>
        <section className="pdf-preview" aria-label="Anteprima del PDF">
          <div className="pdf-preview-toolbar">
            <div>
              <strong>Anteprima in tempo reale</strong>
              <p role="status">
                {!hexValid
                  ? "Controlla il codice colore"
                  : error
                    ? "Anteprima non disponibile"
                    : ready
                      ? "PDF aggiornato · formato A4"
                      : "Aggiornamento del PDF…"}
              </p>
            </div>
            {preview && (
              <a
                className="text-button"
                href={preview.url}
                target="_blank"
                rel="noreferrer"
              >
                Apri PDF in una nuova scheda
              </a>
            )}
            {!ready && !error && hexValid && (
              <LoaderCircle className="spin" size={20} />
            )}
          </div>
          {error && (
            <Errore message={error} onRetry={() => setRetry((v) => v + 1)} />
          )}
          <div
            className={`pdf-preview-document ${!ready ? "updating" : ""}`}
            aria-busy={!ready && !error}
          >
            {preview ? (
              <figure className="pdf-preview-sheet">
                <img
                  src={preview.pagine[currentPage]}
                  alt={`Anteprima PDF · pagina ${currentPage + 1} di ${preview.pagine.length}`}
                />
              </figure>
            ) : (
              <div className="pdf-preview-empty">
                <FileText size={40} />
                <p>Preparazione del documento…</p>
              </div>
            )}
          </div>
          {preview && (
            <div
              className="pdf-page-navigation"
              aria-label="Pagine dell’anteprima"
            >
              <button
                type="button"
                className="button secondary compact"
                aria-label="Pagina precedente"
                disabled={currentPage === 0}
                onClick={() => setPage(currentPage - 1)}
              >
                <ChevronLeft size={18} />
              </button>
              <span>
                Pagina {currentPage + 1} di {preview.pagine.length}
              </span>
              <button
                type="button"
                className="button secondary compact"
                aria-label="Pagina successiva"
                disabled={currentPage === preview.pagine.length - 1}
                onClick={() => setPage(currentPage + 1)}
              >
                <ChevronRight size={18} />
              </button>
            </div>
          )}
        </section>
      </div>
      {saveError && <Errore message={saveError} />}
      <footer className="pdf-editor-footer">
        <p role="status">
          {dirty
            ? "Modifiche non salvate"
            : notice || "Configurazione salvata nel sistema"}
        </p>
        <button
          type="button"
          className="button secondary"
          aria-disabled={!ready}
          onClick={() => {
            if (!ready || !preview) {
              setNotice(
                "Attendi il PDF aggiornato e controlla eventuali errori.",
              );
              return;
            }
            downloadPdf(preview.blob, 0);
            setNotice("Download PDF avviato.");
          }}
        >
          <Download size={18} />
          Scarica PDF di esempio
        </button>
        <button
          type="button"
          className="button primary"
          aria-disabled={saving || !hexValid || logoBusy}
          onClick={() => void salva()}
        >
          {saving ? (
            <LoaderCircle className="spin" size={18} />
          ) : (
            <Save size={18} />
          )}
          {saving ? "Salvataggio…" : "Salva impostazioni PDF"}
        </button>
      </footer>
    </section>
  );
}
