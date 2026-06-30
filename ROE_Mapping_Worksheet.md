# BLACKBIRD-55073 — ROE Message / Field Mapping Worksheet
## Add Target Book (Account) + Target Sub Account (SubBook) to Sales Trading ROE

Model file: `salestrading-roe/src/main/resources/salestrading-model.xml`
(field defs in the `com.barclays.eq.roe.common.*` namespace, referenced via `<fieldRef>`)

---

## 0. THE decision to settle first — reuse vs. new field

The model **already** contains fields that look like what the ticket is asking for:

| Existing common field            | Where seen                                  | Could it be "Target Book"? | Could it be "Target Sub Account"? |
|----------------------------------|---------------------------------------------|----------------------------|-----------------------------------|
| `PositionAccount`                | ClientRequestedOrderGroup, TradeMessage, +5 | Possibly (= Target Book?)  | —                                 |
| `SubBook`                        | ClientRequestedOrderGroup                   | —                          | Possibly (= Target Sub Account?)  |
| `ExchangeTradingAccount`         | TradeMessage, line 164/799                  | ?                          | —                                 |
| `SourceAccount`                  | line 888                                     | —                          | —                                 |
| `CustomerAccount` / `External…`  | lines 783–785                                | —                          | —                                 |

**Question to lock down:** Is "Target Book / Target Sub Account" the **same** as existing `PositionAccount` / `SubBook` (= the destination book/subbook), or a **new, distinct** field (e.g. the EMEA target book, separate from the AMER PositionAccount)?

- If **reuse** → no schema change; work = ensure they're populated at AMER, carried on routing, echoed on fills, consumed by EMEA. Much smaller.
- If **new** → add new common field(s) (e.g. `TargetAccount`, `TargetSubBook`), wire into the right messages/groups, regenerate proto + Java.

Decision: ___________________________________________

---

## 1. Message inventory — which carry the fields?

Tick the messages in scope for "back & forth on Orders and Fills". Note how each gets account fields today (direct `fieldRef` vs embedded group).

| Message                       | id  | Account today (via)                          | Needs Target Book? | Needs Target SubAcct? | Placement (group / direct) |
|-------------------------------|-----|----------------------------------------------|--------------------|-----------------------|----------------------------|
| NewOrderSingleMessage         | 64  | ClientRequestedOrderGroup (PositionAccount, SubBook) | ☐          | ☐                     |                            |
| NewOrderMultilegMessage       | 65  | ?                                            | ☐                  | ☐                     |                            |
| NewOrderCrossMessage          | 67  | ?                                            | ☐                  | ☐                     |                            |
| OrderNewMessage (order event) | 25  | ?                                            | ☐                  | ☐                     |                            |
| OrderCancelReplaceRequest (Amend) | ? | ClientRequestedOrderGroup (restated)        | ☐                  | ☐                     |                            |
| TradeMessage (fill)           | 20  | BarcapExecutionReport (PositionAccount, ExchangeTradingAccount) | ☐      | ☐                     |                            |
| ListSummaryMessage            | 7   | ?                                            | ☐                  | ☐                     |                            |

---

## 2. Shared embedded groups (entities, `asEmbedded="true"`)

Adding a field to one of these propagates it to every message that embeds it.

| Group / inline entity        | Used by                          | Good home for the new fields? |
|------------------------------|----------------------------------|-------------------------------|
| `ClientRequestedOrderGroup` (id 7999) | NewOrderSingle, Amend (restated) | Order side — already holds PositionAccount + SubBook |
| `BarcapExecutionReport`      | TradeMessage                     | Fill / execution side |
| `EmeaRoutingGroup`           | ClientRequestedOrderGroup        | **Ask:** if fields are EMEA-routing-specific, is this the natural home? |
| `NewOrAmendedOrderFieldsGroup` | NewOrderSingle                 | ? |
| `SalestradingFIXRequestGroup`| NewOrderSingle, Multileg, Cross  | ? |
| `FirstOrderParameters`       | ClientRequestedOrderGroup        | ? |

**Trade-off:** shared group = DRY, single edit, consistent. Risk = field appears on messages that shouldn't carry it. Per-message = precise but repetitive and easy to miss one.

Strategy chosen: ___________________________________________

---

## 3. Implementation steps (once reuse-vs-new + placement decided)

If **new fields** are required:
1. Define field(s) in the common model (`common-model.xml`) under `com.barclays.eq.roe.common.*` — name, type, length, id, optional.
2. Add `<fieldRef ref="…"/>` into the chosen group(s)/message(s) in `salestrading-model.xml`. Keep **optional** so existing flows are unaffected.
3. Rebuild ROE → protobuf defs → generated Java classes (confirm regen is automated in the Maven build; note the "cannot install wrapped maven, set Bundled Maven" build issue currently showing).
4. Populate at source (AMER) and consume at EMEA; ensure values are carried unchanged on order → ack → fill → amend.
5. Confirm proto field numbering is **additive** (new tags, nothing reused) for backward compatibility.

If **reuse** (PositionAccount / SubBook):
1. No schema change.
2. Confirm AMER populates them for the funding/swap inter-regional flow.
3. Confirm they survive routing and are echoed on the execution/trade messages.
4. Confirm EMEA consumes them into the correct Atlas folder.

---

## 4. Sharpened questions for the meeting

1. Are Target Book / Target Sub Account **new fields** or the **existing** `PositionAccount` / `SubBook`? (Drives everything.)
2. If new, what distinguishes Target Book from `PositionAccount` and `ExchangeTradingAccount` semantically?
3. Exact field names, types, lengths, optional? Atlas folder/subbook max length?
4. Which **messages** exactly — orders only, or also acks, fills, amends, cancels, cross, multileg?
5. **Round-trip?** Must EMEA echo Book/SubBook back on acks + fills, or AMER→EMEA inbound only?
6. Placement: shared group (which one — `EmeaRoutingGroup`?) vs per-message?
7. Is the ROE shared with EMEA? Who owns it, and do both sides regenerate from the same definition? (Version coordination.)
8. EMEA-side Jira — who raises it (Ivan?) and what's the sequencing vs BATMAN-31279/31280, EGDE-4970?
9. Target release / sprint; SIT test data with Sub Account.
10. Confirm additive/optional so non-funding flows and existing consumers are unaffected.

---

### Account-related fields found in salestrading-model.xml (for reference)
PositionAccount · ExchangeTradingAccount · CrossContraAccount · CustomerAccount · ExternalCustomerAccount · ExternalCustomerAccountSDSID · PECAPSSourceAccount · SourceAccount · AllocAccountList · SubBook
