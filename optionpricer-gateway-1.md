# OptionPricer Gateway — Design

**Ticket:** BLACKBIRD-54916 · **Status:** Design

_A stateless bridge between Blackbird Trading and BiFrost for option theoreticals and Greeks._

**Model:** `BB Trading <--> optionpricer ROE <--> bridge app <--> BiFrost protos over Solace <--> BiFrost`

## 1. State

**Done.** The `optionpricer-roe` module is merged (MR !5728): model, service, enums, and common-model additions. The BiFrost message contract was worked out with Bapu (example payload documented on the BiFrost Confluence page). The ROE pipeline (13298221) is published.

**Pending.** The gateway application itself. The `environment.conf` entry (config Jira). BLACKBIRD-54934 (Solace queue and VPN bridge setup). Mark has a small local scaffold commit to be cherry-picked as the starting point.

## 2. What the bridge does

The bridge is a stateless, on-demand translator. It owns no reference data and holds no persistent state. Blackbird Trading passes everything required in the request; the bridge translates between the Blackbird ROE language and the BiFrost protobuf language, and relays messages in both directions. Blackbird never sees a protobuf message; BiFrost never sees an ROE.

It speaks two languages and is the only component that speaks both:

- **ROE side** (`optionpricer-roe`): the contract between Blackbird Trading and the bridge. Designed by Mark, merged, complete.
- **BiFrost side** (their protobuf, pulled in via Nexus): dictated by EQ Risk; we conform to it, we do not control it.

## 3. End-to-end flow

Request-driven and on-demand. There is no interest list and no start-of-day bulk subscription in scope for now (that is a future enhancement).

1. Blackbird Trading needs pricing for an option. It sends an `OptionPricingSubscriptionRequestMessage` (ROE) on the Blackbird VPN. The inlined `OptionProductData` carries everything the bridge needs: RIC, UnderlyingRIC, strike, expiry, PutOrCall, ExerciseType, SettlementCode.
2. The bridge receives it via a Neeve `@EventHandler`. It generates a CorrelationId and a ProductId, translates the ROE into a BiFrost `PricingRequest` (protobuf), and publishes it to the BiFrost request topic.
3. The Solace VPN bridge (infrastructure, BLACKBIRD-54934) delivers the request across to the BiFrost VPN. The bridge app itself only connects to the Blackbird VPN.
4. BiFrost prices the option and returns a `PricingResponse` on the CorrelationId, then ongoing `RiskProfile` updates on the ProductId.
5. The bridge receives each response/update via raw JCSMP, translates it into an `OptionPricingSnapshotMessage` (ROE), and publishes it back on the Blackbird VPN.
6. Blackbird Trading, subscribed, receives the snapshots like a market-data feed.

### Flow diagram

> Insert a PlantUML macro here (type `/plantuml`) and paste the diagram source kept separately below.

## 4. Architecture

Neeve AEP application following the stockloan-gateway pattern. Neeve's `com.neeve.server.Main` is the JVM entry point; Guice wires `@Managed @Singleton` beans; `@EventHandler` methods dispatch inbound ROE messages. Active/passive HA via event sourcing.

Two messaging styles in one app, exactly as stockloan-gateway:

- Neeve `@EventHandler` for the Blackbird ROE side (inbound subscription request, outbound snapshots).
- Raw JCSMP for the BiFrost protobuf side (outbound request, inbound response and updates). Protobuf-on-topics is foreign-format traffic, not a Neeve ROE.

### Difference from stockloan-gateway

| Concern | stockloan-gateway | optionpricer-gateway |
| --- | --- | --- |
| External service | Magics (locates) | BiFrost (pricing) |
| Wire format | XML SOAP | Protobuf (via Nexus) |
| Interaction | Request / Response | Request + Response + streaming updates |
| State | Loads static data | Stateless — no static data |
| Inbound ROE | EBorrowRequestMessage | OptionPricingSubscriptionRequestMessage |
| Outbound ROE | EBorrowResponseMessage | OptionPricingSnapshotMessage |
| HA policy | Event sourcing | Event sourcing (identical) |

> **Key constraint — "as dumb as possible".** The bridge holds no reference data and performs no enrichment. Blackbird passes everything needed in the request. The only state is transient in-flight correlation (see section 9).

## 5. ROE contract (merged)

```
message OptionPricingSubscriptionRequestMessage (id=1)
  inline:   OptionProductData
  fields:   SendingTime, SenderCompID, PublishingSystemInstanceID

message OptionPricingSnapshotMessage (id=2)
  inline:   OptionPricingData
  fields:   RIC, SendingTime

entity OptionProductData
  fields:   RIC, UnderlyingRIC, OptionStrikePrice, ExpireDate,
            PutOrCall, ExerciseType, SettlementCode

entity OptionPricingData
  fields:   TheoValue, Delta, Gamma, Vega,
            CashRho, BorrowRho, DivRho, Theta
```

## 6. Field mapping (translator spec)

This is the core of the translator and is now fully determined from the BiFrost protos (`pricing_data.proto` and `riskpnl_data.proto`). Both directions are pure functions and unit-testable in isolation.

### Inbound: OptionProductData (ROE) -> ProductData (proto)

The Blackbird request inlines `OptionProductData`. It maps onto BiFrost's `ProductData` (`pricing_data.proto`, message `ProductData`). Field names differ; some need translation (e.g. PutOrCall -> their OptionType enum). The ProductId the bridge generates is carried on `ProductData.ProductId` (field 10).

| ROE (OptionProductData) | Proto (ProductData) | Field # |
| --- | --- | --- |
| OptionStrikePrice | Strike | 3 |
| ExpireDate | Expiry | 2 |
| PutOrCall | OptionType (enum xlate) | 6 |
| ExerciseType | OptionStyle (enum xlate) | 5 |
| SettlementCode | SettlementType | 7 |
| UnderlyingRIC | UnderlyingRic | 12 |
| (generated) ProductId | ProductId | 10 |
| (from request) UnderlyingName | UnderlyingName | 1 |

### Outbound: PricingResult / RiskProfile (proto) -> OptionPricingData (ROE)

The Greeks and theo come off `RiskProfile`, reached via `PricingResult.riskProfile` (field 7). `RiskProfile` lives in `riskpnl_data.proto`. First-order Greeks come from its `firstOrderGreeks` (FirstOrdGreeks, field 8); Gamma is second-order, from `secondOrderGreeks` (SecondOrdGreeks, field 9). TheoValue is `RiskProfile.tv` (field 15).

| ROE (OptionPricingData) | Proto source | Path |
| --- | --- | --- |
| TheoValue | RiskProfile.tv (15) | `riskProfile.tv` |
| Delta | FirstOrdGreeks.delta (1) | `firstOrderGreeks.delta` |
| Vega | FirstOrdGreeks.vega (2) | `firstOrderGreeks.vega` |
| Theta | FirstOrdGreeks.theta (5) | `firstOrderGreeks.theta` |
| CashRho | FirstOrdGreeks.cashRho (6) | `firstOrderGreeks.cashRho` |
| BorrowRho | FirstOrdGreeks.borrowRho (7) | `firstOrderGreeks.borrowRho` |
| DivRho | FirstOrdGreeks.divRho (8) | `firstOrderGreeks.divRho` |
| Gamma | SecondOrdGreeks.gamma (1) | `secondOrderGreeks.gamma` |

> **Use the plain Greeks.** RiskProfile carries several Greek variants — `firstOrderVolGreeks`, `userFirstGreeks`, `firstOrderDynGreeks`, and dollar/variance variants (`dolGamma`, `varGamma`, etc.). For a vanilla option theo+Greeks subscription, use the plain `firstOrderGreeks`(8) and `secondOrderGreeks`(9). The `dol*` fields are dollar-Greeks (Greek x notional); the `var*` fields are variance-swap-specific. Picking the wrong one yields plausible-but-wrong numbers — confirm with Bapu.

## 7. Solace topics & VPN

```
Pricing/REQUEST/{UnderlyingName}/{CorrelationId}    BB -> BiFrost   PricingRequest
Pricing/RESPONSE/{UnderlyingName}/{CorrelationId}   BiFrost -> BB   PricingResponse
Pricing/GREEKS/{UnderlyingName}/{ProductId}         BiFrost -> BB   RiskProfile

UAT   vpn=flowvol_nyk_uat   host=flowvol-nyk-uat-sol
PROD  vpn=flowvol_nyk_prd   host=flowvol-nyk-prd-sol
```

The bridge connects only to the Blackbird VPN. The Solace VPN bridge handles cross-VPN delivery to and from BiFrost.

## 8. Dependencies

The BiFrost protobuf is pulled in via Nexus and used as a normal Java API — generated classes for PricingRequest, PricingResponse, RiskProfile, etc. No hand-written protobuf parsing.

The required proto closure is now known: `pricing_data.proto` imports `riskpnl_data`, `risk_meta`, `product_data`, `mkt_data`, and `stress_data`; `riskpnl_data.proto` in turn imports `risk_meta` and `mkt_data`. All live under the same risk-bo repo, so a correctly-built risk-bo:client jar very likely carries the whole closure — but verify before writing code, not after.

> **Watch-out:** confirm `risk-bo:2.0.181:client` packages the full transitive proto closure (riskpnl, risk_meta, product_data, mkt_data, stress_data + base types Date/Currency/Status/RiskId). If any are split into separate artifacts, add them, or message types will be missing at compile time.

```xml
<!-- Blackbird side: the merged ROE -->
<dependency>
  <groupId>com.barclays.eq.roe</groupId>
  <artifactId>optionpricer-roe</artifactId>
</dependency>
<dependency>
  <groupId>com.barclays.eq.roe</groupId>
  <artifactId>common-roe</artifactId>
</dependency>

<!-- BiFrost side: their protobuf (Nexus) -->
<dependency>
  <groupId>com.barclays.eqrisk</groupId>
  <artifactId>risk-bo</artifactId>
  <version>2.0.181</version>
  <classifier>client</classifier>
</dependency>
<!-- + any additional risk-* artifacts for the other protos (CONFIRM) -->

<!-- Apex/Neeve platform, AutoValue, etc. -- same as stockloan-gateway -->
```

## 9. Component structure

The translator is the heart of the app and is pure — message in, message out, no I/O — so it can be fully unit-tested with no Solace, no bridge, no infrastructure. Publishing is behind interfaces so tests capture the would-be-published message and assert on it.

```
com.barcap.eq.oms.optionpricergateway/
|-- OptionPricerGatewayMain         Neeve entry, @AppHAPolicy, FirstTransactionTask
|                                   (leaner than stockloan: NO static-data load)
|-- module/
|   |-- BiFrostAdapterModule        Guice bindings
|   \-- ConfigurationComponentModule
|-- inbound/
|   \-- SubscriptionRequestHandler  @EventHandler handle(OptionPricingSubscriptionRequestMessage)
|-- server/bifrost/
|   |-- BiFrostAdapter              setup(): JCSMP session on BB VPN, subscriptions
|   |-- PricingMessageTranslator    ROE <-> protobuf  (PURE, fully unit-tested)
|   |-- PricingRequestPublisher     interface  (mockable)
|   |   \-- ...Impl                 raw JCSMP publish
|   |-- PricingResponseHandler      protobuf in -> translate -> publish ROE
|   |-- RiskProfileHandler          protobuf in -> translate -> publish ROE
|   |-- CorrelationRegistry         transient in-flight map (if needed, see note below)
|   \-- OptionPricingParams         @AutoValue carrier
\-- service/
    |-- OptionPricingPublisher      interface  (mockable)
    |   \-- ...Impl                 ROE publish via PublishingService
    |-- ServiceFacade
    \-- OptionPricerServiceFacadeImpl

~20 Java files. Smaller than stockloan-gateway: single outbound ROE type, no XML/JDOM,
no static-data loading.
```

### In-flight correlation — design decision

Every outbound `OptionPricingSnapshotMessage` must be routed back to the correct Blackbird subscription. The two inbound BiFrost messages identify themselves differently, which is the crux of the problem:

- **Initial `PricingResponse`** comes back keyed on **CorrelationId** — the ID the bridge generated when it sent the request. Trivial to route: the request is still logically in flight, so the bridge knows which subscription it belongs to. No state needed.
- **Ongoing `RiskProfile` updates** arrive later, unprompted, keyed on **ProductId** (and carry `rId.symbol` inside the RiskProfile — e.g. `ESM00001` in Bapu's JSON example). No request is in flight when these land, so the bridge must recover the original subscription context from whatever the update carries.

Two ways to resolve this, and the choice is the Blackbird side's to make:

1. **Stateless** — if the outbound Blackbird snapshot topic and contents can be built entirely from fields present in the incoming update (UnderlyingName, ProductId, `rId.symbol`), the bridge holds no state. This is the preferred "as dumb as possible" outcome. It would be implemented through an outbound topic provider modeled on stockloan-gateway's `SolaceTopicPropertiesProvider` — a stateless helper that, given the identifiers, builds the publish destination.
2. **Transient map** — if the outbound side needs something the update does NOT carry (e.g. the original RIC, or the subscriber's SenderCompID), the bridge keeps an in-memory `ProductId -> request-context` map, populated at subscription time and dropped when the subscription ends. This is the `CorrelationRegistry` in the component list. It is transient correlation state, not persistent reference data.

**What decides it:** whether `rId.symbol` (a futures-style code in the example) maps cleanly to the RIC the Blackbird subscriber expects in the snapshot. That mapping is a Blackbird-side judgement, not a BiFrost question.

**Split of what to confirm vs. decide:**

- *Ask Bapu (BiFrost side):* do the ongoing updates reliably carry `rId.symbol` (and/or ProductId/UnderlyingName), and are those the right identifiers to correlate on? This is runtime-population behavior the proto alone cannot answer.
- *Decide on the Blackbird side (mine):* given those identifiers, can the snapshot be routed statelessly, or is a transient ProductId map required? This depends on the outbound `OptionPricingSnapshotMessage` contract, which the Blackbird side owns.

Default working assumption pending Bapu's answer: stateless via topic provider, with `CorrelationRegistry` held in reserve only if a needed field proves unavailable on the updates.

## 10. Testing strategy

The infrastructure (queues, VPN bridges) is not yet available and is hard to test against. The design isolates translation from transport so the bulk of the work can be built and fully tested before any infrastructure exists.

- Translation is pure: input message A, output message B. Unit-test directly — assert ROE -> protobuf and protobuf -> ROE field-by-field.
- Publishing sits behind interfaces (`PricingRequestPublisher`, `OptionPricingPublisher`). Tests inject a capturing fake, never touch the wire, and compare the captured message to an expected value.
- This de-risks the project: it removes the dependency on BLACKBIRD-54934 for everything except final end-to-end UAT.

## 11. Open questions

1. Greek selection — confirm with Bapu that a vanilla option theo+Greeks subscription reads the plain `firstOrderGreeks`(8) and `secondOrderGreeks`(9) off RiskProfile, not the Vol/Dyn/User or dollar/variance variants.
2. TheoValue — confirm TheoValue maps to `RiskProfile.tv`(15), discounted, not `undiscountedTV`(24).
3. Solace payload placement — where in the Solace message does BiFrost expect/send the protobuf payload (binary attachment vs other), in both directions? Not answerable from the proto; must confirm with BiFrost.
4. In-flight correlation — *(ask Bapu)* do the ongoing RiskProfile updates reliably carry `rId.symbol` / ProductId / UnderlyingName? *(decide Blackbird-side)* given those, can the snapshot be routed statelessly via a topic provider, or is a transient ProductId map needed? (see section 9)
5. Nexus access to `risk-bo:2.0.181:client` and confirmation it carries the full proto closure — available or needs request?
6. BLACKBIRD-54934 readiness for UAT.

## 12. Plan

1. Cherry-pick Mark's scaffold commit. Complete the project skeleton (pom, module layout, main class, static.conf).
2. Build `PricingMessageTranslator` (ROE <-> protobuf) behind publisher interfaces. Unit-test in isolation — no infrastructure needed.
3. BiFrostAdapter: JCSMP session on the BB VPN, publish PricingRequest, subscribe for response + updates.
4. Wire the inbound @EventHandler and the outbound ROE publishing through ServiceFacade / PublishingService.
5. environment.conf entries; raise the config Jira.
6. UAT deploy (once BLACKBIRD-54934 is ready); end-to-end test against BiFrost UAT; hand off to Trading.
7. Pilot prod after the compliance gate.

---

## PlantUML diagram source (paste into a separate /plantuml macro in section 3)

```
@startuml
title OptionPricer Gateway -- Subscription Round Trip
autonumber

participant "Blackbird\nTrading" as BB
participant "optionpricer-\ngateway\n(bridge)" as GW
participant "Solace VPN\nbridge\n(BLACKBIRD-54934)" as VPN
participant "BiFrost\n(EQ Risk)" as BF

== Subscribe ==
BB -> GW : OptionPricingSubscriptionRequestMessage (ROE)\ninlines OptionProductData
note right of GW : generate CorrelationId + ProductId\ntranslate ROE -> ProductData
GW -> VPN : PricingRequest (protobuf)\nPricing/REQUEST/{Underlying}/{CorrelationId}
VPN -> BF : (cross-VPN delivery)

== Initial price ==
BF -> VPN : PricingResponse (on CorrelationId)
VPN -> GW : PricingResponse
note right of GW : translate RiskProfile -> OptionPricingData
GW -> BB : OptionPricingSnapshotMessage (ROE)

== Ongoing updates ==
loop while subscribed
  BF -> VPN : RiskProfile update (on ProductId)\nPricing/GREEKS/{Underlying}/{ProductId}
  VPN -> GW : RiskProfile update
  note right of GW : translate -> OptionPricingData
  GW -> BB : OptionPricingSnapshotMessage (ROE)
end
@enduml
```
