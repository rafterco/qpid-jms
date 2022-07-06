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
package org.apache.qpid.transport.network;

import static java.lang.Math.min;
import static org.apache.qpid.transport.network.Frame.FIRST_FRAME;
import static org.apache.qpid.transport.network.Frame.FIRST_SEG;
import static org.apache.qpid.transport.network.Frame.HEADER_SIZE;
import static org.apache.qpid.transport.network.Frame.LAST_FRAME;
import static org.apache.qpid.transport.network.Frame.LAST_SEG;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.FrameSizeObserver;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.ProtocolDelegate;
import org.apache.qpid.transport.ProtocolError;
import org.apache.qpid.transport.ProtocolEvent;
import org.apache.qpid.transport.ProtocolEventSender;
import org.apache.qpid.transport.ProtocolHeader;
import org.apache.qpid.transport.SegmentType;
import org.apache.qpid.transport.Struct;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.util.ByteBufferUtils;

/**
 * Disassembler
 */
public final class Disassembler implements ProtocolEventSender, ProtocolDelegate<Void>, FrameSizeObserver
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Disassembler.class);
    private final ByteBufferSender _sender;
    private final Object _sendlock = new Object();
    private volatile int _maxPayload;
    private final static ThreadLocal<BBEncoder> _encoder = new ThreadLocal<BBEncoder>()
    {
        public BBEncoder initialValue()
        {
            return new BBEncoder(4*1024);
        }
    };

    public Disassembler(ByteBufferSender sender, int maxFrame)
    {
        _sender = sender;
        if (maxFrame <= HEADER_SIZE || maxFrame >= 64*1024)
        {
            throw new IllegalArgumentException("maxFrame must be > HEADER_SIZE and < 64K: " + maxFrame);
        }
        _maxPayload = maxFrame - HEADER_SIZE;
    }

    public void send(ProtocolEvent event)
    {
        event.delegate(null, this);
    }

    public void flush()
    {
        synchronized (_sendlock)
        {
            _sender.flush();
        }
    }

    public void close()
    {
        synchronized (_sendlock)
        {
            _sender.close();
        }
    }

    public void init(Void v, ProtocolHeader header)
    {
        synchronized (_sendlock)
        {
            _sender.send(header.toByteBuffer());
            _sender.flush();
        }
    }

    public void control(Void v, Method method)
    {
        method(method, SegmentType.CONTROL);
    }

    public void command(Void v, Method method)
    {
        method(method, SegmentType.COMMAND);
    }

    private void method(Method method, SegmentType type)
    {
        BBEncoder enc = _encoder.get();
        enc.init();
        enc.writeUint16(method.getEncodedType());
        if (type == SegmentType.COMMAND)
        {
            if (method.isSync())
            {
                enc.writeUint16(0x0101);
            }
            else
            {
                enc.writeUint16(0x0100);
            }
        }
        method.write(enc);
        int methodLimit = enc.position();

        byte flags = FIRST_SEG;

        boolean payload = method.hasPayload();
        if (!payload)
        {
            flags |= LAST_SEG;
        }

        int headerLimit = -1;
        if (payload)
        {
            final Header hdr = method.getHeader();
            if (hdr != null)
            {
                if(hdr.getDeliveryProperties() != null)
                {
                    enc.writeStruct32(hdr.getDeliveryProperties());
                }
                if(hdr.getMessageProperties() != null)
                {
                    enc.writeStruct32(hdr.getMessageProperties());
                }
                if(hdr.getNonStandardProperties() != null)
                {
                    for (Struct st : hdr.getNonStandardProperties())
                    {
                        enc.writeStruct32(st);
                    }
                }
            }
            headerLimit = enc.position();
        }

        synchronized (_sendlock)
        {
            ByteBuffer buf = enc.underlyingBuffer();
            buf.flip();
            ByteBuffer copy = ByteBuffer.allocate(buf.remaining());
            ByteBufferUtils.copyTo(buf, copy);
            copy.flip();

            final ByteBuffer methodBuf = ByteBufferUtils.view(copy,0, methodLimit);
            fragment(flags, type, method, methodBuf);
            if (payload)
            {
                ByteBuffer body = method.getBody();
                ByteBuffer headerBuf = ByteBufferUtils.view(copy, methodLimit, headerLimit);
                fragment(body == null ? LAST_SEG : 0x0, SegmentType.HEADER, method, headerBuf);
                if (body != null)
                {
                    fragment(LAST_SEG, SegmentType.BODY, method, body.duplicate());
                }
            }
        }
    }

    private void fragment(byte flags, SegmentType type, ProtocolEvent event, ByteBuffer buffer)
    {
        byte typeb = (byte) type.getValue();
        byte track = event.getEncodedTrack() == Frame.L4 ? (byte) 1 : (byte) 0;

        int remaining = buffer.remaining();
        boolean first = true;
        while (true)
        {
            int size = min(_maxPayload, remaining);
            remaining -= size;

            byte newflags = flags;
            if (first)
            {
                newflags |= FIRST_FRAME;
                first = false;
            }
            if (remaining == 0)
            {
                newflags |= LAST_FRAME;
            }

            frame(newflags, typeb, track, event.getChannel(), size, buffer);

            if (remaining == 0)
            {
                break;
            }
        }
    }

    private void frame(byte flags, byte type, byte track, int channel, int size, ByteBuffer buffer)
    {
        ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

        data.put(0, flags);
        data.put(1, type);
        data.putShort(2, (short) (size + HEADER_SIZE));
        data.put(4, (byte) 0);
        data.put(5, track);
        data.putShort(6, (short) channel);


        _sender.send(data);

        if(size > 0)
        {
            final ByteBuffer view = ByteBufferUtils.view(buffer, 0, size);
            _sender.send(view);
            buffer.position(buffer.position() + size);
        }
    }

    public void error(Void v, ProtocolError error)
    {
        throw new IllegalArgumentException(String.valueOf(error));
    }

    @Override
    public void setMaxFrameSize(final int maxFrame)
    {
        if (maxFrame <= HEADER_SIZE || maxFrame >= 64*1024)
        {
            throw new IllegalArgumentException("maxFrame must be > HEADER_SIZE and < 64K: " + maxFrame);
        }
        _maxPayload = maxFrame - HEADER_SIZE;

    }
}
