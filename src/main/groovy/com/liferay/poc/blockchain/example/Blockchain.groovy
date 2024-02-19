package com.liferay.poc.blockchain.example

import groovy.json.JsonOutput

class Blockchain {
	
	List<Block> chain
	List<Transaction> pendingTransactions
	BigDecimal miningReward
	int difficulty

	Blockchain() {
		this.chain = [createGenesisBlock()]
		this.pendingTransactions = []
		this.miningReward = 100
		this.difficulty = 2
	}

	Block createGenesisBlock() {
		return new Block(0, '01/01/2023', [], '0')
	}

	Block getLatestBlock() {
		return chain.last()
	}

	void addBlock(Block newBlock) {
		newBlock.previousHash = getLatestBlock().hash
		newBlock.hash = newBlock.calculateHash()
		if (isValidNewBlock(newBlock)) {
			chain << newBlock
		} else {
			throw new RuntimeException("Invalid block")
		}
	}

	boolean isValidNewBlock(Block newBlock) {
		for (Transaction transaction : newBlock.transactions) {
			if (!transaction.isSignatureValid()) {
				return false
			}
		}
		return true
	}

	boolean isChainValid() {
		for (int i = 1; i < chain.size(); i++) {
			def currentBlock = chain[i]
			def previousBlock = chain[i - 1]

			if (currentBlock.hash != currentBlock.calculateHash()) {
				return false
			}

			if (currentBlock.previousHash != previousBlock.hash) {
				return false
			}
		}
		return true
	}

	BigDecimal getBalance(String address) {
		BigDecimal balance = 0
		for (Block block : chain) {
			for (Transaction transaction : block.transactions) {
				if (transaction.fromAddress == address) {
					balance -= transaction.amount
				}
				if (transaction.toAddress == address) {
					balance += transaction.amount
				}
			}
		}
		return balance
	}

	void addTransaction(Transaction transaction) {
		if (!transaction.isSignatureValid()) {
			throw new RuntimeException("Invalid transaction signature")
		}

		if (transaction.amount <= 0) {
			throw new RuntimeException("Transaction amount should be greater than 0")
		}

		if (getBalance(transaction.fromAddress) < transaction.amount) {
			throw new RuntimeException("Not enough balance for the transaction")
		}

		pendingTransactions << transaction
	}

	void minePendingTransactions(String minerAddress) {
		
		pendingTransactions << new Transaction(null, minerAddress, miningReward)
		
		def block = new Block(chain.size(), new Date().toString(), pendingTransactions)
		block.previousHash = getLatestBlock().hash
		block.mineBlock(difficulty)
		chain << block

		// Reset the pending transactions
		pendingTransactions = []
	}


	String toString() {
		return JsonOutput.toJson(this)
	}
}
