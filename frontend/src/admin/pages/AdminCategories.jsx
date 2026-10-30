import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import useLoad from '../useLoad';

export default function AdminCategories() {
  const { data, error, reload } = useLoad(() => api.get('/api/categories'), []);
  const [name, setName] = useState('');
  const [actionError, setActionError] = useState(null);
  const [editing, setEditing] = useState(null);

  const act = async (action) => {
    setActionError(null);
    try {
      await action();
      reload();
      return true;
    } catch (e) {
      setActionError(e);
      return false;
    }
  };

  const create = async (e) => {
    e.preventDefault();
    if (await act(() => api.post('/api/admin/categories', { name }))) setName('');
  };

  const startEdit = (category) => setEditing({ id: category.categoryId, name: category.name });

  const saveEdit = async (e) => {
    e.preventDefault();
    if (!editing.name.trim()) return;
    if (await act(() => api.patch(`/api/admin/categories/${editing.id}`, { name: editing.name.trim() }))) setEditing(null);
  };

  const remove = (category) => {
    if (!window.confirm(`'${category.name}' 카테고리를 삭제할까요?`)) return;
    act(() => api.delete(`/api/admin/categories/${category.categoryId}`));
  };

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">카테고리</h1>
      </div>
      <form className="filters" onSubmit={create}>
        <input className="field__input" placeholder="새 카테고리 이름" value={name} maxLength={50} onChange={(e) => setName(e.target.value)} required />
        <button type="submit" className="btn">
          추가
        </button>
      </form>
      <ErrorNotice error={error ?? actionError} />
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>이름</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.map((category) => (
              <tr key={category.categoryId}>
                <td>{category.categoryId}</td>
                <td>
                  {editing?.id === category.categoryId ? (
                    <form className="chips" onSubmit={saveEdit}>
                      <input
                        className="field__input"
                        value={editing.name}
                        maxLength={50}
                        autoFocus
                        onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                      />
                      <button type="submit" className="chip chip--solid">
                        저장
                      </button>
                      <button type="button" className="chip" onClick={() => setEditing(null)}>
                        취소
                      </button>
                    </form>
                  ) : (
                    category.name
                  )}
                </td>
                <td>
                  <span className="chips">
                    <button type="button" className="chip" onClick={() => startEdit(category)}>
                      이름 변경
                    </button>
                    <button type="button" className="chip" onClick={() => remove(category)}>
                      삭제
                    </button>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
