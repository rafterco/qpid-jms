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

import static org.apache.qpid.transport.Connection.State.OPEN;
import static org.apache.qpid.transport.Connection.State.RESUMING;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.properties.ConnectionStartProperties;


/**
 * ClientDelegate
 *
 */

public class ClientDelegate extends ConnectionDelegate
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientDelegate.class);

    private final ConnectionSettings _connectionSettings;

    public ClientDelegate(ConnectionSettings settings)
    {
        _connectionSettings = settings;
    }

    public void init(Connection conn, ProtocolHeader hdr)
    {
        if (!(hdr.getMajor() == 0 && hdr.getMinor() == 10))
        {
            conn.exception(new ProtocolVersionException(hdr.getMajor(), hdr.getMinor()));
        }
    }

    @Override
    public void connectionStart(Connection conn, ConnectionStart start)
    {
        Map<String,Object> clientProperties = new HashMap<String,Object>();

        if(_connectionSettings.getClientProperties() != null)
        {
            clientProperties.putAll(_connectionSettings.getClientProperties());
        }

        clientProperties.put(ConnectionStartProperties.SESSION_FLOW, 1);
        clientProperties.put(ConnectionStartProperties.PID, ConnectionStartProperties.getPID());
        clientProperties.put(ConnectionStartProperties.PROCESS, System.getProperty(ClientProperties.PROCESS_NAME, "Apache Qpid JMS Client for AMQP 0-9-1/0-10"));
        clientProperties.put(ConnectionStartProperties.VERSION_0_10, CommonProperties.getReleaseVersion());
        clientProperties.put(ConnectionStartProperties.PRODUCT, CommonProperties.getProductName());
        clientProperties.put(ConnectionStartProperties.PLATFORM, ConnectionStartProperties.getPlatformInfo());

        List<Object> brokerMechs = start.getMechanisms();
        if (brokerMechs == null || brokerMechs.isEmpty())
        {
            conn.connectionStartOk
                (clientProperties, null, null, conn.getLocale());
            return;
        }
        conn.setServerProperties(start.getServerProperties());

        try
        {
            final SaslClient sc = createSaslClient(brokerMechs);

            conn.setSaslClient(sc);

            byte[] response = sc.hasInitialResponse() ?
                sc.evaluateChallenge(new byte[0]) : null;
            conn.connectionStartOk
                (clientProperties, sc.getMechanismName(), response,
                 conn.getLocale());
        }
        catch (ConnectionException ce)
        {
            conn.exception(ce);
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }


    protected SaslClient createSaslClient(List<Object> brokerMechs) throws ConnectionException, SaslException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectionSecure(Connection conn, ConnectionSecure secure)
    {
        SaslClient sc = conn.getSaslClient();
        try
        {
            byte[] response = sc.evaluateChallenge(secure.getChallenge());
            conn.connectionSecureOk(response);
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }

    @Override
    public void connectionTune(Connection conn, ConnectionTune tune)
    {
        try
        {
            verifySaslNegotiationComplete(conn);
        }
        catch (SaslException e)
        {
            conn.exception(e);
            return;
        }

        int heartbeatInterval = _connectionSettings.getHeartbeatInterval010();
        float heartbeatTimeoutFactor = _connectionSettings.getHeartbeatTimeoutFactor();
        int actualHeartbeatInterval = calculateHeartbeatInterval(heartbeatInterval,
                                                           tune.getHeartbeatMin(),
                                                           tune.getHeartbeatMax());
        int maxFrameSize = tune.getMaxFrameSize();
        int settingsMaxFrameSize = conn.getConnectionSettings().getMaxFrameSize();
        if(maxFrameSize == 0 && settingsMaxFrameSize != 0 && settingsMaxFrameSize < 0xffff)
        {
            maxFrameSize = Math.max(Constant.MIN_MAX_FRAME_SIZE, settingsMaxFrameSize);
        }
        else if(maxFrameSize != 0 && settingsMaxFrameSize != 0)
        {
            maxFrameSize = Math.max(Constant.MIN_MAX_FRAME_SIZE, Math.min(maxFrameSize, settingsMaxFrameSize));
        }
        conn.connectionTuneOk(tune.getChannelMax(),
                              maxFrameSize,
                              actualHeartbeatInterval);

        conn.getNetworkConnection().setMaxReadIdleMillis((long) (1000L * (actualHeartbeatInterval * heartbeatTimeoutFactor)));
        conn.getNetworkConnection().setMaxWriteIdleMillis(1000L *actualHeartbeatInterval);
        conn.setMaxFrameSize(maxFrameSize == 0 ? 0xffff : maxFrameSize);

        int channelMax = tune.getChannelMax();
        //0 means no implied limit, except available server resources
        //(or that forced by protocol limitations [0xFFFF])
        conn.setChannelMax(channelMax == 0 ? Connection.MAX_CHANNEL_MAX : channelMax);

        conn.connectionOpen(_connectionSettings.getVhost(), null, Option.INSIST);
    }

    @Override
    public void connectionOpenOk(Connection conn, ConnectionOpenOk ok)
    {
        if (conn.isConnectionResuming())
        {
            conn.setState(RESUMING);
        }
        else
        {
            conn.setState(OPEN);
        }
    }

    @Override
    public void connectionRedirect(Connection conn, ConnectionRedirect redir)
    {
        conn.setRedirecting(true);
        conn.getSender().close();
        for(ConnectionListener listener : conn.getListeners())
        {
            if(listener.redirect(redir.getHost(), redir.getKnownHosts()))
            {
                break;
            }
        }
    }

    @Override
    public void connectionHeartbeat(Connection conn, ConnectionHeartbeat hearbeat)
    {
        conn.connectionHeartbeat();
    }

    /**
     * Currently the spec specified the min and max for heartbeat using secs
     */
    int calculateHeartbeatInterval(int heartbeat,int min, int max)
    {
        int i = heartbeat;
        if (i == 0)
        {
            LOGGER.info("Heartbeat interval is 0 sec. Heartbeats are disabled.");
            return 0; // heartbeats are disabled.
        }
        else if (i >= min && i <= max)
        {
            return i;
        }
        else
        {
            LOGGER.info("The broker does not support the configured connection heartbeat interval of {} sec," +
                     " using the brokers max supported value of {} sec instead.", i, max);
            return max;
        }
    }

    public ConnectionSettings getConnectionSettings()
    {
        return _connectionSettings;
    }

    private void verifySaslNegotiationComplete(Connection connection) throws SaslException
    {
        SaslClient client = connection.getSaslClient();
        if (client != null)
        {
            if (!client.isComplete())
            {
                throw new SaslException("SASL negotiation has not been completed - cannot proceed with connection open");
            }

            String mechanismName = client.getMechanismName();
            try
            {
                client.dispose();
            }
            catch (SaslException e)
            {
                LOGGER.warn("Disposal of client sasl for mechanism '{}' has failed", mechanismName, e);
            }
            finally
            {
                connection.setSaslClient(null);
            }
        }
    }
}
