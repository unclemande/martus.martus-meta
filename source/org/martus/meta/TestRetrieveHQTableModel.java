package org.martus.meta;

import java.io.File;
import java.io.StringWriter;
import java.util.Vector;

import org.martus.client.swingui.UiLocalization;
import org.martus.client.swingui.tablemodels.RetrieveHQTableModel;
import org.martus.client.test.*;
import org.martus.client.test.MockMartusApp;
import org.martus.common.MartusUtilities;
import org.martus.common.bulletin.Bulletin;
import org.martus.common.bulletin.BulletinZipUtilities;
import org.martus.common.crypto.MartusCrypto;
import org.martus.common.crypto.MockMartusSecurity;
import org.martus.common.database.Database;
import org.martus.common.database.DatabaseKey;
import org.martus.common.network.NetworkInterface;
import org.martus.common.network.NetworkInterfaceConstants;
import org.martus.common.packet.FieldDataPacket;
import org.martus.common.packet.UniversalId;
import org.martus.common.test.TestCaseEnhanced;
import org.martus.server.forclients.MockMartusServer;
import org.martus.server.forclients.ServerSideNetworkHandler;

public class TestRetrieveHQTableModel extends TestCaseEnhanced
{
	public TestRetrieveHQTableModel(String name) 
	{
		super(name);
	}
	
	public void setUp() throws Exception
	{
		super.setUp();
		if(localization != null)
			return;	
		MartusCrypto hqSecurity = MockMartusSecurity.createHQ();
		localization = new MockUiLocalization();
		hqApp = MockMartusApp.create(hqSecurity);

		MartusCrypto fieldSecurity1 = MockMartusSecurity.createClient();
		fieldApp1 = MockMartusApp.create(fieldSecurity1);
		Database db1 = fieldApp1.getStore().getDatabase();

		MartusCrypto fieldSecurity2 = MockMartusSecurity.createOtherClient();
		fieldApp2 = MockMartusApp.create(fieldSecurity2);
		Database db2 = fieldApp2.getStore().getDatabase();

		assertNotEquals("account Id's equal?", fieldApp1.getAccountId(), fieldApp2.getAccountId());

		b0 = fieldApp1.createBulletin();
		b0.set(Bulletin.TAGTITLE, title0);
		b0.set(Bulletin.TAGAUTHOR, author0);
		b0.setAllPrivate(true);
		b0.setHQPublicKey(hqApp.getAccountId());
		fieldApp1.getStore().saveBulletin(b0);
		b0Size = MartusUtilities.getBulletinSize(fieldApp1.getStore().getDatabase(), b0.getBulletinHeaderPacket());

		b1 = fieldApp1.createBulletin();
		b1.set(Bulletin.TAGTITLE, title1);
		b1.set(Bulletin.TAGAUTHOR, author1);
		b1.setAllPrivate(false);
		b1.setHQPublicKey(hqApp.getAccountId());
		fieldApp1.getStore().saveBulletin(b1);
		b1Size = MartusUtilities.getBulletinSize(fieldApp1.getStore().getDatabase(), b1.getBulletinHeaderPacket());

		b2 = fieldApp2.createBulletin();
		b2.set(Bulletin.TAGTITLE, title2);
		b2.set(Bulletin.TAGAUTHOR, author2);
		b2.setAllPrivate(true);
		b2.setHQPublicKey(hqApp.getAccountId());
		fieldApp2.getStore().saveBulletin(b2);
		b2Size = MartusUtilities.getBulletinSize(fieldApp1.getStore().getDatabase(), b2.getBulletinHeaderPacket());
	
		testServer = new MockServer();
		testServer.verifyAndLoadConfigurationFiles();
		testSSLServerInterface = new ServerSideNetworkHandler(testServer);
		hqApp.setSSLNetworkInterfaceHandlerForTesting(testSSLServerInterface);
		modelWithData = new RetrieveHQTableModel(hqApp, localization);
		modelWithData.initialize(null);

		importBulletinFromFieldOfficeToHq(db1, b0, fieldSecurity1);
		importBulletinFromFieldOfficeToHq(db1, b1, fieldSecurity1);
		importBulletinFromFieldOfficeToHq(db2, b2, fieldSecurity2);
		
		modelWithoutData = new RetrieveHQTableModel(hqApp, localization);
		modelWithoutData.initialize(null);
		assertEquals(0, modelWithoutData.getRowCount());
	}

	void importBulletinFromFieldOfficeToHq(Database db, Bulletin b, MartusCrypto sigVerifier) throws Exception
	{
		File tempFile = createTempFile();
		DatabaseKey headerKey = DatabaseKey.createKey(b.getUniversalId(), b.getStatus());
		BulletinZipUtilities.exportBulletinPacketsFromDatabaseToZipFile(db, headerKey, tempFile, sigVerifier);
		hqApp.getStore().importZipFileToStoreWithSameUids(tempFile);
	}
	
	public void tearDown() throws Exception
	{
		testServer.deleteAllFiles();
    	fieldApp1.deleteAllFiles();
    	fieldApp2.deleteAllFiles();
    	hqApp.deleteAllFiles();
    	super.tearDown();
    }

	public void testGetColumnName()
	{
		assertEquals(localization.getFieldLabel("retrieveflag"), modelWithData.getColumnName(0));
		assertEquals(localization.getFieldLabel(Bulletin.TAGTITLE), modelWithData.getColumnName(1));
		assertEquals(localization.getFieldLabel(Bulletin.TAGAUTHOR), modelWithData.getColumnName(2));
		assertEquals(localization.getFieldLabel("BulletinSize"), modelWithData.getColumnName(3));
	}
	
	public void testGetColumnCount()
	{
		assertEquals(4, modelWithoutData.getColumnCount());
		assertEquals(4, modelWithData.getColumnCount());
	}
	
	public void testGetRowCount()
	{
		assertEquals(0, modelWithoutData.getRowCount());
		assertEquals(3, modelWithData.getRowCount());
	}
	
	public void testIsCellEditable()
	{
		assertEquals("flag", true, modelWithData.isCellEditable(1,0));
		assertEquals("title", false, modelWithData.isCellEditable(1,1));
		assertEquals("author", false, modelWithData.isCellEditable(1,2));
		assertEquals("size", false, modelWithData.isCellEditable(1,3));
	}
	
	public void testGetColumnClass()
	{
		assertEquals(Boolean.class, modelWithData.getColumnClass(0));
		assertEquals(String.class, modelWithData.getColumnClass(1));
		assertEquals(String.class, modelWithData.getColumnClass(2));
		assertEquals(Integer.class, modelWithData.getColumnClass(3));
	}
	
	public void testGetAndSetValueAt()
	{
		Vector authors = new Vector();
		authors.add(modelWithData.getValueAt(0,2));
		authors.add(modelWithData.getValueAt(1,2));
		authors.add(modelWithData.getValueAt(2,2));
		assertContains("Author 0 missing?", b0.get(Bulletin.TAGAUTHOR), authors);
		assertContains("Author 1 missing?", b1.get(Bulletin.TAGAUTHOR), authors);
		assertContains("Author 2 missing?", b2.get(Bulletin.TAGAUTHOR), authors);
		
		assertEquals("start bool", false, ((Boolean)modelWithData.getValueAt(0,0)).booleanValue());
		modelWithData.setValueAt(new Boolean(true), 0,0);
		assertEquals("setget bool", true, ((Boolean)modelWithData.getValueAt(0,0)).booleanValue());

		assertEquals("start title", title2, modelWithData.getValueAt(2,1));
		modelWithData.setValueAt(title2+title2, 2,1);
		assertEquals("keep title", title2, modelWithData.getValueAt(2,1));
		
		assertTrue("B0 Size too small", ((Integer)(modelWithData.getValueAt(0,3))).intValue() > 1);
		assertTrue("B1 Size too small", ((Integer)(modelWithData.getValueAt(1,3))).intValue() > 1);
		assertTrue("B2 Size too small", ((Integer)(modelWithData.getValueAt(2,3))).intValue() > 1);
	}
	
	public void testSetAllFlags()
	{
		Boolean t = new Boolean(true);
		Boolean f = new Boolean(false);
		
		modelWithData.setAllFlags(true);
		for(int allTrueCounter = 0; allTrueCounter < modelWithData.getRowCount(); ++allTrueCounter)
			assertEquals("all true" + allTrueCounter, t, modelWithData.getValueAt(0,0));

		modelWithData.setAllFlags(false);
		for(int allFalseCounter = 0; allFalseCounter < modelWithData.getRowCount(); ++allFalseCounter)
			assertEquals("all false" + allFalseCounter, f, modelWithData.getValueAt(0,0));
	}
	
	public void testGetIdList()
	{
		modelWithData.setAllFlags(false);
		Vector emptyList = modelWithData.getUniversalIdList();
		assertEquals(0, emptyList.size());
		
		modelWithData.setAllFlags(true);

		Vector fullList = modelWithData.getUniversalIdList();
		assertEquals(3, fullList.size());
		assertNotEquals("hq account ID0?", hqApp.getAccountId(), ((UniversalId)fullList.get(0)).getAccountId());
		assertNotEquals("hq account ID1?", hqApp.getAccountId(), ((UniversalId)fullList.get(1)).getAccountId());
		assertNotEquals("hq account ID2?", hqApp.getAccountId(), ((UniversalId)fullList.get(2)).getAccountId());

		assertContains("b0 Uid not in list?", b0.getUniversalId(), fullList);
		assertContains("b1 Uid not in list?", b1.getUniversalId(), fullList);
		assertContains("b2 Uid not in list?", b2.getUniversalId(), fullList);

		modelWithData.setValueAt(new Boolean(false), 1, 0);
		Vector twoList = modelWithData.getUniversalIdList();

		assertEquals(2, twoList.size());
		assertEquals("b0 id", fullList.get(0), twoList.get(0));
		assertEquals("b2 id", fullList.get(2), twoList.get(1));
	}

	class MockServer extends MockMartusServer
	{
		MockServer() throws Exception
		{
			super();
			setSecurity(MockMartusSecurity.createServer());
		}
		
		public Vector listFieldOfficeSealedBulletinIds(String hqAccountId, String authorAccountId, Vector retrieveTags)
		{
			Vector result = new Vector();
			result.add(NetworkInterfaceConstants.OK);
			Vector list = new Vector();
			if(authorAccountId.equals(b0.getAccount()))
				list.add(b0.getLocalId() + "=" + b0.getFieldDataPacket().getLocalId() + "=" + b0Size);
			if(authorAccountId.equals(b1.getAccount()))
				list.add(b1.getLocalId() + "=" + b1.getFieldDataPacket().getLocalId() + "=" + b0Size);
			if(authorAccountId.equals(b2.getAccount()))
				list.add(b2.getLocalId() + "=" + b2.getFieldDataPacket().getLocalId() + "=" + b0Size);
			result.add(list);
			return result;
		}
		
		public Vector listFieldOfficeAccounts(String hqAccountId) 
		{
			Vector v = new Vector();
			v.add(NetworkInterfaceConstants.OK);
			v.add(fieldApp1.getAccountId());
			v.add(fieldApp2.getAccountId());
			return v;			
		}

		public Vector getPacket(String hqAccountId, String authorAccountId, String bulletinLocalId, String packetLocalId)
		{
			Vector result = new Vector();
			try 
			{
				UniversalId uid = UniversalId.createFromAccountAndLocalId(authorAccountId, packetLocalId);
				FieldDataPacket fdp = null;
				MartusCrypto security = fieldApp1.getSecurity();
				if(uid.equals(b0.getFieldDataPacket().getUniversalId()))
					fdp = b0.getFieldDataPacket();
				if(uid.equals(b1.getFieldDataPacket().getUniversalId()))
					fdp = b1.getFieldDataPacket();
				if(uid.equals(b2.getFieldDataPacket().getUniversalId()))
				{
					fdp = b2.getFieldDataPacket();
					security = fieldApp2.getSecurity();
				}

				StringWriter writer = new StringWriter();
				fdp.writeXml(writer, security);
				result.add(NetworkInterfaceConstants.OK);
				result.add(writer.toString());
				writer.close();
			} 
			catch (Exception e) 
			{
				result.add(NetworkInterfaceConstants.SERVER_ERROR);
			}
			return result;
		}

	}
	
	String title0 = "cool title";
	String title1 = "This is a cool title";
	String title2 = "Even cooler";

	String author0 = "Fred 0";
	String author1 = "Betty 1";
	String author2 = "Donna 2";

	static MockMartusServer testServer;
	static NetworkInterface testSSLServerInterface;
	static MockMartusApp fieldApp1;
	static MockMartusApp fieldApp2;
	static MockMartusApp hqApp;
	static UiLocalization localization;
	
	static Bulletin b0;
	static Bulletin b1;
	static Bulletin b2;
	static int b0Size;
	static int b1Size;
	static int b2Size;

	static RetrieveHQTableModel modelWithData;
	static RetrieveHQTableModel modelWithoutData;
}
