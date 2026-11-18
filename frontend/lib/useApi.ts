"use client";

import { useEffect, useState } from "react";
import { api } from "./api";

// Loads one GET endpoint when the page opens (and again whenever the path changes). reload()
// fetches it again, for example after a booking or a cancel.
export function useApi<T>(path: string) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let current = true; // ignore a slow answer that arrives after the page moved on
    api<T>(path)
      .then((result) => {
        if (current) {
          setData(result);
          setError(null);
        }
      })
      .catch((e: Error) => {
        if (current) {
          setError(e.message);
        }
      });
    return () => {
      current = false;
    };
  }, [path, version]);

  return { data, error, reload: () => setVersion((v) => v + 1) };
}
