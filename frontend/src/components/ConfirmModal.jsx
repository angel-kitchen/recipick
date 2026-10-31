import ErrorNotice from './ErrorNotice';

// 확인이 필요한 안내 창. 브라우저 기본 confirm 대신 화면 디자인에 맞춰 쓴다
export default function ConfirmModal({ title, children, confirmLabel, cancelLabel, busy, error, onConfirm, onCancel }) {
  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
      <div className="modal__box card">
        <button type="button" className="modal__close" aria-label="닫기" disabled={busy} onClick={onCancel}>
          ×
        </button>
        <h2 className="tile__title">{title}</h2>
        <div className="field__hint" style={{ margin: '12px 0' }}>
          {children}
        </div>
        <ErrorNotice error={error} />
        <div className="row-actions">
          <button type="button" className="btn btn--large" disabled={busy} onClick={onConfirm}>
            {busy ? '처리하는 중...' : confirmLabel}
          </button>
          {cancelLabel && (
            <button type="button" className="btn btn--large btn--outline" disabled={busy} onClick={onCancel}>
              {cancelLabel}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
