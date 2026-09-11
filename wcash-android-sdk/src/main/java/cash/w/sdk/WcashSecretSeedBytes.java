package cash.w.sdk;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

import kotlin.jvm.functions.Function1;

/**
 * Non-public owner of Wcash seed entropy.
 *
 * <p>The source is copied on entry, every temporary copy is overwritten after use, and closing
 * overwrites the retained copy. R8 rules preserve this class and its member visibility so the raw
 * bytes cannot become part of the release AAR's public surface.
 */
final class WcashSecretSeedBytes {
    private final byte[] secret;
    private boolean closed;

    private WcashSecretSeedBytes(byte[] ownedCopy) {
        secret = ownedCopy;
    }

    static WcashSecretSeedBytes copyOf(@NonNull byte[] source) {
        Objects.requireNonNull(source, "source");
        return new WcashSecretSeedBytes(Arrays.copyOf(source, source.length));
    }

    synchronized <T> T useCopy(@NonNull Function1<? super byte[], ? extends T> block) {
        Objects.requireNonNull(block, "block");
        if (closed) {
            throw new IllegalStateException("Wcash wallet seed has been destroyed");
        }

        byte[] workingCopy = Arrays.copyOf(secret, secret.length);
        try {
            return block.invoke(workingCopy);
        } finally {
            Arrays.fill(workingCopy, (byte) 0);
        }
    }

    synchronized void close() {
        if (!closed) {
            Arrays.fill(secret, (byte) 0);
            closed = true;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "WcashSecretSeedBytes([REDACTED])";
    }
}
