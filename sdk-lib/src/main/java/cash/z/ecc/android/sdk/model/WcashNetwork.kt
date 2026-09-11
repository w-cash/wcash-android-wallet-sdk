package cash.z.ecc.android.sdk.model

/**
 * Immutable identity metadata for a Wcash network whose consensus values are frozen.
 *
 * Only public Testnet and local Regtest are represented. Wcash Mainnet is deliberately absent
 * until its genesis block and transaction-signature domain have been finalized and reviewed.
 *
 * This type does not yet make the Zcash wallet backend Wcash-aware. In particular, callers must
 * never translate a [WcashNetwork] into [ZcashNetwork.id]; those integer IDs select Zcash
 * consensus parameters in the current native backend.
 *
 * The textual hashes and version prefixes are in display order.
 */
@Suppress("LongParameterList")
sealed class WcashNetwork private constructor(
    /** Exact network name accepted by a compatible Wcash node configuration. */
    val nodeNetworkName: String,
    /**
     * Network name currently reported in compact-block tree states.
     *
     * This inherited value is shared by Testnet and Regtest, so clients must also attest
     * [genesisBlockHash] and the active [consensusBranchIdHex] before trusting a server.
     */
    val compactServerNetworkName: String,
    /** Versioned namespace applications must include in per-chain wallet and cache storage. */
    val networkNamespace: String,
    /** Ticker used for valueless funds on this testing network. */
    val currencyTicker: String,
    /** Frozen genesis block hash in display order. */
    val genesisBlockHash: String,
    /** Consensus branch ID in eight-character display-order hexadecimal. */
    val consensusBranchIdHex: String,
    /** Height at which Wcash's Ironwood-only transaction rules activate. */
    val ironwoodActivationHeight: BlockHeight,
    /** Human-readable prefix for Wcash Unified Addresses. */
    val unifiedAddressHrp: String,
    /** Human-readable prefix for Wcash transparent-source-only addresses. */
    val texAddressHrp: String,
    /** Two-byte P2PKH Base58Check version in display-order hexadecimal. */
    val transparentP2pkhVersionHex: String,
    /** Two-byte P2SH Base58Check version in display-order hexadecimal. */
    val transparentP2shVersionHex: String,
) {
    /** Public Wcash Testnet v5. */
    data object Testnet : WcashNetwork(
        nodeNetworkName = "WcashTestnet",
        compactServerNetworkName = "test",
        networkNamespace = "wcashtestnet-v5",
        currencyTicker = "TWC",
        genesisBlockHash = "0271b5b0a10b2838f43cccdec9ca2f72aa72a7c103830082bac8f82f47f0593a",
        consensusBranchIdHex = "b3cfd27e",
        ironwoodActivationHeight = BlockHeight.new(1),
        unifiedAddressHrp = "wutest",
        texAddressHrp = "wtextest",
        transparentP2pkhVersionHex = "1095",
        transparentP2shVersionHex = "1098",
    )

    /** Process-local Wcash Regtest v5. */
    data object Regtest : WcashNetwork(
        nodeNetworkName = "WcashRegtest",
        compactServerNetworkName = "test",
        networkNamespace = "wcashregtest-v5",
        currencyTicker = "TWC",
        genesisBlockHash = "70bf0bab17eff361a6331bb825b3b7253c8c96ff96407f948161d2912658bb1c",
        consensusBranchIdHex = "c3a6678a",
        ironwoodActivationHeight = BlockHeight.new(1),
        unifiedAddressHrp = "wuregtest",
        texAddressHrp = "wtexregtest",
        transparentP2pkhVersionHex = "1090",
        transparentP2shVersionHex = "1093",
    )

    companion object {
        /** Every Wcash network whose identity this SDK can currently recognize. */
        val entries: List<WcashNetwork>
            get() = listOf(Testnet, Regtest)
    }
}
