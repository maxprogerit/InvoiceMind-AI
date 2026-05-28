import type { Metadata } from "next";
import "@/styles/globals.css";
import { ThemeProvider } from "@/hooks/use-theme-provider";
import { QueryProvider } from "@/hooks/use-query-client";

export const metadata: Metadata = {
  title: "InvoiceMind AI",
  description: "Enterprise AI OCR and automation platform"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <ThemeProvider>
          <QueryProvider>{children}</QueryProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
