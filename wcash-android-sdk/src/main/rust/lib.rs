use std::{
    panic::{AssertUnwindSafe, catch_unwind},
    ptr,
};

use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JObject, JString, JValue},
    sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong, jobject, jstring},
};
use secrecy::{ExposeSecret, SecretVec};
use wcash_wallet_core::{
    WcashAddress, WcashAddressKind, WcashNetwork,
    address::{
        B58_P2PKH_REGTEST, B58_P2PKH_TESTNET, B58_P2SH_REGTEST, B58_P2SH_TESTNET, HRP_TEX_REGTEST,
        HRP_TEX_TESTNET, HRP_UNIFIED_REGTEST, HRP_UNIFIED_TESTNET,
    },
    decode_recipient, derive_wallet_spending_key, encode_ironwood_receiver,
    encode_transparent_coinbase_receiver,
};
use zip32::AccountId;

const TESTNET_CODE: jint = 1;
const REGTEST_CODE: jint = 2;
const BIP39_SEED_LENGTH_BYTES: usize = 64;
const IRONWOOD_ADDRESS_CLASS: &str = "cash/w/sdk/internal/jni/JniWcashAddress";
const TRANSPARENT_P2PKH_ADDRESS_CLASS: &str =
    "cash/w/sdk/internal/jni/JniWcashTransparentP2pkhAddress";
const ADDRESS_CONSTRUCTOR: &str = "(Ljava/lang/String;I)V";
const IDENTITY_SEPARATOR: char = '\u{1f}';

#[derive(Debug, Eq, PartialEq)]
struct CanonicalAddress {
    encoded: String,
    network: WcashNetwork,
}

#[derive(Debug, Eq, PartialEq)]
struct CanonicalTransparentP2pkhAddress {
    encoded: String,
    network: WcashNetwork,
}

fn network_from_code(code: jint) -> Result<WcashNetwork, ()> {
    match code {
        TESTNET_CODE => Ok(WcashNetwork::Testnet),
        REGTEST_CODE => Ok(WcashNetwork::Regtest),
        _ => Err(()),
    }
}

const fn network_code(network: WcashNetwork) -> jint {
    match network {
        WcashNetwork::Testnet => TESTNET_CODE,
        WcashNetwork::Regtest => REGTEST_CODE,
    }
}

fn account_from_index(index: jlong) -> Result<AccountId, ()> {
    let index = u32::try_from(index).map_err(|_| ())?;
    AccountId::try_from(index).map_err(|_| ())
}

fn has_bip39_seed_length(seed: &SecretVec<u8>) -> bool {
    seed.expose_secret().len() == BIP39_SEED_LENGTH_BYTES
}

fn validate_seed(seed: SecretVec<u8>) -> bool {
    if !has_bip39_seed_length(&seed) {
        return false;
    }
    let account = AccountId::try_from(0).expect("zero is a valid ZIP 32 account index");
    derive_wallet_spending_key(&seed, WcashNetwork::Testnet, account).is_ok()
}

fn derive_ironwood_address(
    seed: SecretVec<u8>,
    network: WcashNetwork,
    account: AccountId,
) -> Result<CanonicalAddress, ()> {
    if !has_bip39_seed_length(&seed) {
        return Err(());
    }

    let spending_key = derive_wallet_spending_key(&seed, network, account).map_err(|_| ())?;
    let viewing_key = spending_key.to_full_viewing_key().map_err(|_| ())?;
    let encoded = encode_ironwood_receiver(&viewing_key).map_err(|_| ())?;
    parse_ironwood_address(&encoded)
}

fn parse_ironwood_address(encoded: &str) -> Result<CanonicalAddress, ()> {
    let parsed = WcashAddress::try_from_encoded(encoded)
        .or_else(|_| {
            if is_uniform_uppercase(encoded) {
                WcashAddress::try_from_encoded(&encoded.to_ascii_lowercase())
            } else {
                Err(wcash_wallet_core::WcashAddressParseError::NotWcash)
            }
        })
        .map_err(|_| ())?;
    let canonical = parsed.encode();
    let recipient = decode_recipient(&canonical).map_err(|_| ())?;
    if parsed.kind() != WcashAddressKind::Unified
        || recipient.network() != parsed.network()
        || !recipient.has_ironwood_receiver()
        || recipient.has_transparent_receiver()
    {
        return Err(());
    }

    Ok(CanonicalAddress {
        encoded: canonical,
        network: parsed.network(),
    })
}

fn derive_transparent_coinbase_address(
    seed: SecretVec<u8>,
    network: WcashNetwork,
    account: AccountId,
) -> Result<CanonicalTransparentP2pkhAddress, ()> {
    if !has_bip39_seed_length(&seed) {
        return Err(());
    }

    let spending_key = derive_wallet_spending_key(&seed, network, account).map_err(|_| ())?;
    let viewing_key = spending_key.to_full_viewing_key().map_err(|_| ())?;
    let encoded = encode_transparent_coinbase_receiver(&viewing_key).map_err(|_| ())?;
    parse_transparent_p2pkh_address(&encoded)
}

fn parse_transparent_p2pkh_address(encoded: &str) -> Result<CanonicalTransparentP2pkhAddress, ()> {
    let parsed = WcashAddress::try_from_encoded(encoded).map_err(|_| ())?;
    if parsed.kind() != WcashAddressKind::P2pkh || parsed.encode() != encoded {
        return Err(());
    }

    Ok(CanonicalTransparentP2pkhAddress {
        encoded: parsed.encode(),
        network: parsed.network(),
    })
}

fn is_uniform_uppercase(encoded: &str) -> bool {
    let has_uppercase = encoded.bytes().any(|byte| byte.is_ascii_uppercase());
    let has_lowercase = encoded.bytes().any(|byte| byte.is_ascii_lowercase());
    has_uppercase && !has_lowercase
}

fn network_identity(network: WcashNetwork) -> String {
    let (unified_hrp, tex_hrp, p2pkh, p2sh) = match network {
        WcashNetwork::Testnet => (
            HRP_UNIFIED_TESTNET,
            HRP_TEX_TESTNET,
            B58_P2PKH_TESTNET,
            B58_P2SH_TESTNET,
        ),
        WcashNetwork::Regtest => (
            HRP_UNIFIED_REGTEST,
            HRP_TEX_REGTEST,
            B58_P2PKH_REGTEST,
            B58_P2SH_REGTEST,
        ),
    };
    [
        network.node_network_name().to_owned(),
        network.compact_server_chain_name().to_owned(),
        network.storage_namespace().to_owned(),
        network.currency_ticker().to_owned(),
        network.genesis_hash_display().to_owned(),
        format!("{:08x}", u32::from(network.branch_id())),
        u32::from(network.ironwood_activation_height()).to_string(),
        unified_hrp.to_owned(),
        tex_hrp.to_owned(),
        format!("{:02x}{:02x}", p2pkh[0], p2pkh[1]),
        format!("{:02x}{:02x}", p2sh[0], p2sh[1]),
    ]
    .join(&IDENTITY_SEPARATOR.to_string())
}

fn read_seed(env: &JNIEnv, seed: &JByteArray) -> Result<SecretVec<u8>, ()> {
    env.convert_byte_array(seed)
        .map(SecretVec::new)
        .map_err(|_| ())
}

fn read_string(env: &mut JNIEnv, value: &JString) -> Result<String, ()> {
    env.get_string(value).map(String::from).map_err(|_| ())
}

fn to_java_address(
    env: &mut JNIEnv,
    encoded: String,
    network: WcashNetwork,
    class_name: &str,
) -> Result<jobject, ()> {
    let encoded = env.new_string(encoded).map_err(|_| ())?;
    let encoded_object = JObject::from(encoded);
    env.new_object(
        class_name,
        ADDRESS_CONSTRUCTOR,
        &[
            JValue::Object(&encoded_object),
            JValue::Int(network_code(network)),
        ],
    )
    .map(JObject::into_raw)
    .map_err(|_| ())
}

fn to_java_ironwood_address(env: &mut JNIEnv, address: CanonicalAddress) -> Result<jobject, ()> {
    to_java_address(
        env,
        address.encoded,
        address.network,
        IRONWOOD_ADDRESS_CLASS,
    )
}

fn to_java_transparent_p2pkh_address(
    env: &mut JNIEnv,
    address: CanonicalTransparentP2pkhAddress,
) -> Result<jobject, ()> {
    to_java_address(
        env,
        address.encoded,
        address.network,
        TRANSPARENT_P2PKH_ADDRESS_CLASS,
    )
}

fn finish<T>(
    env: &mut JNIEnv,
    result: std::thread::Result<Result<T, ()>>,
    fallback: T,
    exception_class: &'static str,
    fixed_message: &'static str,
) -> T {
    match result {
        Ok(Ok(value)) => value,
        Ok(Err(())) | Err(_) => {
            if !env.exception_check().unwrap_or(false) {
                let _ = env.throw_new(exception_class, fixed_message);
            }
            fallback
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_w_sdk_internal_jni_WcashNativeBridge_validateSeedNative<'local>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    seed: JByteArray<'local>,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| {
        read_seed(&env, &seed).map(|seed| {
            if validate_seed(seed) {
                JNI_TRUE
            } else {
                JNI_FALSE
            }
        })
    }));
    finish(
        &mut env,
        result,
        JNI_FALSE,
        "java/lang/RuntimeException",
        "Wcash wallet seed validation failed",
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_w_sdk_internal_jni_WcashNativeBridge_deriveIronwoodAddressNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    seed: JByteArray<'local>,
    network_code_value: jint,
    account_index: jlong,
) -> jobject {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let seed = read_seed(&env, &seed)?;
        let network = network_from_code(network_code_value)?;
        let account = account_from_index(account_index)?;
        let address = derive_ironwood_address(seed, network, account)?;
        to_java_ironwood_address(&mut env, address)
    }));
    finish(
        &mut env,
        result,
        ptr::null_mut(),
        "java/lang/RuntimeException",
        "Wcash Ironwood address derivation failed",
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_w_sdk_internal_jni_WcashNativeBridge_parseIronwoodAddressNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    encoded: JString<'local>,
) -> jobject {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let encoded = read_string(&mut env, &encoded)?;
        let address = parse_ironwood_address(&encoded)?;
        to_java_ironwood_address(&mut env, address)
    }));
    finish(
        &mut env,
        result,
        ptr::null_mut(),
        "java/lang/IllegalArgumentException",
        "Invalid Wcash Ironwood address",
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_w_sdk_internal_jni_WcashNativeBridge_deriveTransparentCoinbaseAddressNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    seed: JByteArray<'local>,
    network_code_value: jint,
    account_index: jlong,
) -> jobject {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let seed = read_seed(&env, &seed)?;
        let network = network_from_code(network_code_value)?;
        let account = account_from_index(account_index)?;
        let address = derive_transparent_coinbase_address(seed, network, account)?;
        to_java_transparent_p2pkh_address(&mut env, address)
    }));
    finish(
        &mut env,
        result,
        ptr::null_mut(),
        "java/lang/RuntimeException",
        "Wcash transparent coinbase address derivation failed",
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_w_sdk_internal_jni_WcashNativeBridge_parseTransparentP2pkhAddressNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    encoded: JString<'local>,
) -> jobject {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let encoded = read_string(&mut env, &encoded)?;
        let address = parse_transparent_p2pkh_address(&encoded)?;
        to_java_transparent_p2pkh_address(&mut env, address)
    }));
    finish(
        &mut env,
        result,
        ptr::null_mut(),
        "java/lang/IllegalArgumentException",
        "Invalid Wcash transparent P2PKH address",
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_w_sdk_internal_jni_WcashNativeBridge_networkIdentityNative<'local>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    network_code_value: jint,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let network = network_from_code(network_code_value)?;
        env.new_string(network_identity(network))
            .map(JString::into_raw)
            .map_err(|_| ())
    }));
    finish(
        &mut env,
        result,
        ptr::null_mut(),
        "java/lang/RuntimeException",
        "Wcash network identity attestation failed",
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    const TESTNET_ADDRESS: &str = concat!(
        "wutest17mvne4ygv9v8rkjf6yxnrveceejh8nutee8svp8swkgj7s7ac9ga36u2av8",
        "hgpc28cc42u474ypjq2jsdt64utcxtztm2jr6guvaryhh"
    );
    const TESTNET_TRANSPARENT_P2PKH: &str = "WTNjqDPXEGEgKHS1YPtfEgdrqk6egFRULDz";
    const BIP39_TESTNET_ADDRESS: &str = concat!(
        "wutest18rmpm4xcm2d54xg5mg00lac9pg4txaladyp6pacqhm355n5scpn5gja6hy43",
        "uqassvr63g6xuephu8r0qju92778lg4v5nkxfu7j3la6"
    );
    const BIP39_TESTNET_TRANSPARENT_P2PKH: &str = "WTMMWgVvepdG58zdNjePbtyoh4aSwb4kP3E";
    const BIP39_REGTEST_TRANSPARENT_P2PKH: &str = "WRSJjaJAZ75QkqbJoa244F21QmkPHEqhYu8";
    const TESTNET_P2SH: &str = "WUJmKiHCs7MSy6FGzyBvrwdExdsU75uiFgz";
    const TESTNET_TEX: &str = "wtextest1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqf4k4nr";
    const ZCASH_TESTNET_P2PKH: &str = "tmQvJu83NwioWyV852dPCzNXhzdtXVJwMAJ";

    fn vector_seed() -> SecretVec<u8> {
        SecretVec::new((0u8..32).collect())
    }

    fn bip39_seed() -> SecretVec<u8> {
        SecretVec::new(vec![
            0x40, 0x8b, 0x28, 0x5c, 0x12, 0x38, 0x36, 0x00, 0x4f, 0x4b, 0x88, 0x42, 0xc8, 0x93,
            0x24, 0xc1, 0xf0, 0x13, 0x82, 0x45, 0x0c, 0x0d, 0x43, 0x9a, 0xf3, 0x45, 0xba, 0x7f,
            0xc4, 0x9a, 0xcf, 0x70, 0x54, 0x89, 0xc6, 0xfc, 0x77, 0xdb, 0xd4, 0xe3, 0xdc, 0x1d,
            0xd8, 0xcc, 0x6b, 0xc9, 0xf0, 0x43, 0xdb, 0x8a, 0xda, 0x1e, 0x24, 0x3c, 0x4a, 0x0e,
            0xaf, 0xb2, 0x90, 0xd3, 0x99, 0x48, 0x08, 0x40,
        ])
    }

    fn account(index: u32) -> AccountId {
        AccountId::try_from(index).expect("test account index is valid")
    }

    #[test]
    fn non_bip39_core_seed_lengths_are_rejected_by_every_derivation_path() {
        for length in [0, 16, 32, 63, 65, 252, 253] {
            assert!(
                derive_ironwood_address(
                    SecretVec::new(vec![7; length]),
                    WcashNetwork::Testnet,
                    account(0),
                )
                .is_err()
            );
            assert!(
                derive_transparent_coinbase_address(
                    SecretVec::new(vec![7; length]),
                    WcashNetwork::Testnet,
                    account(0),
                )
                .is_err()
            );
        }
    }

    #[test]
    fn canonical_bip39_seed_vector_is_derived() {
        assert!(validate_seed(bip39_seed()));
        assert_eq!(
            derive_ironwood_address(bip39_seed(), WcashNetwork::Testnet, account(0)),
            Ok(CanonicalAddress {
                encoded: BIP39_TESTNET_ADDRESS.to_owned(),
                network: WcashNetwork::Testnet,
            })
        );
        assert_eq!(
            derive_transparent_coinbase_address(bip39_seed(), WcashNetwork::Testnet, account(0),),
            Ok(CanonicalTransparentP2pkhAddress {
                encoded: BIP39_TESTNET_TRANSPARENT_P2PKH.to_owned(),
                network: WcashNetwork::Testnet,
            })
        );
    }

    #[test]
    fn uppercase_input_is_returned_in_canonical_lowercase() {
        assert_eq!(
            parse_ironwood_address(&TESTNET_ADDRESS.to_ascii_uppercase()),
            Ok(CanonicalAddress {
                encoded: TESTNET_ADDRESS.to_owned(),
                network: WcashNetwork::Testnet,
            })
        );
    }

    #[test]
    fn derivation_is_separated_by_network_and_account() {
        let testnet =
            derive_ironwood_address(bip39_seed(), WcashNetwork::Testnet, account(0)).unwrap();
        let regtest =
            derive_ironwood_address(bip39_seed(), WcashNetwork::Regtest, account(0)).unwrap();
        let account_one =
            derive_ironwood_address(bip39_seed(), WcashNetwork::Testnet, account(1)).unwrap();

        assert_ne!(testnet, regtest);
        assert_ne!(testnet, account_one);
    }

    #[test]
    fn frozen_transparent_coinbase_vectors_are_derived() {
        assert_eq!(
            derive_transparent_coinbase_address(bip39_seed(), WcashNetwork::Testnet, account(0),),
            Ok(CanonicalTransparentP2pkhAddress {
                encoded: BIP39_TESTNET_TRANSPARENT_P2PKH.to_owned(),
                network: WcashNetwork::Testnet,
            })
        );
        assert_eq!(
            derive_transparent_coinbase_address(bip39_seed(), WcashNetwork::Regtest, account(0),),
            Ok(CanonicalTransparentP2pkhAddress {
                encoded: BIP39_REGTEST_TRANSPARENT_P2PKH.to_owned(),
                network: WcashNetwork::Regtest,
            })
        );
    }

    #[test]
    fn transparent_parser_is_strictly_p2pkh_and_byte_canonical() {
        assert_eq!(
            parse_transparent_p2pkh_address(TESTNET_TRANSPARENT_P2PKH),
            Ok(CanonicalTransparentP2pkhAddress {
                encoded: TESTNET_TRANSPARENT_P2PKH.to_owned(),
                network: WcashNetwork::Testnet,
            })
        );

        for rejected in [
            TESTNET_ADDRESS,
            TESTNET_P2SH,
            TESTNET_TEX,
            ZCASH_TESTNET_P2PKH,
            &format!(" {TESTNET_TRANSPARENT_P2PKH}"),
            &format!("{TESTNET_TRANSPARENT_P2PKH} "),
            &TESTNET_TRANSPARENT_P2PKH.to_ascii_lowercase(),
            &TESTNET_TRANSPARENT_P2PKH.to_ascii_uppercase(),
            "WTNjqDPXEGEgKHS1YPtfEgdrqk6egFRULDy",
        ] {
            assert!(parse_transparent_p2pkh_address(rejected).is_err());
        }
    }

    #[test]
    fn testnet_identity_serialization_matches_the_frozen_profile() {
        assert_eq!(
            network_identity(WcashNetwork::Testnet),
            [
                "WcashTestnet",
                "test",
                "wcashtestnet-v5",
                "TWC",
                "0271b5b0a10b2838f43cccdec9ca2f72aa72a7c103830082bac8f82f47f0593a",
                "b3cfd27e",
                "1",
                "wutest",
                "wtextest",
                "1095",
                "1098",
            ]
            .join(&IDENTITY_SEPARATOR.to_string())
        );
    }

    #[test]
    fn invalid_inputs_fail_closed() {
        assert!(!validate_seed(vector_seed()));
        assert!(!validate_seed(SecretVec::new(vec![7; 63])));
        assert!(validate_seed(bip39_seed()));
        assert!(!validate_seed(SecretVec::new(vec![7; 65])));
        assert!(!validate_seed(SecretVec::new(vec![7; 31])));
        assert!(!validate_seed(SecretVec::new(vec![7; 253])));
        assert!(network_from_code(0).is_err());
        assert!(network_from_code(3).is_err());
        assert!(account_from_index(-1).is_err());
        assert!(account_from_index(i64::from(u32::MAX)).is_err());
        assert!(parse_ironwood_address("utest1not-a-wcash-address").is_err());
        assert!(parse_ironwood_address(&format!("W{}", &TESTNET_ADDRESS[1..])).is_err());
    }
}
