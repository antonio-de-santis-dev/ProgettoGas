# Progetto Gas

Simulatore gas naturale nella struttura di Progetto Luce: Spring Boot/Java 17, React/TypeScript/Vite, PostgreSQL/Flyway, Docker e Nginx. Mantiene menu, colori, schede, grafici e l'editor PDF del programma luce. Il motore è dedicato al gas; non contiene formule elettriche.

## Avvio Docker

Su Linux, con Docker Compose e Python 3:

```bash
git clone https://github.com/antonio-de-santis-dev/ProgettoGas.git
cd ProgettoGas
./scripts/avvia.sh
```

Apri **http://localhost:8090**. Lo script prepara il file `.env` e genera soltanto la password interna del database, se assente. Non serve una chiave amministratore. I successivi avvii conservano configurazione e dati.

Per aggiornare:

```bash
git pull --ff-only origin main
./scripts/avvia.sh
```

Avvio manuale alternativo: copia `.env.example` in `.env`, imposta `DB_PASSWORD`, quindi `docker compose up --build -d --wait`. `GAS_PORT` cambia la porta. Nome stack `progetto-gas`, database `progettogas`, volume dedicato: può convivere con luce 8088 e business 8089.

Per fermare: `docker compose down`. Non usare `down -v` sui dati che vuoi conservare: rimuove il volume database.

## Le sette sezioni

1. **Confronto**: selezione bolletta, offerta e profilo; totale, risparmio/maggior costo, grafico, categorie e dettaglio mensile; PDF, CSV, JSON e stampa. Puoi caricare quattro esempi storici di regressione.
2. **Bollette clienti**: cliente/ragione sociale, PDR di 14 cifre, partita IVA opzionale, riferimento, fornitore precedente, importo fatturato e altre partite. Da 1 a 24 mesi consecutivi, Smc, quote mensili frazionarie, PSV, profilo per periodo, scaglioni e override motivati.
3. **Offerte**: prezzo fisso o PSV + spread, QVD fissa, CCR e QVD variabile; creazione, modifica, duplicazione, eliminazione e revisioni.
4. **Parametri gestore**: 15 voci standard, voci aggiuntive, scaglioni selezionabili, validità, fonte, stato bozza/pubblicato/archiviato e responsabile approvazione. Un profilo pubblicato è immutabile: duplica per una nuova versione. Sono bloccate sovrapposizioni di validità fra profili pubblicati dello stesso fornitore/area.
5. **Fonti ufficiali**: registrazione manuale del PSV verificato con mese, fonte, motivo e revisioni. Nessun feed esterno aggiorna automaticamente aliquote o tariffe. Il PSV del confronto si inserisce esplicitamente anche nei periodi della bolletta.
6. **Impostazioni PDF**: quattro stili, colori, logo PNG/JPG, dati consulente, anteprima delle pagine e salvataggio nel database. Il PDF reale utilizza il confronto salvato; il PDF di esempio usa clienti sintetici.
7. **Storico**: ricerca per cliente/offerta/profilo/operatore override, filtro data e stato, apertura snapshot e duplicazione dei dati cliente.

## Calcolo

Tutte le tariffe e i risultati monetari sono `BigDecimal`, serializzati come stringhe. Nessun arrotondamento intermedio; `totalRaw` conserva il valore preciso, `total` contiene il valore a centesimi. Il frontend usa Decimal.js per somma e formattazione; la conversione a number avviene soltanto per disegnare il grafico.

- **Compatibilità Excel**: IVA globale 22% su tutte le righe imponibili; le colonne IVA delle righe storiche non alterano il risultato, come nei fogli originali.
- **Regole configurate**: IVA globale, IVA per voce o trattamento non soggetto IVA, secondo il profilo. Le altre partite non soggette IVA sono sempre aggiunte dopo l'IVA: correzione intenzionale del difetto Excel.
- **Quantità**: Smc per le voci variabili, mesi per le quote fisse, 1 per importi assoluti. UG2 fissa negativa ammessa.
- **Offerta**: le quattro voci materia gas provengono dall'offerta; il profilo conserva il loro trattamento IVA e tutte le altre componenti.
- **Scaglioni**: tariffa/scaglione espliciti nel profilo oppure selezione manuale di una fascia configurata per il periodo. Nessuna assegnazione automatica a partire da consumi annui presunti. Una voce tariffaria che richiede fascia ma non ha una scelta esplicita blocca il calcolo.
- **Override**: tariffa originale dopo offerta/fascia, nuovo valore, motivo, operatore e timestamp vengono conservati nel risultato. Il nome operatore è dichiarato, non un'identità autenticata.
- **Proiezione annua**: differenza raw × 12 / numero mesi; nel bimestre × 6. Non è una previsione dei consumi futuri.

## Prova immediata

In Confronto apri “Prova con i quattro esempi Excel”, scegli uno scenario e premi “Carica esempio e calcola”. Crea bolletta, offerta, profili e confronto, usando clienti sintetici.

| Scenario | Totale raw atteso | Totale visualizzato |
|---|---:|---:|
| GENNAIO-FEBBRAIO | 392.605125844 | 392,61 € |
| FEBBRAIO-MARZO | 896.419863600 | 896,42 € |
| MARZO-APRILE | 408.429692774 | 408,43 € |
| APRILE-MAGGIO | 659.773269640 | 659,77 € |

Nel quarto esempio il maggior costo è **106,33 €**. Le tariffe sono storiche **da validare**, non prezzi correnti. L'anno tecnico 2024 dei periodi serve al test di validità e non certifica l'anno della fattura originale; alcune intestazioni originali sono incoerenti. Non sono importati dati personali del workbook. Il foglio GIUGNO-LUGLIO resta escluso perché legacy con formule incoerenti; il foglio vuoto non è migrato.

## Test e sviluppo

Backend con Java 17 e Maven 3.9:

```bash
cd backend
mvn verify
```

Frontend con Node 24:

```bash
cd frontend
npm ci
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Playwright avvia backend H2 temporaneo e frontend preview; non modifica il database Docker. I test backend verificano i quattro totali con tolleranza 1e-9, categorie, IVA mista, esenti, UG2 negativa, maggior costo, fasce, PSV mancante, audit, snapshot, immutabilità, sovrapposizioni, PDF/CSV/JSON e impostazioni.

La CI verifica anche PostgreSQL e lo stack Docker sulla 8090, con persistenza dopo ricreazione dei container. Per uno smoke test sul proprio Docker: `python3 scripts/verifica-compose.py` (aggiunge dati demo); dopo il riavvio usa `python3 scripts/verifica-compose.py --persistenza` nella stessa sessione/macchina.

## Ambito di questa versione

Installazione locale per un singolo operatore, accessibile solo da localhost. Non introduce ruoli, account, autenticazione multiutente o chiavi amministratore. Prima di ospitarla per più utenti occorre aggiungere autenticazione e segregazione dati; il nome dell'approvatore/operatore è una registrazione dichiarativa. Tariffe, scaglioni e trattamento IVA devono essere verificati prima dell'uso commerciale. Nessuna certificazione fiscale o regolatoria viene presunta.

Le fonti, le differenze rispetto all'Excel e le scelte implementative sono documentate in [docs/ANALISI_GAS.md](docs/ANALISI_GAS.md).
