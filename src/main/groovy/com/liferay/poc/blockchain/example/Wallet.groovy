package com.liferay.poc.blockchain.example

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class Wallet {
    PrivateKey privateKey
    PublicKey publicKey

    Wallet() {
        KeyPair keyPair = generateKeyPair()
        this.privateKey = keyPair.private
        this.publicKey = keyPair.public
    }

    KeyPair generateKeyPair() {
        def keyGen = KeyPairGenerator.getInstance("EC")
        def ecSpec = new ECGenParameterSpec("secp256r1")
        keyGen.initialize(ecSpec, new SecureRandom())
        return keyGen.generateKeyPair()
    }

    String getAddress() {
        return Base64.encoder.encodeToString(publicKey.getEncoded())
    }

    String hashPublicKey(PublicKey publicKey) {
        def messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(publicKey.getEncoded())
        return messageDigest.digest().encodeHex().toString()
    }

    byte[] sign(String data) {
        def signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data.bytes)
        return signature.sign()
    }

    static boolean verify(String data, byte[] signatureBytes, PublicKey publicKey) {
        def signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(publicKey)
        signature.update(data.bytes)
        return signature.verify(signatureBytes)
    }

    String getPrivateKeyAsString() {
        return Base64.encoder.encodeToString(privateKey.getEncoded())
    }

    String getPublicKeyAsString() {
        return Base64.encoder.encodeToString(publicKey.getEncoded())
    }

    PrivateKey stringToPrivateKey(String privateKeyAsString) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privateKeyBytes = Base64.decoder.decode(privateKeyAsString)
        KeyFactory kf = KeyFactory.getInstance("EC")
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes)
        return kf.generatePrivate(keySpec)
    }

    PublicKey stringToPublicKey(String publicKeyAsString) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] publicKeyBytes = Base64.decoder.decode(publicKeyAsString)
        KeyFactory kf = KeyFactory.getInstance("EC")
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes)
        return kf.generatePublic(keySpec)
    }
}
