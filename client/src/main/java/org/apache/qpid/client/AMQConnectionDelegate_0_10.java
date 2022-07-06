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

package org.apache.qpid.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.XASession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.QpidException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.failover.FailoverRetrySupport;
import org.apache.qpid.client.transport.ClientConnectionDelegate;
import org.apache.qpid.client.util.JMSExceptionHelper;
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.jms.ChannelLimitReachedException;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.jms.Session;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.protocol.ErrorCodes;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionClose;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ConnectionListener;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.FrameSizeObserver;
import org.apache.qpid.transport.ProtocolVersionException;
import org.apache.qpid.transport.SessionDetachCode;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.TransportException;

public class AMQConnectionDelegate_0_10 implements AMQConnectionDelegate, ConnectionListener, FrameSizeObserver
{
    private static final int DEFAULT_PORT = 5672;

    /**
     * This class logger.
     */
    private static final Logger _logger = LoggerFactory.getLogger(AMQConnectionDelegate_0_10.class);

    /**
     * The AMQ Connection.
     */
    private final AMQConnection _conn;

    /**
     * The QpidConeection instance that is mapped with this JMS connection.
     */
    private org.apache.qpid.transport.Connection _qpidConnection;
    private ConnectionException exception = null;

    //--- constructor
    public AMQConnectionDelegate_0_10(AMQConnection conn)
    {
        _conn = conn;
        _qpidConnection = new Connection();
        _qpidConnection.addConnectionListener(this);
        _qpidConnection.addFrameSizeObserver(this);
    }

    /**
     * create a Session and start it if required.
     */

    public Session createSession(boolean transacted, int acknowledgeMode, int prefetchHigh, int prefetchLow)
    throws JMSException
    {
        return createSession(transacted,acknowledgeMode,prefetchHigh,prefetchLow,null);
    }

    private Session createSession(final boolean transacted, final int acknowledgeMode, final int prefetchHigh, final int prefetchLow, final String name)
            throws JMSException
    {
        _conn.checkNotClosed();

        if (_conn.channelLimitReached())
        {
            throw new ChannelLimitReachedException(_conn.getMaximumChannelCount());
        }

        return new FailoverRetrySupport<>(new FailoverProtectedOperation<Session, JMSException>()
        {
            @Override
            public Session execute() throws JMSException, FailoverException
            {
                int channelId = _conn.getNextChannelID();
                try
                {
                    AMQSession session = new AMQSession_0_10(_qpidConnection,
                                                             _conn,
                                                             channelId,
                                                             transacted,
                                                             acknowledgeMode,
                                                             prefetchHigh,
                                                             prefetchLow,
                                                             name);
                    _conn.registerSession(channelId, session);
                    if (_conn.started())
                    {
                        session.start();
                    }
                    return session;
                }
                catch (Exception e)
                {
                    _logger.error("exception creating session:", e);
                    throw JMSExceptionHelper.chainJMSException(new JMSException("cannot create session"), e);
                }
            }
        }, _conn).execute();
    }

    /**
     * Create an XASession with default prefetch values of:
     * High = MaxPrefetch
     * Low  = MaxPrefetch / 2
     * @return XASession
     * @throws JMSException
     */
    public XASession createXASession() throws JMSException
    {
        return createXASession((int) _conn.getMaxPrefetch(), (int) _conn.getMaxPrefetch() / 2);
    }

    /**
     * create an XA Session and start it if required.
     */
    public XASession createXASession(int prefetchHigh, int prefetchLow) throws JMSException
    {
        _conn.checkNotClosed();

        if (_conn.channelLimitReached())
        {
            throw new ChannelLimitReachedException(_conn.getMaximumChannelCount());
        }

        int channelId = _conn.getNextChannelID();
        XASessionImpl session;
        try
        {
            session = new XASessionImpl(_qpidConnection, _conn, channelId, prefetchHigh, prefetchLow);
            _conn.registerSession(channelId, session);
            if (_conn.started())
            {
                session.start();
            }
        }
        catch (Exception e)
        {
            throw JMSExceptionHelper.chainJMSException(new JMSException("cannot create session"), e);
        }
        return session;
    }

    public XASession createXASession(int ackMode)
        throws JMSException
    {

        _conn.checkNotClosed();

        if (_conn.channelLimitReached())
        {
            throw new ChannelLimitReachedException(_conn.getMaximumChannelCount());
        }

        int channelId = _conn.getNextChannelID();
        XASessionImpl session;
        try
        {
            session = new XASessionImpl(_qpidConnection, _conn, channelId, ackMode, (int)_conn.getMaxPrefetch(), (int)_conn.getMaxPrefetch() / 2);
            _conn.registerSession(channelId, session);
            if (_conn.started())
            {
                session.start();
            }
        }
        catch (Exception e)
        {
            throw JMSExceptionHelper.chainJMSException(new JMSException("cannot create session"), e);
        }
        return session;
    }

    /**
     * Make a connection with the broker
     *
     * @param brokerDetail The detail of the broker to connect to.
     * @throws IOException
     * @throws QpidException
     */
    public ProtocolVersion makeBrokerConnection(BrokerDetails brokerDetail) throws IOException, QpidException
    {
        try
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("connecting to host: " + brokerDetail.getHost()
                        + " port: " + brokerDetail.getPort() + " vhost: "
                        + _conn.getVirtualHost() + " username: "
                        + _conn.getUsername() + " password: "
                        + "********");
            }

            ConnectionSettings conSettings = retrieveConnectionSettings(brokerDetail);

            _qpidConnection.setConnectionDelegate(new ClientConnectionDelegate(conSettings, _conn.getConnectionURL()));
            _qpidConnection.connect(conSettings);

            _conn.setConnected(true);
            _conn.setUsername(_qpidConnection.getUserID());
            _conn.setMaximumChannelCount(_qpidConnection.getChannelMax());
            _conn.getFailoverPolicy().attainedConnection();
            _conn.logConnected(_qpidConnection.getLocalAddress(), _qpidConnection.getRemoteSocketAddress());
            _conn.setConnectionSettings(conSettings);
        }
        catch (ProtocolVersionException pe)
        {
            if (pe.getMajor() == 9 && pe.getMinor() == 1)
            {
                // 0-10 misinterprets 0-91's header (major/minor/revision) by treating minor as the major, and
                // revision as the minor. Correct this so that we find the correct delegate.
                return ProtocolVersion.v0_91;
            }
            else
            {
                return ProtocolVersion.get(pe.getMajor(), pe.getMinor());
            }
        }
        catch (ConnectionException ce)
        {
            int code = ErrorCodes.REPLY_SUCCESS;
            if (ce.getClose() != null && ce.getClose().getReplyCode() != null)
            {
                code = ce.getClose().getReplyCode().getValue();
            }
            String msg = "Cannot connect to broker ("+brokerDetail+"): " + ce.getMessage();
            throw new AMQException(code, msg, ce);
        }

        return null;
    }

    public void failoverPrep()
    {
        List<AMQSession> sessions = new ArrayList<AMQSession>(_conn.getSessions().values());
        for (AMQSession s : sessions)
        {
            ((AMQSession_0_10)s).failoverPrep();
        }
    }

    public void resubscribeSessions() throws JMSException, QpidException, FailoverException
    {
        _logger.debug("Resuming connection");
        getQpidConnection().resume();
        List<AMQSession> sessions = _conn.getSessions().values();
        _logger.debug("Resubscribing sessions = {} sessions.size = {}", sessions, sessions.size());
        for (AMQSession s : sessions)
        {
            s.resubscribe();
        }
    }

    public void closeConnection(long timeout) throws JMSException, QpidException
    {
        try
        {
            _qpidConnection.close();
        }
        catch (TransportException e)
        {
            throw new QpidException(e.getMessage(), e);
        }
    }

    public void opened(Connection conn) {}

    public void exception(Connection conn, ConnectionException exc)
    {
        if (exception != null)
        {
            _logger.error("previous exception", exception);
        }

        exception = exc;
    }

    public void closed(Connection conn)
    {
        final ConnectionException exc = exception;
        exception = null;

        if (exc == null)
        {
            return;
        }

        final ConnectionClose close = exc.getClose();
        if (close == null || close.getReplyCode() == ConnectionCloseCode.CONNECTION_FORCED)
        {
            final CountDownLatch failoverLatch = new CountDownLatch(1);
            _conn.getProtocolHandler().setFailoverLatch(failoverLatch);
            final AtomicBoolean failoverDone = new AtomicBoolean();
            try
            {
                _qpidConnection.notifyFailoverRequired();
                _conn.doWithAllLocks(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            boolean preFailover = _conn.firePreFailover(false);
                            if (preFailover)
                            {
                                boolean reconnected;
                                if (exc instanceof RedirectConnectionException)
                                {
                                    RedirectConnectionException redirect = (RedirectConnectionException) exc;
                                    reconnected = attemptRedirection(redirect.getHost(), redirect.getKnownHosts());
                                }
                                else
                                {
                                    reconnected = _conn.attemptReconnection();
                                }
                                if (reconnected)
                                {
                                    failoverPrep();
                                    _conn.resubscribeSessions();
                                    _conn.fireFailoverComplete();
                                    failoverDone.set(true);
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            _logger.error("error during failover", e);
                        }
                    }
                });
            }
            finally
            {
                failoverLatch.countDown();
                _conn.getProtocolHandler().setFailoverLatch(null);
            }


            if (failoverDone.get())
            {
                return;
            }

        }

        for(AMQSession<?,?> session :  _conn.getSessions().values())
        {
            session.markClosed();
        }

        _conn.setClosed();

        final ExceptionListener listener = _conn.getExceptionListenerNoCheck();
        if (listener == null)
        {
            _logger.error("connection exception: " + conn, exc);
        }
        else
        {
            _conn.performConnectionTask(new Runnable()
            {
                @Override
                public void run()
                {
                    String code = null;
                    if (close != null)
                    {
                        code = close.getReplyCode().toString();
                    }
                    listener.onException(JMSExceptionHelper.chainJMSException(new JMSException(exc.getMessage(), code),
                                                                              exc));
                }
            });

        }
    }

    @Override
    public boolean redirect(final String host, final List<Object> knownHosts)
    {
        exception = new RedirectConnectionException(host,knownHosts);

        return false;
    }

    private boolean attemptRedirection(String host, List<Object> knownHosts)
    {

        boolean redirected = host != null && attemptRedirection(host);
        if(knownHosts != null)
        {
            for(Object knownHost : knownHosts)
            {
                redirected = attemptRedirection(String.valueOf(knownHost));
                if(redirected)
                {
                    break;
                }
            }
        }
        return redirected;
    }

    private boolean attemptRedirection(String host)
    {
        int portIndex = host.indexOf(':');

        int port;
        if (portIndex == -1)
        {
            port = DEFAULT_PORT;
        }
        else
        {
            try
            {
                port = Integer.parseInt(host.substring(portIndex + 1));
            }
            catch(NumberFormatException e)
            {
                _logger.info("Unable to redirect to " + host + " - does not look like a valid address");
                return false;
            }
            host = host.substring(0, portIndex);

        }
        return _conn.attemptReconnection(host,port,false);
    }

    public <T, E extends Exception> T executeRetrySupport(FailoverProtectedOperation<T,E> operation) throws E
    {
        if (_conn.isFailingOver())
        {
            try
            {
                _conn.blockUntilNotFailingOver();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        synchronized (_conn.getFailoverMutex())
        {
            try
            {
                return operation.execute();
            }
            catch (FailoverException e)
            {
                // FailoverException never thrown on 0-10 path
                throw new RuntimeException(e);
            }
        }
    }

    public int getMaxChannelID()
    {
        //For a negotiated channelMax N, there are channels 0 to N-1 available.
        return _qpidConnection.getChannelMax() - 1;
    }

    public int getMinChannelID()
    {
        return Connection.MIN_USABLE_CHANNEL_NUM;
    }

    public ProtocolVersion getProtocolVersion()
    {
        return ProtocolVersion.v0_10;
    }

    public String getUUID()
    {
        return (String)_qpidConnection.getServerProperties().get(ServerPropertyNames.FEDERATION_TAG);
    }

    /*
     * @see org.apache.qpid.client.AMQConnectionDelegate#isSupportedServerFeature(java.lang.String)
     */
    public boolean isSupportedServerFeature(final String featureName)
    {
        if (featureName == null)
        {
            throw new IllegalArgumentException("featureName cannot be null");
        }
        final Map<String, Object> serverProperties = _qpidConnection.getServerProperties();
        boolean featureSupported = false;
        if (serverProperties != null && serverProperties.containsKey(ServerPropertyNames.QPID_FEATURES))
        {
            final Object supportServerFeatures = serverProperties.get(ServerPropertyNames.QPID_FEATURES);
            featureSupported = supportServerFeatures instanceof List && ((List<String>)supportServerFeatures).contains(featureName);
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Server support for feature '" + featureName + "' : " + featureSupported);
        }

        return featureSupported;
    }

    @Override
    public void setHeartbeatListener(HeartbeatListener listener)
    {
        ((ClientConnectionDelegate)(_qpidConnection.getConnectionDelegate())).setHeartbeatListener(listener);
    }

    private ConnectionSettings retrieveConnectionSettings(BrokerDetails brokerDetail)
    {
        ConnectionSettings conSettings = brokerDetail.buildConnectionSettings();

        conSettings.setVhost(_conn.getVirtualHost());
        conSettings.setUsername(_conn.getUsername());
        conSettings.setPassword(_conn.getPassword());

        // Pass client name from connection URL
        Map<String, Object> clientProps = new HashMap<String, Object>();
        try
        {
            clientProps.put(ConnectionStartProperties.CLIENT_ID_0_10, _conn.getClientID());
            if(_conn.isMessageCompressionDesired())
            {
                clientProps.put(ConnectionStartProperties.QPID_MESSAGE_COMPRESSION_SUPPORTED, Boolean.TRUE.toString());
            }
            conSettings.setClientProperties(clientProps);
        }
        catch (JMSException e)
        {
            // Ignore
        }

        //Check connection-level ssl override setting
        String connectionSslOption = _conn.getConnectionURL().getOption(ConnectionURL.OPTIONS_SSL);
        if(connectionSslOption != null)
        {
            boolean connUseSsl = Boolean.parseBoolean(connectionSslOption);
            boolean brokerlistUseSsl = conSettings.isUseSSL();

            if( connUseSsl != brokerlistUseSsl)
            {
                conSettings.setUseSSL(connUseSsl);

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Applied connection ssl option override, setting UseSsl to: " + connUseSsl );
                }
            }
        }

        return conSettings;
    }
    protected org.apache.qpid.transport.Connection getQpidConnection()
    {
        return _qpidConnection;
    }

    public boolean verifyClientID() throws JMSException, QpidException
    {
        int prefetch = (int)_conn.getMaxPrefetch();
        AMQSession_0_10 ssn = (AMQSession_0_10)createSession(false, 1,prefetch,prefetch,_conn.getClientID());
        org.apache.qpid.transport.Session ssn_0_10 = ssn.getQpidSession();
        try
        {
            ssn_0_10.awaitOpen();
        }
        catch(SessionException se)
        {
            //if due to non unique client id for user return false, otherwise wrap and re-throw.
            if (ssn_0_10.getDetachCode() != null &&
                ssn_0_10.getDetachCode() == SessionDetachCode.SESSION_BUSY)
            {
                return false;
            }
            else
            {
                throw new AMQException(ErrorCodes.INTERNAL_ERROR, "Unexpected SessionException thrown while awaiting session opening", se);
            }
        }
        return true;
    }

    @Override
    public boolean supportsIsBound()
    {
        //0-10 supports the isBound method
        return true;
    }

    @Override
    public boolean isMessageCompressionSupported()
    {
        return _qpidConnection.isMessageCompressionSupported();
    }

    @Override
    public boolean isVirtualHostPropertiesSupported()
    {
        return _qpidConnection.isVirtualHostPropertiesSupported();
    }

    @Override
    public boolean isQueueLifetimePolicySupported()
    {
        return _qpidConnection.isQueueLifetimePolicySupported();
    }

    @Override
    public void setMaxFrameSize(final int frameSize)
    {
        _conn.setMaximumFrameSize(frameSize);
    }

    private class RedirectConnectionException extends ConnectionException
    {
        private static final long serialVersionUID = 1L;

        private final String _host;
        private final List<Object> _knownHosts;

        public RedirectConnectionException(final String host,
                                           final List<Object> knownHosts)
        {
            super("Connection redirected to " + host + " alternates " + knownHosts);
            _host = host;
            _knownHosts = knownHosts;
        }

        public String getHost()
        {
            return _host;
        }

        public List<Object> getKnownHosts()
        {
            return _knownHosts;
        }
    }
}
