package com.github.rmannibucau.rules.api.ftp;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({ TYPE, METHOD })
@Retention(RUNTIME)
public @interface FtpServer {
	// after before it sets the port in this system property
	final String FTP_PORT = "com.github.rmannibucau.rules.ftp.port";

	FtpFile[] files() default {};
	int port() default 0; // random
	String user() default "test";
	String password() default "testpwd";
	String root() default "/";
}
