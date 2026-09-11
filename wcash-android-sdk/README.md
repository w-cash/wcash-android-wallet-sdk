# Wcash Android SDK

`wcash-android-sdk` is a separate Android library and native library for Wcash. It does not reuse
the Zcash backend's integer network selector and does not modify or disable Zcash features.

## Supported now

- Frozen Wcash Testnet v5 and local Regtest v5 identities
- Wcash domain-separated seed validation and account derivation
- Canonical Ironwood-only Wcash Unified Address derivation
- Canonical Ironwood address parsing, including lowercase normalization of valid uppercase input
- Default transparent P2PKH coinbase address derivation and exact Base58Check parsing
- Rejection of Zcash, P2SH, TEX, malformed, non-canonical, and wrong-network addresses

Mainnet is deliberately absent until its genesis hash, transaction branch ID, and checkpoints are
frozen. This module does not yet synchronize compact blocks, persist a wallet, report balances, or
create and sign transactions.

## Source integration

```kotlin
dependencies {
    implementation(project(":wcash-android-sdk"))
}
```

```kotlin
val seed = WcashWalletSeed.fromBip39SeedBytes(bip39SeedBytes)
try {
    val tool = WcashWalletTool.create()
    val account = WcashAccountIndex.from(0)
    val address = tool.deriveIronwoodAddress(
        seed = seed,
        network = WcashNetwork.Testnet,
        accountIndex = account
    )
    val transparentCoinbaseAddress = tool.deriveTransparentCoinbaseAddress(
        seed = seed,
        network = WcashNetwork.Testnet,
        accountIndex = account
    )
} finally {
    seed.close()
}
```

`bip39SeedBytes` is the 64-byte output of BIP-39 PBKDF2 processing for the wallet's mnemonic and
optional passphrase. Mnemonic entropy (16-32 bytes) is deliberately rejected so one recovery
phrase cannot silently select different derivation input in different wallets. The SDK copies and
Rust-validates the BIP-39 seed and never renders it. The application still owns and must overwrite
its original byte array. `WcashWalletSeed.close()` overwrites the SDK's retained copy and prevents
reuse.

`WcashWalletSeed` deliberately uses a Wcash-specific secret holder instead of the upstream
`FirstClassByteArray`: it copies caller input, overwrites temporary and retained arrays, prevents
reuse after `close()`, and redacts string rendering. JVM garbage collection can still create copies
outside the SDK's control, so callers must keep the seed lifetime short and overwrite their own
input.

## Release boundary

This is a source-integration milestone, not a publishable binary release. Do not distribute the AAR
until dependency notices, native provenance, and an SBOM are packaged with it.

The supported minified wallet profile must not enable global R8 access modification. The release
gate builds an optimizing consumer fixture without `-allowaccessmodification` and inspects its final
DEX so the seed constructor and raw secret storage remain non-public. A future opaque native seed
handle can remove this optimizer-profile restriction; it is intentionally outside this milestone.

## Native dependency

The minimal `wcashwalletsdk` Rust `cdylib` is pinned to
`w-cash/librustzcash@688dc1d2157b8bef431e5e6fe7b0acb90fc4b363`. Raw JNI types are internal to
the module; public APIs accept and return Wcash semantic types.
