package com.github.rmannibucau.rules.test.ftp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.filesystem.FileSystem;

import com.github.rmannibucau.rules.api.UnitInject;
import com.github.rmannibucau.rules.api.ftp.FtpFile;
import com.github.rmannibucau.rules.api.ftp.FtpServer;
import com.github.rmannibucau.rules.api.ftp.FtpServerRule;

@FtpServer(files = {@FtpFile(name = "foo.txt", content = "bar.txt")})
public class TestFtpRuleOnClass {
	@Rule
	public final FtpServerRule unit = new FtpServerRule(this);

	@UnitInject
	private FileSystem fs;

	@UnitInject
	private FakeFtpServer server;

	private static FakeFtpServer oldServer;

	private String stayNull = null;

	@Test
	public void checkInjections() throws IOException {
		assertNull(stayNull);
		assertNotNull(fs);
		assertNotNull(server);
		assertTrue(server.getServerControlPort() > 0);
		assertEquals(Integer.toString(server.getServerControlPort()), System.getProperty(FtpServer.FTP_PORT));
		oldServer = server;
	}

	@AfterClass
	public static void checkServerIsOff() {
		assertTrue(oldServer.isShutdown());
	}
}
