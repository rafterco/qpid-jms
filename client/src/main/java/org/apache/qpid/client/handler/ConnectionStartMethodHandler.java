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
package org.apache.qpid.client.handler;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Map;
import java.util.StringTokenizer;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.QpidException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.security.AMQCallbackHandler;
import org.apache.qpid.client.security.CallbackHandlerRegistry;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.transport.ConnectionSettings;

public class ConnectionStartMethodHandler implements StateAwareMethodListener<ConnectionStartBody>
{
    private static final Logger _log = LoggerFactory.getLogger(ConnectionStartMethodHandler.class);

    private static final ConnectionStartMethodHandler _instance = new ConnectionStartMethodHandler();

    private final CloseWhenNoRouteSettingsHelper _closeWhenNoRouteHelper = new CloseWhenNoRouteSettingsHelper();

    public static ConnectionStartMethodHandler getInstance()
    {
        return _instance;
    }

    private ConnectionStartMethodHandler()
    { }

    @Override
    public void methodReceived(AMQProtocolSession session, ConnectionStartBody body, int channelId)
            throws QpidException
    {
        _log.debug("public void methodReceived(AMQStateManager stateManager, AMQProtocolSession protocolSession, "
            + "AMQMethodEvent evt): called");

        ProtocolVersion pv = ProtocolVersion.get((byte) body.getVersionMajor(), (byte) body.getVersionMinor());

        // 0-9-1 is indistinguishable from 0-9 using only major and minor ... if we established the connection as 0-9-1
        // and now get back major = 0 , minor = 9 then we can assume it means 0-9-1

        if(pv.equals(ProtocolVersion.v0_9) && session.getProtocolVersion().equals(ProtocolVersion.v0_91))
        {
            pv = ProtocolVersion.v0_91;
        }

        // For the purposes of interop, we can make the client accept the broker's version string.
        // If it does, it then internally records the version as being the latest one that it understands.
        // It needs to do this since frame lookup is done by version.
        if (Boolean.getBoolean("qpid.accept.broker.version") && !pv.isSupported())
        {

            pv = ProtocolVersion.getLatestSupportedVersion();
        }

        if (pv.isSupported())
        {
            session.setProtocolVersion(pv);

            try
            {
                // Used to hold the SASL mechanism to authenticate with.
                String mechanism;
                final ConnectionSettings connectionSettings = session.getConnectionSettings();

                if (body.getMechanisms()== null)
                {
                    throw new QpidException("mechanism not specified in ConnectionStart method frame", null);
                }
                else
                {
                    String restriction = connectionSettings.getSaslMechs();
                    mechanism = chooseMechanism(body.getMechanisms(), restriction);
                    _log.debug("mechanism = " + mechanism);
                }

                if (mechanism == null)
                {
                    throw new QpidException("No supported security mechanism found, passed: " + new String(body.getMechanisms()), null);
                }

                byte[] saslResponse;
                try
                {
                    final Map<String, ?> saslProps;
                    if (connectionSettings.isUseSASLEncryption())
                    {
                        saslProps = Collections.singletonMap(Sasl.QOP, "auth-conf");
                    }
                    else
                    {
                        saslProps = null;
                    }

                    String saslProtocol = connectionSettings.getSaslProtocol();
                    String saslServerName = connectionSettings.getSaslServerName();
                    if(saslServerName == null)
                    {
                        saslServerName = connectionSettings.getHost();
                    }
                    SaslClient sc =
                        Sasl.createSaslClient(new String[] { mechanism }, null, saslProtocol, saslServerName, saslProps,
                            createCallbackHandler(mechanism, session));
                    if (sc == null)
                    {
                        throw new QpidException("Client SASL configuration error: no SaslClient could be created for mechanism " + mechanism
                            + ". Please ensure all factories are registered. See DynamicSaslRegistrar for "
                            + " details of how to register non-standard SASL client providers.", null);
                    }

                    session.setSaslClient(sc);
                    saslResponse = (sc.hasInitialResponse() ? sc.evaluateChallenge(new byte[0]) : null);
                }
                catch (SaslException e)
                {
                    session.setSaslClient(null);
                    throw new QpidException("Unable to create SASL client: " + e, e);
                }

                if (body.getLocales() == null)
                {
                    throw new QpidException("Locales is not defined in Connection Start method", null);
                }

                final String locales = new String(body.getLocales(), "utf8");
                final StringTokenizer tokenizer = new StringTokenizer(locales, " ");
                if (tokenizer.hasMoreTokens())
                {
                    tokenizer.nextToken();
                }
                else
                {
                    throw new QpidException("No locales sent from server, passed: " + locales, null);
                }

                session.getStateManager().changeState(AMQState.CONNECTION_NOT_TUNED);

                FieldTable clientProperties = FieldTableFactory.newFieldTable();

                clientProperties.setString(ConnectionStartProperties.CLIENT_ID_0_8,
                        session.getClientID());
                clientProperties.setString(ConnectionStartProperties.PRODUCT,
                        CommonProperties.getProductName());
                clientProperties.setString(ConnectionStartProperties.VERSION_0_8,
                        CommonProperties.getReleaseVersion());
                clientProperties.setString(ConnectionStartProperties.PLATFORM,
                        ConnectionStartProperties.getPlatformInfo());
                clientProperties.setString(ConnectionStartProperties.PROCESS,
                        System.getProperty(ClientProperties.PROCESS_NAME, "Apache Qpid JMS Client for AMQP 0-9-1/0-10"));
                clientProperties.setInteger(ConnectionStartProperties.PID,
                        ConnectionStartProperties.getPID());

                FieldTable serverProperties = body.getServerProperties();
                session.setConnectionStartServerProperties(serverProperties);

                ConnectionURL url = getConnectionURL(session);
                _closeWhenNoRouteHelper.setClientProperties(clientProperties, url, serverProperties);

                clientProperties.setString(ConnectionStartProperties.QPID_MESSAGE_COMPRESSION_SUPPORTED,
                                           String.valueOf(session.getAMQConnection().isMessageCompressionDesired()));

                ConnectionStartOkBody connectionStartOkBody = session.getMethodRegistry().createConnectionStartOkBody(clientProperties,new AMQShortString(mechanism),saslResponse,new AMQShortString(locales));
                // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                // Be aware of possible changes to parameter order as versions change.
                session.writeFrame(connectionStartOkBody.generateFrame(channelId));

            }
            catch (UnsupportedEncodingException e)
            {
                throw new QpidException("Unable to decode data: " + e, e);
            }
        }
        else
        {
            _log.error("Broker requested Protocol [" + body.getVersionMajor() + "-" + body.getVersionMinor()
                       + "] which is not supported by this version of the client library");

            session.closeProtocolSession();
        }
    }

    private String chooseMechanism(byte[] availableMechanisms, final String restriction) throws UnsupportedEncodingException
    {
        final String mechanisms = new String(availableMechanisms, "utf8");
        return CallbackHandlerRegistry.getInstance().selectMechanism(mechanisms, restriction);
    }

    private AMQCallbackHandler createCallbackHandler(String mechanism, AMQProtocolSession protocolSession)
        throws QpidException
    {
        try
        {
            AMQCallbackHandler instance = CallbackHandlerRegistry.getInstance().createCallbackHandler(mechanism);
            instance.initialise(getConnectionURL(protocolSession));

            return instance;
        }
        catch (IllegalArgumentException e)
        {
            throw new QpidException("Unable to create callback handler: " + e, e);
        }
    }

    private ConnectionURL getConnectionURL(AMQProtocolSession protocolSession)
    {
        return protocolSession.getAMQConnection().getConnectionURL();
    }
}
