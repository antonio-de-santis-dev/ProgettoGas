import { useEffect, useState } from "react";
import { NavLink, Routes, Route, useLocation, Link } from "react-router-dom";
import {
  Flame,
  ArrowLeftRight,
  Tags,
  FileText,
  SlidersHorizontal,
  History,
  Menu,
  X,
  ArrowUpRight,
} from "lucide-react";
import "./business/business.css";
import "./gas.css";
import {ConfrontoPage,OffertePage,BollettePage,ParametriPage,FontiPage,StoricoPage} from "./pages/GasPages";
import ImpostazioniPdfPage from "./pages/ImpostazioniPdfPage";

const nav = [
  { to: "/", label: "Confronto", icon: ArrowLeftRight },
  { to: "/bollette", label: "Bollette clienti", icon: FileText },
  { to: "/offerte", label: "Offerte", icon: Tags },
  { to: "/parametri", label: "Parametri gestore", icon: SlidersHorizontal },
  { to: "/fonti", label: "Fonti ufficiali", icon: SlidersHorizontal },
  { to: "/impostazioni-pdf", label: "Impostazioni PDF", icon: FileText },
  { to: "/storico", label: "Storico", icon: History },
];
export default function App() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => {
    setOpen(false);
    document.title = `${nav.find((n) => n.to === location.pathname)?.label ?? "Pagina non trovata"} · Progetto Gas`;
    document.querySelector<HTMLElement>("#main-content")?.focus();
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return (
    <div className="app business-shell">
      <a href="#main-content" className="skip-link">
        Vai al contenuto
      </a>
      <aside
        id="gas-navigation"
        className={`sidebar ${open ? "open" : ""}`}
      >
        {open && (
          <button
            className="icon-button business-nav-close"
            aria-label="Chiudi menu"
            onClick={() => setOpen(false)}
          >
            <X size={20} />
          </button>
        )}
        <Link to="/" className="brand" aria-label="Progetto Gas">
          <span>
            <Flame size={23} fill="currentColor" />
          </span>
          <div>
            progetto
            <strong>
              gas<span>.</span>
            </strong>
          </div>
        </Link>
        <span className="business-tag">GAS NATURALE</span>
        <p className="nav-caption">PROGETTO GAS</p>
        <nav aria-label="Navigazione principale">
          {nav.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end
              className={({ isActive }) =>
                isActive ? "nav-link active" : "nav-link"
              }
            >
              <n.icon size={19} />
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="mini-sun" aria-hidden="true">
            ✳
          </div>
          <h3>
            Ogni scelta
            <br />
            merita chiarezza.
          </h3>
          <p>
            Consumi reali.
            <br />
            Calcoli trasparenti.
          </p>
          <span>PROGETTO GAS</span>
        </div>
      </aside>
      {open && (
        <button
          className="nav-scrim"
          aria-label="Chiudi navigazione"
          onClick={() => setOpen(false)}
        />
      )}
      <div
        className={
          location.pathname === "/impostazioni-pdf"
            ? "workspace workspace-pdf"
            : "workspace"
        }
      >
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-menu"
              aria-label="Apri menu"
              aria-controls="gas-navigation"
              aria-expanded={open}
              onClick={() => setOpen(!open)}
            >
              {open ? <X size={21} /> : <Menu size={21} />}
            </button>
            <span>Gas naturale</span>
            <span className="slash">/</span>
            <strong>
              {nav.find((n) => n.to === location.pathname)?.label ?? "404"}
            </strong>
          </div>
          <div className="topbar-right">
            <span className="topbar-label">
              <span className="status-dot" />
              Consulenza energetica
            </span>
            <div className="avatar" aria-label="Area consulente">
              PG
            </div>
          </div>
        </header>
        <main id="main-content" tabIndex={-1}>
          <Routes>
            <Route path="/" element={<ConfrontoPage />} />
            <Route path="/offerte" element={<OffertePage />} />
            <Route path="/bollette" element={<BollettePage />} />
            <Route path="/parametri" element={<ParametriPage />} />
            <Route path="/impostazioni-pdf" element={<ImpostazioniPdfPage />} />
            <Route path="/fonti" element={<FontiPage />} />
            <Route path="/storico" element={<StoricoPage />} />
            <Route
              path="*"
              element={
                <div className="empty">
                  <h1>Pagina non trovata</h1>
                  <Link to="/" className="button primary">
                    Torna al confronto
                    <ArrowUpRight size={18} />
                  </Link>
                </div>
              }
            />
          </Routes>
        </main>
        <footer className="workspace-footer">
          <span>
            Progetto Gas · Strumenti per consulenti energetici
          </span>
          <span>Calcolo parametrico · Prima versione</span>
        </footer>
      </div>
    </div>
  );
}
