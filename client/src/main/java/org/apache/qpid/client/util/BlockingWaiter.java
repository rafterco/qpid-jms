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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.QpidException;
import org.apache.qpid.client.failover.FailoverException;

/**
 * BlockingWaiter is a 'rendezvous' which delegates handling of
 * incoming Objects to a listener implemented as a sub-class of this and hands off the process or
 * error to a consumer. The producer of the event does not have to wait for the consumer to take the event, so this
 * differs from a 'rendezvous' in that sense.
 * <p>
 * BlockingWaiters are used to coordinate when waiting for an an event that expect a response.
 * They are always used in a 'one-shot' manner, that is, to receive just one response. Usually the caller has to register
 * them as method listeners with an event dispatcher and remember to de-register them (in a finally block) once they
 * have been completed.
 * <p>
 * The {@link #process} must return <tt>true</tt> on any incoming method that it handles. This indicates to
 * this listeners that the object just processed ends the waiting process.
 * <p>
 * Errors from the producer are rethrown to the consumer.
 * <p>
 * TODO  Interruption is caught but not handled. This could be allowed to fall through. This might actually be useful
 * for fail-over where a thread is blocking when failure happens, it could be interrupted to abandon or retry
 * when this happens. At the very least, restore the interrupted status flag.
 * <p>
 * TODO  If the retrotranslator can handle it, could use a SynchronousQueue to implement this rendezvous. Need to
 * check that SynchronousQueue has a non-blocking put method available.
 */
public abstract class BlockingWaiter<T>
{
    private static final Logger _logger = LoggerFactory.getLogger(BlockingWaiter.class);

    /** This flag is used to indicate that the blocked for method has been received. */
    private volatile boolean _ready = false;

    /** This flag is used to indicate that the received error has been processed. */
    private volatile boolean _errorAck = false;

    /** Used to protect the shared event and ready flag between the producer and consumer. */
    private final ReentrantLock _lock = new ReentrantLock();

    /** Used to signal that a method has been received */
    private final Condition _receivedCondition = _lock.newCondition();

    /** Used to signal that a error has been processed */
    private final Condition _errorConditionAck = _lock.newCondition();

    /** Used to hold the most recent exception that is passed to the {@link #error(Exception)} method. */
    private volatile Exception _error;

    /** Holds the incoming Object. */
    private Object _doneObject = null;
    private AtomicBoolean _waiting = new AtomicBoolean(false);
    private boolean _closed = false;

    /**
     * Delegates processing of the incoming object to the handler.
     *
     * @param object The object to process.
     *
     * @return <tt>true</tt> if the waiting is complete, <tt>false</tt> if waiting should continue.
     */
    public abstract boolean process(T object);

    /**
     * An Object has been received and should be processed to see if our wait condition has been reached.
     *
     * @param object The object received.
     *
     * @return <tt>true</tt> if the waiting is complete, <tt>false</tt> if waiting should continue.
     */
    public boolean received(T object)
    {

        boolean ready = process(object);

        if (ready)
        {
            // we only update the flag from inside the synchronized block
            // so that the blockForFrame method cannot "miss" an update - it
            // will only ever read the flag from within the synchronized block
            _lock.lock();
            try
            {
                _doneObject = object;
                _ready = ready;
                _receivedCondition.signal();
            }
            finally
            {
                _lock.unlock();
            }
        }

        return ready;
    }

    /**
     * Blocks until an object is received that is handled by process, or the specified timeout
     * has passed.
     *
     * Once closed any attempt to wait will throw an exception.
     *
     * @param timeout The timeout in milliseconds.
     *
     * @return The object that resolved the blocking.
     *
     * @throws QpidException
     * @throws FailoverException
     */
    public Object block(long timeout) throws QpidException, FailoverException
    {
        if (timeout < 0)
        {
            throw new IllegalArgumentException("timeout must be zero or greater");
        }

        long nanoTimeout = TimeUnit.MILLISECONDS.toNanos(timeout);

        _lock.lock();

        try
        {
            if (_closed)
            {
                throw throwClosedException();
            }

            if (_error == null)
            {
                _waiting.set(true);

                while (!_ready && _error == null)
                {
                    try
                    {
                        nanoTimeout = _receivedCondition.awaitNanos(nanoTimeout);

                        if (nanoTimeout <= 0 && !_ready && _error == null)
                        {
                            final String errorMsg = String.format(
                                    "The server's response was not received within the time-out period of %d ms. "
                                    + "Possible reasons include: the server may be too busy, the network may be "
                                    + "overloaded, or this JVM itself may be too busy to process the response. [%s]",
                                    timeout, getConnectionDetails() == null ? "" : getConnectionDetails());
                            _error = new AMQTimeoutException(errorMsg, null);
                            _ready = true;
                        }
                    }
                    catch (InterruptedException e)
                    {
                        _logger.error(e.getMessage(), e);
                        // IGNORE    -- //fixme this isn't ideal as being interrupted isn't equivalent to success
                    }
                }
            }

            if (_error != null)
            {
                if (_error instanceof QpidException)
                {
                    throw (QpidException) _error;
                }
                else if (_error instanceof FailoverException)
                {
                    // This should ensure that FailoverException is not wrapped and can be caught.
                    throw (FailoverException) _error; // needed to expose FailoverException.
                }
                else
                {
                    throw new QpidException("Woken up due to " + _error.getClass(), _error);
                }
            }

        }
        finally
        {
            _waiting.set(false);

            //Release Error handling thread
            if (_error != null)
            {
                _errorAck = true;
                _errorConditionAck.signal();

                _error = null;
            }
            _lock.unlock();
        }

        return _doneObject;
    }

    /**
     * This is a callback, called when an error has occurred that should interrupt any waiter.
     * It is also called from within this class to avoid code repetition but it should only be called by the MINA threads.
     *
     * Once closed any notification of an exception will be ignored.
     *
     * @param e The exception being propagated.
     */
    public void error(Exception e)
    {
        // set the error so that the thread that is blocking (against blockForFrame())
        // can pick up the exception and rethrow to the caller

        _lock.lock();

        try
        {
            if (_closed)
            {
                return;
            }

            if (_error == null)
            {
                _error = e;
            }
            else
            {
                _logger.error("WARNING: new error '" + (e == null ? "null" : e.getMessage()) + "' arrived while old one not yet processed:" + _error.getMessage());
            }

            if (_waiting.get())
            {

                _ready = true;
                _receivedCondition.signal();

                while (!_errorAck)
                {
                    try
                    {
                        _errorConditionAck.await();
                    }
                    catch (InterruptedException e1)
                    {
                        _logger.error(e1.getMessage(), e1);
                    }
                }
                _errorAck = false;
            }
        }
        finally
        {
            _lock.unlock();
        }
    }

    /**
     * Close this Waiter so that no more errors are processed.
     * This is a preventative method to ensure that a second error thread does not get stuck in the error method after
     * the await has returned. This has not happened but in practise but if two errors occur on the Connection at
     * the same time then it is conceivably possible for the second to get stuck if the first one is processed by a
     * waiter.
     *
     * Once closed any attempt to wait will throw an exception.
     * Any notification of an exception will be ignored.
     */
    public void close()
    {
        _lock.lock();
        try
        {
            //if we have already closed then our job is done.
            if (_closed)
            {
                return;
            }

            //Close Waiter so no more exceptions are processed
            _closed = true;

            //Wake up any await() threads

            //If we are waiting then use the error() to wake them up.
            if (_waiting.get())
            {
                error(throwClosedException());
            }
            //If they are not waiting then there is nothing to do.

            // Wake up any error handling threads

            if (!_errorAck)
            {
                _errorAck = true;
                _errorConditionAck.signal();

                _error = null;
            }
        }
        finally
        {
            _lock.unlock();
        }
    }

    /**
     * Helper method to generate the a closed Exception.
     *
     * todo: This should be converted to something more friendly. 
     *
     * @return AMQException to throw to waiters when the Waiter is closed.
     */
    private QpidException throwClosedException()
    {
        return new QpidException("Waiter was closed.", null);
    }

    public abstract String getConnectionDetails();

}
