import { useState } from "react";
import { Link } from "react-router-dom";
import { Plus, FileText, Tags, SlidersHorizontal, ArrowRight, ArrowLeftRight, Download, CheckCircle2 } from "lucide-react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import Decimal from "decimal.js";
import { errorePdf, verificaPdf } from "../components/pdfPersonalizzazione";
import { api, messaggioErrore, useLista } from "../api";
import { Titolo, Campo, Decimale, Dialogo, Errore, Caricamento, Vuoto } from "../components/ui";
import { OfferFields, BillFields, ProfileFields, EntityForm } from "./GasForms";
import { categories, bill, offer, profile, euro, number, negative, absolute, sum, type Saved, type Offer, type Bill, type Profile, type Comparison, type Category } from "../domain";
export { default as FontiPage } from "./GasFontiPage";
function Listing<T>({ kind, title, description, create, fields, name, details }: {
    kind: string;
    title: string;
    description: string;
    create: () => T;
    fields: (v: T, set: (v: T) => void) => React.ReactNode;
    name: (v: T) => string;
    details: (v: T) => React.ReactNode;
}) {
    const list = useLista<Saved<T>>(`/${kind}`);
    const [edit, setEdit] = useState<Saved<T> | null>(null), [remove, setRemove] = useState<Saved<T> | null>(null), [search, setSearch] = useState(""), [error, setError] = useState(""), [notice, setNotice] = useState(""), [audit, setAudit] = useState<unknown[] | null>(null), [busy, setBusy] = useState(false);
    async function duplicate(r: Saved<T>) { setError(""); try {
        await api.post(`/${kind}/${r.id}/duplica`);
        list.reload();
        setNotice("Copia creata. Puoi modificare i dati.");
    }
    catch (e) {
        setError(messaggioErrore(e));
    } }
    async function deletion() { if (!remove)
        return; setBusy(true); setError(""); try {
        await api.delete(`/${kind}/${remove.id}`, { params: { version: remove.version } });
        setRemove(null);
        list.reload();
        setNotice("Dato eliminato. I confronti salvati conservano lo snapshot.");
    }
    catch (e) {
        setError(messaggioErrore(e));
    }
    finally {
        setBusy(false);
    } }
    return <><Titolo eyebrow="Il tuo spazio di consulenza" title={title} description={description} action={<button className="button primary" onClick={() => setEdit({ id: 0, version: 0, createdAt: "", data: create() })}><Plus size={18}/> {kind === "offerte" ? "Nuova offerta" : kind === "bollette" ? "Nuova bolletta" : "Nuovo profilo"}</button>}/>{notice && <p role="status" className="notice">{notice}</p>}{(error || list.error) && <Errore message={error || list.error}/>}<Campo label="Cerca" value={search} onChange={e => setSearch(e.target.value)}/>{list.loading ? <Caricamento /> : list.data.length === 0 ? <Vuoto title="Inizia dai tuoi dati" text="Crea il primo elemento oppure carica un esempio Excel nella sezione Confronto."/> : <div className="gas-list">{list.data.filter(r => name(r.data).toLowerCase().includes(search.toLowerCase())).map(r => { const published = kind === "parametri" && (r.data as Profile).status === "PUBLISHED"; return <article className="panel" key={r.id}><div className="panel-top"><h2>{name(r.data)}</h2><span className="badge">#{r.id} · versione {r.version + 1}</span></div>{details(r.data)}<div className="actions"><button className="button secondary" disabled={published} onClick={() => setEdit(r)}>Modifica</button><button className="button secondary" onClick={() => duplicate(r)}>Duplica</button><button className="text-button" onClick={async () => { try {
        setAudit((await api.get(`/${kind}/${r.id}/revisioni`)).data);
    }
    catch (e) {
        setError(messaggioErrore(e));
    } }}>Revisioni</button><button className="text-button" disabled={published} onClick={() => setRemove(r)}>Elimina</button></div>{published && <p className="help">Versione pubblicata immutabile. Duplica per creare una nuova bozza.</p>}</article>; })}</div>}{edit && <Dialogo title={edit.id ? "Modifica dati" : "Nuovi dati"} onClose={() => setEdit(null)}><EntityForm kind={kind} initial={edit} onClose={() => setEdit(null)} onSaved={() => { list.reload(); setNotice("Dati salvati. I confronti precedenti conservano i valori originali."); }}>{fields}</EntityForm></Dialogo>}{remove && <Dialogo title="Eliminare questo dato?" onClose={() => setRemove(null)}><p>{name(remove.data)}</p><p>I confronti già salvati restano disponibili.</p><button className="button secondary" onClick={() => setRemove(null)}>Annulla</button><button className="button primary" disabled={busy} onClick={deletion}>Conferma eliminazione</button></Dialogo>}{audit && <Dialogo title="Revisioni salvate" onClose={() => setAudit(null)}><pre className="raw-json">{JSON.stringify(audit, null, 2)}</pre></Dialogo>}</>;
}
export function OffertePage() { return <Listing<Offer> kind="offerte" title="Le tue offerte gas" description="Prezzo fisso o PSV, quote QVD e CCR. Condizioni commerciali separate dai dati del cliente." create={offer} name={v => `${v.name} · ${v.provider}`} fields={(v, set) => <OfferFields value={v} set={set}/>} details={v => <p>{v.type === "FIXED" ? `Prezzo fisso ${number(v.price)} €/Smc` : `PSV + ${number(v.spread)} €/Smc`} · QVD fissa {euro(v.qvdFixed)}/PDR/mese · {v.active ? "Attiva" : "Disattivata"}</p>}/>; }
export function BollettePage() { return <Listing<Bill> kind="bollette" title="Bollette clienti" description="Fornitura gas naturale: PDR, consumi Smc, periodi e importo fatturato dal fornitore attuale." create={bill} name={v => `${v.client} · ${v.periods[0].month}`} fields={(v, set) => <BillFields value={v} set={set}/>} details={v => <p>PDR {v.pdr} · {number(sum(v.periods.map(p => p.consumptionSmc)))} Smc · {v.periods.length} mesi · {euro(v.previousTotal)}</p>}/>; }
export function ParametriPage() { return <><p className="notice">Tariffe e scaglioni devono essere espliciti e verificati. Nessuna fonte esterna aggiorna automaticamente accise, oneri o IVA.</p><Listing<Profile> kind="parametri" title="Parametri del gestore gas" description="Profili versionati con validità, scaglioni e aliquote. Compatibilità Excel o IVA configurata per voce." create={profile} name={v => `${v.name} · ${v.provider}`} fields={(v, set) => <ProfileFields value={v} set={set}/>} details={v => <><span className={`badge ${v.status === "PUBLISHED" ? "green" : ""}`}>{v.status === "PUBLISHED" ? "Pubblicato" : v.status === "DRAFT" ? "Da validare" : "Archiviato"}</span><p>{v.validFrom} → {v.validTo ?? "senza scadenza"} · {v.mode === "SOURCE_COMPATIBILITY" ? "Compatibilità Excel" : "Regole configurate"} · {v.lines.length} voci</p><p className="help">{v.source}</p></>}/></>; }
export function Risultato({ saved }: {
    saved: Saved<Comparison>;
}) {
    const [error, setError] = useState("");
    const [pdfBusy, setPdfBusy] = useState(false);
    const c = saved.data, r = c.result, more = negative(r.differenceRaw);
    async function download() { setError(""); setPdfBusy(true); try {
        const response = await api.get(`/confronti/${saved.id}/pdf`, { responseType: "blob" });
        verificaPdf(response.data);
        const url = URL.createObjectURL(response.data);
        const a = document.createElement("a");
        a.href = url;
        a.download = `confronto-gas-${saved.id}.pdf`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
    catch (e) {
        setError(await errorePdf(e));
    } finally { setPdfBusy(false); } }
    const chart = Object.entries(r.categories).map(([k, v]) => ({ name: categories[k as Category], value: new Decimal(v).toNumber() }));
    return <section className="result" aria-label="Risultato del confronto"><div className="result-banner"><div><span className="badge green"><CheckCircle2 size={14}/>Confronto salvato #{saved.id}</span><h2>{c.bill.client}</h2><p>{c.offer.name} · {r.months} mesi · {number(r.consumptionSmc)} Smc · {c.bill.periods[0].month} → {c.bill.periods.at(-1)?.month}</p></div><div className="result-actions"><span className="result-date">{new Date(saved.createdAt).toLocaleDateString("it-IT")}</span><button className="button primary" disabled={pdfBusy} onClick={download}><Download size={18}/>{pdfBusy ? "Preparazione PDF…" : "Scarica PDF"}</button></div></div>{r.unapproved && <p className="notice">Calcolo con profilo da validare. Le tariffe non sono approvate per uso commerciale.</p>}<div className="result-metrics"><div className="metric"><span>Bolletta attuale</span><strong>{euro(c.bill.previousTotal)}</strong></div><div className="metric"><span>Offerta proposta</span><strong>{euro(r.total)}</strong></div><div className={`metric ${more ? "gas-negative" : ""}`}><span>{more ? "Maggior costo stimato" : "Risparmio nel periodo"}</span><strong>{euro(absolute(r.difference))}</strong><small>{r.percentage === null ? "Percentuale non disponibile" : `${number(absolute(r.percentage), 2)}% ${more ? "in più" : "in meno"}`}</small></div></div><p>{more ? "Maggior costo annuale indicativo" : "Risparmio annuale indicativo"}: <strong>{euro(absolute(r.annual))}</strong></p><p className="help">Proiezione del periodo × 12 / mesi; non considera stagionalità o variazioni future del PSV.</p>
 <div className="result-grid"><div className="panel"><h3>Come si forma il costo</h3><div className="chart"><ResponsiveContainer width="100%" height={280}><BarChart data={chart} layout="vertical"><CartesianGrid strokeDasharray="3 3"/><XAxis type="number"/><YAxis type="category" dataKey="name" width={110} tick={{ fontSize: 10 }} interval={0}/><Tooltip formatter={v => euro(String(v))}/><Bar dataKey="value" name="Costo (€)" isAnimationActive={false} fill="#194D3D" radius={[4, 4, 0, 0]}/></BarChart></ResponsiveContainer></div></div><div className="panel"><dl className="cost-list">{Object.entries(r.categories).map(([k, v]) => <div key={k}><dt>{categories[k as Category]}</dt><dd>{euro(v)}</dd></div>)}<div><dt>Imponibile</dt><dd>{euro(r.taxable)}</dd></div><div><dt>IVA</dt><dd>{euro(r.vat)}</dd></div><div><dt>Non soggetto IVA</dt><dd>{euro(r.nonTaxable)}</dd></div><div><dt>Totale</dt><dd>{euro(r.total)}</dd></div></dl></div></div>
 {c.bill.periods.map(p => <details className="panel" key={p.month}><summary>Come è calcolato · {p.month} · {number(p.consumptionSmc)} Smc</summary><p>{c.profiles[p.month].name} · {c.profiles[p.month].mode} · {c.profiles[p.month].source}</p><div className="table-scroll"><table><thead><tr><th>Voce</th><th>Quantità</th><th>Tariffa</th><th>Importo</th><th>IVA</th><th>Scaglione e origine</th></tr></thead><tbody>{r.rows.filter(l => l.month === p.month).map(l => <tr key={l.code}><td>{l.label}</td><td>{number(l.quantity)} {l.unit === "€/Smc" ? "Smc" : l.unit === "€/PDR/mese" ? "mesi" : ""}</td><td>{number(l.rate, 12)} {l.unit}</td><td>{euro(l.amount)}</td><td>{euro(l.vat)}</td><td>{l.band}<small>{l.origin}</small></td></tr>)}</tbody></table></div></details>)}
 {r.audit.length > 0 && <details className="panel"><summary>Override tariffari registrati · {r.audit.length}</summary>{r.audit.map((a, i) => <p key={i}>{a.month} · {a.code}: {number(a.original, 12)} → {number(a.updated, 12)} · {a.reason} · {a.operator} · {new Date(a.timestamp).toLocaleString("it-IT")}</p>)}</details>}
 <details className="panel"><summary>Precisione e snapshot</summary><p>Totale raw: {r.totalRaw} € · Differenza raw: {r.differenceRaw} € · Motore {c.engineVersion}</p><p>I valori dello storico non cambiano dopo modifiche a offerte, bollette o profili. I prezzi delle righe sono conservati con la loro origine.</p></details>{error && <Errore message={error}/>}</section>;
}
export function ConfrontoPage() {
    const bills = useLista<Saved<Bill>>("/bollette"), offers = useLista<Saved<Offer>>("/offerte"), profiles = useLista<Saved<Profile>>("/parametri"), examples = useLista<{
        name: string;
        expectedRaw: string;
    }>("/esempi");
    const [billId, setBill] = useState(""), [offerId, setOffer] = useState(""), [profileId, setProfile] = useState(""), [example, setExample] = useState("GENNAIO-FEBBRAIO"), [saved, setSaved] = useState<Saved<Comparison> | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState("");
    async function loadExample() { setBusy(true); setError(""); try {
        const { data } = await api.post(`/esempi/${example}`);
        setBill(String(data.bill.id));
        setOffer(String(data.offer.id));
        setProfile(String(data.profiles[0].id));
        setSaved(data.comparison);
        bills.reload();
        offers.reload();
        profiles.reload();
    }
    catch (e) {
        setError(messaggioErrore(e));
    }
    finally {
        setBusy(false);
    } }
    const steps = [{ icon: FileText, title: "La bolletta del cliente", text: "PDR, consumi Smc e importo fatturato.", link: "/bollette", cta: "Gestisci bollette" }, { icon: Tags, title: "L’offerta da proporre", text: "Prezzo fisso o PSV, QVD e CCR.", link: "/offerte", cta: "Gestisci offerte" }, { icon: SlidersHorizontal, title: "I parametri del gestore", text: "Trasporto, oneri, scaglioni e fiscalità.", link: "/parametri", cta: "Configura parametri" }];
    return <><Titolo eyebrow="Il tuo spazio di consulenza" title="Il confronto gas, voce per voce." description="Stessi consumi del cliente, tutte le componenti della tua proposta spiegate."/><div className="intro-banner"><div className="sun-disc"><ArrowLeftRight size={34}/></div><div><span className="eyebrow">FORNITURA GAS NATURALE</span><h2>Un confronto trasparente.<br />Una proposta più semplice.</h2><p>Due sorgenti separate: bolletta del cliente e condizioni della tua offerta.</p></div><span className="banner-stamp">PROGETTO<br /><strong>GAS</strong></span></div><div className="steps">{steps.map((s, i) => <Link key={s.link} to={s.link} className="step-card"><div className="step-top"><s.icon size={22}/><span>0{i + 1}</span></div><h3>{s.title}</h3><p>{s.text}</p><div className="step-link">{s.cta}<ArrowRight size={16}/></div></Link>)}</div>
 {error && <Errore message={error}/>}<form className="panel compare-form" onSubmit={async (e) => { e.preventDefault(); setBusy(true); setError(""); try {
        setSaved((await api.post("/confronti", { billId: Number(billId), offerId: Number(offerId), profileId: Number(profileId) })).data);
    }
    catch (e) {
        setError(messaggioErrore(e));
    }
    finally {
        setBusy(false);
    } }}><div className="panel-top"><h2>Avvia un nuovo confronto</h2><span className="badge">Gas naturale</span></div><div className="form-grid three"><label className="field"><span>Bolletta del cliente</span><select required value={billId} onChange={e => { setBill(e.target.value); setSaved(null); }}><option value="">Seleziona una bolletta</option>{bills.data.map(b => <option key={b.id} value={b.id}>{b.data.client} · {b.data.periods[0].month}</option>)}</select></label><label className="field"><span>Offerta proposta</span><select required value={offerId} onChange={e => { setOffer(e.target.value); setSaved(null); }}><option value="">Seleziona un'offerta</option>{offers.data.filter(x => x.data.active).map(o => <option key={o.id} value={o.id}>{o.data.name} · {o.data.provider}</option>)}</select></label><label className="field"><span>Profilo gestore</span><select required value={profileId} onChange={e => { setProfile(e.target.value); setSaved(null); }}><option value="">Seleziona un profilo</option>{profiles.data.filter(x => x.data.status !== "ARCHIVED").map(p => <option key={p.id} value={p.id}>{p.data.name} · {p.data.status === "DRAFT" ? "da validare" : "approvato"}</option>)}</select></label></div><p className="help">Il profilo eventualmente assegnato al singolo periodo prevale sul profilo selezionato qui. Gli scaglioni sono espliciti; l’app non li sceglie automaticamente.</p><button className="button primary" disabled={busy || bills.loading || offers.loading || profiles.loading}>Calcola confronto</button></form>
 <details className="panel"><summary>Prova con i quattro esempi Excel</summary><p>Clienti sintetici e tariffe storiche da validare. Il foglio legacy GIUGNO-LUGLIO è escluso dai profili standard.</p><label className="field"><span>Scenario Excel</span><select value={example} onChange={e => setExample(e.target.value)}>{examples.data.map(e => <option key={e.name}>{e.name}</option>)}</select></label><button className="button secondary" disabled={busy} onClick={loadExample}>Carica esempio e calcola</button></details>{saved && <Risultato saved={saved}/>}</>;
}
export function StoricoPage() {
    const list = useLista<Saved<Comparison>>("/confronti");
    const [search, setSearch] = useState(""), [date, setDate] = useState(""), [status, setStatus] = useState(""), [selected, setSelected] = useState<Saved<Comparison> | null>(null);
    return <><Titolo eyebrow="Confronti conservati" title="Storico confronti gas" description="Ogni risultato conserva bolletta, offerta, tariffe e modifiche manuali usate nel calcolo."/><div className="form-grid three"><Campo label="Cerca cliente, offerta, profilo o operatore" value={search} onChange={e => setSearch(e.target.value)}/><Campo label="Data del confronto" type="date" value={date} onChange={e => setDate(e.target.value)}/><label className="field"><span>Stato tariffe</span><select value={status} onChange={e => setStatus(e.target.value)}><option value="">Tutti</option><option value="draft">Da validare</option><option value="approved">Approvate</option></select></label></div>{list.error && <Errore message={list.error}/>} {list.loading ? <Caricamento /> : <div className="gas-list">{list.data.filter(s => JSON.stringify([s.data.bill.client, s.data.offer.name, Object.values(s.data.profiles).map(p => p.name), s.data.result.audit.map(a => a.operator)]).toLowerCase().includes(search.toLowerCase()) && (!date || s.createdAt.slice(0, 10) === date) && (!status || (status === "draft") === s.data.result.unapproved)).map(s => <article className="panel" key={s.id}><div className="panel-top"><h2>#{s.id} · {s.data.bill.client}</h2><button className="button secondary" onClick={() => setSelected(s)}>Apri</button></div><p>{new Date(s.createdAt).toLocaleString("it-IT")} · {s.data.offer.name} · {euro(s.data.result.total)}</p><span className="badge">{s.data.result.unapproved ? "Da validare" : "Approvato"}</span></article>)}</div>}{selected && <Risultato saved={selected}/>}</>;
}
