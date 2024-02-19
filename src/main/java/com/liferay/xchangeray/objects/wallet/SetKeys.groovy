package com.liferay.xchangeray.objects.wallet

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

import com.liferay.object.service.ObjectEntryLocalServiceUtil
import com.liferay.portal.kernel.service.ServiceContext

try {
	
	def wallet = new Wallet()
	def userId = Long.valueOf(creator)
	def entryValues = [privateKey: wallet.privateKey, publicKey: wallet.publicKey]
	ObjectEntryLocalServiceUtil.updateObjectEntry(userId, id, entryValues, new ServiceContext())
	
} catch (Exception e) {
	println e.message
	e.printStackTrace()
}

class Wallet {

	def KeyPair keyPair

	Wallet() {
		this.keyPair = this.generateKeyPair()
	}

	String getPrivateKey() {
		return Base64.encoder.encodeToString(keyPair.getPrivate().getEncoded())
	}

	String getPublicKey() {
		return Base64.encoder.encodeToString(keyPair.getPublic().getEncoded())
	}

	private KeyPair generateKeyPair() {
		def keyGen = KeyPairGenerator.getInstance("EC")
		def ecSpec = new ECGenParameterSpec("secp256r1")
		keyGen.initialize(ecSpec, new SecureRandom())
		return keyGen.generateKeyPair()
	}
}