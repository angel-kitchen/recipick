import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

const ToastContext = createContext({ show: () => {} });

// 화면 아래에 잠깐 떴다 사라지는 안내. link를 주면 "장바구니 보기" 같은 바로가기가 붙는다
export function ToastProvider({ children }) {
  const [toast, setToast] = useState(null);
  const timer = useRef(null);

  const show = useCallback((message, link) => {
    clearTimeout(timer.current);
    setToast({ message, link, id: Date.now() });
    timer.current = setTimeout(() => setToast(null), 4000);
  }, []);

  useEffect(() => () => clearTimeout(timer.current), []);

  const value = useMemo(() => ({ show }), [show]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-area" role="status" aria-live="polite">
        {toast && (
          <div key={toast.id} className="toast">
            <span>{toast.message}</span>
            {toast.link && (
              <Link to={toast.link.to} onClick={() => setToast(null)}>
                {toast.link.label}
              </Link>
            )}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
