import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Home from "./page";

describe("Home", () => {
  it("タイトルと変換フォームを表示する", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", { level: 1, name: "YouTube Handle ⇄ Channel ID Converter" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/1 行に 1 件ずつ/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "変換する" })).toBeDisabled();
  });
});
