package cash.w.sdk.model

/**
 * Immutable identity for a frozen Wcash network.
 *
 * Only public Testnet and process-local Regtest are available. Mainnet is deliberately absent
 * until its genesis block, transaction-signature domain, and checkpoints are finalized.
 */
@Suppress("LongParameterList")
sealed class WcashNetwork private constructor(
    /** Exact selector accepted by a compatible Wcash node. */
    val nodeNetworkName: String,
    /** Chain name currently returned by the compact-block server protocol. */
    val compactServerNetworkName: String,
    /** Versioned namespace for every database, cache, and preferences file. */
    val networkNamespace: String,
    /** Symbol for valueless Wcash testing funds. */
    val currencyTicker: String,
    /** Frozen genesis hash in display byte order. */
    val genesisBlockHash: String,
    /** Frozen v6 transaction branch ID in display-order hexadecimal. */
    val consensusBranchIdHex: String,
    /** First height at which Wcash Ironwood rules are active. */
    val ironwoodActivationHeight: Long,
    /** Human-readable prefix for Wcash Unified Addresses. */
    val unifiedAddressHrp: String,
    /** Human-readable prefix for Wcash transparent-source-only addresses. */
    val texAddressHrp: String,
    /** Two-byte P2PKH Base58Check version in display-order hexadecimal. */
    val transparentP2pkhVersionHex: String,
    /** Two-byte P2SH Base58Check version in display-order hexadecimal. */
    val transparentP2shVersionHex: String,
    @get:JvmSynthetic
    internal val nativeCode: Int
) {
    /** Public Wcash Testnet v5. */
    data object Testnet : WcashNetwork(
        nodeNetworkName = "WcashTestnet",
        compactServerNetworkName = "test",
        networkNamespace = "wcashtestnet-v5",
        currencyTicker = "TWC",
        genesisBlockHash = "0271b5b0a10b2838f43cccdec9ca2f72aa72a7c103830082bac8f82f47f0593a",
        consensusBranchIdHex = "b3cfd27e",
        ironwoodActivationHeight = 1,
        unifiedAddressHrp = "wutest",
        texAddressHrp = "wtextest",
        transparentP2pkhVersionHex = "1095",
        transparentP2shVersionHex = "1098",
        nativeCode = TESTNET_NATIVE_CODE
    )

    /** Process-local Wcash Regtest v5. */
    data object Regtest : WcashNetwork(
        nodeNetworkName = "WcashRegtest",
        compactServerNetworkName = "test",
        networkNamespace = "wcashregtest-v5",
        currencyTicker = "TWC",
        genesisBlockHash = "70bf0bab17eff361a6331bb825b3b7253c8c96ff96407f948161d2912658bb1c",
        consensusBranchIdHex = "c3a6678a",
        ironwoodActivationHeight = 1,
        unifiedAddressHrp = "wuregtest",
        texAddressHrp = "wtexregtest",
        transparentP2pkhVersionHex = "1090",
        transparentP2shVersionHex = "1093",
        nativeCode = REGTEST_NATIVE_CODE
    )

    companion object {
        private const val TESTNET_NATIVE_CODE = 1
        private const val REGTEST_NATIVE_CODE = 2

        /** Every Wcash network supported by this artifact. */
        @JvmStatic
        val entries: List<WcashNetwork>
            get() = listOf(Testnet, Regtest)

        internal fun fromNativeCode(code: Int): WcashNetwork =
            when (code) {
                TESTNET_NATIVE_CODE -> Testnet
                REGTEST_NATIVE_CODE -> Regtest
                else -> throw IllegalArgumentException("Unsupported Wcash network")
            }
    }
}
