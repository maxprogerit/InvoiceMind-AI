import type { ReactNode } from "react";

export interface TableColumn<T> {
  header: string;
  render: (row: T) => ReactNode;
}

interface TableProps<T> {
  rows: T[];
  columns: TableColumn<T>[];
  emptyMessage: string;
}

export function Table<T>({ rows, columns, emptyMessage }: TableProps<T>) {
  if (!rows.length) {
    return <div className="empty-state">{emptyMessage}</div>;
  }

  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.header}>{column.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index}>
              {columns.map((column) => (
                <td key={column.header}>{column.render(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
