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
package org.apache.qpid.client.util;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A blocking queue that emits events above a user specified threshold allowing the caller to take action (e.g. flow
 * control) to try to prevent the queue growing (much) further. The underlying queue itself is not bounded therefore the
 * caller is not obliged to react to the events.
 * <p>
 * This implementation is <b>only</b> safe where we have a single
 * thread adding items and a single (different) thread removing items.
 * <p>
 * TODO  Make this implement java.util.Queue and hide the implementation. Then different queue types can be substituted.
 */
public class FlowControllingBlockingQueue<T>
{
	private static final Logger _logger = LoggerFactory.getLogger(FlowControllingBlockingQueue.class);
	
    /** This queue is bounded and is used to store messages before being dispatched to the consumer */
    private final Queue<T> _queue = new ConcurrentLinkedQueue<T>();

    private final int _flowControlHighThreshold;
    private final int _flowControlLowThreshold;

    private final ThresholdListener _listener;

    /** We require a separate count so we can track whether we have reached the threshold */
    private int _count;
    
    private boolean disableFlowControl; 

    private volatile boolean _closed;

    public boolean isEmpty()
    {
        return _queue.isEmpty();
    }

    public void close()
    {
        synchronized (this)
        {
            _closed = true;
            notifyAll();
        }
    }


    public interface ThresholdListener
    {
        void aboveThreshold(int currentValue);

        void underThreshold(int currentValue);
    }

    public FlowControllingBlockingQueue(int threshold, ThresholdListener listener)
    {
        this(threshold, threshold, listener);
    }

    public FlowControllingBlockingQueue(int highThreshold, int lowThreshold, ThresholdListener listener)
    {
        _flowControlHighThreshold = highThreshold;
        _flowControlLowThreshold = lowThreshold;
        _listener = listener;
        if (highThreshold <= 0)
        {
            disableFlowControl = true;
        }
        else if (lowThreshold > highThreshold)
        {
            throw new IllegalArgumentException(String.format(
                    "Invalid low threshold %d : it should be less or equal high threshold %d",
                    lowThreshold,
                    highThreshold));
        }
        else if (lowThreshold < 1)
        {
            throw new IllegalArgumentException(String.format("Invalid low threshold %d: it should be greater than 0",
                                                             lowThreshold));
        }
    }

    public T blockingPeek() throws InterruptedException
    {
        T o = _queue.peek();
        if (o == null)
        {
            synchronized (this)
            {
                while (!_closed && (o = _queue.peek()) == null)
                {
                    wait();
                }
            }
        }
        return o;
    }

    public T nonBlockingTake() throws InterruptedException
    {
        T o = _queue.poll();

        if (o != null && !disableFlowControl && _listener != null)
        {
            reportBelowIfNecessary();
        }

        return o;
    }

    public T take() throws InterruptedException
    {
        T o = _queue.poll();
        if(o == null)
        {
            synchronized(this)
            {
                while(!_closed && (o = _queue.poll())==null)
                {
                    wait();
                }
            }
        }
        if (!_closed && !disableFlowControl && _listener != null)
        {
            reportBelowIfNecessary();
        }

        return o;
    }

    public void add(T o)
    {
        synchronized(this)
        {
            _queue.add(o);

            notifyAll();
        }
        if (!disableFlowControl && _listener != null)
        {
            reportAboveIfNecessary();
        }
    }

    public boolean remove(final T o)
    {
        final boolean removed = _queue.remove(o);
        if (removed && !disableFlowControl && _listener != null)
        {
            reportBelowIfNecessary();
        }
        return removed;
    }

    public Iterator<T> iterator()
    {
        return _queue.iterator();
    }

    public void clear()
    {
        _queue.clear();

        if (!disableFlowControl && _listener != null)
        {
            synchronized (_listener)
            {
                int count = _count;
                _count = 0;

                if (count >= _flowControlLowThreshold)
                {
                    _listener.underThreshold(0);
                }
            }

        }
    }

    private void reportAboveIfNecessary()
    {
        synchronized (_listener)
        {
            if (++_count == _flowControlHighThreshold)
            {
                _listener.aboveThreshold(_count);
            }
        }
    }

    private void reportBelowIfNecessary()
    {
        synchronized (_listener)
        {
            if (_count-- == _flowControlLowThreshold)
            {
                _listener.underThreshold(_count);
            }
        }
    }
}
