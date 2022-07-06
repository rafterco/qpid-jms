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
package org.apache.qpid.transport;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConnectionSettingsTest extends QpidTestCase
{
    private static final String TEST_ALGORITHM_NAME = "algorithmName";

    private ConnectionSettings _conConnectionSettings;

    protected void setUp() throws Exception
    {
        super.setUp();
        _conConnectionSettings = new ConnectionSettings();
    }

    public void testTcpNoDelayDefault()
    {
        assertTrue("Default for isTcpNodelay() should be true", _conConnectionSettings.isTcpNodelay());
    }

    public void testTcpNoDelayOverrideTrue()
    {
        systemPropertyOverrideForTcpDelay(ClientProperties.QPID_TCP_NODELAY_PROP_NAME, true);
    }
    
    public void testTcpNoDelayOverrideFalse()
    {
        systemPropertyOverrideForTcpDelay(ClientProperties.QPID_TCP_NODELAY_PROP_NAME, false);
    }

    @SuppressWarnings("deprecation")
    public void testTcpNoDelayLegacyOverrideTrue()
    {
        systemPropertyOverrideForTcpDelay(LegacyClientProperties.AMQJ_TCP_NODELAY_PROP_NAME, true);
    }

    @SuppressWarnings("deprecation")
    public void testTcpNoDelayLegacyOverrideFalse()
    {
        systemPropertyOverrideForTcpDelay(LegacyClientProperties.AMQJ_TCP_NODELAY_PROP_NAME, false);
    }

    public void testKeyManagerFactoryAlgorithmDefault()
    {
        assertEquals(KeyManagerFactory.getDefaultAlgorithm(), _conConnectionSettings.getKeyManagerFactoryAlgorithm());
    }

    public void testKeyManagerFactoryAlgorithmOverridden()
    {
        String algorithmName = TEST_ALGORITHM_NAME;
        systemPropertyOverrideForKeyFactoryAlgorithm(ClientProperties.QPID_SSL_KEY_MANAGER_FACTORY_ALGORITHM_PROP_NAME, algorithmName);
    }

    @SuppressWarnings("deprecation")
    public void testKeyManagerFactoryAlgorithmLegacyOverridden()
    {
        String algorithmName = TEST_ALGORITHM_NAME;
        systemPropertyOverrideForKeyFactoryAlgorithm(LegacyClientProperties.QPID_SSL_KEY_STORE_CERT_TYPE_PROP_NAME, algorithmName);
    }

    public void testTrustManagerFactoryAlgorithmDefault()
    {
        assertEquals(TrustManagerFactory.getDefaultAlgorithm(), _conConnectionSettings.getTrustManagerFactoryAlgorithm());
    }

    public void testTrustManagerFactoryAlgorithmOverridden()
    {
        String algorithmName = TEST_ALGORITHM_NAME;
        systemPropertyOverrideForTrustFactoryAlgorithm(ClientProperties.QPID_SSL_TRUST_MANAGER_FACTORY_ALGORITHM_PROP_NAME, algorithmName);
    }

    @SuppressWarnings("deprecation")
    public void testTrustManagerFactoryAlgorithmLegacyOverridden()
    {
        String algorithmName = TEST_ALGORITHM_NAME;
        systemPropertyOverrideForTrustFactoryAlgorithm(LegacyClientProperties.QPID_SSL_TRUST_STORE_CERT_TYPE_PROP_NAME, algorithmName);
    }

    public void testSendBufferSizeDefault()
    {
        assertEquals("unexpected default for buffer size", 65535, _conConnectionSettings.getWriteBufferSize());
    }

    public void testSendBufferSizeOverridden()
    {
        systemPropertyOverrideForSocketBufferSize(ClientProperties.SEND_BUFFER_SIZE_PROP_NAME, 1024, false);
    }

    @SuppressWarnings("deprecation")
    public void testtestSendBufferSizeOverriddenLegacyOverridden()
    {
        systemPropertyOverrideForSocketBufferSize(LegacyClientProperties.LEGACY_SEND_BUFFER_SIZE_PROP_NAME, 1024, false);
    }

    public void testReceiveBufferSizeDefault()
    {
        assertEquals("unexpected default for buffer size", 65535, _conConnectionSettings.getReadBufferSize());
    }

    public void testReceiveBufferSizeOverridden()
    {
        systemPropertyOverrideForSocketBufferSize(ClientProperties.RECEIVE_BUFFER_SIZE_PROP_NAME, 1024, true);
    }

    @SuppressWarnings("deprecation")
    public void testReceiveBufferSizeOverriddenLegacyOverridden()
    {
        systemPropertyOverrideForSocketBufferSize(LegacyClientProperties.LEGACY_RECEIVE_BUFFER_SIZE_PROP_NAME, 1024, true);
    }

    public void testHeartbeatingDefaults()
    {
        assertNull(_conConnectionSettings.getHeartbeatInterval08());
        assertEquals(ClientProperties.QPID_HEARTBEAT_INTERVAL_010_DEFAULT,_conConnectionSettings.getHeartbeatInterval010());
        assertEquals(2.0, _conConnectionSettings.getHeartbeatTimeoutFactor(), 0.1);
    }

    public void testHeartbeatingOverridden()
    {
        resetSystemProperty(ClientProperties.QPID_HEARTBEAT_INTERVAL, "60");
        resetSystemProperty(ClientProperties.QPID_HEARTBEAT_TIMEOUT_FACTOR, "2.5");

        assertEquals(Integer.valueOf(60), _conConnectionSettings.getHeartbeatInterval08());
        assertEquals(60, _conConnectionSettings.getHeartbeatInterval010());
        assertEquals(2.5, _conConnectionSettings.getHeartbeatTimeoutFactor(), 0.1);
    }

    @SuppressWarnings("deprecation")
	public void testHeartbeatingOverriddenUsingAmqjLegacyOption()
    {
        resetSystemProperty(LegacyClientProperties.AMQJ_HEARTBEAT_DELAY, "30");
        resetSystemProperty(LegacyClientProperties.AMQJ_HEARTBEAT_TIMEOUT_FACTOR, "1.5");

        assertEquals(Integer.valueOf(30), _conConnectionSettings.getHeartbeatInterval08());
        assertEquals(30, _conConnectionSettings.getHeartbeatInterval010());
        assertEquals(1.5, _conConnectionSettings.getHeartbeatTimeoutFactor(), 0.1);
    }

    @SuppressWarnings("deprecation")
    public void testHeartbeatingOverriddenUsingOlderLegacyOption()
    {
        resetSystemProperty(LegacyClientProperties.IDLE_TIMEOUT_PROP_NAME, "30000");

        assertEquals(Integer.valueOf(30), _conConnectionSettings.getHeartbeatInterval08());
        assertEquals(30, _conConnectionSettings.getHeartbeatInterval010());
    }

    private void systemPropertyOverrideForTcpDelay(String propertyName, boolean value)
    {
        resetSystemProperty(propertyName, String.valueOf(value));
        assertEquals("Value for isTcpNodelay() is incorrect", value, _conConnectionSettings.isTcpNodelay());
    }

    private void systemPropertyOverrideForKeyFactoryAlgorithm(String propertyName, String value)
    {
        resetSystemProperty(propertyName, value);
        assertEquals(value, _conConnectionSettings.getKeyManagerFactoryAlgorithm());
    }

    private void systemPropertyOverrideForTrustFactoryAlgorithm(String propertyName, String value)
    {
        resetSystemProperty(propertyName, value);
        assertEquals(value, _conConnectionSettings.getTrustManagerFactoryAlgorithm());
    }


    private void systemPropertyOverrideForSocketBufferSize(String propertyName, int value, boolean read)
    {
        resetSystemProperty(propertyName, String.valueOf(value));
        if(read)
        {
            assertEquals("unexpected value for receive buffer", value, _conConnectionSettings.getReadBufferSize());
        }
        else
        {
            assertEquals("unexpected value for send buffer", value, _conConnectionSettings.getWriteBufferSize());
        }
    }

    private void resetSystemProperty(String propertyName, String value)
    {
        setTestSystemProperty(propertyName, value);

        _conConnectionSettings = new ConnectionSettings();
    }
}
