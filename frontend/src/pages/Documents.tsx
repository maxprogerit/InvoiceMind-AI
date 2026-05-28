import { useMemo, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { FileUpload } from "../components/FileUpload";
import { Input } from "../components/Input";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import { byQuery } from "../utils/helpers";

export default function Documents() {
  const documents = useCRMStore((state) => state.documents);
  const deleteDocument = useCRMStore((state) => state.deleteDocument);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");

  const rows = useMemo(
    () =>
      byQuery(documents, query, (doc) => `${doc.name} ${doc.linkedTo}`)
        .filter((doc) => (filter === "all" ? true : doc.type === filter)),
    [documents, query, filter]
  );

  return (
    <div className="stack">
      <Card title="Upload Document" subtitle="Add contracts, invoices, and receipts">
        <FileUpload />
      </Card>
      <Card title="Documents" subtitle="Filters, search, table, and empty states">
        <div className="grid cols-4">
          <Input placeholder="Search document..." value={query} onChange={(event) => setQuery(event.target.value)} />
          <Select value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="all">All types</option>
            <option value="general">General</option>
            <option value="contract">Contract</option>
            <option value="invoice">Invoice</option>
            <option value="receipt">Receipt</option>
            <option value="legal">Legal</option>
          </Select>
        </div>
        <Table
          rows={rows}
          emptyMessage="No documents uploaded yet."
          columns={[
            { header: "Name", render: (row) => row.name },
            { header: "Type", render: (row) => row.type },
            { header: "Size", render: (row) => `${Math.round(row.size / 1024)} KB` },
            { header: "Uploaded", render: (row) => row.uploadedAt },
            { header: "Linked To", render: (row) => row.linkedTo },
            {
              header: "Action",
              render: (row) => (
                <Button variant="danger" onClick={() => deleteDocument(row.id)}>
                  Delete
                </Button>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
