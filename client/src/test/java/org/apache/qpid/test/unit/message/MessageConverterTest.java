/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.test.unit.message;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.apache.qpid.test.utils.QpidTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.MockAMQConnection;
import org.apache.qpid.client.message.AMQMessageDelegateFactory;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.JMSMapMessage;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.client.message.MessageConverter;
import org.apache.qpid.exchange.ExchangeDefaults;


public class MessageConverterTest extends QpidTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(MessageConverterTest.class);
    public static final String JMS_CORR_ID = "QPIDID_01";
    public static final int JMS_DELIV_MODE = 1;
    public static final String JMS_TYPE = "test.jms.type";
    public static final Destination JMS_REPLY_TO = new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME,"my.replyto");

    protected JMSTextMessage testTextMessage;

    protected JMSMapMessage testMapMessage;
    private AMQConnection _connection;
    private AMQSession _session;


    protected void setUp() throws Exception
    {
        super.setUp();

        _connection =  new MockAMQConnection("amqp://guest:guest@client/test?brokerlist='tcp://localhost:1'");
        _session = new TestAMQSession(_connection);

        testTextMessage = new JMSTextMessage(AMQMessageDelegateFactory.FACTORY_0_8);

        //Set Message Text
        testTextMessage.setText("testTextMessage text");
        setMessageProperties(testTextMessage);

        testMapMessage = new JMSMapMessage(AMQMessageDelegateFactory.FACTORY_0_8);
        testMapMessage.setString("testMapString", "testMapStringValue");
        testMapMessage.setDouble("testMapDouble", Double.MAX_VALUE);
    }

    public void testSetProperties() throws Exception
    {
        AbstractJMSMessage newMessage = new MessageConverter(_session, (TextMessage) testTextMessage).getConvertedMessage();
        mesagePropertiesTest(testTextMessage, newMessage);
    }

    public void testJMSTextMessageConversion() throws Exception
    {
        AbstractJMSMessage newMessage = new MessageConverter(_session, (TextMessage) testTextMessage).getConvertedMessage();
        assertEquals("Converted message text mismatch", ((JMSTextMessage) newMessage).getText(), testTextMessage.getText());
    }

    public void testJMSMapMessageConversion() throws Exception
    {
        AbstractJMSMessage newMessage = new MessageConverter(_session, (MapMessage) testMapMessage).getConvertedMessage();
        assertEquals("Converted map message String mismatch", ((JMSMapMessage) newMessage).getString("testMapString"),
                     testMapMessage.getString("testMapString"));
        assertEquals("Converted map message Double mismatch", ((JMSMapMessage) newMessage).getDouble("testMapDouble"),
                     testMapMessage.getDouble("testMapDouble"));

    }

    private void setMessageProperties(Message message) throws JMSException
    {
        message.setJMSCorrelationID(JMS_CORR_ID);
        message.setJMSDeliveryMode(JMS_DELIV_MODE);
        message.setJMSType(JMS_TYPE);
        message.setJMSReplyTo(JMS_REPLY_TO);

        //Add non-JMS properties
        message.setStringProperty("testProp1", "testValue1");
        message.setDoubleProperty("testProp2", Double.MIN_VALUE);
    }


    private void mesagePropertiesTest(Message expectedMessage, Message actualMessage)
    {
        try
        {
            //check JMS prop values on newMessage match
            assertEquals("JMS Correlation ID mismatch", expectedMessage.getJMSCorrelationID(), actualMessage.getJMSCorrelationID());
            assertEquals("JMS Delivery mode mismatch", expectedMessage.getJMSDeliveryMode(), actualMessage.getJMSDeliveryMode());
            assertEquals("JMS Type mismatch", expectedMessage.getJMSType(), actualMessage.getJMSType());
            assertEquals("JMS Reply To mismatch", expectedMessage.getJMSReplyTo(), actualMessage.getJMSReplyTo());

            //check non-JMS standard props ok too
            assertEquals("Test String prop value mismatch", expectedMessage.getStringProperty("testProp1"),
                         actualMessage.getStringProperty("testProp1"));

            assertEquals("Test Double prop value mismatch", expectedMessage.getDoubleProperty("testProp2"),
                         actualMessage.getDoubleProperty("testProp2"));
        }
        catch (JMSException e)
        {
            _logger.error("An error occured testing the property values", e);
            fail("An error occured testing the property values" + e.getCause());
        }
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        testTextMessage = null;
    }


}
