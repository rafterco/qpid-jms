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

import org.apache.qpid.QpidException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQMethodNotImplementedException;
import org.apache.qpid.framing.BasicRecoverSyncBody;
import org.apache.qpid.framing.BasicRecoverSyncOkBody;
import org.apache.qpid.framing.ChannelAlertBody;
import org.apache.qpid.framing.MethodDispatcher;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.framing.QueueUnbindOkBody;

public class ClientMethodDispatcherImpl_8_0 extends ClientMethodDispatcherImpl implements MethodDispatcher
{
    public ClientMethodDispatcherImpl_8_0(AMQProtocolSession session)
    {
        super(session);
    }

    public boolean dispatchChannelAlert(ChannelAlertBody body, int channelId) throws QpidException
    {
        return false;
    }

    @Override
    public boolean dispatchQueueUnbindOk(final QueueUnbindOkBody queueUnbindOkBody, final int channelId)
    {
        return false;
    }

    @Override
    public boolean dispatchBasicRecoverSyncOk(final BasicRecoverSyncOkBody basicRecoverSyncOkBody,
                                              final int channelId)
    {
        return false;
    }

    @Override
    public boolean dispatchQueueUnbind(final QueueUnbindBody body, final int channelId) throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }

    @Override
    public boolean dispatchBasicRecoverSync(final BasicRecoverSyncBody body, final int channelId)
            throws QpidException
    {
        throw new AMQMethodNotImplementedException(body);
    }
}
