import { Link } from 'react-router-dom';
import Icon from './Icon';

// 비어 있는 목록에서 글자 한 줄 대신, 다음에 할 일을 버튼으로 알려 준다. actions는 [{ to, label }]
export default function EmptyState({ icon, title, hint, actions = [] }) {
  return (
    <div className="empty">
      <span className="empty__icon">
        <Icon name={icon} size={36} />
      </span>
      <p className="empty__title">{title}</p>
      {hint && <p className="field__hint">{hint}</p>}
      {actions.length > 0 && (
        <div className="row-actions" style={{ justifyContent: 'center' }}>
          {actions.map((action, index) => (
            <Link key={action.to} to={action.to} className={`btn ${index > 0 ? 'btn--outline' : ''}`}>
              {action.label}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
