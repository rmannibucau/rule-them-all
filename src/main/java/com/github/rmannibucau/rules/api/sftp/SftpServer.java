package com.github.rmannibucau.rules.api.sftp;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({ TYPE, METHOD })
@Retention(RUNTIME)
public @interface SftpServer {
	final String PORT = "com.swissquote.findata.unit.sftp.port";

	SftpFile[] value() default {};
	int port() default 0; // random
	String user() default "test";
	String password() default "testpwd";
}
