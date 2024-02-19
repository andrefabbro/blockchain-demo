package com.liferay.raycoin.objects.blockchain

import java.security.MessageDigest

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonArrayBuilder
import javax.json.JsonBuilderFactory
import javax.json.JsonObject

import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPut
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils

import com.liferay.object.model.ObjectEntry
import com.liferay.object.service.ObjectDefinitionLocalServiceUtil
import com.liferay.object.service.ObjectEntryLocalServiceUtil
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.kernel.service.ServiceContext
import com.liferay.portal.kernel.service.UserLocalServiceUtil
import com.liferay.portal.kernel.workflow.WorkflowConstants

def userId = Long.valueOf(creator)
def user = UserLocalServiceUtil.getUserById(userId)

def objDef = ObjectDefinitionLocalServiceUtil.fetchObjectDefinition(user.companyId, "C_Transaction")
List<ObjectEntry> pendingTransactions = ObjectEntryLocalServiceUtil.getObjectEntries(
		0, objDef.objectDefinitionId, WorkflowConstants.STATUS_PENDING, QueryUtil.ALL_POS, QueryUtil.ALL_POS)

if(pendingTransactions.size() > maxPendingTransactions) {
	def blockchain = new Blockchain(
			user.companyId, userId, id, rewardAddress, rewardValue, pendingTransactions, authorization, blockchainURL)
	blockchain.minePendingTransactions()
}

class Blockchain {

	long companyId
	long userId
	long blockchainId
	String rewardAddress
	BigDecimal rewardValue
	List<ObjectEntry> pendingTransactions
	String authorization
	String blockchainURL

	Blockchain(companyId, userId, blockchainId, rewardAddress, rewardValue, pendingTransactions, authorization, blockchainURL) {
		this.companyId = companyId
		this.userId = userId
		this.blockchainId = blockchainId
		this.rewardAddress = rewardAddress
		this.rewardValue = rewardValue
		this.pendingTransactions = pendingTransactions
		this.authorization = authorization
		this.blockchainURL = blockchainURL
	}

	void minePendingTransactions() {

		if(pendingTransactions == null || pendingTransactions.size() <= 0) {
			println "There is no transactions to process"
			return
		}

		List<Transaction> transactions = new ArrayList<Transaction>()

		// Get all pending transactions and put it on an internal list
		pendingTransactions.each { pt ->
			// get the transaction only if the signature is valid
			if(pt.getValues().get("signatureValid") == true) {
				transactions << new Transaction(
						pt.getValues().get("fromAddress"),
						pt.getValues().get("toAddress"),
						pt.getValues().get("amount"),
						pt.getValues().get("signature"),
						pt.objectEntryId)
			}
		}

		// Create reward transaction and add to transactions list
		def rewardTransaction = new Transaction("none", rewardAddress, rewardValue)
		rewardTransaction.save(companyId, userId)
		transactions << rewardTransaction

		// get the hash and index from the latest block on the chain
		JsonObject latestBlockJson = this.latestBlockJson.getJsonObject(0)
		def index = latestBlockJson.getJsonNumber("index").intValue()+1
		def previousHash = latestBlockJson.getString("hash")

		// update the status of all transactions to scheduled, so it will avoid processing from another thread
		transactions.each { t ->
			t.updateStatus(userId, WorkflowConstants.STATUS_SCHEDULED)
		}

		// creates the block objects and mine based on all information and using the difficulty level
		def block = new Block(index, new Date().toString(), transactions, previousHash, blockchainId)
		def difficulty = 2
		block.mineBlock(difficulty)

		// check if there is another block with the same previousHash,
		// if true, there is already another block mined and then we should give up this block
		def blockWithPreviousHashJson = this.getBlockWithPreviousHashJson(previousHash)
		if(blockWithPreviousHashJson != null && blockWithPreviousHashJson.size() > 0) {

			// we have to remove the reward transaction too
			rewardTransaction.delete()

			// go back all the transactions to pending as the block won't be saved
			transactions.each { t ->
				t.updateStatus(userId, WorkflowConstants.STATUS_PENDING)
			}

			// stop the execution
			return
		}

		// persist the block
		ObjectEntry blockMined = block.save(companyId, userId)

		// get the balances of all transactions and put in a hash map to validate balances
		Map<String, BigDecimal> walletBalances = new HashMap<String, BigDecimal>()
		transactions.each { t ->
			walletBalances.put(t.fromAddress, this.getWalletBalance(t.fromAddress))
		}

		// process all scheduled transactions
		transactions.each { t ->

			// add the transaction to block
			t.addToBlock(userId, blockMined.objectEntryId)

			// check if the addressFrom has balance to debt the value
			BigDecimal balance = walletBalances.get(t.fromAddress)
			if(balance < t.amount) {
				println "Wallet $t.fromAddress doesn't have sufficient coins"
				t.updateStatus(userId, WorkflowConstants.STATUS_DENIED)
			} else {
				walletBalances.put(t.fromAddress, balance - t.amount)
				t.updateStatus(userId, WorkflowConstants.STATUS_APPROVED)
			}

			// also, if a address is receiving coins in the same block, credit this value to it balance
			if(walletBalances.containsKey(t.toAddress)) {
				walletBalances.put(t.toAddress, (walletBalances.get(t.toAddress)+t.amount))
			}
		}

		def computeBalanceUrl = "${blockchainURL}/o/c/blockchains/${blockchainId}/object-actions/computeBalances"

		Thread.start {
			sleep(3000)

			// add the reward transaction to this block
			rewardTransaction.updateStatus(userId, WorkflowConstants.STATUS_APPROVED)
			rewardTransaction.addToBlock(userId, blockMined.objectEntryId)

			// also compute the balances
			HttpClient httpClient = HttpClientBuilder.create().build()
			HttpPut httpPut = new HttpPut(computeBalanceUrl)

			httpPut.setHeader('Content-Type', 'application/json')
			httpPut.setHeader('Authorization', "$authorization")

			HttpResponse putResponse = httpClient.execute(httpPut)
			int putResponseStatus = putResponse.getStatusLine().getStatusCode()

			println "PUT response status: ${putResponse.getStatusLine()}"

			if (putResponseStatus == HttpStatus.SC_OK || putResponseStatus == HttpStatus.SC_NO_CONTENT) {
				println "Successfully PUT to computeBalances."
			} else {
				println "Error in PUT minePendingTransactions. Error code: $putResponseStatus"
			}
		}
	}

	private BigDecimal getWalletBalance(String address) {

		def encodedFilter = URLEncoder.encode("address eq '${address}'", "UTF-8")
		def getUrl = "${this.blockchainURL}/o/c/walletbalances/?filter=${encodedFilter}"

		JsonArray walletBalancesJsonArray = this.fetchItemsFromApi(getUrl)
		if(walletBalancesJsonArray.empty) {
			return new BigDecimal(0)
		} else {
			JsonObject walletBalance = walletBalancesJsonArray.getJsonObject(0)
			return new BigDecimal(walletBalance.getJsonNumber("balance").toString())
		}
	}

	private JsonArray getLatestBlockJson() {

		def encodedSort = URLEncoder.encode("id:desc", "UTF-8")
		def getUrl = "${this.blockchainURL}/o/c/blocks/?sort=${encodedSort}&pageSize=1"

		return this.fetchItemsFromApi(getUrl)
	}

	private JsonArray getBlockWithPreviousHashJson(String previousHash) {

		def encodedFilter = URLEncoder.encode("previousHash eq '${previousHash}'", "UTF-8")
		def getUrl = "${this.blockchainURL}/o/c/blocks/?filter=${encodedFilter}"

		return this.fetchItemsFromApi(getUrl)
	}

	private JsonArray fetchItemsFromApi(url, authheader = null) {
		HttpGet httpGet = new HttpGet(url)
		httpGet.setHeader('Content-Type', 'application/json')
		if(authheader != null) {
			httpGet.setHeader('Authorization', "$authheader")
		}

		HttpClient httpClient = HttpClientBuilder.create().build()
		HttpResponse getResponse = httpClient.execute(httpGet)
		String getResponseBody = EntityUtils.toString(getResponse.getEntity())
		JsonObject getResponseJson = Json.createReader(new StringReader(getResponseBody)).readObject()
		return getResponseJson.getJsonArray("items")
	}
}

class Block {

	long blockchainId
	int index
	String timestamp
	List<Transaction> transactions
	String previousHash
	String hash
	int nonce

	Block(int index, String timestamp, List<Transaction> transactions, String previousHash, long blockchainId) {
		this.index = index
		this.timestamp = timestamp
		this.transactions = transactions
		this.previousHash = previousHash
		this.nonce = 0
		this.hash = calculateHash()
		this.blockchainId = blockchainId
	}

	String calculateHash() {
		return md5("${index}${timestamp}${Transaction.toJsonArray(transactions)}${previousHash}${nonce}")
	}

	void mineBlock(int difficulty) {
		while (!hash[0..<difficulty].every { it == '0' }) {
			nonce++
			hash = calculateHash()
		}
		println "Block mined: ${hash}"
	}

	String md5(String input) {
		MessageDigest digest = MessageDigest.getInstance("MD5")
		byte[] hash = digest.digest(input.getBytes("UTF-8"))
		return hash.encodeHex().toString()
	}

	ObjectEntry save(long companyId, long userId) {
		def values = [index: index, hash: hash, previousHash: previousHash, nonce: nonce, r_blockchain_c_blockchainId: blockchainId]
		def objDef = ObjectDefinitionLocalServiceUtil.fetchObjectDefinition(companyId, "C_Block")
		return ObjectEntryLocalServiceUtil.addObjectEntry(
				userId, 0, objDef.objectDefinitionId, values, new ServiceContext())
	}
}

class Transaction {

	Long id
	String fromAddress
	String toAddress
	BigDecimal amount
	String signature

	Transaction(String fromAddress, String toAddress, BigDecimal amount, String signature = "0", Long id = 0) {
		this.fromAddress = fromAddress
		this.toAddress = toAddress
		this.amount = amount
		this.signature = signature
		this.id = id
	}

	String getTransactionData() {
		return "${fromAddress}${toAddress}${amount}"
	}

	JsonObject toJson() {
		JsonBuilderFactory factory = Json.createBuilderFactory(null);
		JsonObject transactionJson = factory.createObjectBuilder()
				.add("fromAddress", fromAddress)
				.add("toAddress", toAddress)
				.add("amount", amount)
				.add("signature", signature)
				.add("id", id)
				.build();
		return transactionJson;
	}

	static JsonArray toJsonArray(List<Transaction> transactions) {
		JsonBuilderFactory factory = Json.createBuilderFactory(null);
		JsonArrayBuilder arrayBuilder = factory.createArrayBuilder();
		for (Transaction transaction : transactions) {
			arrayBuilder.add(transaction.toJson());
		}
		return arrayBuilder.build();
	}

	String toString() {
		return toJson().toString();
	}

	ObjectEntry save(long companyId, long userId) {
		def values = [fromAddress: fromAddress, toAddress: toAddress, amount: amount, signature: signature]
		def objDef = ObjectDefinitionLocalServiceUtil.fetchObjectDefinition(companyId, "C_Transaction")
		ObjectEntry obj = ObjectEntryLocalServiceUtil.addObjectEntry(
				userId, 0, objDef.objectDefinitionId, values, new ServiceContext())
		this.id = obj.objectEntryId
		return obj
	}

	ObjectEntry updateStatus(long userId, int status) {
		return ObjectEntryLocalServiceUtil.updateStatus(userId, this.id, status, new ServiceContext())
	}

	ObjectEntry addToBlock(long userId, long blockId) {
		def values = [r_transactions_c_blockId: blockId]
		return ObjectEntryLocalServiceUtil.updateObjectEntry(userId, this.id, values, new ServiceContext())
	}

	void delete() {
		ObjectEntryLocalServiceUtil.deleteObjectEntry(this.id)
	}
}
