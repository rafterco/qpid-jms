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
package org.apache.qpid.transport.network.security.ssl;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.transport.ExceptionHandlingByteBufferReceiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.security.SSLStatus;

public class SSLReceiver implements ExceptionHandlingByteBufferReceiver
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLReceiver.class);

    private final ExceptionHandlingByteBufferReceiver delegate;
    private final SSLEngine engine;
    private final int sslBufSize;
    private final ByteBuffer localBuffer;
    private final SSLStatus _sslStatus;
    private ByteBuffer appData;
    private boolean dataCached = false;

    private String _hostname;

    public SSLReceiver(final SSLEngine engine, final ExceptionHandlingByteBufferReceiver delegate, final SSLStatus sslStatus)
    {
        this.engine = engine;
        this.delegate = delegate;
        this.sslBufSize = engine.getSession().getApplicationBufferSize();
        appData = ByteBuffer.allocate(sslBufSize);
        localBuffer = ByteBuffer.allocate(sslBufSize);
        _sslStatus = sslStatus;
    }

    public void setHostname(String hostname)
    {
        _hostname = hostname;
    }
    
    public void closed()
    {
       delegate.closed();
    }

    public void exception(Throwable t)
    {
        delegate.exception(t);
    }

    private ByteBuffer addPreviouslyUnreadData(ByteBuffer buf)
    {
        if (dataCached)
        {
            ByteBuffer b = ByteBuffer.allocate(localBuffer.remaining() + buf.remaining());
            b.put(localBuffer);
            b.put(buf);
            b.flip();
            dataCached = false;
            return b;
        }
        else
        {
            return buf;
        }
    }

    public void received(ByteBuffer buf)
    {
        ByteBuffer netData = addPreviouslyUnreadData(buf);

        HandshakeStatus handshakeStatus;
        Status status;

        while (netData.hasRemaining())
        {
            try
            {
                SSLEngineResult result = engine.unwrap(netData, appData);
                synchronized (_sslStatus.getSslLock())
                {
                    _sslStatus.getSslLock().notifyAll();
                }

                int read = result.bytesProduced();
                status = result.getStatus();
                handshakeStatus = result.getHandshakeStatus();

                if (read > 0)
                {
                    int limit = appData.limit();
                    appData.limit(appData.position());
                    appData.position(appData.position() - read);

                    ByteBuffer data = appData.slice();

                    appData.limit(limit);
                    appData.position(appData.position() + read);

                    delegate.received(data);
                }


                switch(status)
                {
                    case CLOSED:
                        synchronized(_sslStatus.getSslLock())
                        {
                            _sslStatus.getSslLock().notifyAll();
                        }
                        return;

                    case BUFFER_OVERFLOW:
                        appData = ByteBuffer.allocate(sslBufSize);
                        continue;

                    case BUFFER_UNDERFLOW:
                        localBuffer.clear();
                        localBuffer.put(netData);
                        localBuffer.flip();
                        dataCached = true;
                        break;

                    case OK:
                        break; // do nothing

                    default:
                        throw new IllegalStateException("SSLReceiver: Invalid State " + status);
                }

                switch (handshakeStatus)
                {
                    case NEED_UNWRAP:
                        if (netData.hasRemaining())
                        {
                            continue;
                        }
                        break;
                    case NEED_WRAP:
                        break;
                    case NEED_TASK:
                        doTasks();
                        break;
                    case FINISHED:
                        if (_hostname != null)
                        {
                            SSLUtil.verifyHostname(engine, _hostname);
                        }
                        break;
                    case NOT_HANDSHAKING:
                        break;
                    default:
                        throw new IllegalStateException("SSLReceiver: Invalid State " + status);
                }
                if (handshakeStatus != HandshakeStatus.NEED_UNWRAP)
                {
                    synchronized(_sslStatus.getSslLock())
                    {
                        _sslStatus.getSslLock().notifyAll();
                    }
                }

            }
            catch(SSLException e)
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Error caught in SSLReceiver", e);
                }
                _sslStatus.setSslErrorFlag();
                synchronized(_sslStatus.getSslLock())
                {
                    _sslStatus.getSslLock().notifyAll();
                }                
                exception(new TransportException("Error in SSLReceiver: " + e.getMessage(),e));
            }

        }
    }

    private void doTasks()
    {
        Runnable runnable;
        while ((runnable = engine.getDelegatedTask()) != null)
        {
            runnable.run();
        }
    }

}
