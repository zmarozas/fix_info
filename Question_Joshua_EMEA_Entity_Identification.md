## Question for Joshua — identifying BBPLC vs BCSL on EMEA-side fills

**Context:** Funding trade flow (BLACKBIRD-49040 / 55073). AMER Blackbird must populate **FillType** on the trade event based on the EMEA fill entity:
- BBPLC → BBPLC ⇒ FillType = **Internal Transfer**
- BBPLC → BCSL ⇒ FillType = **House**

From EMEA, AMER only receives two messages: the **order accept (OrderNew)** and the **Trade** message. The entity info must come from one of these.

**Questions:**
1. Which field(s) on OrderNew / Trade identify the EMEA entity (BBPLC vs BCSL)? Candidates: `LegalEntity`, `OriginatingLegalEntity`, `ContraBroker`, `ExecutingFirm` — or something else?
2. Is the entity guaranteed on the **Trade message itself**, or only on the accept (meaning AMER must remember it from the accept and apply it to the fill)?
3. Is it **stable across partial fills** — can a single order fill from both BBPLC and BCSL books, or is it fixed per order?

We need a deterministic rule since FillType on the trade event will be derived from this.
