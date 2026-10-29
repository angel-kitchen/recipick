import { useCallback, useEffect, useState } from 'react';

// loader를 부르고 {data, error, loading}을 돌려준다. deps가 바뀌거나 reload를 부르면 다시 읽는다
export default function useLoad(loader, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let ignore = false;
    setState((prev) => ({ ...prev, loading: true, error: null }));
    loader()
      .then((data) => !ignore && setState({ data, error: null, loading: false }))
      .catch((error) => !ignore && setState({ data: null, error, loading: false }));
    return () => {
      ignore = true;
    };
    // loader는 매 렌더 새로 만들어지므로 deps로만 다시 읽는 시점을 정한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((value) => value + 1), []);
  return { ...state, reload };
}
