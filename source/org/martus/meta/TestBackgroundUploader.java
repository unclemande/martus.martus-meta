/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2004, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/

package org.martus.meta;

import java.io.File;
import java.util.Vector;

import org.martus.client.core.BackgroundUploader;
import org.martus.client.core.BulletinFolder;
import org.martus.client.core.BulletinStore;
import org.martus.client.core.MartusApp;
import org.martus.client.core.BackgroundUploader.UploadResult;
import org.martus.client.test.MockMartusApp;
import org.martus.client.test.NoServerNetworkInterfaceHandler;
import org.martus.client.test.NullProgressMeter;
import org.martus.common.ProgressMeterInterface;
import org.martus.common.bulletin.Bulletin;
import org.martus.common.bulletin.BulletinForTesting;
import org.martus.common.crypto.MockMartusSecurity;
import org.martus.common.network.NetworkInterface;
import org.martus.common.network.NetworkInterfaceConstants;
import org.martus.common.test.TestCaseEnhanced;
import org.martus.server.forclients.MockMartusServer;
import org.martus.server.forclients.ServerSideNetworkHandler;
import org.martus.util.Base64;
import org.martus.util.UnicodeReader;

public class TestBackgroundUploader extends TestCaseEnhanced
{
	public TestBackgroundUploader(String name)
	{
		super(name);
	}

	public void setUp() throws Exception
	{
		super.setUp();
		TRACE_BEGIN("setUp");
		if(mockSecurityForApp == null)
			mockSecurityForApp = MockMartusSecurity.createClient();
		
		if(mockSecurityForServer == null)
			mockSecurityForServer = MockMartusSecurity.createServer();

		mockServer = new MockMartusServer();
		mockServer.verifyAndLoadConfigurationFiles();
		mockServer.setSecurity(mockSecurityForServer);
		mockSSLServerHandler = new MockServerInterfaceHandler(mockServer);

		if(appWithoutServer == null)
		{
			appWithoutServer = MockMartusApp.create(mockSecurityForApp);
			NetworkInterface noServer = new NoServerNetworkInterfaceHandler();
			appWithoutServer.setSSLNetworkInterfaceHandlerForTesting(noServer);
		}
		
		appWithServer = MockMartusApp.create(mockSecurityForApp);
		appWithServer.setServerInfo("mock", mockServer.getAccountId(), "");
		appWithServer.setSSLNetworkInterfaceHandlerForTesting(mockSSLServerHandler);

		File keyPairFile = appWithServer.getCurrentKeyPairFile();
		keyPairFile.delete();
		appWithServer.getUploadInfoFile().delete();
		appWithServer.getConfigInfoFile().delete();
		appWithServer.getConfigInfoSignatureFile().delete();

		ProgressMeterInterface nullProgressMeter = new NullProgressMeter();
		uploaderWithServer = new BackgroundUploader(appWithServer, nullProgressMeter);		
		uploaderWithoutServer = new BackgroundUploader(appWithoutServer, nullProgressMeter);		
		mockServer.deleteAllData();

		TRACE_END();
	}

	public void tearDown() throws Exception
	{
		mockServer.deleteAllFiles();

		appWithoutServer.deleteAllFiles();
		appWithServer.deleteAllFiles();
		super.tearDown();
	}

	public void testBackgroundUploadSealedWithBadPort() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadSealedWithBadPort");

		createSealedBulletin(appWithoutServer);
		UploadResult result = uploaderWithoutServer.backgroundUpload();
		assertNull("No server", result.result);
		assertEquals("Bulletin disappeared?", 1, appWithoutServer.getFolderOutbox().getBulletinCount());
		TRACE_END();
	}

	public void testBackgroundUploadDraftWithBadPort() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadDraftWithBadPort");

		createDraftBulletin(appWithoutServer);
		UploadResult result = uploaderWithoutServer.backgroundUpload();
		assertNull("No server", result.result);
		assertEquals("Bulletin disappeared?", 1, appWithoutServer.getFolderDraftOutbox().getBulletinCount());
		TRACE_END();
	}

	public void testBackgroundUploadNothingToSend() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadNothingToSend");
		mockSecurityForApp.loadSampleAccount();
		BulletinFolder outbox = appWithServer.getFolderOutbox();

		assertEquals("Empty outbox", 0, outbox.getBulletinCount());
		UploadResult result = uploaderWithServer.backgroundUpload();
		assertEquals("Empty outbox", NetworkInterfaceConstants.OK, result.result);
		TRACE_END();
	}

	public void testBackgroundUploadSealedWorked() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadSealedWorked");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
		BulletinFolder outbox = appWithServer.getFolderOutbox();
		
		mockServer.allowUploads(appWithServer.getAccountId());
		mockServer.loadBannedClients();
		createSealedBulletin(appWithServer);
		UploadResult result = uploaderWithServer.backgroundUpload();
		assertEquals("Should work", NetworkInterfaceConstants.OK, result.result);
		assertEquals("It was sent", 0, outbox.getBulletinCount());
		assertEquals("It was sent", 1, appWithServer.getFolderSent().getBulletinCount());

		assertEquals("Again Empty outbox", NetworkInterfaceConstants.OK, result.result);
		mockServer.serverForClients.clearCanUploadList();
		TRACE_END();
	}

	public void testBackgroundUploadDraftWorked() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadDraftWorked");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
		BulletinFolder draftOutbox = appWithServer.getFolderDraftOutbox();
		
		mockServer.allowUploads(appWithServer.getAccountId());
		mockServer.loadBannedClients();
		
		createDraftBulletin(appWithServer);
		createDraftBulletin(appWithServer);
		assertEquals("first returned an error?", NetworkInterfaceConstants.OK, uploaderWithServer.backgroundUpload().result);
		assertEquals("first didn't get removed?", 1, draftOutbox.getBulletinCount());
		assertEquals("second returned an error?", NetworkInterfaceConstants.OK, uploaderWithServer.backgroundUpload().result);
		assertEquals("second didn't get removed?", 0, draftOutbox.getBulletinCount());

		mockServer.serverForClients.clearCanUploadList();
		TRACE_END();
	}

	public void testBackgroundUploadSealedFail() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadSealedFail");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
		BulletinFolder outbox = appWithServer.getFolderOutbox();

		createSealedBulletin(appWithServer);
		String FAILRESULT = "Some error tag would go here";
		mockServer.uploadResponse = FAILRESULT;
		assertEquals("Should fail", FAILRESULT, uploaderWithServer.backgroundUpload().result);
		assertEquals("Still in outbox", 1, outbox.getBulletinCount());
		assertEquals("Not in sent folder", 0, appWithServer.getFolderSent().getBulletinCount());
		Bulletin stillSealed = outbox.getBulletinSorted(0);
		assertTrue("Should still be sealed", stillSealed.isSealed());
		mockServer.uploadResponse = null;
		TRACE_END();
	}

	public void testBackgroundUploadDraftFail() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadDraftFail");
		mockSecurityForApp.loadSampleAccount();
		assertTrue("must be able to ping", appWithServer.isSSLServerAvailable());
		BulletinFolder draftOutbox = appWithServer.getFolderDraftOutbox();

		createDraftBulletin(appWithServer);
		String FAILRESULT = "Some error tag would go here";
		mockServer.uploadResponse = FAILRESULT;
		assertEquals("Should fail", FAILRESULT, uploaderWithServer.backgroundUpload().result);
		assertEquals("Still in draft outbox", 1, draftOutbox.getBulletinCount());
		mockServer.uploadResponse = null;
		TRACE_END();
	}

	public void testBackgroundUploadLogging() throws Exception
	{
		TRACE_BEGIN("testBackgroundUploadLogging");
		String serverName = "some silly server";
		appWithServer.setServerInfo(serverName, mockServer.getAccountId(), "");
		appWithServer.setSSLNetworkInterfaceHandlerForTesting(mockSSLServerHandler);
		File logFile = new File(appWithServer.getUploadLogFilename());
		logFile.delete();

		createSealedBulletin(appWithServer);
		mockServer.uploadResponse = NetworkInterfaceConstants.OK;
		assertEquals("Should work", NetworkInterfaceConstants.OK, uploaderWithServer.backgroundUpload().result);
		assertEquals("Created a log?", false, logFile.exists());

		appWithServer.enableUploadLogging();
		Bulletin logged = createSealedBulletin(appWithServer);
		assertEquals("Should work", NetworkInterfaceConstants.OK, uploaderWithServer.backgroundUpload().result);
		assertEquals("No log?", true, logFile.exists());
		mockServer.uploadResponse = null;

		UnicodeReader reader = new UnicodeReader(logFile);
		String line1 = reader.readLine();
		assertEquals(logged.getLocalId(), line1);
		String line2 = reader.readLine();
		assertEquals(serverName, line2);
		String line3 = reader.readLine();
		assertEquals(logged.get(Bulletin.TAGTITLE), line3);
		reader.close();
		
		TRACE_END();
	}

	public class MockServerInterfaceHandler extends ServerSideNetworkHandler
	{
		MockServerInterfaceHandler(MockMartusServer serverToUse)
		{
			super(serverToUse);
		}
		
		public void nullGetFieldOfficeAccountIds(boolean shouldReturnNull)
		{
			nullGetFieldOfficeAccountIds = shouldReturnNull;
		}
		
		public Vector getFieldOfficeAccountIds(String myAccountId, Vector parameters, String signature)
		{
			if(nullGetFieldOfficeAccountIds)
				return null;
			return super.getFieldOfficeAccountIds(myAccountId, parameters, signature);
		}
		
		boolean nullGetFieldOfficeAccountIds;
	}

	private Bulletin createSealedBulletin(MartusApp app) throws Exception
	{
		Bulletin b = app.createBulletin();
		b.setSealed();
		b.set(Bulletin.TAGTITLE, "test title");
		app.getStore().saveBulletin(b);
		app.getFolderOutbox().add(b);
		return b;
	}

	private Bulletin createDraftBulletin(MartusApp app) throws Exception
	{
		Bulletin b = app.createBulletin();
		b.setDraft();
		b.set(Bulletin.TAGTITLE, "test title");
		app.getStore().saveBulletin(b);
		app.getFolderDraftOutbox().add(b);
		return b;
	}


	Bulletin createAndUploadSampleBulletin() throws Exception
	{
		BulletinStore store = appWithServer.getStore();
		mockServer.allowUploads(appWithServer.getAccountId());
		Bulletin b2 = appWithServer.createBulletin();
		b2.setSealed();
		store.saveBulletin(b2);
		assertEquals("upload b2", NetworkInterfaceConstants.OK, uploaderWithServer.uploadBulletin(b2));
		store.destroyBulletin(b2);
		return b2;
	}

	byte[] getBulletinZipBytes(Bulletin b) throws Exception
	{
		return Base64.decode(BulletinForTesting.saveToZipString(appWithServer.getStore().getDatabase(), b, mockSecurityForApp));
	}
		
	private static MockMartusSecurity mockSecurityForApp;
	private static MockMartusSecurity mockSecurityForServer;

	private static MockMartusApp appWithoutServer;
	private MockMartusApp appWithServer;

	private MockMartusServer mockServer;
	private MockServerInterfaceHandler mockSSLServerHandler;
	
	private BackgroundUploader uploaderWithServer;
	private BackgroundUploader uploaderWithoutServer;
	static final String sampleMagicWord = "beans!";


}
