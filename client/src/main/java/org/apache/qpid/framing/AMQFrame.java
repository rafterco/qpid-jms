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

import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.util.ByteBufferUtils;

public class AMQFrame extends AMQDataBlock implements EncodableAMQDataBlock
{
    private static final int HEADER_SIZE = 7;
    private final int _channel;

    private final AMQBody _bodyFrame;
    public static final byte FRAME_END_BYTE = (byte) 0xCE;

    public AMQFrame(final int channel, final AMQBody bodyFrame)
    {
        _channel = channel;
        _bodyFrame = bodyFrame;
    }

    public long getSize()
    {
        return 1 + 2 + 4 + _bodyFrame.getSize() + 1;
    }

    public static final int getFrameOverhead()
    {
        return 1 + 2 + 4 + 1;
    }


    private static final ByteBuffer FRAME_END_BYTE_BUFFER = ByteBuffer.allocate(1);
    static
    {
        FRAME_END_BYTE_BUFFER.put(FRAME_END_BYTE);
        FRAME_END_BYTE_BUFFER.flip();
    }

    @Override
    public long writePayload(final ByteBufferSender sender)
    {
        ByteBuffer frameHeader = ByteBuffer.allocate(HEADER_SIZE);

        frameHeader.put(_bodyFrame.getFrameType());
        ByteBufferUtils.putUnsignedShort(frameHeader, _channel);
        ByteBufferUtils.putUnsignedInt(frameHeader, _bodyFrame.getSize());
        frameHeader.flip();
        sender.send(frameHeader);
        long size = 8 + _bodyFrame.writePayload(sender);

        sender.send(FRAME_END_BYTE_BUFFER.duplicate());
        return size;
    }

    public final int getChannel()
    {
        return _channel;
    }

    public final AMQBody getBodyFrame()
    {
        return _bodyFrame;
    }

    public String toString()
    {
        return "Frame channelId: " + _channel + ", bodyFrame: " + String.valueOf(_bodyFrame);
    }

}
