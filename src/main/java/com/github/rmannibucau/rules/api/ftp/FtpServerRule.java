package com.github.rmannibucau.rules.api.ftp;

import static com.github.rmannibucau.rules.internal.Reflections.findFields;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockftpserver.core.util.IoUtil;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import com.github.rmannibucau.rules.api.LifecycleUnitException;
import com.github.rmannibucau.rules.api.UnitInject;
import com.github.rmannibucau.rules.internal.Reflections;

public class FtpServerRule implements TestRule {
	public static final String CLASSPATH_PREFIX = "classpath:";

	private final Object instance;

	public FtpServerRule() {
		this(null);
	}

	public FtpServerRule(final Object instance) {
		this.instance = instance;
	}

	@Override
	public Statement apply(final Statement base, final Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				FtpServer server = description.getAnnotation(FtpServer.class);
				if (server == null) {
					server = description.getTestClass().getAnnotation(FtpServer.class);
				}

				if (server == null) {
					base.evaluate();
					return;
				}

				final UnixFakeFileSystem fileSystem = new UnixFakeFileSystem();
				fileSystem.setCreateParentDirectoriesAutomatically(true);
				for (final FtpFile file : server.files()) {
					fileSystem.add(new FileEntry('/' + file.name(), content(file.content())));
				}

				final FakeFtpServer ftp = new FakeFtpServer();
				ftp.addUserAccount(new UserAccount(server.user(), server.password(), server.root()));
				ftp.setFileSystem(fileSystem);
				ftp.setServerControlPort(server.port());
				ftp.start();

				System.setProperty(FtpServer.FTP_PORT, Integer.toString(ftp.getServerControlPort()));

				inject(fileSystem, ftp);

				try {
					base.evaluate();
				} finally {
					ftp.stop();
				}
			}
		};
	}

	private void inject(final UnixFakeFileSystem fileSystem, final FakeFtpServer ftp) throws IllegalAccessException {
		for (final Field f : findFields(instance.getClass(), UnitInject.class)) {
			if (FileSystem.class.equals(f.getType())) {
				checks(f);
				Reflections.set(f, instance, fileSystem);
			} else if (FakeFtpServer.class.equals(f.getType())) {
				checks(f);
				Reflections.set(f, instance, ftp);
			}
		}
	}

	private void checks(final Field f) {
		if (Reflections.get(f, instance, null) != null) {
			throw new LifecycleUnitException("Field already set: " + f.getName());
		} else if (instance == null) {
			throw new LifecycleUnitException("Pass test instance as parameter constructor if you want to inject FileSystem or FtpServer");
		}
	}

	private static String content(final String content) {
		if (content.startsWith(CLASSPATH_PREFIX)) {
			try {
				return new String(IoUtil.readBytes(
						Thread.currentThread().getContextClassLoader().getResourceAsStream(
								content.substring(CLASSPATH_PREFIX.length()))
				));
			} catch (IOException e) {
				throw new LifecycleUnitException(e);
			}
		}
		return content;
	}
}
