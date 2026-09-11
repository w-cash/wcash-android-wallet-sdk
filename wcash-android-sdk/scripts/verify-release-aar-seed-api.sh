#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <release-aar>" >&2
    exit 2
fi

aar_path=$1
if [[ ! -f "$aar_path" ]]; then
    echo "release AAR not found: $aar_path" >&2
    exit 1
fi

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javap" ]]; then
    javap_command="$JAVA_HOME/bin/javap"
else
    javap_command=$(command -v javap)
fi

temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT
unzip -p "$aar_path" classes.jar > "$temporary_directory/classes.jar"

seed_api=$(
    "$javap_command" \
        -classpath "$temporary_directory/classes.jar" \
        -p \
        -s \
        cash.w.sdk.WcashWalletSeed
)
secret_bytes_api=$(
    "$javap_command" \
        -classpath "$temporary_directory/classes.jar" \
        -p \
        -s \
        cash.w.sdk.WcashSecretSeedBytes
)

javap_verbose() {
    "$javap_command" -classpath "$temporary_directory/classes.jar" -p -v "$1"
}

flags_after_declaration() {
    local class_dump=$1
    local declaration=$2

    awk -v declaration="$declaration" '
        index($0, declaration) {
            found = 1
            next
        }
        found && /^[[:space:]]+flags: / {
            print
            exit
        }
        found && /^  [^[:space:]]/ {
            exit
        }
    ' <<< "$class_dump"
}

assert_flags() {
    local class_dump=$1
    local declaration=$2
    shift 2

    local flags
    flags=$(flags_after_declaration "$class_dump" "$declaration")
    if [[ -z "$flags" ]]; then
        echo "release AAR member is missing: $declaration" >&2
        exit 1
    fi

    local required_flag
    for required_flag in "$@"; do
        if [[ "$flags" != *"$required_flag"* ]]; then
            echo "release AAR member lost $required_flag: $declaration" >&2
            echo "$flags" >&2
            exit 1
        fi
    done
}

extract_public_members() {
    awk '
        /^  public / {
            declaration = $0
            if (getline <= 0 || $0 !~ /^    descriptor: /) {
                print "missing descriptor for " declaration > "/dev/stderr"
                exit 1
            }
            sub(/^    descriptor: /, "")
            print declaration " @ " $0
        }
    '
}

actual_seed_members=$(printf '%s\n' "$seed_api" | extract_public_members | LC_ALL=C sort)
expected_seed_members=$(cat <<'EOF' | LC_ALL=C sort
  public java.lang.String toString(); @ ()Ljava/lang/String;
  public static cash.w.sdk.WcashWalletSeed fromBip39SeedBytes(byte[]); @ ([B)Lcash/w/sdk/WcashWalletSeed;
  public void close(); @ ()V
EOF
)

if [[ "$actual_seed_members" != "$expected_seed_members" ]]; then
    echo "unsafe WcashWalletSeed public API in release AAR" >&2
    diff -u <(printf '%s\n' "$expected_seed_members") <(printf '%s\n' "$actual_seed_members") >&2 || true
    exit 1
fi

if ! grep -Fq 'private cash.w.sdk.WcashWalletSeed(cash.w.sdk.WcashSecretSeedBytes);' <<< "$seed_api"; then
    echo "WcashWalletSeed constructor is not private and semantic in release AAR" >&2
    exit 1
fi

if ! grep -Fq 'final class cash.w.sdk.WcashSecretSeedBytes {' <<< "$secret_bytes_api"; then
    echo "WcashSecretSeedBytes became publicly accessible in release AAR" >&2
    exit 1
fi

if ! grep -Fq 'private final byte[] secret;' <<< "$secret_bytes_api"; then
    echo "WcashSecretSeedBytes raw storage is not private final in release AAR" >&2
    exit 1
fi

actual_secret_bytes_members=$(printf '%s\n' "$secret_bytes_api" | extract_public_members | LC_ALL=C sort)
expected_secret_bytes_members='  public java.lang.String toString(); @ ()Ljava/lang/String;'
if [[ "$actual_secret_bytes_members" != "$expected_secret_bytes_members" ]]; then
    echo "unsafe WcashSecretSeedBytes public API in release AAR" >&2
    diff \
        -u \
        <(printf '%s\n' "$expected_secret_bytes_members") \
        <(printf '%s\n' "$actual_secret_bytes_members") >&2 || true
    exit 1
fi

# Kotlin emits public bridge constructors for private constructors. They are inaccessible to Java
# source only while ACC_SYNTHETIC survives R8, so assert that flag rather than trusting source
# visibility or Kotlin metadata.
account_index_api=$(javap_verbose cash.w.sdk.model.WcashAccountIndex)
ironwood_address_api=$(javap_verbose cash.w.sdk.model.WcashIronwoodAddress)
transparent_address_api=$(javap_verbose cash.w.sdk.model.WcashTransparentP2pkhAddress)
network_api=$(javap_verbose cash.w.sdk.model.WcashNetwork)
wallet_tool_api=$(javap_verbose cash.w.sdk.WcashWalletTool)

assert_flags \
    "$account_index_api" \
    'public cash.w.sdk.model.WcashAccountIndex(long, kotlin.jvm.internal.DefaultConstructorMarker);' \
    ACC_PUBLIC ACC_SYNTHETIC
assert_flags \
    "$ironwood_address_api" \
    'public cash.w.sdk.model.WcashIronwoodAddress(java.lang.String, cash.w.sdk.model.WcashNetwork, kotlin.jvm.internal.DefaultConstructorMarker);' \
    ACC_PUBLIC ACC_SYNTHETIC
assert_flags \
    "$transparent_address_api" \
    'public cash.w.sdk.model.WcashTransparentP2pkhAddress(java.lang.String, cash.w.sdk.model.WcashNetwork, kotlin.jvm.internal.DefaultConstructorMarker);' \
    ACC_PUBLIC ACC_SYNTHETIC
assert_flags \
    "$network_api" \
    'public cash.w.sdk.model.WcashNetwork(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, long, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, kotlin.jvm.internal.DefaultConstructorMarker);' \
    ACC_PUBLIC ACC_SYNTHETIC
assert_flags \
    "$wallet_tool_api" \
    'public cash.w.sdk.WcashWalletTool(cash.w.sdk.internal.jni.WcashNativeBackend, kotlin.jvm.internal.DefaultConstructorMarker);' \
    ACC_PUBLIC ACC_SYNTHETIC

# Internal JNI transport accessors compile as public JVM methods. Java callers cannot invoke them
# only while their synthetic flag remains intact after minification.
for jni_class in \
    cash.w.sdk.internal.jni.JniWcashAddress \
    cash.w.sdk.internal.jni.JniWcashTransparentP2pkhAddress; do
    jni_api=$(javap_verbose "$jni_class")
    # The dollar sign is part of Kotlin's literal JVM member name.
    # shellcheck disable=SC2016
    assert_flags "$jni_api" 'public final java.lang.String getEncoded$wcash_android_sdk_release();' \
        ACC_PUBLIC ACC_SYNTHETIC
    # shellcheck disable=SC2016
    assert_flags "$jni_api" 'public final int getNetworkCode$wcash_android_sdk_release();' \
        ACC_PUBLIC ACC_SYNTHETIC
done

native_bridge_api=$(javap_verbose cash.w.sdk.internal.jni.WcashNativeBridge)
# These dollar signs are part of Kotlin-generated JVM accessor names.
# shellcheck disable=SC2016
for accessor in \
    'public static final boolean access$validateSeedNative(byte[]);' \
    'public static final cash.w.sdk.internal.jni.JniWcashAddress access$deriveIronwoodAddressNative(byte[], int, long);' \
    'public static final cash.w.sdk.internal.jni.JniWcashAddress access$parseIronwoodAddressNative(java.lang.String);' \
    'public static final cash.w.sdk.internal.jni.JniWcashTransparentP2pkhAddress access$deriveTransparentCoinbaseAddressNative(byte[], int, long);' \
    'public static final cash.w.sdk.internal.jni.JniWcashTransparentP2pkhAddress access$parseTransparentP2pkhAddressNative(java.lang.String);' \
    'public static final java.lang.String access$networkIdentityNative(int);'; do
    assert_flags "$native_bridge_api" "$accessor" ACC_PUBLIC ACC_SYNTHETIC
done

echo "Verified release AAR API: semantic constructors, seed bytes, and JNI accessors are sealed."
