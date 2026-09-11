package cash.w.sdk;

import androidx.annotation.NonNull;

import java.util.Objects;

import cash.w.sdk.internal.jni.JniWcashBackend;
import kotlin.jvm.functions.Function1;

/**
 * Validated caller-owned BIP-39 seed bytes for Wcash wallet derivation.
 *
 * <p>Construction copies the input and validates it through the Wcash Rust library. Call
 * {@link #close()} when the wallet no longer needs this object; closing overwrites the retained
 * copy and makes further derivation fail. String rendering never includes key material.
 */
public final class WcashWalletSeed implements AutoCloseable {
    private static final int BIP39_SEED_LENGTH_BYTES = 64;

    private final WcashSecretSeedBytes secret;

    private WcashWalletSeed(WcashSecretSeedBytes secret) {
        this.secret = secret;
    }

    /**
     * Copies and validates an app-provided BIP-39 seed.
     *
     * <p>This accepts exactly the 64-byte output of BIP-39 PBKDF2 processing, not the 16-32 bytes
     * of entropy encoded by a mnemonic. Invalid input is rejected with a fixed message that cannot
     * contain the input.
     */
    @NonNull
    public static WcashWalletSeed fromBip39SeedBytes(@NonNull byte[] bip39SeedBytes) {
        return fromCandidate(bip39SeedBytes, JniWcashBackend.INSTANCE::validateSeed);
    }

    @NonNull
    static WcashWalletSeed fromCandidate(
            @NonNull byte[] entropy,
            @NonNull Function1<? super byte[], Boolean> validator) {
        Objects.requireNonNull(entropy, "entropy");
        Objects.requireNonNull(validator, "validator");
        if (entropy.length != BIP39_SEED_LENGTH_BYTES) {
            throw new IllegalArgumentException("Invalid Wcash BIP-39 seed");
        }
        WcashSecretSeedBytes retained = WcashSecretSeedBytes.copyOf(entropy);
        boolean accepted = false;
        try {
            final boolean isValid;
            try {
                isValid = Boolean.TRUE.equals(retained.useCopy(validator));
            } catch (RuntimeException ignored) {
                throw new IllegalStateException("Could not validate Wcash BIP-39 seed");
            }

            if (!isValid) {
                throw new IllegalArgumentException("Invalid Wcash BIP-39 seed");
            }
            WcashWalletSeed result = new WcashWalletSeed(retained);
            accepted = true;
            return result;
        } finally {
            if (!accepted) {
                retained.close();
            }
        }
    }

    final <T> T useCopy(@NonNull Function1<? super byte[], ? extends T> block) {
        return secret.useCopy(block);
    }

    /** Overwrites this object's retained entropy. This operation is idempotent. */
    @Override
    public void close() {
        secret.close();
    }

    @NonNull
    @Override
    public String toString() {
        return "WcashWalletSeed([REDACTED])";
    }
}
