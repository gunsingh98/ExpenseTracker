import { useCallback, useEffect, useRef, useState } from "react";

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

/** Loads async data on mount and whenever `deps` change; exposes a manual reload. */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const reload = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    loader()
      .then((result) => {
        if (active) setData(result);
      })
      .catch((e: unknown) => {
        if (active) setError(e instanceof Error ? e.message : "Something went wrong");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  return { data, loading, error, reload };
}

interface CursorListState<T> {
  items: T[];
  /** First page in flight (initial load, filter change or reload). */
  loading: boolean;
  /** A follow-on page in flight. */
  loadingMore: boolean;
  error: string | null;
  hasMore: boolean;
  loadMore: () => void;
  reload: () => void;
}

/**
 * Accumulates a keyset-paginated list. Restarts from the first page whenever
 * `deps` change (e.g. filters) or on `reload`; `loadMore` appends the next page.
 * Responses from a superseded generation are dropped, so a slow page for old
 * filters can never land in the new list.
 */
export function useCursorList<T>(
  fetchPage: (cursor: string | null) => Promise<{ items: T[]; nextCursor: string | null }>,
  deps: unknown[],
): CursorListState<T> {
  const [items, setItems] = useState<T[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  const generation = useRef(0);
  const busy = useRef(false);

  const load = useCallback(
    (from: string | null, gen: number) => {
      busy.current = true;
      setError(null);
      if (from === null) setLoading(true);
      else setLoadingMore(true);
      fetchPage(from)
        .then((page) => {
          if (gen !== generation.current) return;
          setItems((prev) => (from === null ? page.items : [...prev, ...page.items]));
          setCursor(page.nextCursor);
          setHasMore(page.nextCursor !== null);
        })
        .catch((e: unknown) => {
          if (gen === generation.current) setError(e instanceof Error ? e.message : "Something went wrong");
        })
        .finally(() => {
          if (gen !== generation.current) return;
          busy.current = false;
          setLoading(false);
          setLoadingMore(false);
        });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    deps,
  );

  useEffect(() => {
    const gen = ++generation.current;
    load(null, gen);
  }, [load, tick]);

  const loadMore = useCallback(() => {
    if (busy.current || !hasMore) return;
    load(cursor, generation.current);
  }, [load, cursor, hasMore]);

  const reload = useCallback(() => setTick((t) => t + 1), []);

  return { items, loading, loadingMore, error, hasMore, loadMore, reload };
}

/**
 * Calls `onVisible` when the returned ref's element scrolls into view (with a
 * margin so the next page starts loading before the user hits the bottom).
 */
export function useInView(onVisible: () => void) {
  const ref = useRef<HTMLDivElement | null>(null);
  const callback = useRef(onVisible);
  callback.current = onVisible;

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) callback.current();
      },
      { rootMargin: "400px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  });

  return ref;
}
