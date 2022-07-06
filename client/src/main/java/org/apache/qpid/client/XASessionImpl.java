/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.client;

import org.apache.qpid.QpidException;

import javax.jms.JMSException;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TopicSession;
import javax.jms.TransactionInProgressException;
import javax.jms.XAQueueSession;
import javax.jms.XASession;
import javax.jms.XATopicSession;
import javax.transaction.xa.XAResource;

/**
 * This is an implementation of the javax.jms.XASession interface.
 */
public class XASessionImpl extends AMQSession_0_10 implements XASession, XATopicSession, XAQueueSession
{
    /**
     * XAResource associated with this XASession
     */
    private final XAResourceImpl _xaResource;

    /**
     * This XASession Qpid DtxSession
     */
    private org.apache.qpid.transport.Session _qpidDtxSession;

    /**
     * The standard session
     */
    private Session _jmsSession;


    //   Constructors
    /**
     * Create a JMS XASession
     */
    public XASessionImpl(org.apache.qpid.transport.Connection qpidConnection, AMQConnection con, int channelId,
                         int defaultPrefetchHigh, int defaultPrefetchLow)
    {
        this(qpidConnection, con, channelId, false, Session.AUTO_ACKNOWLEDGE, defaultPrefetchHigh, defaultPrefetchLow, null);
     }

     public XASessionImpl(org.apache.qpid.transport.Connection qpidConnection, AMQConnection con, int channelId,
                int ackMode, int defaultPrefetchHigh, int defaultPrefetchLow)
     {
        this(qpidConnection, con, channelId, false, ackMode,
                        defaultPrefetchHigh, defaultPrefetchLow, null);

     }

     public XASessionImpl(org.apache.qpid.transport.Connection qpidConnection, AMQConnection con, int channelId,
               boolean transacted, int ackMode, int defaultPrefetchHigh, int defaultPrefetchLow,
               String name)
     {
        super(qpidConnection,
              con,
              channelId,
              transacted,
              ackMode,
              defaultPrefetchHigh,
              defaultPrefetchLow,
              name);
        _xaResource = new XAResourceImpl(this);
     }


    //   public methods

    /**
     * Create a qpid session.
     */
    @Override
    public org.apache.qpid.transport.Session createSession()
    {
        _qpidDtxSession = getQpidConnection().createSession(0,true);
        _qpidDtxSession.dtxSelect();
        _qpidDtxSession.setSessionListener(this);
        return _qpidDtxSession;
    }

    /**
     * Gets the session associated with this XASession.
     *
     * @return The session object.
     * @throws JMSException if an internal error occurs.
     */
    public Session getSession() throws JMSException
    {
        return this;
    }

    /**
     * Returns an XA resource.
     *
     * @return An XA resource.
     */
    public XAResource getXAResource()
    {
        return _xaResource;
    }

    //   overwritten mehtods
    /**
     * Throws a {@link TransactionInProgressException}, since it should
     * not be called for an XASession object.
     *
     * @throws TransactionInProgressException always.
     */
    public void commit() throws JMSException
    {
        throw new TransactionInProgressException(
                "XASession:  A direct invocation of the commit operation is probibited!");
    }

    /**
     * Throws a {@link TransactionInProgressException}, since it should
     * not be called for an XASession object.
     *
     * @throws TransactionInProgressException always.
     */
    public void rollback() throws JMSException
    {
        throw new TransactionInProgressException(
                "XASession: A direct invocation of the rollback operation is probibited!");
    }

    /**
     * Access to the underlying Qpid Session
     *
     * @return The associated Qpid Session.
     */
    protected org.apache.qpid.transport.Session getQpidSession()
    {
        return _qpidDtxSession;
    }

    //    interface  XAQueueSession
    /**
     * Gets the topic session associated with this <CODE>XATopicSession</CODE>.
     *
     * @return the topic session object
     * @throws JMSException If an internal error occurs.
     */
    public QueueSession getQueueSession() throws JMSException
    {
        return this;
    }

    //    interface  XATopicSession

    /**
     * Gets the topic session associated with this <CODE>XATopicSession</CODE>.
     *
     * @return the topic session object
     * @throws JMSException If an internal error occurs.
     */
    public TopicSession getTopicSession() throws JMSException
    {
        return this;
    }

    @Override
    protected void acknowledgeImpl()
    {
        if (_xaResource.isEnlisted())
        {
            acknowledgeMessage(Long.MAX_VALUE, true) ;
        }
        else
        {
            super.acknowledgeImpl() ;
        }
    }

    @Override
    void resubscribe() throws QpidException
    {
        super.resubscribe();
        createSession();
    }
}
