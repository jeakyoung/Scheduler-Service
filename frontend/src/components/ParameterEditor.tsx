import type { ParamDataType, ScheduleParameterInput, ValueSource } from "../api/types";

interface Props {
  parameters: ScheduleParameterInput[];
  onChange: (parameters: ScheduleParameterInput[]) => void;
}

const DATA_TYPES: ParamDataType[] = ["STRING", "INT", "DECIMAL", "DATE", "DATETIME", "BOOLEAN"];
const VALUE_SOURCES: ValueSource[] = ["LITERAL", "CURRENT_DATE", "CURRENT_DATETIME"];

export default function ParameterEditor({ parameters, onChange }: Props) {
  function update(index: number, patch: Partial<ScheduleParameterInput>) {
    onChange(parameters.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  }

  function addRow() {
    onChange([
      ...parameters,
      { paramOrder: parameters.length + 1, paramName: "", dataType: "STRING", valueSource: "LITERAL", literalValue: "" },
    ]);
  }

  function removeRow(index: number) {
    onChange(parameters.filter((_, i) => i !== index).map((p, i) => ({ ...p, paramOrder: i + 1 })));
  }

  return (
    <div>
      {parameters.length === 0 && (
        <p className="muted">파라미터가 없습니다 (파라미터 없는 EXEC로 호출됩니다).</p>
      )}
      <table>
        <thead>
          <tr>
            <th>순서</th>
            <th>파라미터명</th>
            <th>타입</th>
            <th>값 소스</th>
            <th>리터럴 값</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {parameters.map((p, index) => (
            <tr key={index}>
              <td>{p.paramOrder}</td>
              <td>
                <input
                  value={p.paramName}
                  onChange={(e) => update(index, { paramName: e.target.value })}
                  placeholder="@FromDate"
                />
              </td>
              <td>
                <select value={p.dataType} onChange={(e) => update(index, { dataType: e.target.value as ParamDataType })}>
                  {DATA_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </td>
              <td>
                <select value={p.valueSource} onChange={(e) => update(index, { valueSource: e.target.value as ValueSource })}>
                  {VALUE_SOURCES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </td>
              <td>
                {p.valueSource === "LITERAL" && (
                  <input value={p.literalValue ?? ""} onChange={(e) => update(index, { literalValue: e.target.value })} />
                )}
              </td>
              <td>
                <button type="button" className="btn-danger btn-sm" onClick={() => removeRow(index)}>
                  삭제
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button type="button" onClick={addRow}>
        + 파라미터 추가
      </button>
    </div>
  );
}
