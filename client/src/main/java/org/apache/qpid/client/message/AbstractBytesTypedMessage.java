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

package org.apache.qpid.client.message;

import org.apache.qpid.QpidException;
import org.apache.qpid.client.util.JMSExceptionHelper;
import org.apache.qpid.transport.util.Functions;

import javax.jms.JMSException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import java.nio.ByteBuffer;

/**
 * @author Apache Software Foundation
 */
public abstract class AbstractBytesTypedMessage extends AbstractJMSMessage
{
    private boolean _readableMessage = false;

    AbstractBytesTypedMessage(AMQMessageDelegateFactory delegateFactory, boolean fromReceivedMessage)
    {

        super(delegateFactory, fromReceivedMessage); // this instanties a content header
        _readableMessage = fromReceivedMessage;
    }

    AbstractBytesTypedMessage(AMQMessageDelegate delegate, boolean fromReceivedMessage) throws QpidException
    {

        super(delegate, fromReceivedMessage);
        _readableMessage = fromReceivedMessage;

    }

    protected void checkReadable() throws MessageNotReadableException
    {
        if (!_readableMessage)
        {
            throw new MessageNotReadableException("You need to call reset() to make the message readable");
        }
    }

    @Override
    protected void checkWritable() throws MessageNotWriteableException
    {
        super.checkWritable();
        if(_readableMessage)
        {
            throw new MessageNotWriteableException("You need to call clearBody() to make the message writable");
        }
    }

    public void clearBody() throws JMSException
    {
        super.clearBody();
        _readableMessage = false;
    }

    protected void setReadable(boolean readable)
    {
        _readableMessage = readable;
    }


    public String toBodyString() throws JMSException
    {
        try
        {
            ByteBuffer data = getData();
        	if (data != null)
        	{
        		return Functions.str(data, 100, 0);
        	}
        	else
        	{
        		return "";
        	}

        }
        catch (Exception e)
        {
            throw JMSExceptionHelper.chainJMSException(new JMSException(e.toString()), e);
        }

    }


    abstract public void reset();




}
