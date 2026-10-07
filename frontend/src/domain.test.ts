import { describe, it, expect } from "vitest";
import { euro, sum, absolute, negative, bill, profile } from "./domain";
describe("decimal gas", () => {
    it("formats raw source totals without float rounding", () => { expect(euro("392.605125844")).toBe("392,61 €"); expect(euro("659.773269640")).toBe("659,77 €"); expect(sum(["0.1", "0.2"])).toBe("0.3"); expect(euro("1234567.995")).toBe("1.234.568,00 €"); });
    it("distinguishes higher cost", () => { expect(negative("-106.333269640")).toBe(true); expect(euro(absolute("-106.333269640"))).toBe("106,33 €"); });
    it("starts with empty customer data and explicit draft rates", () => { expect(bill().pdr).toBe(""); expect(bill().periods).toHaveLength(2); expect(profile().status).toBe("DRAFT"); expect(profile().lines).toHaveLength(15); expect(profile().lines.find(l => l.code === "EXCISE")!.band).toBe(""); });
});
