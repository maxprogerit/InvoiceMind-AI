import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Button } from "./button";

const classesOf = (name: string) => screen.getByRole("button", { name }).className.split(/\s+/);

describe("primary button surfaces", () => {
  it("keeps the full accent for a small primary button, where it is a signal", () => {
    render(<Button>Save</Button>);
    expect(classesOf("Save")).toContain("bg-accent");
    expect(classesOf("Save")).not.toContain("bg-accent-soft");
  });

  it.each(["lg", "block"] as const)("uses the soft accent for a large primary surface (%s)", (size) => {
    render(<Button size={size}>Continue</Button>);
    const c = classesOf("Continue");
    expect(c).toContain("bg-accent-soft");
    expect(c).toContain("text-accent");       // coral label on the soft fill, 5.5:1
    expect(c).not.toContain("bg-accent");     // the raw accent is gone, not merely overridden
    expect(c).not.toContain("text-accent-fg");
  });

  it("does not touch other variants of a large button", () => {
    render(<Button size="lg" variant="outline">Back</Button>);
    expect(classesOf("Back")).not.toContain("bg-accent-soft");
  });
});
