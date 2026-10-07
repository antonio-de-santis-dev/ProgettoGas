import axios from "axios";
import { useCallback, useEffect, useState } from "react";
export const api = axios.create({ baseURL: "/api", timeout: 20000 });
export function messaggioErrore(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;
    if (data?.fields && Object.keys(data.fields).length)
      return `${data.message}: ${Object.entries(data.fields)
        .map(([k, v]) => `${k}: ${v}`)
        .join("; ")}`;
    return (
      data?.message ??
      (error.response
        ? "Operazione non riuscita. Riprova."
        : "Backend non raggiungibile. Controlla che sia avviato e riprova.")
    );
  }
  return error instanceof Error ? error.message : "Operazione non riuscita";
}
export function useLista<T>(path: string) {
  const [data, setData] = useState<T[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [version, setVersion] = useState(0);
  const reload = useCallback(() => setVersion((v) => v + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    api
      .get<T[]>(path, { signal: controller.signal })
      .then((r) => {
        if (!controller.signal.aborted) setData(r.data);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(messaggioErrore(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [path, version]);
  return { data, loading, error, reload };
}
