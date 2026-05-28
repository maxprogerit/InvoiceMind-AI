import { useState } from "react";
import { askAiAssistant } from "../services/api";
import { Button } from "./Button";
import { Input } from "./Input";

interface Message {
  role: "user" | "assistant";
  content: string;
}

export function AIAssistant() {
  const [open, setOpen] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [messages, setMessages] = useState<Message[]>([
    { role: "assistant", content: "Hi! I can help summarize pipeline risk, cashflow, and task priorities." },
  ]);
  const [loading, setLoading] = useState(false);

  const send = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!prompt.trim()) return;
    const question = prompt.trim();
    setPrompt("");
    setMessages((prev) => [...prev, { role: "user", content: question }]);
    setLoading(true);
    const answer = await askAiAssistant(question);
    setMessages((prev) => [...prev, { role: "assistant", content: answer }]);
    setLoading(false);
  };

  return (
    <div className={`assistant ${open ? "open" : ""}`}>
      <button className="assistant-toggle" onClick={() => setOpen((value) => !value)}>
        AI Assistant
      </button>
      {open && (
        <div className="assistant-panel">
          <div className="assistant-messages">
            {messages.map((message, index) => (
              <p key={index} className={message.role === "assistant" ? "assistant-bubble" : "user-bubble"}>
                {message.content}
              </p>
            ))}
            {loading && <p className="assistant-bubble">Thinking...</p>}
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
