package com.github.rmannibucau.rules.api.sftp;

import com.github.rmannibucau.rules.api.LifecycleUnitException;
import com.github.rmannibucau.rules.api.UnitInject;
import com.github.rmannibucau.rules.internal.Reflections;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.file.FileSystemView;
import org.apache.sshd.common.file.SshFile;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPassword;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockftpserver.core.util.IoUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SftpServerRule implements TestRule {
	public static final String CLASSPATH_PREFIX = "classpath:";

	private final Object instance;

	private ThreadLocal<Data> data = new ThreadLocal<Data>() {
		@Override
		protected Data initialValue() {
			return new Data();
		}
	};

	public SftpServerRule() {
		this(null);
	}

	public SftpServerRule(final Object instance) {
		this.instance = instance;
	}

	@Override
	public Statement apply(final Statement base, final Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				SftpServer server = description.getAnnotation(SftpServer.class);
				if (server == null) {
					server = description.getTestClass().getAnnotation(SftpServer.class);
				}

				if (server == null) {
					base.evaluate();
					return;
				}

				final Data d = data.get();
				d.user = server.user();
				d.password = server.password();
				d.files = new ConcurrentHashMap<String, FakeSftpFile>();

				for (final SftpFile file : server.value()) {
					final String name = file.name();
					final String content = file.content();
					addFile(name, content);
				}
				d.files.put("/", new FakeSftpFile("/", null, d.user, false, d.files));
				d.files.put(".", new FakeSftpFile(".", null, d.user, false, d.files));

				final SshServer ssh = SshServer.setUpDefaultServer();
				ssh.setPort(server.port());
				ssh.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
				ssh.setUserAuthFactories(Arrays.<NamedFactory<UserAuth>> asList(new UserAuthPassword.Factory()));
				ssh.setPasswordAuthenticator(new PasswordAuthenticator() {
					@Override
					public boolean authenticate(final String username, final String password, final ServerSession session) {
						return d.user.equals(username) && password.equals(password);
					}
				});
				ssh.setCommandFactory(new ScpCommandFactory());
				ssh.setSubsystemFactories(Arrays.<NamedFactory<Command>> asList(new SftpSubsystem.Factory()));
				ssh.setFileSystemFactory(new FileSystemFactory() {
					@Override
					public FileSystemView createFileSystemView(final Session session) throws IOException {
						return new FileSystemView() {
							@Override
							public SshFile getFile(final String file) {
								return d.files.get(file);
							}

							@Override
							public SshFile getFile(final SshFile baseDir, final String file) {
								return getFile(baseDir.getAbsolutePath() + '/' + file);
							}

							@Override
							public FileSystemView getNormalizedView() {
								return this;
							}
						};
					}
				});

				ssh.start();

				System.setProperty(SftpServer.PORT, Integer.toString(ssh.getPort()));
				inject(ssh);

				try {
					base.evaluate();
				}
				finally {
					data.remove();
					ssh.stop();
				}
			}
		};
	}

	public String getUser() {
		return data.get().user;
	}

	public String getPassword() {
		return data.get().password;
	}

	public void addFile(final String name, final String contentOrPath) {
		final Data d = data.get();
		final String key = (!name.startsWith("/") ? "/" : "") + name;
		d.files.put(key, new FakeSftpFile(key, content(contentOrPath), d.user, true, Collections.<String, FakeSftpFile> emptyMap()));
		final String[] path = key.split("/");
		final StringBuilder folder = new StringBuilder();
		for (int i = 0; i < path.length - 1; i++) {
			if (i > 0) {
				folder.append('/');
			}
			folder.append(path[ i ]);

			final String current = folder.toString();
			d.files.putIfAbsent(current, new FakeSftpFile(current, null, d.user, false, d.files));
		}
	}

	private void inject(final SshServer server) throws IllegalAccessException {
		for (final Field f : Reflections.findFields(instance.getClass(), UnitInject.class)) {
			if (SshServer.class.equals(f.getType())) {
				checks(f);
				Reflections.set(f, instance, server);
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

	private static byte[] content(final String content) {
		if (content.startsWith(CLASSPATH_PREFIX)) {
			try {
				final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
				return IoUtil.readBytes(contextClassLoader.getResourceAsStream(content.substring(CLASSPATH_PREFIX.length())));
			}
			catch (IOException e) {
				throw new LifecycleUnitException(e);
			}
		}
		return content.getBytes();
	}

	private static class FakeSftpFile implements SshFile {
		private final String path;
		private final boolean isRegular;
		private final byte[] entry;
		private final String user;
		private final Map<String, FakeSftpFile> all;
		private Map<Attribute, Object> attributes = new HashMap<Attribute, Object>();
		private long lastModified;

		public FakeSftpFile(final String path, final byte[] content, final String user, final boolean isRegular,
				final Map<String, FakeSftpFile> files) {
			this.path = path;
			this.entry = content;
			this.user = user;
			this.isRegular = isRegular;
			this.lastModified = System.currentTimeMillis();
			this.all = files;

			initAttributes();
		}

		private void initAttributes() {
			this.attributes.put(Attribute.CreationTime, lastModified);
			this.attributes.put(Attribute.IsDirectory, isDirectory());
			this.attributes.put(Attribute.IsRegularFile, isFile());
			this.attributes.put(Attribute.IsSymbolicLink, false);
			this.attributes.put(Attribute.Size, getSize());
			this.attributes.put(Attribute.Permissions, EnumSet.allOf(Permission.class));
			this.attributes.put(Attribute.Owner, getOwner());
			this.attributes.put(Attribute.Group, getOwner());
			this.attributes.put(Attribute.LastModifiedTime, getLastModified());
		}

		@Override
		public String getAbsolutePath() {
			return path;
		}

		@Override
		public String getName() {
			return path;
		}

		@Override
		public Map<Attribute, Object> getAttributes(final boolean followLinks) throws IOException {
			return attributes;
		}

		@Override
		public void setAttributes(final Map<Attribute, Object> attributes) throws IOException {
			this.attributes = attributes;
		}

		@Override
		public Object getAttribute(final Attribute attribute, final boolean followLinks) throws IOException {
			return attributes.get(attribute);
		}

		@Override
		public void setAttribute(final Attribute attribute, final Object value) throws IOException {
			attributes.put(attribute, value);
		}

		@Override
		public String readSymbolicLink() throws IOException {
			return null;
		}

		@Override
		public void createSymbolicLink(final SshFile destination) throws IOException {
			// no-op
		}

		@Override
		public String getOwner() {
			return user;
		}

		@Override
		public boolean isDirectory() {
			return !isRegular;
		}

		@Override
		public boolean isFile() {
			return isRegular;
		}

		@Override
		public boolean doesExist() {
			return true;
		}

		@Override
		public boolean isReadable() {
			return true;
		}

		@Override
		public boolean isWritable() {
			return false;
		}

		@Override
		public boolean isExecutable() {
			return false;
		}

		@Override
		public boolean isRemovable() {
			return false;
		}

		@Override
		public SshFile getParentFile() {
			return null;
		}

		@Override
		public long getLastModified() {
			return lastModified;
		}

		@Override
		public boolean setLastModified(final long time) {
			lastModified = time;
			return true;
		}

		@Override
		public long getSize() {
			return entry != null ? entry.length : -1;
		}

		@Override
		public boolean mkdir() {
			return isDirectory();
		}

		@Override
		public boolean delete() {
			return false;
		}

		@Override
		public boolean create() throws IOException {
			return false;
		}

		@Override
		public void truncate() throws IOException {
			// no-op
		}

		@Override
		public boolean move(final SshFile destination) {
			return false;
		}

		@Override
		public List<SshFile> listSshFiles() {
			final List<SshFile> children = new LinkedList<SshFile>();
			if (!isDirectory()) {
				return children;
			}

			for (final Map.Entry<String, FakeSftpFile> entry : all.entrySet()) {
				final FakeSftpFile value = entry.getValue();
				if (entry.getKey().startsWith(path) && value != this) {
					children.add(value);
				}
			}
			return children;
		}

		@Override
		public OutputStream createOutputStream(final long offset) throws IOException {
			return new ByteArrayOutputStream();
		}

		@Override
		public InputStream createInputStream(final long offset) throws IOException {
			return new ByteArrayInputStream(entry != null ? entry: new byte[0]);
		}g

		@Override
		public void handleClose() throws IOException {
			// no-op
		}
	}

	private static class Data {
		private String user;
		private String password;
		private ConcurrentMap<String, FakeSftpFile> files;
	}
}
