package com.ordo.notification.service;

import static com.ordo.notification.service.WebPushCrypto.decodeBase64Url;
import static com.ordo.notification.service.WebPushCrypto.encodeBase64Url;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** RFC 8291 5절·부록 A 의 예시 값 그대로 */
class WebPushCryptoTest {

    static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    static final String MESSAGE = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void matchesRfc8291TestVector() throws Exception {
        KeyPair asKeyPair = new KeyPair(WebPushCrypto.toPublicKey(decodeBase64Url(AS_PUBLIC)),
                WebPushCrypto.toPrivateKey(decodeBase64Url(AS_PRIVATE)));

        byte[] message = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.US_ASCII),
                decodeBase64Url(UA_PUBLIC), decodeBase64Url(AUTH), asKeyPair, decodeBase64Url(SALT));

        assertThat(encodeBase64Url(message)).isEqualTo(MESSAGE);
    }

    /** 임시 키·salt 를 무작위로 만든 경우: 받는 쪽(브라우저) 개인키로 RFC 절차를 거꾸로 하면 원문이 나온다 */
    @Test
    void randomEncryptionDecryptsWithReceiverKey() throws Exception {
        byte[] uaPublic = decodeBase64Url(UA_PUBLIC);
        byte[] auth = decodeBase64Url(AUTH);
        byte[] plaintext = "{\"title\":\"디자인씽킹 과제 제출\"}".getBytes(StandardCharsets.UTF_8);

        byte[] message = WebPushCrypto.encrypt(plaintext, uaPublic, auth);
        byte[] again = WebPushCrypto.encrypt(plaintext, uaPublic, auth);

        assertThat(message).isNotEqualTo(again);  // 매번 다른 salt·임시 키
        assertThat(decrypt(message, uaPublic, decodeBase64Url(UA_PRIVATE), auth)).isEqualTo(plaintext);
    }

    @Test
    void rejectsMalformedKeys() {
        assertThatThrownBy(() -> WebPushCrypto.toPublicKey(new byte[64])).isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> WebPushCrypto.toPublicKey(new byte[65])).isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> WebPushCrypto.toPrivateKey(new byte[31])).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void publicKeyRoundTrips() throws Exception {
        ECPublicKey key = WebPushCrypto.toPublicKey(decodeBase64Url(AS_PUBLIC));

        assertThat(encodeBase64Url(WebPushCrypto.encodePublicKey(key))).isEqualTo(AS_PUBLIC);
    }

    // RFC 8291 3절의 받는 쪽 절차 (테스트 전용)
    private static byte[] decrypt(byte[] message, byte[] uaPublic, byte[] uaPrivate, byte[] auth) throws Exception {
        byte[] salt = Arrays.copyOfRange(message, 0, 16);
        int idLength = message[20];
        byte[] asPublic = Arrays.copyOfRange(message, 21, 21 + idLength);
        byte[] ciphertext = Arrays.copyOfRange(message, 21 + idLength, message.length);

        ECPrivateKey privateKey = WebPushCrypto.toPrivateKey(uaPrivate);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(WebPushCrypto.toPublicKey(asPublic), true);
        byte[] prkKey = hmac(auth, agreement.generateSecret());
        byte[] keyInfo = join("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1});
        byte[] prk = hmac(salt, hmac(prkKey, keyInfo));
        byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] join(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(p -> p.length).sum();
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
