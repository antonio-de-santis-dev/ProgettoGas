# Analisi e decisioni Progetto Gas

## Fonti

Workbook fornito “SIMULATORE GAS BIMESTRALE.xlsx” e documento “Analisi funzionale e specifica AI — Simulatore Gas Bimestrale.pdf”, sezioni 4–7 e 10–13. Base grafica: ProgettoLuce, branch simulatore-business, commit e485f9559c51396f4394adc140ef61f9ee5febd4. Il codice sorgente e il motore gas sono nella repository ProgettoGas; nessun branch luce viene modificato.

## Inventario

- ACCISE GAS: accise, addizionali, distribuzione, UG2 e PSV storici. Non collegato alle formule degli scenari. Consultato come riferimento, non come fonte normativa attuale.
- GENNAIO-FEBBRAIO, FEBBRAIO-MARZO, MARZO-APRILE, APRILE-MAGGIO: quattro regressioni standard.
- GIUGNO-LUGLIO: legacy con voci/layout diversi e somme incomplete. Non inserito nel motore standard.
- FEBBRAIO-MARZO 2025: vuoto; nessuna simulazione da migrare.

## Tracciamento formule

Per ciascuno dei quattro fogli standard, primo periodo alle righe 53–73 e secondo alle righe 103–123. Sono importati soltanto corrispettivi numerici, quantità, IVA indicata e label delle scelte tariffarie; i clienti demo sono sintetici.

| Righe primo/secondo periodo | Codice | Categoria | Base |
|---|---|---|---|
| 53/103 | QVD_FIXED | Materia gas | mesi |
| 54/104 | COMMODITY | Materia gas | Smc |
| 55/105 | CCR | Materia gas | Smc |
| 56/106 | QVD_VAR | Materia gas | Smc |
| 59/109 | DIST_FIXED | Trasporto | mesi |
| 60/110 | DIST_VAR | Trasporto | Smc |
| 61/111 | QT | Trasporto | Smc |
| 62/112 | RS | Trasporto | Smc |
| 63/113 | UG1 | Trasporto | Smc |
| 66/116 | UG2_FIXED | Oneri | mesi |
| 67/117 | RE | Oneri | Smc |
| 68/118 | UG3 | Oneri | Smc |
| 69/119 | UG2_VAR | Oneri | Smc |
| 72/122 | EXCISE | Imposte | Smc |
| 73/123 | LOCAL_ADDITION | Imposte | Smc |

Somma dei 30 prodotti tariffa × quantità → imponibile F126. IVA F127 = imponibile × 22/100. Totale C29 = imponibile + IVA. I valori Excel memorizzati includono artefatti floating point (es. 0.17499999999999999); gli input sono normalizzati ai 12 decimali ammessi, recuperando le tariffe espresse, senza copiare errori binari. La regressione confronta i totali raw con tolleranza 0,000000001.

La fattura precedente nei quattro test è rispettivamente 582,83, 905,15, 582,83 e 553,44 euro. Le label delle fasce possono non corrispondere alle tariffe del foglio ACCISE GAS: vengono conservate come scelte storiche esplicite, senza correggerle o dedurne aliquote automatiche.

## Differenze intenzionali

1. “Energia elettrica”, POD e €/kWh sono sostituiti da gas naturale, PDR e €/Smc. Nessuna quota potenza elettrica.
2. Altre partite esenti incluse dopo IVA, anche se diverse da zero. Nel workbook non erano incluse realmente.
3. Totali raw salvati prima dell'arrotondamento, più importi a centesimi per presentazione. La somma delle categorie visualizzate può differire di un centesimo dall'importo calcolato sulle righe precise.
4. Ogni confronto salva uno snapshot completo, compresa l'offerta, le condizioni per periodo, le righe e l'audit. Aggiornare una tariffa non aggiorna lo storico.
5. Le quattro voci commerciali provengono dall'offerta. Le differenze fra mesi negli esempi storici sono override espliciti con fonte e operatore sintetico; gli altri parametri usano profili diversi per periodo.
6. I profili pubblicati sono immutabili e non possono sovrapporre la validità per fornitore/area. L'approvazione richiede un nominativo dichiarato; non equivale a un account autenticato.
7. La modalità configurata permette IVA per riga ed esenti; non sono codificate regole fiscali presunte.
8. Non è dedotto l'anno dalle intestazioni incoerenti. Nei fixture i mesi sono mappati a un anno tecnico 2024 documentato, esclusivamente per eseguire il test di validità.
9. Nessun import automatico del foglio legacy; nessuna cifra hardcoded usata per simulare il calcolo: tutte le fixture attraversano lo stesso motore e le stesse API delle simulazioni manuali.
10. Nessuna chiave amministratore. La versione richiesta replica l'uso locale del programma luce. Ruoli e isolamento multiutente non sono introdotti e richiedono una futura implementazione prima di accesso condiviso/pubblico.

## Architettura e API

backend/src/main/java/it/progettogas/gas: Model (contratti e validazioni), GasEngine (calcolo puro), GasService (persistenza, versioni e snapshot), GasController (REST), Examples (fixture anonimizzate).

Persistenza PostgreSQL: tabella gas_records con tipo, payload JSON testuale, versione ottimistica e timestamp; indice tipo/id. Audit con payload precedente e aggiornato. Una transazione salva il confronto; gli esempi vengono inseriti atomicamente. Le stringhe monetarie evitano conversioni float nel client.

GET/POST /api/bollette, /api/offerte, /api/parametri, /api/fonti; PUT/DELETE /{id}; POST /{id}/duplica; GET /{id}/revisioni. Le scritture CRUD usano {data, version}; version obbligatoria sulle modifiche esistenti.

POST /api/confronti/anteprima calcola senza salvataggio; POST /api/confronti salva il risultato con {billId, offerId, profileId}. GET /api/confronti e /{id} leggono snapshot. /{id}/pdf esporta il PDF; /{id}/export?format=csv|json esporta dati. POST /{id}/duplica crea una nuova bolletta dai dati cliente del confronto. Un nuovo calcolo deve scegliere esplicitamente le condizioni da utilizzare.

GET/PUT /api/impostazioni-pdf e POST /api/impostazioni-pdf/anteprima condividono il renderer con il report reale. Font incorporati e pagine rasterizzate dal PDF effettivo; nessuna anteprima HTML divergente. Logo validato per MIME, dimensioni e lettura immagine.

GET /api/esempi elenca gli scenari e il controllo raw; POST /api/esempi/{nome} li carica con clienti sintetici.

Docker espone soltanto frontend su 127.0.0.1:8090. Backend e PostgreSQL non hanno porte pubblicate. Nginx applica CSP e header di sicurezza, inoltra /api e gestisce SPA; volume PostgreSQL dedicato. Lo script avvia.sh genera una password database casuale se assente.
