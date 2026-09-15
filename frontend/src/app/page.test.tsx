import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Home from "./page";

describe("Home", () => {
  it("タイトルと準備中の案内を表示する", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", { level: 1, name: "YouTube Handle ⇄ Channel ID Converter" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("準備中");
  });
});
