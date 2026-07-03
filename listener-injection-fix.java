// ============================================================
// FIX: constructor injection for the queue name (no field @Inject)
// Replace the field + constructor in BifrostInboundListener:
// ============================================================

    private final String bifrostInboundQueueName;   // plain final field, no annotations

    private FlowReceiver bifrostInboundConsumer;

    @Inject
    public BifrostInboundListener(PricingResponseHandler pricingResponseHandler,
                                  RiskProfileUpdateHandler riskProfileUpdateHandler,
                                  EnvironmentPrefixTranslator environmentPrefixTranslator,
                                  Scheduler scheduler,
                                  @Named(BifrostAdapter.Solace.BIFROST_INBOUND_QUEUE)
                                  String bifrostInboundQueueName) {
        this.pricingResponseHandler = pricingResponseHandler;
        this.riskProfileUpdateHandler = riskProfileUpdateHandler;
        this.environmentPrefixTranslator = environmentPrefixTranslator;
        this.scheduler = scheduler;
        this.bifrostInboundQueueName = bifrostInboundQueueName;
    }

// ============================================================
// TEST setUp() change (replaces the package-private assignment):
// ============================================================

    private static final String QUEUE_NAME = "SOLQIN.AMM.BB.OPT.1";

    @Before
    public void setUp() {
        listener = new BifrostInboundListener(pricingResponseHandler,
                riskProfileUpdateHandler,
                environmentPrefixTranslator,   // or the real new EnvironmentPrefixTranslator("bb_dev") as today
                scheduler,
                QUEUE_NAME);
    }

// ============================================================
// static.conf (queue name follows the EXISTING provisioned queue):
//   bifrostInboundQueue = "SOLQIN.AMM.BB.OPT.1"
//   bifrostInboundQueue = ${?optionPricerGateway.solace.bifrostInboundQueue}
// No environment.conf entry (per Erb's hardcode-in-static approach).
// If multi-instance later: derive suffix from application.instance the same
// way the apex input queue does — but confirm with Erb; instance 1 only today.
//
// VERIFY in solview: SOLQIN.AMM.BB.OPT.1 -> subscriptions tab should show
// Pricing/RESPONSE/> and Pricing/GREEKS/> (or Erb's equivalents). If the subs
// are missing, add via SEMP before switching the code over.
// ============================================================
