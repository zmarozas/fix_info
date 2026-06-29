# BLACKBIRD-49040 — Funding Trade Field Support (AMER BB → Mocha)

## Subject
Funding Trade – AMER BB to populate new fields to MO (Mocha) for FundingTrade workflow [v350]

---

## Description

### Context
AMER BB to populate the below fields to Mocha (MO) for the Funding Trade workflow. Both flows (BBPLC→BBPLC and BBPLC→BCSL) are covered in this Jira. Per Vishwa, only the **FillType** value differs between the two flows; everything else is identical. To be rolled out as part of **v350**.

### Trigger Condition (all must be true)
Security = US Security, Desk = SWAP, Legal Entity = BBPLC, and order routed to EMEA Funding Desk.

---

### Flow 1 — BBPLC → BBPLC

|| Field BB sends to Mocha || Value || Dependency ||
| TrdType  | FundingTrade      | Trigger condition above            |
| FillType | Internal Transfer | When TrdType = FundingTrade        |
| LastMkt  | XOFF              | When FillType = Internal Transfer  |
| IDF      | BBPLC Trader ID   | When TrdType = FundingTrade        |
| IQR      | Person            | When TrdType = FundingTrade        |
| EDF      | NORE              | When TrdType = FundingTrade        |
| EQR      | Client            | When TrdType = FundingTrade        |

---

### Flow 2 — BBPLC → BCSL

|| Field BB sends to Mocha || Value || Dependency ||
| TrdType  | FundingTrade    | Trigger condition above       |
| FillType | *House*         | When TrdType = FundingTrade   |
| LastMkt  | XOFF            | When FillType = House         |
| IDF      | BBPLC Trader ID | When TrdType = FundingTrade   |
| IQR      | Person          | When TrdType = FundingTrade   |
| EDF      | NORE            | When TrdType = FundingTrade   |
| EQR      | Client          | When TrdType = FundingTrade   |

---

### Note
Only the FillType value (*Internal Transfer* vs *House*) differs between the two flows. All other field mappings are the same.
