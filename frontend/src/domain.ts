import Decimal from "decimal.js";
Decimal.set({ precision: 40, rounding: Decimal.ROUND_HALF_UP });
export type Category = "COMMODITY" | "TRANSPORT" | "SYSTEM" | "TAX" | "OTHER";
export const categories: Record<Category, string> = { COMMODITY: "Materia gas", TRANSPORT: "Trasporto e contatore", SYSTEM: "Oneri di sistema", TAX: "Accisa e addizionali", OTHER: "Altre partite" };
export type Line = {
    code: string;
    label: string;
    category: Category;
    basis: "SMC" | "FIXED_MONTH" | "AMOUNT";
    rate: string;
    vatTreatment: "GLOBAL" | "LINE_RATE" | "NON_TAXABLE";
    vatRate: string;
    band: string;
};
export type Band = {
    code: string;
    label: string;
    min: string;
    max: string | null;
    rate: string;
};
export type Profile = {
    name: string;
    provider: string;
    area: string;
    validFrom: string;
    validTo: string | null;
    status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
    mode: "SOURCE_COMPATIBILITY" | "CONFIGURED_RULES";
    vatRate: string;
    source: string;
    approvedBy: string;
    lines: Line[];
    bands: Band[];
};
export type Offer = {
    name: string;
    provider: string;
    type: "FIXED" | "PSV";
    price: string;
    spread: string;
    qvdFixed: string;
    ccr: string;
    qvdVariable: string;
    active: boolean;
    notes: string;
};
export type Override = {
    rate: string;
    reason: string;
    operator: string;
};
export type Period = {
    month: string;
    consumptionSmc: string;
    fixedMonths: string;
    profileId: number | null;
    psv: string | null;
    chosenBands: Record<string, string>;
    overrides: Record<string, Override>;
};
export type Bill = {
    client: string;
    pdr: string;
    partitaIva: string;
    supplier: string;
    reference: string;
    previousTotal: string;
    otherTaxable: string;
    otherNonTaxable: string;
    periods: Period[];
};
export type Saved<T> = {
    id: number;
    version: number;
    createdAt: string;
    data: T;
};
export type Row = {
    month: string;
    code: string;
    label: string;
    category: Category;
    unit: string;
    quantity: string;
    rate: string;
    amount: string;
    vat: string;
    band: string | null;
    origin: string;
};
export type Audit = {
    month: string;
    code: string;
    original: string;
    updated: string;
    reason: string;
    operator: string;
    timestamp: string;
};
export type Result = {
    rows: Row[];
    categories: Record<Category, string>;
    taxable: string;
    vat: string;
    nonTaxable: string;
    totalRaw: string;
    total: string;
    differenceRaw: string;
    difference: string;
    percentage: string | null;
    annualRaw: string;
    annual: string;
    consumptionSmc: string;
    months: number;
    unapproved: boolean;
    audit: Audit[];
};
export type Comparison = {
    bill: Bill;
    offer: Offer;
    profiles: Record<string, Profile>;
    result: Result;
    engineVersion: string;
};
export type Source = {
    month: string;
    psv: string;
    source: string;
    reason: string;
};
export function number(value: string, digits = 8) { return new Decimal(value).toFixed(digits).replace(/\.?0+$/, "").replace(/\B(?=(\d{3})+(?!\d))/g, "").replace('.', ','); }
export function euro(value: string) { const [whole, fraction] = new Decimal(value).toFixed(2).split('.'); return whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ',' + fraction + ' €'; }
export const negative = (value: string) => new Decimal(value).isNegative();
export const absolute = (value: string) => new Decimal(value).abs().toString();
export const sum = (values: string[]) => values.reduce((a, b) => a.plus(b), new Decimal(0)).toString();
export const period = (): Period => ({ month: "", consumptionSmc: "", fixedMonths: "1", profileId: null, psv: null, chosenBands: {}, overrides: {} });
export const bill = (): Bill => ({ client: "", pdr: "", partitaIva: "", supplier: "", reference: "", previousTotal: "", otherTaxable: "0", otherNonTaxable: "0", periods: [period(), period()] });
export const offer = (): Offer => ({ name: "", provider: "", type: "FIXED", price: "", spread: "0", qvdFixed: "0", ccr: "0", qvdVariable: "0", active: true, notes: "" });
const definitions: [
    string,
    string,
    Category,
    Line["basis"]
][] = [
    ["QVD_FIXED", "QVD quota fissa", "COMMODITY", "FIXED_MONTH"], ["COMMODITY", "Prezzo materia prima", "COMMODITY", "SMC"], ["CCR", "Componente CCR", "COMMODITY", "SMC"], ["QVD_VAR", "QVD quota variabile", "COMMODITY", "SMC"],
    ["DIST_FIXED", "Quota fissa distribuzione", "TRANSPORT", "FIXED_MONTH"], ["DIST_VAR", "Distribuzione per scaglione", "TRANSPORT", "SMC"], ["QT", "Componente trasporto QT", "TRANSPORT", "SMC"], ["RS", "Componente RS", "TRANSPORT", "SMC"], ["UG1", "Componente UG1", "TRANSPORT", "SMC"],
    ["UG2_FIXED", "Quota fissa UG2", "SYSTEM", "FIXED_MONTH"], ["RE", "Componente RE", "SYSTEM", "SMC"], ["UG3", "Componente UG3", "SYSTEM", "SMC"], ["UG2_VAR", "UG2 per scaglione", "SYSTEM", "SMC"], ["EXCISE", "Accisa gas", "TAX", "SMC"], ["LOCAL_ADDITION", "Addizionale enti locali", "TAX", "SMC"]
];
export const profile = (): Profile => ({ name: "", provider: "", area: "", validFrom: "", validTo: null, status: "DRAFT", mode: "SOURCE_COMPATIBILITY", vatRate: "22", source: "", approvedBy: "", lines: definitions.map(([code, label, category, basis]) => ({ code, label, category, basis, rate: "0", vatTreatment: "GLOBAL", vatRate: "22", band: "" })), bands: [] });
export const bandCodes = ["DIST_VAR", "UG2_VAR", "EXCISE", "LOCAL_ADDITION"];
