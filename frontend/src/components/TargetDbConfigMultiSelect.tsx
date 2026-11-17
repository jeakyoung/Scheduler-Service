import type { TargetDbConfig } from "../api/types";

interface Props {
  options: TargetDbConfig[];
  selectedIds: number[];
  onChange: (ids: number[]) => void;
}

/**
 * PLAN.md §4.3b/§7 - a schedule must select at least one target DB. Client-side validation
 * here is a UX nicety only; the server independently rejects an empty array (T18).
 */
export default function TargetDbConfigMultiSelect({ options, selectedIds, onChange }: Props) {
  function toggle(id: number) {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((selectedId) => selectedId !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  }

  return (
    <div>
      {options.length === 0 ? (
        <p className="muted">등록된 대상 DB가 없습니다. 먼저 "대상 DB 설정"에서 추가해 주세요.</p>
      ) : (
        <div className="checkbox-list">
          {options.map((option) => (
            <label key={option.id}>
              <input type="checkbox" checked={selectedIds.includes(option.id)} onChange={() => toggle(option.id)} />
              {option.name}
            </label>
          ))}
        </div>
      )}
      {options.length > 0 && selectedIds.length === 0 && (
        <p className="error-text">최소 1개 이상의 대상 DB를 선택해야 합니다.</p>
      )}
    </div>
  );
}
