"use client";

import { FileWarning } from "lucide-react";

import { Skeleton } from "@/components/ui/misc";
import { useDocumentBlob } from "@/lib/api/hooks";
import { cn } from "@/lib/utils";

/**
 * Shows the original upload. The file endpoint requires the bearer token, so it is fetched as a blob
 * and shown through an object URL: PDFs in the browser's own viewer (zoom, search, print), images inline.
 */
export function DocumentViewer({
  invoiceId,
  filename,
  className,
  chrome = true,
}: {
  invoiceId: string;
  filename: string;
  className?: string;
  /** false hides the PDF toolbar (used for the small preview card) */
  chrome?: boolean;
}) {
  const { data, isLoading, isError } = useDocumentBlob(invoiceId);

  if (isLoading) return <Skeleton className={cn("h-full min-h-48 w-full", className)} />;
  if (isError || !data) {
    return (
      <div className={cn("flex h-full min-h-48 flex-col items-center justify-center gap-2 rounded-control border border-dashed border-line-strong text-sm text-dim", className)}>
        <FileWarning className="size-6" />
        The document could not be loaded.
      </div>
    );
  }
  if (data.type.startsWith("image/")) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- blob: URL, next/image cannot optimise it
      <img src={data.url} alt={`Scan of ${filename}`} className={cn("h-full w-full rounded-control bg-white object-contain", className)} />
    );
  }
  return (
    <iframe
      title={`Document ${filename}`}
      src={chrome ? data.url : `${data.url}#toolbar=0&navpanes=0&scrollbar=0&view=FitH`}
      className={cn("h-full w-full rounded-control border-0 bg-white", className)}
    />
  );
}
