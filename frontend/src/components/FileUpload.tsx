import { useState } from "react";
import { Button } from "./Button";
import { Input } from "./Input";
import { Select } from "./Select";
import { useCRMStore } from "../store/crmStore";
import type { DocumentCategory } from "../types/types";

export function FileUpload() {
  const addDocumentFile = useCRMStore((state) => state.addDocumentFile);
  const addDocument = useCRMStore((state) => state.addDocument);
  const clients = useCRMStore((state) => state.clients);
  const companies = useCRMStore((state) => state.companies);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [linkedClientId, setLinkedClientId] = useState("");
  const [linkedCompanyId, setLinkedCompanyId] = useState("");
  const [type, setType] = useState<DocumentCategory>("Client File");
  const [file, setFile] = useState<File | null>(null);
  const [manualName, setManualName] = useState("");
  const [dragging, setDragging] = useState(false);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!file) {
      if (!manualName.trim()) {
        pushToast("Select a file or enter manual document name.", "error");
        return;
      }
      addDocument({
        type,
        fileName: manualName.trim(),
        fileSize: 0,
        linkedClientId,
        linkedCompanyId,
      });
      pushToast("Document created successfully.", "success");
      setManualName("");
      return;
    }
    addDocumentFile(file, linkedClientId, linkedCompanyId, type);
    pushToast("Document uploaded successfully.", "success");
    setFile(null);
    setLinkedClientId("");
    setLinkedCompanyId("");
  };

  return (
    <form className="grid cols-4" onSubmit={submit}>
      <div
        className="empty-state"
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          setFile(event.dataTransfer.files?.[0] ?? null);
        }}
        style={{ gridColumn: "1 / -1", borderColor: dragging ? "#2563eb" : undefined }}
      >
        {file ? `Ready: ${file.name}` : "Drag and drop a file here or use file picker."}
      </div>
      <Select value={type} onChange={(event) => setType(event.target.value as DocumentCategory)}>
        <option value="Contract">Contract</option>
        <option value="Proposal">Proposal</option>
        <option value="Invoice">Invoice</option>
        <option value="Receipt">Receipt</option>
        <option value="Report">Report</option>
        <option value="Legal">Legal</option>
        <option value="Client File">Client File</option>
      </Select>
      <Select value={linkedClientId} onChange={(event) => setLinkedClientId(event.target.value)}>
        <option value="">Linked client</option>
        {clients.map((client) => (
          <option key={client.id} value={client.id}>
            {client.name}
          </option>
        ))}
      </Select>
      <Select value={linkedCompanyId} onChange={(event) => setLinkedCompanyId(event.target.value)}>
        <option value="">Linked company</option>
        {companies.map((company) => (
          <option key={company.id} value={company.id}>
            {company.name}
          </option>
        ))}
      </Select>
      <Input
        type="file"
        onChange={(event) => setFile(event.target.files?.[0] ?? null)}
      />
      <Input placeholder="Manual document name" value={manualName} onChange={(event) => setManualName(event.target.value)} />
      <Button type="submit">Upload</Button>
    </form>
  );
}
