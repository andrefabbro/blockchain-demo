package com.liferay.raycoin.objects.transaction

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonObject

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils

import com.liferay.object.service.ObjectDefinitionLocalServiceUtil
import com.liferay.object.service.ObjectEntryLocalServiceUtil
import com.liferay.portal.kernel.service.ServiceContext
import com.liferay.portal.kernel.workflow.WorkflowConstants

// if it is a reward transaction, doesn't validate
if(fromAddress == 'none') {
	return
}

def userId = Long.valueOf(creator)
def obj = ObjectEntryLocalServiceUtil.getObjectEntry(id)

def transactionData = "${fromAddress}${toAddress}${amount}"
def transactionValidator = new TransactionValidator(obj.companyId, amount, fromAddress, transactionData, signature)

try {

	// first, set the signatureValid to false
	ObjectEntryLocalServiceUtil.updateObjectEntry(userId, id, [signatureValid: false], new ServiceContext())

	// then, validate the signature
	transactionValidator.validateSignature()
	println "Transaction signature successfully validated"

	// set the signatureValid to true
	ObjectEntryLocalServiceUtil.updateObjectEntry(userId, id, [signatureValid: true], new ServiceContext())
	
	// validate if the wallet has enough funds for this transaction
	transactionValidator.validateBalance()

	// set the transaction status to pending
	transactionValidator.updateTransactionStatus(userId, id, WorkflowConstants.STATUS_PENDING)
	
} catch (Exception e) {
	// as the signature wasn't validated or the wallet doesn't have funds, set the status to denied
	transactionValidator.updateTransactionStatus(userId, id, WorkflowConstants.STATUS_DENIED)
}

class TransactionValidator {

	long companyId
	String fromAddress
	String transactionData
	String signature
	BigDecimal amount

	TransactionValidator(companyId, amount, fromAddress, transactionData, signature) {
		this.companyId = companyId
		this.amount = amount
		this.fromAddress = fromAddress
		this.transactionData = transactionData
		this.signature = signature
	}

	void validateSignature() throws Exception {
		if (this.fromAddress == 'none') {
			return
		}

		byte[] signatureBytes = Base64.getDecoder().decode(this.signature)

		if (signatureBytes == null || signatureBytes.length == 0) {
			throw new RuntimeException("No signature found in this transaction")
		}

		def keyFactory = KeyFactory.getInstance("EC")
		def publicKeySpec = new X509EncodedKeySpec(Base64.decoder.decode(this.fromAddress))
		def publicKey = keyFactory.generatePublic(publicKeySpec)

		def signatureInstance = Signature.getInstance("SHA256withECDSA")
		signatureInstance.initVerify(publicKey)
		signatureInstance.update(this.transactionData.bytes)

		if(!signatureInstance.verify(signatureBytes)) {
			throw new Exception("Signature validation failed")
		}
	}

	void validateBalance() {

		def blockChainObjDef = ObjectDefinitionLocalServiceUtil.fetchObjectDefinition(companyId, "C_Blockchain")
		def blockChainObj = ObjectEntryLocalServiceUtil.getObjectEntries(0, blockChainObjDef.objectDefinitionId, 0, 1).get(0)
		def blockchainUrl = blockChainObj.values.get("blockchainURL")

		def encodedFilter = URLEncoder.encode("address eq '${fromAddress}'", "UTF-8")
		def getUrl = "${blockchainUrl}/o/c/walletbalances/?filter=${encodedFilter}"

		HttpGet httpGet = new HttpGet(getUrl)
		httpGet.setHeader('Content-Type', 'application/json')

		HttpClient httpClient = HttpClientBuilder.create().build()
		HttpResponse getResponse = httpClient.execute(httpGet)
		String getResponseBody = EntityUtils.toString(getResponse.getEntity())
		JsonObject getResponseJson = Json.createReader(new StringReader(getResponseBody)).readObject()

		JsonArray walletBalancesJsonArray = getResponseJson.getJsonArray("items")
		if(walletBalancesJsonArray.empty) {
			throw new Exception("Wallet doesn't have enough funds")
		} else {
			JsonObject walletBalance = walletBalancesJsonArray.getJsonObject(0)
			def balance = new BigDecimal(walletBalance.getJsonNumber("balance").toString())
			if(amount > balance) {
				throw new Exception("Wallet doesn't have enough funds")
			}
		}
	}

	void updateTransactionStatus(userId, id, status) {
		Thread.start {
			sleep(3000) // Sleep for 3 seconds
			ObjectEntryLocalServiceUtil.updateStatus(userId, id, status, new ServiceContext())
		}
	}
}

