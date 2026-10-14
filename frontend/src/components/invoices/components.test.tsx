import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ALL_STATUSES } from "@/lib/api/types";
import { STATUS_META } from "@/lib/status";

import { DecisionDialog } from "./decision-dialog";
import { PipelineStepper } from "./pipeline-stepper";
import { StatusBadge } from "./status-badge";

function wrap(ui: React.ReactElement) {
  return render(<QueryClientProvider client={new QueryClient()}>{ui}</QueryClientProvider>);
}

describe("StatusBadge", () => {
  it("renders a readable label for every status", () => {
    for (const status of ALL_STATUSES) {
      const { unmount } = render(<StatusBadge status={status} />);
      expect(screen.getByText(STATUS_META[status].label)).toBeInTheDocument();
      unmount();
    }
  });
});

describe("PipelineStepper", () => {
  it("marks reached steps and shows failure at the AI step", () => {
    const { rerender } = render(<PipelineStepper status="VALIDATED" />);
    expect(screen.getByLabelText("Processing pipeline")).toBeInTheDocument();
    expect(screen.getByText("Validated")).toBeInTheDocument();
    rerender(<PipelineStepper status="FAILED" />);
    expect(screen.getByText("OCR + AI")).toBeInTheDocument();
  });
});

describe("DecisionDialog", () => {
  it("requires a reason to reject but not to approve", async () => {
    const user = userEvent.setup();
    const { unmount } = wrap(<DecisionDialog invoiceId="i1" kind="reject" summary="ACME · INV-1" open onOpenChange={vi.fn()} />);
    const rejectButton = screen.getByRole("button", { name: "Reject" });
    expect(rejectButton).toBeDisabled();
    await user.type(screen.getByLabelText(/reason/i), "ab");
    expect(rejectButton).toBeDisabled(); // backend requires at least 3 characters
    await user.type(screen.getByLabelText(/reason/i), "c");
    expect(rejectButton).toBeEnabled();
    unmount();

    wrap(<DecisionDialog invoiceId="i1" kind="approve" summary="ACME · INV-1" open onOpenChange={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
  });
});
