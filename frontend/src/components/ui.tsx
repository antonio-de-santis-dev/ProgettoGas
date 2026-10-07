import {
  useEffect,
  useId,
  useRef,
  type ReactNode,
  type InputHTMLAttributes,
} from "react";
import { X, Plus, ArrowRight, AlertCircle, LoaderCircle } from "lucide-react";
export function Campo({
  label,
  hint,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  const id = useId();
  return (
    <label className="field" htmlFor={id}>
      <span>
        {label}
        {props.required && <span aria-hidden="true"> *</span>}
      </span>
      <input
        id={id}
        aria-describedby={hint ? `${id}-hint` : undefined}
        {...props}
      />
      {hint && <small id={`${id}-hint`}>{hint}</small>}
    </label>
  );
}
export function Decimale(props: Parameters<typeof Campo>[0]) {
  return (
    <Campo type="number" inputMode="decimal" step="any" min="0" {...props} />
  );
}
export function Titolo({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <header className="page-title">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="description">{description}</p>
      </div>
      {action}
    </header>
  );
}
export function Dialogo({
  title,
  children,
  onClose,
  className = "",
}: {
  className?: string;
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null),
    id = useId();
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className={`modal ${className}`}
      aria-labelledby={id}
      onCancel={onClose}
    >
      <header>
        <h2 id={id}>{title}</h2>
        <button
          type="button"
          className="icon-button"
          aria-label="Chiudi"
          onClick={onClose}
        >
          <X size={20} />
        </button>
      </header>
      {children}
    </dialog>
  );
}
export function Errore({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="error" role="alert">
      <AlertCircle size={20} />
      <div>
        {message}
        {onRetry && (
          <button className="text-button" onClick={onRetry}>
            Riprova
          </button>
        )}
      </div>
    </div>
  );
}
export function Caricamento() {
  return (
    <div className="loading" role="status">
      <LoaderCircle size={22} className="spin" /> Caricamento dei dati…
    </div>
  );
}
export function Vuoto({
  title,
  text,
  action,
}: {
  title: string;
  text: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <Plus size={26} />
      </div>
      <h2>{title}</h2>
      <p>{text}</p>
      {action}
    </div>
  );
}
export function Salva({
  busy,
  label = "Salva",
  onCancel,
}: {
  busy: boolean;
  label?: string;
  onCancel?: () => void;
}) {
  return (
    <footer className="form-actions">
      {onCancel && (
        <button type="button" className="button secondary" onClick={onCancel}>
          Annulla
        </button>
      )}
      <button type="submit" className="button primary" disabled={busy}>
        {busy ? (
          <LoaderCircle size={18} className="spin" />
        ) : (
          <ArrowRight size={18} />
        )}{" "}
        {busy ? "Salvataggio…" : label}
      </button>
    </footer>
  );
}
