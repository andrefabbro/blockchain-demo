package com.liferay.poc.blockchain.example

import groovy.json.JsonOutput

class Block {

	int index
	String timestamp
	List<Transaction> transactions
	String previousHash
	String hash
	int nonce

	Block(int index, String timestamp, List<Transaction> transactions, String previousHash = '') {
		this.index = index
		this.timestamp = timestamp
		this.transactions = transactions
		this.previousHash = previousHash
		this.nonce = 0
		this.hash = calculateHash()
	}

	String calculateHash() {
		return "${index}${timestamp}${JsonOutput.toJson(transactions)}${previousHash}${nonce}".md5()
	}

	void mineBlock(int difficulty) {
		while (!hash[0..<difficulty].every { it == '0' }) {
			nonce++
			hash = calculateHash()
		}
		println "Block mined: ${hash}"
	}

	String toString() {
		return JsonOutput.toJson(this)
	}
}
