package com.liferay.raycoin.objects.blockchain

import com.liferay.object.model.ObjectEntry
import com.liferay.object.service.ObjectDefinitionLocalServiceUtil
import com.liferay.object.service.ObjectEntryLocalServiceUtil
import com.liferay.portal.kernel.dao.orm.QueryUtil
import com.liferay.portal.kernel.service.ServiceContext
import com.liferay.portal.kernel.workflow.WorkflowConstants

def obj = ObjectEntryLocalServiceUtil.getObjectEntry(id)
balance = new BlockchainBalance(obj.getCompanyId(), Long.valueOf(creator))
balance.computeBalance()

class BlockchainBalance {

	long companyId
	long userId
	long objectDefinitionId

	BlockchainBalance(long companyId, long userId) {
		this.companyId = companyId
		this.userId = userId
		this.objectDefinitionId = ObjectDefinitionLocalServiceUtil.fetchObjectDefinition(
				this.companyId, "C_WalletBalance").getObjectDefinitionId()
	}

	void computeBalance() {

		removeAllBalances()

		Map<String, BigDecimal> balances = new HashMap<String, BigDecimal>()

		def objTransactionDef = ObjectDefinitionLocalServiceUtil.fetchObjectDefinition(
				this.companyId, "C_Transaction")
		List<ObjectEntry> approvedTransactions = ObjectEntryLocalServiceUtil.getObjectEntries(
				0, objTransactionDef.objectDefinitionId, WorkflowConstants.STATUS_APPROVED, QueryUtil.ALL_POS, QueryUtil.ALL_POS)

		// fix the balance for 'none' wallet to be able to distribute reward to miners
		balances.put("none", 1000000000)

		approvedTransactions.each { t ->
			def v = ObjectEntryLocalServiceUtil.getValues(t.objectEntryId)

			def fromAddress = v.get("fromAddress")
			def toAddress = v.get("toAddress")
			def amount = v.get("amount")

			if(balances.get(fromAddress) == null) {
				balances.put(fromAddress, new BigDecimal(0))
			}

			if(balances.get(toAddress) == null) {
				balances.put(toAddress, new BigDecimal(0))
			}

			balances.put(fromAddress, balances.get(fromAddress) - amount)
			balances.put(toAddress, balances.get(toAddress) + amount)
		}

		balances.each { address, balance ->
			def walletBalanceValues = [address: address, balance: balance]
			ObjectEntryLocalServiceUtil.addObjectEntry(
					userId, 0, objectDefinitionId, walletBalanceValues, new ServiceContext())
		}
	}

	void removeAllBalances() {
		def balanceEntries = ObjectEntryLocalServiceUtil.getObjectEntries(
				0, objectDefinitionId, QueryUtil.ALL_POS, QueryUtil.ALL_POS)
		balanceEntries.each { b ->
			ObjectEntryLocalServiceUtil.deleteObjectEntry(b.objectEntryId)
		}
	}
}
