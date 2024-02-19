package com.liferay.poc.blockchain.example

class Demonstration {
    static void main(String[] args) {

        def walletAlice = new Wallet()
        def walletBob = new Wallet()
        def walletCarol = new Wallet()
        def walletMiner = new Wallet()

        def simpleCoin = new Blockchain()

        // Distribute some inicial rewards
        simpleCoin.minePendingTransactions(walletAlice.getAddress())
        simpleCoin.minePendingTransactions(walletBob.getAddress())
        simpleCoin.minePendingTransactions(walletCarol.getAddress())

        println "Initial balances:"
        println "Alice: ${simpleCoin.getBalance(walletAlice.getAddress())}"
        println "Bob: ${simpleCoin.getBalance(walletBob.getAddress())}"
        println "Carol: ${simpleCoin.getBalance(walletCarol.getAddress())}"
        println "Miner: ${simpleCoin.getBalance(walletMiner.getAddress())}"

        println "Creating signed transactions..."
        def tx1 = new Transaction(walletAlice.getAddress(), walletBob.getAddress(), 10)
        tx1.signTransaction(walletAlice)
        simpleCoin.addTransaction(tx1)

        def tx2 = new Transaction(walletBob.getAddress(), walletCarol.getAddress(), 5)
        tx2.signTransaction(walletBob)
        simpleCoin.addTransaction(tx2)

        println "Mining block with pending transactions..."
        simpleCoin.minePendingTransactions(walletMiner.getAddress())

        println "Updated balances:"
        println "Alice: ${simpleCoin.getBalance(walletAlice.getAddress())}"
        println "Bob: ${simpleCoin.getBalance(walletBob.getAddress())}"
        println "Carol: ${simpleCoin.getBalance(walletCarol.getAddress())}"
        println "Miner: ${simpleCoin.getBalance(walletMiner.getAddress())}"

        println "Blockchain valid? ${simpleCoin.isChainValid()}"

        println "Current Blockchain:"
        println simpleCoin
    }
}