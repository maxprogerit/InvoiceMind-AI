import { useState } from "react";
import { Button } from "./Button";
import { Input } from "./Input";
import { useCRMStore } from "../store/crmStore";

export function AIAssistant() {
  const open = useCRMStore((state) => state.assistantOpen);
  const messages = useCRMStore((state) => state.assistantMessages);
  const loading = useCRMStore((state) => state.assistantTyping);
  const toggleAssistant = useCRMStore((state) => state.toggleAssistant);
  const sendAssistantMessage = useCRMStore((state) => state.sendAssistantMessage);
  const [prompt, setPrompt] = useState("");

  const quickPrompts = [
    "Summarize dashboard",
    "Find overdue invoices",
    "Show top clients",
    "Analyze sales pipeline",
    "Suggest follow ups",
    "Show missing documents",
    "Forecast revenue",
  ];

  const send = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!prompt.trim()) return;
    const question = prompt.trim();
    setPrompt("");
    await sendAssistantMessage(question);
  };

  return (
    <div className={`assistant ${open ? "open" : ""}`}>
      <button className="assistant-toggle" onClick={toggleAssistant}>
        Open AI Assistant
      </button>
      {open && (
        <div className="assistant-panel">
          <div className="row" style={{ flexWrap: "wrap", marginBottom: 8 }}>
            {quickPrompts.map((quickPrompt) => (
              <Button
                key={quickPrompt}
                variant="secondary"
                type="button"
                onClick={() => {
                  setPrompt(quickPrompt);
                  void sendAssistantMessage(quickPrompt);
                }}
              >
                {quickPrompt}
              </Button>
            ))}
          </div>
          <div className="assistant-messages">
            {messages.map((message) => (
              <p key={message.id} className={message.role === "assistant" ? "assistant-bubble" : "user-bubble"}>
                {message.content}
              </p>
            ))}
            {loading && <p className="assistant-bubble">Typing...</p>}
          </div>
          <form onSubmit={send} className="row">
            <Input value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder="Ask about CRM performance..." />
            <Button type="submit">Send</Button>
          </form>
        </div>
      )}
    </div>
  );
}
