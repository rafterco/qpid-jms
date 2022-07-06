/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.test.unit.client.BrokerDetails;

import org.apache.qpid.client.BrokerDetails;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.url.URLSyntaxException;

public class BrokerDetailsTest extends QpidTestCase
{
    public void testDefaultTCP_NODELAY() throws URLSyntaxException
    {
        String brokerURL = "tcp://localhost:5672";
        BrokerDetails broker = new BrokerDetails(brokerURL);

        assertNull("default value should be null", broker.getProperty(BrokerDetails.OPTIONS_TCP_NO_DELAY));
    }

    public void testOverridingTCP_NODELAY() throws URLSyntaxException
    {
        String brokerURL = "tcp://localhost:5672?tcp_nodelay='true'";
        BrokerDetails broker = new BrokerDetails(brokerURL);

        assertTrue("value should be true", Boolean.valueOf(broker.getProperty(BrokerDetails.OPTIONS_TCP_NO_DELAY)));

        brokerURL = "tcp://localhost:5672?tcp_nodelay='false''&maxprefetch='1'";
        broker = new BrokerDetails(brokerURL);

        assertFalse("value should be false", Boolean.valueOf(broker.getProperty(BrokerDetails.OPTIONS_TCP_NO_DELAY)));
    }

    public void testDefaultConnectTimeout() throws URLSyntaxException
    {
        String brokerURL = "tcp://localhost:5672";
        BrokerDetails broker = new BrokerDetails(brokerURL);

        ConnectionSettings settings = broker.buildConnectionSettings();

        assertEquals("unexpected default connect timeout value",
                     BrokerDetails.DEFAULT_CONNECT_TIMEOUT, settings.getConnectTimeout());
    }

    public void testOverridingConnectTimeout() throws URLSyntaxException
    {
        int timeout = 2 * BrokerDetails.DEFAULT_CONNECT_TIMEOUT;
        assertTrue(timeout != BrokerDetails.DEFAULT_CONNECT_TIMEOUT);

        String brokerURL = "tcp://localhost:5672?" + BrokerDetails.OPTIONS_CONNECT_TIMEOUT + "='" + timeout + "'";
        BrokerDetails broker = new BrokerDetails(brokerURL);

        ConnectionSettings settings = broker.buildConnectionSettings();

        assertEquals("unexpected connect timeout value", timeout, settings.getConnectTimeout());
    }

    public void testMultiParameters() throws URLSyntaxException
    {
        String url = "tcp://localhost:5672?timeout='200',immediatedelivery='true'";

        BrokerDetails broker = new BrokerDetails(url);

        assertTrue(broker.getProperty("timeout").equals("200"));
        assertTrue(broker.getProperty("immediatedelivery").equals("true"));
    }

    public void testTransportsDefaultToTCP() throws URLSyntaxException
    {
        String url = "localhost:5672";

        BrokerDetails broker = new BrokerDetails(url);
        assertTrue(broker.getTransport().equals("tcp"));
    }

    public void testCheckDefaultPort() throws URLSyntaxException
    {
        String url = "tcp://localhost";

        BrokerDetails broker = new BrokerDetails(url);
        assertTrue(broker.getPort() == BrokerDetails.DEFAULT_PORT);
    }

    public void testBothDefaults() throws URLSyntaxException
    {
        String url = "localhost";

        BrokerDetails broker = new BrokerDetails(url);

        assertTrue(broker.getTransport().equals("tcp"));
        assertTrue(broker.getPort() == BrokerDetails.DEFAULT_PORT);
    }

    public void testWrongOptionSeparatorInBroker()
    {
        String url = "tcp://localhost:5672+option='value'";
        try
        {
            new BrokerDetails(url);
        }
        catch (URLSyntaxException urise)
        {
            assertTrue(urise.getReason().equals("Illegal character in port number"));
        }
    }

    public void testToStringMasksKeyStorePassword() throws Exception
    {
        String url = "tcp://localhost:5672?key_store_password='password'";
        BrokerDetails details = new BrokerDetails(url);

        String expectedToString = "tcp://localhost:5672?key_store_password='********'";
        String actualToString = details.toString();

        assertEquals("Unexpected toString", expectedToString, actualToString);
    }

    public void testToStringMasksTrustStorePassword() throws Exception
    {
        String url = "tcp://localhost:5672?trust_store_password='password'";
        BrokerDetails details = new BrokerDetails(url);

        String expectedToString = "tcp://localhost:5672?trust_store_password='********'";
        String actualToString = details.toString();

        assertEquals("Unexpected toString", expectedToString, actualToString);
    }

    public void testToStringMasksEncryptionTrustStorePassword() throws Exception
    {
        String url = "tcp://localhost:5672?encryption_trust_store_password='password'";
        BrokerDetails details = new BrokerDetails(url);

        String expectedToString = "tcp://localhost:5672?encryption_trust_store_password='********'";
        String actualToString = details.toString();

        assertEquals("Unexpected toString", expectedToString, actualToString);
    }

    public void testToStringMasksEncryptionKeyStorePassword() throws Exception
    {
        String url = "tcp://localhost:5672?encryption_key_store_password='password'";
        BrokerDetails details = new BrokerDetails(url);

        String expectedToString = "tcp://localhost:5672?encryption_key_store_password='********'";
        String actualToString = details.toString();

        assertEquals("Unexpected toString", expectedToString, actualToString);
    }

    public void testDefaultSsl() throws URLSyntaxException
    {
        String brokerURL = "tcp://localhost:5672";
        BrokerDetails broker = new BrokerDetails(brokerURL);

        assertNull("default value should be null", broker.getProperty(BrokerDetails.OPTIONS_SSL));
    }

    public void testOverridingSsl() throws URLSyntaxException
    {
        String brokerURL = "tcp://localhost:5672?ssl='true'";
        BrokerDetails broker = new BrokerDetails(brokerURL);

        assertTrue("value should be true", Boolean.valueOf(broker.getProperty(BrokerDetails.OPTIONS_SSL)));

        brokerURL = "tcp://localhost:5672?ssl='false''&maxprefetch='1'";
        broker = new BrokerDetails(brokerURL);

        assertFalse("value should be false", Boolean.valueOf(broker.getProperty(BrokerDetails.OPTIONS_SSL)));
    }

    public void testHeartbeatDefaultsToNull() throws Exception
    {
        String brokerURL = "tcp://localhost:5672";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        assertNull("unexpected default value for " + BrokerDetails.OPTIONS_HEARTBEAT, broker.getProperty(
                BrokerDetails.OPTIONS_HEARTBEAT));
    }

    public void testOverriddingHeartbeat() throws Exception
    {
        String brokerURL = "tcp://localhost:5672?heartbeat='60'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        assertEquals(60, Integer.parseInt(broker.getProperty(BrokerDetails.OPTIONS_HEARTBEAT)));

        assertEquals(Integer.valueOf(60), broker.buildConnectionSettings().getHeartbeatInterval08());
    }

    @SuppressWarnings("deprecation")
	public void testLegacyHeartbeat() throws Exception
    {
        String brokerURL = "tcp://localhost:5672?idle_timeout='60000'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        assertEquals(60000, Integer.parseInt(broker.getProperty("idle_timeout")));

        assertEquals(Integer.valueOf(60), broker.buildConnectionSettings().getHeartbeatInterval08());
    }

    public void testSslVerifyHostNameIsTurnedOnByDefault() throws Exception
    {
        String brokerURL = "tcp://localhost:5672?ssl='true'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        ConnectionSettings connectionSettings = broker.buildConnectionSettings();
        assertTrue(String.format("Unexpected '%s' option value", BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME),
                connectionSettings.isVerifyHostname());
        assertNull(String.format("Unexpected '%s' property value", BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME),
                broker.getProperty(BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME));
    }

    public void testSslVerifyHostNameIsTurnedOff() throws Exception
    {
        String brokerURL = "tcp://localhost:5672?ssl='true'&ssl_verify_hostname='false'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        ConnectionSettings connectionSettings = broker.buildConnectionSettings();
        assertFalse(String.format("Unexpected '%s' option value", BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME),
                connectionSettings.isVerifyHostname());
        assertEquals(String.format("Unexpected '%s' property value", BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME),
                "false", broker.getProperty(BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME));
    }

    public void testSslVerifyHostNameTurnedOffViaSystemProperty() throws Exception
    {
        setTestSystemProperty(ClientProperties.CONNECTION_OPTION_SSL_VERIFY_HOST_NAME, "false");
        String brokerURL = "tcp://localhost:5672?ssl='true'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        ConnectionSettings connectionSettings = broker.buildConnectionSettings();
        assertFalse(String.format("Unexpected '%s' option value", BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME),
                connectionSettings.isVerifyHostname());
        assertNull(String.format("Unexpected '%s' property value", BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME),
                broker.getProperty(BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME));
    }

    public void testEncryptionKeyStorePath() throws Exception
    {
        String brokerURL = "tcp://localhost:5672?ssl='true'&encryption_key_store='path'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        ConnectionSettings connectionSettings = broker.buildConnectionSettings();
        assertEquals("path", connectionSettings.getEncryptionKeyStorePath());
    }

    public void testEncryptionKeyStorePassword() throws Exception
    {
        String brokerURL = "tcp://localhost:5672?ssl='true'&encryption_key_store_password='pass'&encryption_trust_store_password='foo'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        ConnectionSettings connectionSettings = broker.buildConnectionSettings();
        assertEquals("pass", connectionSettings.getEncryptionKeyStorePassword());
    }

    public void testEncryptionTrustStorePassword() throws Exception
    {
        String brokerURL = "tcp://localhost:5672?ssl='true'&encryption_key_store_password='pass'&encryption_trust_store_password='foo'";
        BrokerDetails broker = new BrokerDetails(brokerURL);
        ConnectionSettings connectionSettings = broker.buildConnectionSettings();
        assertEquals("foo", connectionSettings.getEncryptionTrustStorePassword());
    }
}
