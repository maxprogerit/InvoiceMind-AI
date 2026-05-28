export async function fakeDelay(ms = 220): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

export async function askAiAssistant(prompt: string): Promise<string> {
  await fakeDelay();
  return `AI insight: ${prompt.slice(0, 100)}. Focus on high-value deals, overdue invoices, and follow-up tasks this week.`;
}
