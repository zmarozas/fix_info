## BLACKBIRD-49040 — Funding Trade field support (AMER BB → Mocha)

Populates the funding-trade fields on the internal trade event for orders routed AMER → EMEA Funding Desk (identified by the new `AmerToEmeaFundingDeskSpecification`).

**FillType — pass-through from EMEA (agreed with Ivan/EMEA BB):** EMEA sends the correct FillType on the Trade message itself — `InternalTransfer` for BBPLC→BBPLC, `House` for BBPLC→BCSL — and `TradeMessageMapper` passes it through unchanged. No transformation on the AMER side when publishing the internal trade event.

**Field population when the funding-desk spec matches:**
- `FillType` — from EMEA trade message (InternalTransfer / House)
- `TrdType` — FundingTrade
- `LastMkt` — XOFF (off-exchange)
- `IDF` (InvestmentDecisionWithinFirm) — root order's entering trader (BBPLC trader ID)
- `IQR` (InvestorQualifiedRole) — Person
- `EDF` (ExecutionWithinFirm) — NORE
- `EQR` (ExecutorQualifiedRole) — Client
- System roles — `AMER_TO_EMEA_FUNDING_DESK_ROLES` (TradingRole, InternalMarketRole) via `SystemRolesOtcMapper`

**Tests** (`SingleLegOctonSwapPrincipalFundingTradeTest`):
- `testOrderFillForInternalTransfer` — EMEA fills with FillType=InternalTransfer; trade event asserts InternalTransfer + all fields above
- `testOrderFillForHouseFill` — EMEA fills with FillType=House; trade event asserts House + all fields above

Both confirm the trade event is published with correct values end-to-end, no AMER-side FillType derivation needed.
