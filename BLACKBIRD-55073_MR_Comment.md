Adds `TargetPositionAccount` and `TargetSubBook` to the Sales Trading ROE so they flow on all order and fill event messages used for AMER ↔ EMEA Blackbird communication.

**common-model.xml** — field definitions:
- `TargetPositionAccount` (String, poolable, id 22232)
- `TargetSubBook` (String, poolable, id 22233)

**salestrading-model.xml** — fieldRefs added to:
- `ClientRequestedOrderGroup` (7999) — also covers OrderCancelReplace / Cross / Multileg via the inline group
- `OrderNewMessageDetails` (8003) — order-new event
- `TradeMessage` (20) — fill
- `TradeCorrectMessage` (26) — trade correction

Fields are optional / pass-through (no booking impact, no validation). Live population depends on EQDS-4979 (EMEA Book & SubBook repo in AMER).
