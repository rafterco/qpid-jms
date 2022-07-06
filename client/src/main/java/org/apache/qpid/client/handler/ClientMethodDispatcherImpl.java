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

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.QpidException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQMethodNotImplementedException;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.framing.*;

public class ClientMethodDispatcherImpl implements MethodDispatcher
{

    private static final BasicCancelOkMethodHandler _basicCancelOkMethodHandler = BasicCancelOkMethodHandler.getInstance();
    private static final BasicDeliverMethodHandler _basicDeliverMethodHandler = BasicDeliverMethodHandler.getInstance();
    private static final BasicReturnMethodHandler _basicReturnMethodHandler = BasicReturnMethodHandler.getInstance();
    private static final ChannelCloseMethodHandler _channelCloseMethodHandler = ChannelCloseMethodHandler.getInstance();
    private static final ChannelCloseOkMethodHandler _channelCloseOkMethodHandler = ChannelCloseOkMethodHandler.getInstance();
    private static final ChannelFlowOkMethodHandler _channelFlowOkMethodHandler = ChannelFlowOkMethodHandler.getInstance();
    private static final ChannelFlowMethodHandler _channelFlowMethodHandler = ChannelFlowMethodHandler.getInstance();
    private static final ConnectionCloseMethodHandler _connectionCloseMethodHandler = ConnectionCloseMethodHandler.getInstance();
    private static final ConnectionOpenOkMethodHandler _connectionOpenOkMethodHandler = ConnectionOpenOkMethodHandler.getInstance();
    private static final ConnectionRedirectMethodHandler _connectionRedirectMethodHandler = ConnectionRedirectMethodHandler.getInstance();
    private static final ConnectionSecureMethodHandler _connectionSecureMethodHandler = ConnectionSecureMethodHandler.getInstance();
    private static final ConnectionStartMethodHandler _connectionStartMethodHandler = ConnectionStartMethodHandler.getInstance();
    private static final ConnectionTuneMethodHandler _connectionTuneMethodHandler = ConnectionTuneMethodHandler.getInstance();
    private static final ExchangeBoundOkMethodHandler _exchangeBoundOkMethodHandler = ExchangeBoundOkMethodHandler.getInstance();
    private static final QueueDeleteOkMethodHandler _queueDeleteOkMethodHandler = QueueDeleteOkMethodHandler.getInstance();

    private static final Logger _logger = LoggerFactory.getLogger(ClientMethodDispatcherImpl.class);


    private static interface DispatcherFactory
    {
        public ClientMethodDispatcherImpl createMethodDispatcher(AMQProtocolSession session);
    }

    private static final Map<ProtocolVersion, DispatcherFactory> _dispatcherFactories =
            new HashMap<ProtocolVersion, DispatcherFactory>();

    static
    {
        _dispatcherFactories.put(ProtocolVersion.v0_8,
                                 new DispatcherFactory()
                                 {
                                     public ClientMethodDispatcherImpl createMethodDispatcher(AMQProtocolSession session)
                                     {
                                         return new ClientMethodDispatcherImpl_8_0(session);
                                     }
                                 });

        _dispatcherFactories.put(ProtocolVersion.v0_9,
                                 new DispatcherFactory()
                                 {
                                     public ClientMethodDispatcherImpl createMethodDispatcher(AMQProtocolSession session)
                                     {
                                         return new ClientMethodDispatcherImpl_0_9(session);
                                     }
                                 });


        _dispatcherFactories.put(ProtocolVersion.v0_91,
                                 new DispatcherFactory()
                                 {
                                     public ClientMethodDispatcherImpl createMethodDispatcher(AMQProtocolSession session)
                                     {
                                         return new ClientMethodDispatcherImpl_0_91(session);
                                     }
                                 });

    }

    public static ClientMethodDispatcherImpl newMethodDispatcher(ProtocolVersion version, AMQProtocolSession session)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("New Method Dispatcher:" + session);
        }
        
        DispatcherFactory factory = _dispatcherFactories.get(version);
        if(factory == null)
        {
            throw new UnsupportedOperationException("The protocol version " + version + " is not supported");
        }
        return factory.createMethodDispatcher(session);
    }

    private AMQProtocolSession _session;

    public ClientMethodDispatcherImpl(AMQProtocolSession session)
    {
        _session = session;
    }

    public AMQStateManager getStateManager()
    {
        return _session.getStateManager();
    }

    public boolean dispatchAccessRequestOk(AccessRequestOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    @Override
    public boolean dispatchQueueUnbindOk(final QueueUnbindOkBody body, final int channelId)
            throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    @Override
    public boolean dispatchBasicRecoverSyncOk(final BasicRecoverSyncOkBody basicRecoverSyncOkBody,
                                              final int channelId)
            throws QpidException
    {
        return false;
    }

    @Override
    public boolean dispatchChannelAlert(final ChannelAlertBody channelAlertBody, final int channelId)
            throws QpidException
    {
        return false;
    }

    @Override
    public boolean dispatchConfirmSelectOk(final ConfirmSelectOkBody confirmSelectOkBody, final int channelId)
            throws QpidException
    {
        return false;
    }

    public boolean dispatchBasicCancelOk(BasicCancelOkBody body, int channelId) throws QpidException
    {
        _basicCancelOkMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchBasicConsumeOk(BasicConsumeOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchBasicDeliver(BasicDeliverBody body, int channelId) throws QpidException
    {
        _basicDeliverMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchBasicGetEmpty(BasicGetEmptyBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchBasicGetOk(BasicGetOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchBasicQosOk(BasicQosOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchBasicReturn(BasicReturnBody body, int channelId) throws QpidException
    {
        _basicReturnMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchChannelClose(ChannelCloseBody body, int channelId) throws QpidException
    {
        _channelCloseMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchChannelCloseOk(ChannelCloseOkBody body, int channelId) throws QpidException
    {
        _channelCloseOkMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchChannelFlow(ChannelFlowBody body, int channelId) throws QpidException
    {
        _channelFlowMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchChannelFlowOk(ChannelFlowOkBody body, int channelId) throws QpidException
    {
        _channelFlowOkMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchChannelOpenOk(ChannelOpenOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchConnectionClose(ConnectionCloseBody body, int channelId) throws QpidException
    {
        _connectionCloseMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchConnectionCloseOk(ConnectionCloseOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchConnectionOpenOk(ConnectionOpenOkBody body, int channelId) throws QpidException
    {
        _connectionOpenOkMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchConnectionRedirect(ConnectionRedirectBody body, int channelId) throws QpidException
    {
        _connectionRedirectMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchConnectionSecure(ConnectionSecureBody body, int channelId) throws QpidException
    {
        _connectionSecureMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchConnectionStart(ConnectionStartBody body, int channelId) throws QpidException
    {
        _connectionStartMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchConnectionTune(ConnectionTuneBody body, int channelId) throws QpidException
    {
        _connectionTuneMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchQueueDeleteOk(QueueDeleteOkBody body, int channelId) throws QpidException
    {
        _queueDeleteOkMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchQueuePurgeOk(QueuePurgeOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchAccessRequest(AccessRequestBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    @Override
    public boolean dispatchBasicAck(BasicAckBody body, int channelId) throws QpidException
    {
        return false;
    }

    @Override
    public boolean dispatchBasicNack(final BasicNackBody basicNackBody, final int channelId)
    {
        return false;
    }


    public boolean dispatchBasicCancel(BasicCancelBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchBasicConsume(BasicConsumeBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchBasicGet(BasicGetBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchBasicPublish(BasicPublishBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchBasicQos(BasicQosBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchBasicRecover(BasicRecoverBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchBasicReject(BasicRejectBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchChannelOpen(ChannelOpenBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchConnectionOpen(ConnectionOpenBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchConnectionSecureOk(ConnectionSecureOkBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchConnectionStartOk(ConnectionStartOkBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchConnectionTuneOk(ConnectionTuneOkBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchExchangeBound(ExchangeBoundBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchExchangeDeclare(ExchangeDeclareBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchExchangeDelete(ExchangeDeleteBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchQueueBind(QueueBindBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchQueueDeclare(QueueDeclareBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchQueueDelete(QueueDeleteBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchQueuePurge(QueuePurgeBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }


    public boolean dispatchTxCommit(TxCommitBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchTxRollback(TxRollbackBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchTxSelect(TxSelectBody body, int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    @Override
    public boolean dispatchQueueUnbind(final QueueUnbindBody queueUnbindBody, final int channelId) throws QpidException
    {
        return false;
    }

    @Override
    public boolean dispatchBasicRecoverSync(final BasicRecoverSyncBody basicRecoverSyncBody, final int channelId)
            throws QpidException
    {
        return false;
    }

    @Override
    public boolean dispatchConfirmSelect(final ConfirmSelectBody body, final int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    public boolean dispatchExchangeBoundOk(ExchangeBoundOkBody body, int channelId) throws QpidException
    {
        _exchangeBoundOkMethodHandler.methodReceived(_session, body, channelId);
        return true;
    }

    public boolean dispatchExchangeDeclareOk(ExchangeDeclareOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchExchangeDeleteOk(ExchangeDeleteOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchQueueBindOk(QueueBindOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchQueueDeclareOk(QueueDeclareOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchTxCommitOk(TxCommitOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchTxRollbackOk(TxRollbackOkBody body, int channelId) throws QpidException
    {
        return false;
    }

    public boolean dispatchTxSelectOk(TxSelectOkBody body, int channelId) throws QpidException
    {
        return false;
    }

}
