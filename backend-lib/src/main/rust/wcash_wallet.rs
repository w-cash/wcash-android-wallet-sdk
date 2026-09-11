use std::{ptr, thread};

use anyhow::anyhow;
use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JString},
    sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong, jstring},
};
use secrecy::SecretVec;
use wcash_wallet_core::{
    WcashNetwork, decode_recipient, derive_wallet_spending_key, encode_ironwood_receiver,
};
use zip32::AccountId;

use crate::utils::{catch_unwind, java_bytes_to_rust, java_string_to_rust};

const TESTNET_ID: jint = 1;
const REGTEST_ID: jint = 2;

fn unwrap_fixed_or<T>(
    env: &mut JNIEnv,
    result: thread::Result<anyhow::Result<T>>,
    error_value: T,
    message: &'static str,
) -> T {
    match result {
        Ok(Ok(value)) => value,
        Ok(Err(_)) | Err(_) => {
            if !env.exception_check().unwrap_or(false) {
                let _ = env.throw_new("java/lang/RuntimeException", message);
            }
            error_value
        }
    }
}

fn network_from_id(network_id: jint) -> anyhow::Result<WcashNetwork> {
    match network_id {
        TESTNET_ID => Ok(WcashNetwork::Testnet),
        REGTEST_ID => Ok(WcashNetwork::Regtest),
        _ => Err(anyhow!("unsupported Wcash network")),
    }
}

fn network_id(network: WcashNetwork) -> jint {
    match network {
        WcashNetwork::Testnet => TESTNET_ID,
        WcashNetwork::Regtest => REGTEST_ID,
    }
}

fn account_from_index(account_index: jlong) -> anyhow::Result<AccountId> {
    let account_index =
        u32::try_from(account_index).map_err(|_| anyhow!("invalid Wcash account index"))?;
    AccountId::try_from(account_index).map_err(|_| anyhow!("invalid Wcash account index"))
}

fn validate_seed(seed: Vec<u8>) -> bool {
    let seed = SecretVec::new(seed);
    let account = AccountId::try_from(0).expect("zero is a valid ZIP 32 account index");
    derive_wallet_spending_key(&seed, WcashNetwork::Testnet, account).is_ok()
}

fn derive_address(
    seed: Vec<u8>,
    network: WcashNetwork,
    account: AccountId,
) -> anyhow::Result<String> {
    let seed = SecretVec::new(seed);
    let spending_key = derive_wallet_spending_key(&seed, network, account)
        .map_err(|_| anyhow!("Wcash wallet key derivation failed"))?;
    let viewing_key = spending_key
        .to_full_viewing_key()
        .map_err(|_| anyhow!("Wcash wallet key derivation failed"))?;
    encode_ironwood_receiver(&viewing_key)
        .map_err(|_| anyhow!("Wcash Ironwood address derivation failed"))
}

fn parse_address_network(address: &str) -> anyhow::Result<WcashNetwork> {
    decode_recipient(address)
        .map(|recipient| recipient.network())
        .map_err(|_| anyhow!("invalid Wcash Ironwood address"))
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_z_ecc_android_sdk_internal_jni_RustWcashWallet_isValidSeedNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    seed: JByteArray<'local>,
) -> jboolean {
    let result = catch_unwind(&mut env, |env| {
        let seed = java_bytes_to_rust(env, &seed)
            .map_err(|_| anyhow!("could not read Wcash wallet seed"))?;
        Ok(if validate_seed(seed) {
            JNI_TRUE
        } else {
            JNI_FALSE
        })
    });
    unwrap_fixed_or(
        &mut env,
        result,
        JNI_FALSE,
        "Wcash wallet seed validation failed",
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_z_ecc_android_sdk_internal_jni_RustWcashWallet_deriveIronwoodAddressNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    seed: JByteArray<'local>,
    network_id: jint,
    account_index: jlong,
) -> jstring {
    let result = catch_unwind(&mut env, |env| {
        let network = network_from_id(network_id)?;
        let account = account_from_index(account_index)?;
        let seed = java_bytes_to_rust(env, &seed)
            .map_err(|_| anyhow!("could not read Wcash wallet seed"))?;
        let address = derive_address(seed, network, account)?;
        Ok(env.new_string(address)?.into_raw())
    });
    unwrap_fixed_or(
        &mut env,
        result,
        ptr::null_mut(),
        "Wcash Ironwood address derivation failed",
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_cash_z_ecc_android_sdk_internal_jni_RustWcashWallet_parseIronwoodAddressNetworkNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _: JClass<'local>,
    address: JString<'local>,
) -> jint {
    let result = catch_unwind(&mut env, |env| {
        let address = java_string_to_rust(env, &address)
            .map_err(|_| anyhow!("could not read Wcash address"))?;
        parse_address_network(&address).map(network_id)
    });
    unwrap_fixed_or(
        &mut env,
        result,
        0,
        "Wcash Ironwood address validation failed",
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    const TESTNET_ADDRESS: &str = "wutest17mvne4ygv9v8rkjf6yxnrveceejh8nutee8svp8swkgj7s7ac9ga36u2av8hgpc28cc42u474ypjq2jsdt64utcxtztm2jr6guvaryhh";

    fn vector_seed() -> Vec<u8> {
        (0u8..32).collect()
    }

    #[test]
    fn derives_the_frozen_testnet_vector() {
        let account = AccountId::try_from(0).unwrap();
        assert_eq!(
            derive_address(vector_seed(), WcashNetwork::Testnet, account).unwrap(),
            TESTNET_ADDRESS
        );
    }

    #[test]
    fn derivation_is_network_and_account_separated() {
        let account_zero = AccountId::try_from(0).unwrap();
        let account_one = AccountId::try_from(1).unwrap();
        let testnet = derive_address(vector_seed(), WcashNetwork::Testnet, account_zero).unwrap();
        let regtest = derive_address(vector_seed(), WcashNetwork::Regtest, account_zero).unwrap();
        let account_one =
            derive_address(vector_seed(), WcashNetwork::Testnet, account_one).unwrap();

        assert_ne!(testnet, regtest);
        assert_ne!(testnet, account_one);
        assert_eq!(
            parse_address_network(&testnet).unwrap(),
            WcashNetwork::Testnet
        );
        assert_eq!(
            parse_address_network(&regtest).unwrap(),
            WcashNetwork::Regtest
        );
    }

    #[test]
    fn validation_rejects_bad_seed_lengths_and_non_wcash_addresses() {
        assert!(validate_seed(vector_seed()));
        assert!(!validate_seed(vec![7; 31]));
        assert!(!validate_seed(vec![7; 253]));
        assert!(parse_address_network("utest1not-a-wcash-address").is_err());
        assert!(parse_address_network("WTNjqDPXEGEgKHS1YPtfEgdrqk6egFRULDz").is_err());
    }

    #[test]
    fn integer_selectors_fail_closed() {
        assert_eq!(network_from_id(TESTNET_ID).unwrap(), WcashNetwork::Testnet);
        assert_eq!(network_from_id(REGTEST_ID).unwrap(), WcashNetwork::Regtest);
        assert!(network_from_id(0).is_err());
        assert!(network_from_id(3).is_err());
        assert!(account_from_index(-1).is_err());
        assert!(account_from_index(i64::from(u32::MAX)).is_err());
    }
}
