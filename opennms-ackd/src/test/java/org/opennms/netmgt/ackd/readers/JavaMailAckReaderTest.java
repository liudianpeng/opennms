/*
 * This file is part of the OpenNMS(R) Application.
 *
 * OpenNMS(R) is Copyright (C) 2009 The OpenNMS Group, Inc.  All rights reserved.
 * OpenNMS(R) is a derivative work, containing both original code, included code and modified
 * code that was published under the GNU General Public License. Copyrights for modified
 * and included code are below.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * Modifications:
 * 
 * Created: January 27, 2009
 *
 * Copyright (C) 2009 The OpenNMS Group, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * For more information contact:
 *      OpenNMS Licensing       <license@opennms.org>
 *      http://www.opennms.org/
 *      http://www.opennms.com/
 */
package org.opennms.netmgt.ackd.readers;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.javamail.JavaMailerException;
import org.opennms.javamail.JavaSendMailer;
import org.opennms.netmgt.ackd.Ackd;
import org.opennms.netmgt.config.ackd.AckdConfiguration;
import org.opennms.netmgt.config.common.End2endMailConfig;
import org.opennms.netmgt.config.common.ReadmailConfig;
import org.opennms.netmgt.config.common.ReadmailHost;
import org.opennms.netmgt.config.common.ReadmailProtocol;
import org.opennms.netmgt.config.common.SendmailConfig;
import org.opennms.netmgt.config.common.SendmailHost;
import org.opennms.netmgt.config.common.SendmailMessage;
import org.opennms.netmgt.config.common.SendmailProtocol;
import org.opennms.netmgt.config.common.UserAuth;
import org.opennms.netmgt.dao.AckdConfigurationDao;
import org.opennms.netmgt.dao.JavaMailConfigurationDao;
import org.opennms.netmgt.dao.castor.DefaultAckdConfigurationDao;
import org.opennms.netmgt.dao.db.OpenNMSConfigurationExecutionListener;
import org.opennms.netmgt.dao.db.TemporaryDatabaseExecutionListener;
import org.opennms.netmgt.model.AckAction;
import org.opennms.netmgt.model.AckType;
import org.opennms.netmgt.model.OnmsAcknowledgment;
import org.opennms.netmgt.model.acknowledgments.AckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({
    OpenNMSConfigurationExecutionListener.class,
    TemporaryDatabaseExecutionListener.class,
    DependencyInjectionTestExecutionListener.class,
    DirtiesContextTestExecutionListener.class,
    TransactionalTestExecutionListener.class
})
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-daemon.xml",
        "classpath*:/META-INF/opennms/component-service.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/applicationContext-ackd.xml" })

/**
 * Integration test of for the Javamail Acknowledgement Reader Implementation.
 */
public class JavaMailAckReaderTest {

    @Autowired
    private Ackd m_daemon;
    
    @Autowired
    private JavaMailConfigurationDao m_jmDao;
    
    @Autowired
    private MailAckProcessor m_processor;

    @Autowired
    private AckService m_ackService;

    
    @Test
    public void verifyWiring() {
        Assert.assertNotNull(m_ackService);
        Assert.assertNotNull(m_daemon);
        Assert.assertNotNull(m_jmDao);
        Assert.assertNotNull(m_processor);
    }
    
    /**
     * tests the ability to detect an aknowledgable ID
     */
    @Test
    public void detectId() {
        String expression = m_daemon.getConfigDao().getConfig().getNotifyidMatchExpression();
        Integer id = MailAckProcessor.detectId("Notice #1234", expression);
        Assert.assertEquals(new Integer(1234), id);
    }

    /**
     * tests the ability to create acknowledgments from an email for plain text.  This test
     * creates a message from scratch rather than reading from an inbox. 
     */
    @Test
    public void workingWithSimpleTextMessages() {
        Properties props = new Properties();
        Message msg = new MimeMessage(Session.getDefaultInstance(props));
        try {
            Address[] addrs = new Address[1];
            addrs[0] = new InternetAddress("david@opennms.org");
            msg.addFrom(addrs);
            msg.addRecipient(javax.mail.internet.MimeMessage.RecipientType.TO, addrs[0]);
            msg.setSubject("Notice #1234 JavaMailReaderImplTest Test Message");
            msg.setText("ack");
        } catch (AddressException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        List<Message> msgs = new ArrayList<Message>(1);
        msgs.add(msg);
        List<OnmsAcknowledgment> acks = MailAckProcessor.createAcks(msgs);
        
        Assert.assertEquals(1, acks.size());
        Assert.assertEquals(AckType.NOTIFICATION, acks.get(0).getAckType());
        Assert.assertEquals("david@opennms.org", acks.get(0).getAckUser());
        Assert.assertEquals(AckAction.ACKNOWLEDGE, acks.get(0).getAckAction());
        Assert.assertEquals(new Integer(1234), acks.get(0).getRefId());
    }
    
    /**
     * tests the ability to create acknowledgments from an email for a multi-part text.  This test
     * creates a message from scratch rather than reading from an inbox.  This message creation
     * may not actually represent what comes from a mail server.
     */
    @Test
    public void workingWithMultiPartMessages() throws JavaMailerException, MessagingException {
        List<Message> msgs = new ArrayList<Message>();
        Properties props = new Properties();
        Message msg = new MimeMessage(Session.getDefaultInstance(props));
        Address[] addrs = new Address[1];
        addrs[0] = new InternetAddress("david@opennms.org");
        msg.addFrom(addrs);
        msg.addRecipient(RecipientType.TO, new InternetAddress("david@opennms.org"));
        msg.setSubject("Notice #1234 JavaMailReaderImplTest Test Message");
        Multipart mpContent = new MimeMultipart();
        BodyPart textBp = new MimeBodyPart();
        BodyPart htmlBp = new MimeBodyPart();
        textBp.setText("ack");
        htmlBp.setContent("<html>\n" + 
        		" <head>\n" + 
        		"  <title>\n" + 
        		"   Acknowledge\n" + 
        		"  </title>\n" + 
        		" </head>\n" + 
        		" <body>\n" + 
        		"  <h1>\n" + 
        		"   ack\n" + 
        		"  </h1>\n" + 
        		" </body>\n" + 
        		"</html>", "text/html");
        
        mpContent.addBodyPart(textBp);
        mpContent.addBodyPart(htmlBp);
        msg.setContent(mpContent);
        
        msgs.add(msg);
        
        List<OnmsAcknowledgment> acks = MailAckProcessor.createAcks(msgs);
        Assert.assertEquals(1, acks.size());
        Assert.assertEquals(AckType.NOTIFICATION, acks.get(0).getAckType());
        Assert.assertEquals("david@opennms.org", acks.get(0).getAckUser());
        Assert.assertEquals(AckAction.ACKNOWLEDGE, acks.get(0).getAckAction());
        Assert.assertEquals(new Integer(1234), acks.get(0).getRefId());
    }

    @Test
    @Ignore
    public void findAndProcessAcks() throws InterruptedException {
        JavaMailAckReader reader = new JavaMailAckReader();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        reader.setMailAckProcessor(m_processor);
        Future<?> f = executor.schedule(m_processor, 5, TimeUnit.SECONDS);
        m_processor.setJmConfigDao(new JmCnfDao());
        m_processor.setAckService(m_ackService);
        m_processor.setAckdConfigDao(createAckdConfigDao());
        //Thread.sleep(20000);
        while (!f.isDone()) {
            Thread.sleep(10);
        }
        Assert.assertTrue(f.isDone());
    }


    private AckdConfigurationDao createAckdConfigDao() {
        
        class AckdConfigDao extends DefaultAckdConfigurationDao {

            public AckdConfiguration getConfig() {
                AckdConfiguration config = new AckdConfiguration();
                config.setAckExpression("~^ack$");
                config.setAlarmidMatchExpression("~.*alarmid:([0-9]+).*");
                config.setAlarmSync(true);
                config.setClearExpression("~^(resolve|clear)$");
                config.setEscalateExpression("~^esc$");
                config.setNotifyidMatchExpression("~.*Re:.*Notice #([0-9]+).*");
                config.setReadmailConfig("default");
                config.setUnackExpression("~^unack$");
                return config;
            }

        }
        
        return new AckdConfigDao();
        
    }


    protected class JmCnfDao implements JavaMailConfigurationDao {
        
        ReadmailConfig m_readConfig = createReadMailConfig();
        SendmailConfig m_sendConfig = createSendMailConfig();
        End2endMailConfig m_e2eConfig = createE2Ec();
        

        public ReadmailConfig getDefaultReadmailConfig() {
            return m_readConfig;
        }

        private ReadmailConfig createReadMailConfig() {
            ReadmailConfig config = new ReadmailConfig();
            updateConfigWithGoogleReadConfiguration(config, getUser(), getPassword());
            m_readConfig = config;
            return m_readConfig;
        }

        private End2endMailConfig createE2Ec() {
            return new End2endMailConfig();
        }

        private SendmailConfig createSendMailConfig() {
            return new SendmailConfig();
        }

        public SendmailConfig getDefaultSendmailConfig() {
            return m_sendConfig;
        }

        public End2endMailConfig getEnd2EndConfig(String name) {
            return m_e2eConfig;
        }

        public List<End2endMailConfig> getEnd2EndConfigs() {
            List<End2endMailConfig> list = new ArrayList<End2endMailConfig>();
            list.add(m_e2eConfig);
            return list;
        }

        public ReadmailConfig getReadMailConfig(String name) {
            return m_readConfig;
        }

        public List<ReadmailConfig> getReadmailConfigs() {
            List<ReadmailConfig> list = new ArrayList<ReadmailConfig>();
            list.add(m_readConfig);
            return list;
        }

        public SendmailConfig getSendMailConfig(String name) {
            return m_sendConfig;
        }

        public List<SendmailConfig> getSendmailConfigs() {
            List<SendmailConfig> list = new ArrayList<SendmailConfig>();
            list.add(m_sendConfig);
            return list;
        }

        public void verifyMarshaledConfiguration() throws IllegalStateException {
        }
        
    }
    
    @Ignore
    public void createAcknowledgment() {
        fail("Not yet implemented");
    }

    @Ignore
    public void determineAckAction() {
        fail("Not yet implemented");
    }

    @Ignore
    public void start() {
        fail("Not yet implemented");
    }

    @Ignore
    public void pause() {
        fail("Not yet implemented");
    }

    @Ignore
    public void resume() {
        fail("Not yet implemented");
    }

    @Ignore
    public void stop() {
        fail("Not yet implemented");
    }

    
    /**
     * This test requires that 4 emails can be read from a Google account.  The mails should be
     * in this order:
     * Subject matching ackd-configuration expression of action type ack
     * Subject matching ackd-configuration expression of action type ack
     * Subject matching ackd-configuration expression of action type ack
     * Subject matching ackd-configuration expression of action type clear
     * 
     * The test has been updated to now include sending an email message to a gmail account.  Just correct
     * the account details for your own local testing.
     * 
     * @throws JavaMailerException 
     * 
     */
    @Test
    @Ignore
    public void testIntegration() throws JavaMailerException {
        
        String gmailAccount = getUser();
        String gmailPassword = getPassword();
        
        JavaSendMailer sendMailer = createSendMailer(gmailAccount, gmailPassword);
        
        SendmailMessage sendMsg = createAckMessage(gmailAccount, "1", "ack");
        sendMailer.setMessage(sendMailer.buildMimeMessage(sendMsg));
        sendMailer.send();
        sendMsg = createAckMessage(gmailAccount, "2", "ack");
        sendMailer.setMessage(sendMailer.buildMimeMessage(sendMsg));
        sendMailer.send();
        sendMsg = createAckMessage(gmailAccount, "3", "ack");
        sendMailer.setMessage(sendMailer.buildMimeMessage(sendMsg));
        sendMailer.send();
        sendMsg = createAckMessage(gmailAccount, "4", "clear");
        sendMailer.setMessage(sendMailer.buildMimeMessage(sendMsg));
        sendMailer.send();
        
        //this is bad mojo
        String readmailConfig = m_daemon.getConfigDao().getConfig().getReadmailConfig();
        Assert.assertNotNull(readmailConfig);
        ReadmailConfig config = m_jmDao.getReadMailConfig(readmailConfig);
        updateConfigWithGoogleReadConfiguration(config, gmailAccount, gmailPassword);
        
        List<Message> msgs = MailAckProcessor.retrieveAckMessages();
        
        List<OnmsAcknowledgment> acks = MailAckProcessor.createAcks(msgs);
        
        Assert.assertNotNull(acks);
        Assert.assertEquals(4, acks.size());
        
        Assert.assertEquals(AckType.NOTIFICATION, acks.get(0).getAckType());
        Assert.assertEquals(AckAction.ACKNOWLEDGE, acks.get(0).getAckAction());
        Assert.assertEquals(Integer.valueOf(1), acks.get(0).getRefId());
        Assert.assertEquals(getUser()+"@gmail.com", acks.get(0).getAckUser());
        
        Assert.assertEquals(AckType.NOTIFICATION, acks.get(1).getAckType());
        Assert.assertEquals(AckAction.ACKNOWLEDGE, acks.get(1).getAckAction());
        Assert.assertEquals(Integer.valueOf(2), acks.get(1).getRefId());
        Assert.assertEquals(getUser()+"@gmail.com", acks.get(1).getAckUser());
        
        Assert.assertEquals(AckType.NOTIFICATION, acks.get(2).getAckType());
        Assert.assertEquals(AckAction.ACKNOWLEDGE, acks.get(2).getAckAction());
        Assert.assertEquals(Integer.valueOf(3), acks.get(2).getRefId());
        Assert.assertEquals(getUser()+"@gmail.com", acks.get(2).getAckUser());
        
        Assert.assertEquals(AckType.NOTIFICATION, acks.get(3).getAckType());
        Assert.assertEquals(AckAction.CLEAR, acks.get(3).getAckAction());
        Assert.assertEquals(Integer.valueOf(4), acks.get(3).getRefId());
        Assert.assertEquals(getUser()+"@gmail.com", acks.get(3).getAckUser());
    }

    private String getPassword() {
        return "bar";
    }

    private String getUser() {
        return "foo";
    }

    private SendmailMessage createAckMessage(String gmailAccount, String noticeId, String body) {
        SendmailMessage sendMsg = new SendmailMessage();
        sendMsg.setTo(gmailAccount+"@gmail.com");
        sendMsg.setFrom(gmailAccount+"@gmail.com");
        sendMsg.setSubject("Re: Notice #"+noticeId+":");
        sendMsg.setBody(body);
        return sendMsg;
    }

    private JavaSendMailer createSendMailer(String gmailAccount, String gmailPassword) throws JavaMailerException {
        
        SendmailConfig config = new SendmailConfig();
        
        config.setAttemptInterval(1000);
        config.setDebug(true);
        config.setName("test");
        
        SendmailMessage sendmailMessage = new SendmailMessage();
        sendmailMessage.setBody("service is down");
        sendmailMessage.setFrom("bamboo.opennms@gmail.com");
        sendmailMessage.setSubject("Notice #1234: service down");
        sendmailMessage.setTo("bamboo.opennms@gmail.com");
        config.setSendmailMessage(sendmailMessage);
        
        SendmailHost host = new SendmailHost();
        host.setHost("smtp.gmail.com");
        host.setPort(465);
        config.setSendmailHost(host);
        
        SendmailProtocol protocol = new SendmailProtocol();
        protocol.setSslEnable(true);
        protocol.setTransport("smtps");
        config.setSendmailProtocol(protocol);
        
        config.setUseAuthentication(true);
        config.setUseJmta(false);
        UserAuth auth = new UserAuth();
        auth.setUserName(gmailAccount);
        auth.setPassword(gmailPassword);
        config.setUserAuth(auth);
        
        return new JavaSendMailer(config);
    }
    
    private void updateConfigWithGoogleReadConfiguration(ReadmailConfig config, String gmailAccount, String gmailPassword) {
        config.setDebug(true);
        config.setDeleteAllMail(false);
        config.setMailFolder("INBOX");
        ReadmailHost readmailHost = new ReadmailHost();
        readmailHost.setHost("imap.gmail.com");
        readmailHost.setPort(993);
        ReadmailProtocol readmailProtocol = new ReadmailProtocol();
        readmailProtocol.setSslEnable(true);
        readmailProtocol.setStartTls(false);
        readmailProtocol.setTransport("imaps");
        readmailHost.setReadmailProtocol(readmailProtocol);
        config.setReadmailHost(readmailHost);
        UserAuth userAuth = new UserAuth();
        userAuth.setPassword(gmailPassword);
        userAuth.setUserName(gmailAccount);
        config.setUserAuth(userAuth);
    }

}
