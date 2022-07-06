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
package org.apache.qpid.framing;

import java.nio.ByteBuffer;

import org.apache.qpid.QpidException;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.util.ByteBufferUtils;

public class ContentHeaderBody implements AMQBody
{
    public static final byte TYPE = 2;
    public static final int CLASS_ID =  60;
    private static final int HEADER_SIZE = 14;

    private long _bodySize;

    /** must never be null */
    private final BasicContentHeaderProperties _properties;

    public ContentHeaderBody(ByteBuffer buffer, long size) throws AMQFrameDecodingException
    {
        ByteBufferUtils.getUnsignedShort(buffer);
        ByteBufferUtils.getUnsignedShort(buffer);
        _bodySize = buffer.getLong();
        int propertyFlags = ByteBufferUtils.getUnsignedShort(buffer);
        ContentHeaderPropertiesFactory factory = ContentHeaderPropertiesFactory.getInstance();
        _properties = factory.createContentHeaderProperties(CLASS_ID, propertyFlags, buffer, (int)size - 14);

    }

    public ContentHeaderBody(BasicContentHeaderProperties props)
    {
        _properties = props;
    }

    public ContentHeaderBody(BasicContentHeaderProperties props, long bodySize)
    {
        _properties = props;
        _bodySize = bodySize;
    }

    public byte getFrameType()
    {
        return TYPE;
    }

    /**
     * Helper method that is used currently by the persistence layer.
     * @param buffer buffer to decode
     * @param size size of the body
     *
     * @return the decoded header body
     * @throws AMQFrameDecodingException if there is a decoding issue
     * @throws AMQProtocolVersionException if there is a version issue
     */
    public static ContentHeaderBody createFromBuffer(ByteBuffer buffer, long size)
        throws AMQFrameDecodingException, AMQProtocolVersionException
    {
        ContentHeaderBody body = new ContentHeaderBody(buffer, size);
        
        return body;
    }

    public int getSize()
    {
        return 2 + 2 + 8 + 2 + _properties.getPropertyListSize();
    }

    @Override
    public long writePayload(final ByteBufferSender sender)
    {
        ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);
        ByteBufferUtils.putUnsignedShort(data, CLASS_ID);
        ByteBufferUtils.putUnsignedShort(data, 0);
        data.putLong(_bodySize);
        ByteBufferUtils.putUnsignedShort(data, _properties.getPropertyFlags());
        data.flip();
        sender.send(data);
        return HEADER_SIZE + _properties.writePropertyListPayload(sender);
    }

    public long writePayload(final ByteBuffer buf)
    {
        ByteBufferUtils.putUnsignedShort(buf, CLASS_ID);
        ByteBufferUtils.putUnsignedShort(buf, 0);
        buf.putLong(_bodySize);
        ByteBufferUtils.putUnsignedShort(buf, _properties.getPropertyFlags());
        return HEADER_SIZE + _properties.writePropertyListPayload(buf);
    }

    public void handle(final int channelId, final AMQVersionAwareProtocolSession session)
            throws QpidException
    {
        session.contentHeaderReceived(channelId, this);
    }

    public static AMQFrame createAMQFrame(int channelId,
                                          BasicContentHeaderProperties properties,
                                          long bodySize)
    {
        return new AMQFrame(channelId, new ContentHeaderBody(properties, bodySize));
    }

    public BasicContentHeaderProperties getProperties()
    {
        return _properties;
    }

    @Override
    public String toString()
    {
        return "ContentHeaderBody{" +
                "classId=" + CLASS_ID +
                ", weight=" + 0 +
                ", bodySize=" + _bodySize +
                ", properties=" + _properties +
                '}';
    }

    public int getClassId()
    {
        return CLASS_ID;
    }

    public int getWeight()
    {
        return 0;
    }

    /** unsigned long but java can't handle that anyway when allocating byte array
     *
     * @return the body size */
    public long getBodySize()
    {
        return _bodySize;
    }

    public void setBodySize(long bodySize)
    {
        _bodySize = bodySize;
    }

    public static void process(final ByteBuffer buffer,
                               final ChannelMethodProcessor methodProcessor, final long size)
            throws AMQFrameDecodingException
    {

        int classId = ByteBufferUtils.getUnsignedShort(buffer);
        ByteBufferUtils.getUnsignedShort(buffer);
        long bodySize = buffer.getLong();
        int propertyFlags = ByteBufferUtils.getUnsignedShort(buffer);

        BasicContentHeaderProperties properties;

        if (classId != CLASS_ID)
        {
            throw new AMQFrameDecodingException("Unsupported content header class id: " + classId, null);
        }
        properties = new BasicContentHeaderProperties();
        properties.populatePropertiesFromBuffer(buffer, propertyFlags, (int)(size-14));

        if(!methodProcessor.ignoreAllButCloseOk())
        {
            methodProcessor.receiveMessageHeader(properties, bodySize);
        }
    }

    public void dispose()
    {
        _properties.dispose();
    }

    public void clearEncodedForm()
    {
        _properties.clearEncodedForm();
    }
}
