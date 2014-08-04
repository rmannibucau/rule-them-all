package com.github.rmannibucau.rules.test.sftp;

import com.github.rmannibucau.rules.api.UnitInject;
import com.github.rmannibucau.rules.api.sftp.SftpFile;
import com.github.rmannibucau.rules.api.sftp.SftpServer;
import com.github.rmannibucau.rules.api.sftp.SftpServerRule;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.apache.sshd.SshServer;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestSftpRule {
	@Rule
	public final SftpServerRule unit = new SftpServerRule(this);

	@UnitInject
	private SshServer server;

	private static SshServer oldServer;

	@Test
	@SftpServer(@SftpFile(name = "/dummy/foo.txt", content = "Awesome"))
	public void checkServer() throws Exception {
		assertNotNull(server);
		assertTrue(server.getPort() > 0);
		assertEquals(Integer.toString(server.getPort()), System.getProperty(SftpServer.PORT));
		oldServer = server;

		final JSch jSch = new JSch();
		final Session session = jSch.getSession("test", "localhost", server.getPort());
		session.setPassword("testpwd");
		session.setConfig(new Properties() {{ put("StrictHostKeyChecking", "no"); }});
		session.connect();
		final ChannelSftp channel = ChannelSftp.class.cast(session.openChannel("sftp"));
		channel.connect();
		final ByteArrayOutputStream dst = new ByteArrayOutputStream();
		channel.get("/dummy/foo.txt", dst);
		assertEquals("Awesome", new String(dst.toByteArray()));
	}

	@AfterClass
	public static void checkServerIsOff() {
		assertTrue(oldServer.isClosed());
	}
}
