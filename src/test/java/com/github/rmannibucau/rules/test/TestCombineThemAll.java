package com.github.rmannibucau.rules.test;

import com.github.rmannibucau.rules.api.ftp.FtpServer;
import com.github.rmannibucau.rules.api.ftp.FtpServerRule;
import com.github.rmannibucau.rules.api.spring.SpringRule;
import com.github.rmannibucau.rules.api.systemproperty.PlaceHoldableSystemProperties;
import com.github.rmannibucau.rules.api.systemproperty.SystemProperties;
import com.github.rmannibucau.rules.api.systemproperty.SystemProperty;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.Assert.assertTrue;

@SystemProperties(@SystemProperty(key = "ftp.port", value = "${" + FtpServer.FTP_PORT + '}'))
@ContextConfiguration("classpath:ftp.xml")
public class TestCombineThemAll {
    @Rule
    public RuleChain chain = RuleChain.outerRule(new FtpServerRule(this))
            .around(new PlaceHoldableSystemProperties())
            .around(new SpringRule(this));

    @Autowired
    @Qualifier("port")
    private int port;

    @Test
    @FtpServer
    public void run() {
        assertTrue(port > 0);
    }
}
