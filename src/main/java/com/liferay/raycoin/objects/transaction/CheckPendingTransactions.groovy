package com.liferay.raycoin.objects.transaction

import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPut
import org.apache.http.impl.client.HttpClientBuilder

import com.liferay.object.service.ObjectDefinitionLocalServiceUtil
import com.liferay.object.service.ObjectEntryLocalServiceUtil

// if it is a reward transaction, doesn't trigger mining
if(fromAddress == 'none') return

def obj = ObjectEntryLocalServiceUtil.getObjectEntry(id)

def objDef = ObjectDefinitionLocalServiceUtil.fetchObjectDefinition(obj.companyId, "C_Blockchain")

def objectsEntries = ObjectEntryLocalServiceUtil.getObjectEntries(
		0, objDef.objectDefinitionId, 0, 1)
def objEntry = objectsEntries.get(0)
def authheader = objEntry.values.get("authorization")
def blockchainUrl = objEntry.values.get("blockchainURL")
def putUrl = "${blockchainUrl}/o/c/blockchains/${objEntry.objectEntryId}/object-actions/minePendingTransactions"

Thread.start {
	sleep(5000) // Sleep for 5 seconds

	HttpClient httpClient = HttpClientBuilder.create().build()
	HttpPut httpPut = new HttpPut(putUrl)

	httpPut.setHeader('Content-Type', 'application/json')
	httpPut.setHeader('Authorization', "$authheader")

	HttpResponse putResponse = httpClient.execute(httpPut)
	int putResponseStatus = putResponse.getStatusLine().getStatusCode()

	println "PUT response status: ${putResponse.getStatusLine()}"

	if (putResponseStatus == HttpStatus.SC_OK || putResponseStatus == HttpStatus.SC_NO_CONTENT) {
		println "Successfully PUT to minePendingTransactions."
	} else {
		println "Error in PUT minePendingTransactions. Error code: $putResponseStatus"
	}
}



