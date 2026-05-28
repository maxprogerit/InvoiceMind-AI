import { useState } from "react";
import { Button } from "./Button";
import { Input } from "./Input";
import { Select } from "./Select";
import { useCRMStore } from "../store/crmStore";

export function FileUpload() {
  const addDocumentFile = useCRMStore((state) => state.addDocumentFile);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [linkedTo, setLinkedTo] = useState("");
  const [type, setType] = useState("general");
  const [file, setFile] = useState<File | null>(null);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!file) {
      pushToast("Select a file first.", "error");
      return;
    }
    addDocumentFile(file, linkedTo || "Unassigned", type);
    pushToast("Document uploaded.", "success");
    setFile(null);
    setLinkedTo("");
  };

  return (
    <form className="grid cols-4" onSubmit={submit}>
      <Input placeholder="Linked to (Client/Deal)" value={linkedTo} onChange={(event) => setLinkedTo(event.target.value)} />
      <Select value={type} onChange={(event) => setType(event.target.value)}>
        <option value="general">General</option>
        <option value="contract">Contract</option>
        <option value="invoice">Invoice</option>
        <option value="receipt">Receipt</option>
      </Select>
      <Input
        type="file"
        onChange={(event) => setFile(event.target.files?.[0] ?? null)}
      />
      <Button type="submit">Upload</Button>
    </form>
  );
}
