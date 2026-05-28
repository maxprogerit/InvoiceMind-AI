import { useMemo, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { FileUpload } from "../components/FileUpload";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { DocumentCategory, DocumentItem } from "../types/types";
import { byQuery } from "../utils/helpers";

export default function Documents() {
  const documents = useCRMStore((state) => state.documents);
  const clients = useCRMStore((state) => state.clients);
  const companies = useCRMStore((state) => state.companies);
  const addDocument = useCRMStore((state) => state.addDocument);
  const deleteDocument = useCRMStore((state) => state.deleteDocument);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [openManual, setOpenManual] = useState(false);
  const [manual, setManual] = useState<Omit<DocumentItem, "id" | "uploadDate">>({
    type: "Client File",
    fileName: "",
    fileSize: 0,
    linkedClientId: "",
    linkedCompanyId: "",
    content: "",
  });

  const rows = useMemo(
    () =>
      byQuery(
        documents,
        query,
        (document) =>
          `${document.fileName} ${document.type} ${clients.find((client) => client.id === document.linkedClientId)?.name ?? ""}`
      ).filter((document) => (filter === "all" ? true : document.type === filter)),
    [documents, query, filter, clients]
  );

  const selectedDocument = documents.find((document) => document.id === previewId) ?? null;

  return (
    <div className="stack">
      <Card title="Upload Document" subtitle="Drag/drop, file picker or manual creation">
        <FileUpload />
      </Card>

      <Card title="Manual Document Creation" actions={<Button variant="secondary" onClick={() => setOpenManual(true)}>Create Manually</Button>}>
        <p className="muted">Use this when file upload is not available and still keep document records complete.</p>
      </Card>

      <Card title="Documents" subtitle="Search, filter, preview, download and delete">
        <div className="grid cols-4">
          <Input placeholder="Search document..." value={query} onChange={(event) => setQuery(event.target.value)} />
          <Select value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="all">All types</option>
            {["Contract", "Proposal", "Invoice", "Receipt", "Report", "Legal", "Client File"].map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </Select>
        </div>
        <Table
          rows={rows}
          emptyMessage="No documents uploaded yet."
          columns={[
            { header: "Type", render: (row) => row.type },
            { header: "File Name", render: (row) => row.fileName },
            { header: "Size", render: (row) => `${Math.round(row.fileSize / 1024)} KB` },
            { header: "Upload Date", render: (row) => row.uploadDate },
            { header: "Linked Client", render: (row) => clients.find((client) => client.id === row.linkedClientId)?.name ?? "-" },
            { header: "Linked Company", render: (row) => companies.find((company) => company.id === row.linkedCompanyId)?.name ?? "-" },
            {
              header: "Action",
              render: (row) => (
                <div className="row">
                  <Button variant="secondary" onClick={() => setPreviewId(row.id)}>
                    Preview
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => {
                      pushToast(`Download started for ${row.fileName}.`, "success");
                    }}
                  >
                    Download
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => {
                      if (!window.confirm("Delete this document?")) return;
                      deleteDocument(row.id);
                      pushToast("Document deleted successfully.", "info");
                    }}
                  >
                    Delete
                  </Button>
                </div>
              ),
            },
          ]}
        />
      </Card>

      <Modal title="Document Preview" open={!!selectedDocument} onClose={() => setPreviewId(null)}>
        {selectedDocument ? (
          <div className="stack">
            <p>
              <strong>{selectedDocument.fileName}</strong> ({selectedDocument.type})
            </p>
            <p className="muted">
              Uploaded on {selectedDocument.uploadDate} - {Math.round(selectedDocument.fileSize / 1024)} KB
            </p>
            <pre style={{ whiteSpace: "pre-wrap" }}>
              {selectedDocument.content || "Preview not available for binary files. Metadata preview shown instead."}
            </pre>
          </div>
        ) : null}
      </Modal>

      <Modal title="Create Document Manually" open={openManual} onClose={() => setOpenManual(false)}>
        <form
          className="form"
          onSubmit={(event) => {
            event.preventDefault();
            if (!manual.fileName.trim()) {
              pushToast("File name is required.", "error");
              return;
            }
            addDocument({ ...manual, type: manual.type as DocumentCategory });
            pushToast("Document created successfully.", "success");
            setManual({
              type: "Client File",
              fileName: "",
              fileSize: 0,
              linkedClientId: "",
              linkedCompanyId: "",
              content: "",
            });
            setOpenManual(false);
          }}
        >
          <Select value={manual.type} onChange={(event) => setManual({ ...manual, type: event.target.value as DocumentCategory })}>
            {["Contract", "Proposal", "Invoice", "Receipt", "Report", "Legal", "Client File"].map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </Select>
          <Input placeholder="File name" value={manual.fileName} onChange={(event) => setManual({ ...manual, fileName: event.target.value })} required />
          <Input
            type="number"
            min={0}
            placeholder="File size (bytes)"
            value={manual.fileSize}
            onChange={(event) => setManual({ ...manual, fileSize: Number(event.target.value) })}
          />
          <Select value={manual.linkedClientId} onChange={(event) => setManual({ ...manual, linkedClientId: event.target.value })}>
            <option value="">Linked client</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
          <Select value={manual.linkedCompanyId} onChange={(event) => setManual({ ...manual, linkedCompanyId: event.target.value })}>
            <option value="">Linked company</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </Select>
          <Input placeholder="Preview content (optional)" value={manual.content ?? ""} onChange={(event) => setManual({ ...manual, content: event.target.value })} />
          <Button type="submit">Create Document</Button>
        </form>
      </Modal>
    </div>
  );
}
