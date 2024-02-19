package com.liferay.xchangeray.objects.transaction

import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec

import javax.json.Json
import javax.json.JsonObject

import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils

import com.liferay.object.service.ObjectEntryLocalServiceUtil
import com.liferay.portal.kernel.service.ServiceContext


// Create Wallet and Transaction objects
Wallet wallet = new Wallet(r_wallet_c_walletId)
Transaction transaction = new Transaction(toAddress, amount, wallet)

// Sign the transaction
transaction.sign()

// Update the object entry with the new signature
def userId = Long.valueOf(creator)
ObjectEntryLocalServiceUtil.updateObjectEntry(userId, id, [signature: transaction.signature], new ServiceContext())

// send the transaction to RayCoin
transaction.sendToBlockchain("http://raycoin.local:8080/o/c/transactions/")

class Transaction {

	def toAddress
	def amount
	Wallet wallet
	def transactionData
	def signature

	Transaction(toAddress, amount, wallet) {
		this.toAddress = toAddress
		this.amount = amount
		this.wallet = wallet
		this.transactionData = "${this.wallet.publicKey}${toAddress}${amount}"
	}

	String getSignature() {
		return signature
	}

	void sign() {
		PrivateKey privateKey = stringToPrivateKey(wallet.privateKey)
		byte[] signedMessage = sign(transactionData, privateKey)
		signature = Base64.getEncoder().encodeToString(signedMessage)
	}

	byte[] sign(String data, PrivateKey privateKey) {
		def signatureInstance = Signature.getInstance("SHA256withECDSA")
		signatureInstance.initSign(privateKey)
		signatureInstance.update(data.bytes)
		return signatureInstance.sign()
	}

	PrivateKey stringToPrivateKey(String privateKeyAsString) throws NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] privateKeyBytes = Base64.decoder.decode(privateKeyAsString)
		KeyFactory kf = KeyFactory.getInstance("EC")
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes)
		return kf.generatePrivate(keySpec)
	}

	void sendToBlockchain(postUrl) {

		JsonObject jsonParams = Json.createObjectBuilder()
				.add("fromAddress", this.wallet.publicKey)
				.add("toAddress", toAddress)
				.add("amount", amount)
				.add("signature", signature)
				.add("signatureValid", true)
				.add("transactionData", transactionData)
				.add("transactionStatus", "pending")
				.build()

		String json = jsonParams.toString()

		HttpPost httpPost = new HttpPost(postUrl)

		httpPost.setHeader('Content-Type', 'application/json')
		httpPost.setEntity(new StringEntity(json))

		HttpClient httpClient = HttpClientBuilder.create().build()

		HttpResponse httpResponse = httpClient.execute(httpPost)
		int statusCode = httpResponse.getStatusLine().getStatusCode()
		HttpEntity httpEntity = httpResponse.getEntity()

		if (statusCode == 200) {

			String postResponseBody = EntityUtils.toString(httpResponse.getEntity())
			def rayCoinTransaction = Json.createReader(new StringReader(postResponseBody)).readObject()
			def rayCoinTransactionId = rayCoinTransaction.getJsonNumber("id").longValue()

			println 'Transaction with id ' + rayCoinTransactionId + ' registered successfully'
		} else {

			String responseBody = EntityUtils.toString(httpEntity)
			JsonObject responseJson = Json.createReader(new StringReader(responseBody)).readObject()
			String errorMessage = responseJson.containsKey('error') ? responseJson.getString('error') : 'Unknow error sending transaction to blockchain'

			println "Error code (HTTP $statusCode), message: $errorMessage"
		}
	}
}

class Wallet {

	def privateKey
	def publicKey

	Wallet(walletId) {
		def objEntry = ObjectEntryLocalServiceUtil.getObjectEntry(walletId)
		this.privateKey = objEntry.getValues().get("privateKey")
		this.publicKey = objEntry.getValues().get("publicKey")
	}

	String getPrivateKey() {
		return privateKey
	}

	String getPublicKey() {
		return publicKey
	}
}