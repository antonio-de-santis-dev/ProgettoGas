import { useState, type FormEvent } from "react";
import { ExternalLink, Plus, BookOpen, Pencil, History } from "lucide-react";
import { api, messaggioErrore, useLista } from "../api";
import { Campo, Decimale, Dialogo, Titolo, Errore, Caricamento } from "../components/ui";
import { number, type Saved, type Source } from "../domain";
import "../gas-fonti.css";

const catalogo = [
  { categoria: "Indici gas", ente: "ARERA", titolo: "Materia prima gas · CMEMm", testo: "Riferimento mensile del servizio di tutela della vulnerabilità, basato sul PSV day ahead.", uso: "Verifica mese, unità e potere calorifico di riferimento. Per il mercato libero usa l’indice indicato nel contratto.", url: "https://www.arera.it/area-operatori/prezzi-e-tariffe/valore-cmemm-vulnerabili" },
  { categoria: "Indici gas", ente: "GME", titolo: "IG Index GME", testo: "Indice giornaliero pubblicato dal Gestore dei Mercati Energetici sul mercato italiano del gas.", uso: "È un indice distinto: non sostituisce automaticamente il PSV previsto dall’offerta.", url: "https://www.mercatoelettrico.org/Home/Pubblicazioni/Indici-GME/IGIndexGmeEsiti" },
  { categoria: "Tariffe e oneri", ente: "ARERA", titolo: "Distribuzione, misura e oneri", testo: "Tariffe e componenti regolate per trasporto, gestione del contatore e oneri generali del gas.", uso: "Controlla periodo, ambito tariffario e scaglioni; riporta i valori applicabili in Parametri gestore.", url: "https://www.arera.it/area-operatori/prezzi-e-tariffe/tariffe-di-distribuzione-misura-oneri-generali" },
  { categoria: "Imposte", ente: "ADM", titolo: "Accise sul gas naturale", testo: "Normativa, aliquote e chiarimenti dell’Agenzia delle Dogane e dei Monopoli.", uso: "Verifica destinazione d’uso, territorio e condizioni applicabili prima di configurare l’accisa.", url: "https://www.adm.gov.it/portale/accise" },
  { categoria: "Imposte", ente: "REGIONI", titolo: "Addizionali regionali", testo: "Le amministrazioni regionali pubblicano i riferimenti per l’addizionale sul gas. Qui puoi consultare il portale della Puglia.", uso: "Per forniture in altre regioni consulta il relativo portale tributario e verifica il periodo di validità.", url: "https://www.regione.puglia.it/web/tributi/arisgan" },
];
const vuota = (): Source => ({ month: "", psv: "", source: "", reason: "" });
export default function GasFontiPage() {
  const lista = useLista<Saved<Source>>("/fonti");
  const [categoria, setCategoria] = useState("Tutte");
  const [mese, setMese] = useState("");
  const [dialog, setDialog] = useState(false);
  const [edit, setEdit] = useState<Saved<Source> | null>(null);
  const [value, setValue] = useState<Source>(vuota);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revisioni, setRevisioni] = useState<unknown[] | null>(null);
  function apri(record: Saved<Source> | null) {
    setEdit(record); setValue(record ? { ...record.data } : vuota()); setError(""); setDialog(true);
  }
  async function salva(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError("");
    try {
      if (edit) await api.put(`/fonti/${edit.id}`, { data: value, version: edit.version });
      else await api.post("/fonti", { data: value });
      lista.reload(); setDialog(false); setNotice("Riferimento PSV salvato.");
    } catch (e) { setError(messaggioErrore(e)); }
    finally { setBusy(false); }
  }
  const records = lista.data.filter(r => !mese || r.data.month === mese)
    .slice().sort((a, b) => b.data.month.localeCompare(a.data.month) || b.id - a.id);
  return <div className="gas-fonti-page">
    <Titolo eyebrow="Dati verificabili, scelte trasparenti" title="Fonti ufficiali"
      description="Consulta i riferimenti pubblici per gas, tariffe e imposte. Conserva le fonti dei valori utilizzati nelle simulazioni." />
    <section className="panel gas-fonti-catalogo" aria-label="Catalogo fonti ufficiali gas">
      <div className="panel-top"><div><h2>Riferimenti per il gas</h2><p>Apri la pubblicazione dell’ente e verifica le condizioni della tua fornitura.</p></div><BookOpen size={24}/></div>
      <div className="gas-fonti-tabs" role="group" aria-label="Categorie delle fonti">
        {["Tutte", "Indici gas", "Tariffe e oneri", "Imposte"].map(c =>
          <button key={c} type="button" className={categoria === c ? "active" : ""} aria-pressed={categoria === c} onClick={() => setCategoria(c)}>{c}</button>)}
      </div>
      <div className="gas-fonti-cards">{catalogo.filter(f => categoria === "Tutte" || f.categoria === categoria).map(f =>
        <article className="gas-fonte-card" key={f.titolo}>
          <div className="gas-fonte-meta"><span className="badge">{f.ente}</span><small>{f.categoria}</small></div>
          <h3>{f.titolo}</h3><p>{f.testo}</p><p className="help">{f.uso}</p>
          <a className="button secondary" href={f.url} target="_blank" rel="noopener noreferrer" aria-label={`Consulta ${f.titolo}`}>Consulta fonte <ExternalLink size={15}/></a>
        </article>)}</div>
      <p className="gas-fonti-note">Le pubblicazioni si aprono sul sito dell’ente. I valori non vengono importati automaticamente: il PSV si inserisce nei mesi della bolletta; tariffe e aliquote in Parametri gestore.</p>
    </section>
    <section className="panel gas-fonti-archivio" aria-label="Archivio riferimenti PSV">
      <div className="panel-top"><div><h2>Riferimenti PSV salvati</h2><p>Archivio dei valori inseriti manualmente, con mese e provenienza.</p></div>
        <button className="button primary" onClick={() => apri(null)}><Plus size={18}/>Aggiungi riferimento PSV</button></div>
      {notice && <p className="notice" role="status">{notice}</p>}
      {!dialog && error && <Errore message={error}/>}
      {lista.error && <Errore message={lista.error}/>}
      <div className="gas-fonti-filter"><Campo label="Filtra per mese" type="month" value={mese} onChange={e => setMese(e.target.value)}/>{mese && <button className="text-button" onClick={() => setMese("")}>Mostra tutti</button>}</div>
      {lista.loading ? <Caricamento/> : records.length === 0 ? <div className="gas-fonti-empty"><BookOpen size={26}/><h3>{mese ? "Nessun riferimento per questo mese" : "Nessun riferimento PSV salvato"}</h3><p>Aggiungi il valore usato nell’offerta, indicando la pubblicazione o il documento da cui proviene.</p></div> :
        <div className="gas-fonti-records">{records.map(r => <article className="gas-fonti-record" key={r.id}>
          <div><span className="eyebrow">{new Intl.DateTimeFormat("it-IT", { month: "long", year: "numeric" }).format(new Date(r.data.month + "-01T12:00:00"))}</span><h3>{number(r.data.psv, 12)} <small>€/Smc</small></h3><span className="badge">Inserimento manuale</span></div>
          <div className="gas-fonti-provenienza"><strong>{r.data.source}</strong><p>{r.data.reason}</p></div>
          <div className="gas-fonti-record-actions"><button className="button secondary" onClick={() => apri(r)}><Pencil size={15}/>Modifica</button><button className="text-button" onClick={async () => { try { setRevisioni((await api.get(`/fonti/${r.id}/revisioni`)).data); } catch (e) { setError(messaggioErrore(e)); } }}><History size={15}/>Revisioni</button></div>
        </article>)}</div>}
    </section>
    {dialog && <Dialogo title={edit ? "Modifica riferimento PSV" : "Aggiungi riferimento PSV"} onClose={() => setDialog(false)}>
      <form onSubmit={salva}>{error && <Errore message={error}/>}<p className="help">Registra il valore in €/Smc e la sua provenienza. Il riferimento salvato non compila automaticamente i periodi della bolletta.</p>
        <div className="form-grid"><Campo label="Mese PSV" type="month" required value={value.month} onChange={e => setValue({ ...value, month: e.target.value })}/><Decimale label="PSV (€/Smc)" required value={value.psv} onChange={e => setValue({ ...value, psv: e.target.value })}/><Campo label="Fonte verificata" required placeholder="Ente, pubblicazione o URL" value={value.source} onChange={e => setValue({ ...value, source: e.target.value })}/><Campo label="Motivo / riferimento" required value={value.reason} onChange={e => setValue({ ...value, reason: e.target.value })}/></div>
        <div className="actions"><button className="button secondary" type="button" onClick={() => setDialog(false)}>Annulla</button><button className="button primary" disabled={busy}>{busy ? "Salvataggio…" : "Salva fonte"}</button></div>
      </form>
    </Dialogo>}
    {revisioni && <Dialogo title="Revisioni del riferimento PSV" onClose={() => setRevisioni(null)}>{revisioni.length ? <pre className="raw-json">{JSON.stringify(revisioni, null, 2)}</pre> : <p>Nessuna modifica registrata.</p>}</Dialogo>}
  </div>;
}
