package com.liferay.poc.blockchain.example

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

import groovy.json.JsonOutput

class Transaction {
	String fromAddress
	String toAddress
	BigDecimal amount
	byte[] signature

	Transaction(String fromAddress, String toAddress, BigDecimal amount) {
		this.fromAddress = fromAddress
		this.toAddress = toAddress
		this.amount = amount
	}

	void signTransaction(Wallet wallet) {
		if (wallet.getAddress() != fromAddress) {
			throw new RuntimeException("You cannot sign transactions for other wallets!")
		}
		String transactionData = getTransactionData()
		signature = wallet.sign(transactionData)
	}

	boolean isSignatureValid() {
		if (fromAddress == null) {
			return true
		}

		if (signature == null || signature.length == 0) {
			throw new RuntimeException("No signature found in this transaction")
		}

		def keyFactory = KeyFactory.getInstance("EC")
		def publicKeySpec = new X509EncodedKeySpec(Base64.decoder.decode(fromAddress))
		def publicKey = keyFactory.generatePublic(publicKeySpec)

		def signatureInstance = Signature.getInstance("SHA256withECDSA")
		signatureInstance.initVerify(publicKey)
		signatureInstance.update(getTransactionData().bytes)
		return signatureInstance.verify(signature)
	}


	String getTransactionData() {
		return "${fromAddress}${toAddress}${amount}"
	}

	String toString() {
		return JsonOutput.toJson(this)
	}
}

