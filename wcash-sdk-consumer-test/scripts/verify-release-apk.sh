#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <release-apk> <r8-mapping>" >&2
    exit 2
fi

apk_path=$1
mapping_path=$2
if [[ ! -f "$apk_path" ]]; then
    echo "release consumer APK not found: $apk_path" >&2
    exit 1
fi
if [[ ! -f "$mapping_path" ]]; then
    echo "release consumer R8 mapping not found: $mapping_path" >&2
    exit 1
fi

expected_native_libraries=$(cat <<'EOF' | LC_ALL=C sort
lib/arm64-v8a/libwcashwalletsdk.so
lib/armeabi-v7a/libwcashwalletsdk.so
lib/x86/libwcashwalletsdk.so
lib/x86_64/libwcashwalletsdk.so
EOF
)
actual_native_libraries=$(
    unzip -Z1 "$apk_path" |
        grep -E '^lib/[^/]+/[^/]+\.so$' |
        LC_ALL=C sort
)
if [[ "$actual_native_libraries" != "$expected_native_libraries" ]]; then
    echo "minified consumer APK has an unexpected native-library set" >&2
    diff \
        -u \
        <(printf '%s\n' "$expected_native_libraries") \
        <(printf '%s\n' "$actual_native_libraries") >&2 || true
    exit 1
fi

# These classes must survive the consuming application's R8 pass under their API names. The
# runtime instrumentation test then invokes them across the actual packaged JNI library.
for class_name in \
    cash.w.sdk.WcashSecretSeedBytes \
    cash.w.sdk.WcashWalletSeed \
    cash.w.sdk.WcashWalletTool \
    cash.w.sdk.model.WcashAccountIndex \
    cash.w.sdk.model.WcashIronwoodAddress \
    cash.w.sdk.model.WcashNetwork \
    cash.w.sdk.model.WcashNetwork\$Regtest \
    cash.w.sdk.model.WcashNetwork\$Testnet \
    cash.w.sdk.model.WcashTransparentP2pkhAddress; do
    if ! grep -Fqx "$class_name -> $class_name:" "$mapping_path"; then
        echo "consumer R8 renamed or removed protected Wcash class: $class_name" >&2
        exit 1
    fi
done

temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT
dex_files=$(unzip -Z1 "$apk_path" | grep -E '^classes[0-9]*\.dex$')
if [[ -z "$dex_files" ]]; then
    echo "minified consumer APK contains no DEX payload" >&2
    exit 1
fi

sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$sdk_root" || ! -d "$sdk_root/build-tools" ]]; then
    echo "ANDROID_SDK_ROOT or ANDROID_HOME must identify an Android SDK" >&2
    exit 1
fi
dexdump_command=$(
    find "$sdk_root/build-tools" -type f -name dexdump -perm -u+x |
        LC_ALL=C sort |
        tail -n 1
)
if [[ -z "$dexdump_command" ]]; then
    echo "Android build-tools dexdump was not found" >&2
    exit 1
fi

: > "$temporary_directory/classes.dex.all"
: > "$temporary_directory/dexdump.txt"
while IFS= read -r dex_file; do
    extracted_dex="$temporary_directory/${dex_file//\//_}"
    unzip -p "$apk_path" "$dex_file" > "$extracted_dex"
    cat "$extracted_dex" >> "$temporary_directory/classes.dex.all"
    "$dexdump_command" "$extracted_dex" >> "$temporary_directory/dexdump.txt"
done <<< "$dex_files"

for descriptor in \
    'Lcash/w/sdk/WcashSecretSeedBytes;' \
    'Lcash/w/sdk/WcashWalletSeed;' \
    'Lcash/w/sdk/WcashWalletTool;' \
    'Lcash/w/sdk/model/WcashAccountIndex;' \
    'Lcash/w/sdk/model/WcashIronwoodAddress;' \
    'Lcash/w/sdk/model/WcashNetwork;' \
    'Lcash/w/sdk/model/WcashTransparentP2pkhAddress;'; do
    if ! grep -aFq "$descriptor" "$temporary_directory/classes.dex.all"; then
        echo "protected Wcash class is absent from consumer DEX: $descriptor" >&2
        exit 1
    fi
done

extract_class() {
    local descriptor=$1
    awk -v descriptor="'$descriptor'" '
        /^Class #[0-9]+/ && capturing {
            exit
        }
        /Class descriptor/ && index($0, descriptor) {
            capturing = 1
        }
        capturing {
            print
        }
    ' "$temporary_directory/dexdump.txt"
}

secret_holder=$(extract_class 'Lcash/w/sdk/WcashSecretSeedBytes;')
seed_class=$(extract_class 'Lcash/w/sdk/WcashWalletSeed;')
if [[ -z "$secret_holder" || -z "$seed_class" ]]; then
    echo "could not inspect protected seed classes in final DEX" >&2
    exit 1
fi

secret_holder_flags=$(awk '/Access flags/{ print; exit }' <<< "$secret_holder")
if [[ "$secret_holder_flags" != *"(FINAL)"* || "$secret_holder_flags" == *"PUBLIC"* ]]; then
    echo "WcashSecretSeedBytes must remain non-public and final in final DEX" >&2
    echo "$secret_holder_flags" >&2
    exit 1
fi

if ! awk '
    /Instance fields/ { in_fields = 1; next }
    /Direct methods/ { in_fields = 0 }
    in_fields && /name[[:space:]]*: '\''secret'\''/ { found_name = 1; next }
    found_name && /type[[:space:]]*: '\''\[B'\''/ { found_type = 1; next }
    found_type && /access[[:space:]]*: .*[[:space:]]\(PRIVATE FINAL\)/ { found_access = 1 }
    END { exit !(found_name && found_type && found_access) }
' <<< "$secret_holder"; then
    echo "WcashSecretSeedBytes.secret must remain a private final byte array in final DEX" >&2
    exit 1
fi

if ! awk '
    /Direct methods/ { in_methods = 1; next }
    /Virtual methods/ { in_methods = 0 }
    in_methods && /name[[:space:]]*: '\''<init>'\''/ { found_name = 1; next }
    found_name && /type[[:space:]]*: '\''\(Lcash\/w\/sdk\/WcashSecretSeedBytes;\)V'\''/ {
        found_type = 1
        next
    }
    found_type && /access[[:space:]]*: .*[[:space:]]\(PRIVATE CONSTRUCTOR\)/ { found_access = 1 }
    END { exit !(found_name && found_type && found_access) }
' <<< "$seed_class"; then
    echo "WcashWalletSeed must retain its private semantic constructor in final DEX" >&2
    exit 1
fi

actual_public_seed_methods=$(
    awk '
        /Direct methods/ || /Virtual methods/ {
            in_methods = 1
            next
        }
        in_methods && /name[[:space:]]*:/ {
            split($0, values, "'\''")
            method_name = values[2]
            next
        }
        in_methods && /type[[:space:]]*:/ {
            split($0, values, "'\''")
            method_type = values[2]
            next
        }
        in_methods && /access[[:space:]]*:/ {
            if ($0 ~ /\(.*PUBLIC/) {
                print method_name " " method_type
            }
            method_name = ""
            method_type = ""
        }
    ' <<< "$seed_class" | LC_ALL=C sort
)
expected_public_seed_methods=$(cat <<'EOF' | LC_ALL=C sort
close ()V
fromBip39SeedBytes ([B)Lcash/w/sdk/WcashWalletSeed;
toString ()Ljava/lang/String;
EOF
)
if [[ "$actual_public_seed_methods" != "$expected_public_seed_methods" ]]; then
    echo "WcashWalletSeed final DEX exposes an unexpected public method" >&2
    diff \
        -u \
        <(printf '%s\n' "$expected_public_seed_methods") \
        <(printf '%s\n' "$actual_public_seed_methods") >&2 || true
    exit 1
fi

echo "Verified minified consumer APK: seed visibility and four isolated JNI libraries survived R8."
