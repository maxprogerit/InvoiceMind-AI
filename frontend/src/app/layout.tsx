import type { Metadata, Viewport } from "next";

import { Providers } from "@/components/providers";

import "./globals.css";

export const metadata: Metadata = {
  title: { default: "InvoiceMind", template: "%s · InvoiceMind" },
  description: "Upload invoices, extract the data with OCR and AI, validate, approve and export.",
  icons: { icon: "/icon.svg" },
};

export const viewport: Viewport = { themeColor: "#110b0b", colorScheme: "dark" };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
