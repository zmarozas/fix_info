// ============================================================================
// CORRECTIONS for BifrostAdapter.java — replace ONLY these two regions.
// Everything else in the class stays exactly as-is.
// ============================================================================


// ---- REGION 1: the Solace inner class --------------------------------------
// Change: optionpricerGateway -> optionPricerGateway (capital P) on all 8 paths,
// to match your environment.conf / static.conf and ENVIRONMENT_PREFIX above.

    public static class Solace {
        public static final String AUTHENTICATION_TYPE         = "optionPricerGateway.bifrost.solace.authType";
        public static final String HOST                        = "optionPricerGateway.bifrost.solace.host";
        public static final String VPN                         = "optionPricerGateway.bifrost.solace.vpn";
        public static final String SSL_KEY_STORE               = "optionPricerGateway.bifrost.solace.auth.ssl.keyStore";
        public static final String SSL_KEY_STORE_PASSWORD_FILE = "optionPricerGateway.bifrost.solace.auth.ssl.keyStorePasswordFile";
        public static final String REQUEST_TOPIC_PATTERN       = "optionPricerGateway.bifrost.solace.topics.requestPattern";
        public static final String RESPONSE_SUBSCRIPTION       = "optionPricerGateway.bifrost.solace.topics.responseSubscription";
        public static final String GREEKS_SUBSCRIPTION         = "optionPricerGateway.bifrost.solace.topics.greeksSubscription";
    }


// ---- REGION 2: readPassword ------------------------------------------------
// Change: fail fast with the file path instead of returning null (Duo's point).
// Only reached in the SSL/cert auth path, so this throws at setup, never silently.

    private String readPassword(String filePath) {
        try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
            return stream.findFirst()
                    .orElseThrow(() -> new IOException("Keystore password file is empty: " + filePath));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Solace keystore password file: " + filePath, e);
        }
    }
