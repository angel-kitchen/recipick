import ErrorNotice from './ErrorNotice';

// 확인이 필요한 안내 창. 브라우저 기본 confirm 대신 화면 디자인에 맞춰 쓴다
export default function ConfirmModal({
  title,
  children,
  confirmLabel,
  cancelLabel,
  secondaryLabel,
  busy,
  error,
  onConfirm,
  onSecondary,
  onCancel,
  onClose = onCancel,
}) {
  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
      <div className="modal__box card">
        <button type="button" className="modal__close" aria-label="닫기" disabled={busy} onClick={onClose}>
          ×
        </button>
        <h2 className="tile__title">{title}</h2>
        <div className="field__hint" style={{ margin: '12px 0' }}>
          {children}
        </div>
        <ErrorNotice error={error} />
        <div className="row-actions">
          {/* 보조 버튼이 있으면 그쪽을 기본(채움)으로 두고 원래 확인 버튼은 윤곽선으로 낮춘다 */}
          <button
            type="button"
            className={`btn btn--large${secondaryLabel ? ' btn--outline' : ''}`}
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? '처리하는 중...' : confirmLabel}
          </button>
          {secondaryLabel && (
            <button type="button" className="btn btn--large" disabled={busy} onClick={onSecondary}>
              {secondaryLabel}
            </button>
          )}
          {cancelLabel && (
            <button
              type="button"
              className={`btn btn--large btn--outline${secondaryLabel ? ' row-actions__end' : ''}`}
              disabled={busy}
              onClick={onCancel}
            >
              {cancelLabel}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
