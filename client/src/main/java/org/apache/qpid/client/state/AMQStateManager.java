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
package org.apache.qpid.client.state;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.qpid.client.AMQProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.QpidException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;

/**
 * The state manager is responsible for managing the state of the protocol session.
 * <p>
 * For each {@link AMQProtocolHandler} there is a separate state manager.
 * <p>
 * The AMQStateManager is now attached to the {@link AMQProtocolHandler} and that is the sole point of reference so that
 * As the {@link AMQProtocolSession} changes due to failover the AMQStateManager need not be copied around.
 * <p>
 * The StateManager works by any component can wait for a state change to occur by using the following sequence.
 * <ul>
 * <li>{@literal StateWaiter waiter = stateManager.createWaiter(Set<AMQState> states); }
 * <li> // Perform action that will cause state change
 * <li>waiter.await();
 * </ul>
 * <p>
 * The two step process is required as there is an inherit race condition between starting a process that will cause
 * the state to change and then attempting to wait for that change. The interest in the change must be first set up so
 * that any asynchronous errors that occur can be delivered to the correct waiters.
 */
public class AMQStateManager implements AMQMethodListener
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQStateManager.class);

    private AMQProtocolSession _protocolSession;

    /** The current state */
    private AMQState _currentState;

    private final Object _stateLock = new Object();

    private static final long MAXIMUM_STATE_WAIT_TIME = Long.parseLong(System.getProperty("amqj.MaximumStateWait", "30000"));

    private final List<StateWaiter> _waiters = new CopyOnWriteArrayList<StateWaiter>();
    private Exception _lastException;

    public AMQStateManager()
    {
        this(null);
    }

    public AMQStateManager(AMQProtocolSession protocolSession)
    {
        this(AMQState.CONNECTION_NOT_STARTED, protocolSession);
    }

    protected AMQStateManager(AMQState state, AMQProtocolSession protocolSession)
    {
        _protocolSession = protocolSession;
        _currentState = state;
    }

    public AMQState getCurrentState()
    {
        synchronized (_stateLock)
        {
            return _currentState;
        }
    }

    public void changeState(AMQState newState)
    {
        _logger.debug("State changing to " + newState + " from old state " + _currentState);

        synchronized (_stateLock)
        {
            _currentState = newState;

            _logger.debug("Notififying State change to " + _waiters.size() + " : " + _waiters);

            for (StateWaiter waiter : _waiters)
            {
                waiter.received(newState);
            }
        }
    }

    public <B extends AMQMethodBody> boolean methodReceived(AMQMethodEvent<B> evt) throws QpidException
    {
        B method = evt.getMethod();

        method.execute(_protocolSession.getMethodDispatcher(), evt.getChannelId());
        return true;
    }

    /**
     * Setting of the ProtocolSession will be required when Failover has been successfully completed.
     *
     * The new {@link AMQProtocolSession} that has been re-established needs to be provided as that is now the
     * connection to the network.
     *
     * @param session The new protocol session
     */
    public void setProtocolSession(AMQProtocolSession session)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Setting ProtocolSession:" + session);
        }
        _protocolSession = session;
    }

    /**
     * Propagate error to waiters
     *
     * @param error The error to propagate.
     */
    public void error(Exception error)
    {
        // TODO - this logic seems particularly strange and the assumptions it relies upon around the difference between
        //        QpidException and non-QpidException have probably not been true for a while.
        if (error instanceof QpidException)
        {
            // AMQException should be being notified before closing the
            // ProtocolSession. Which will change the State to CLOSED.
            // if we have a hard error.
            if (!(error instanceof AMQException && !((AMQException)error).isHardError()))
            {
                changeState(AMQState.CONNECTION_CLOSING);
            }
        }
        else
        {
            // Be on the safe side here and mark the connection closed
            changeState(AMQState.CONNECTION_CLOSED);
        }

        if (_waiters.size() == 0)
        {
            _logger.info("No Waiters for error. Saving as last error:" + error.getMessage());
            _lastException = error;
        }
        propagateExceptionToStateWaiters(error);
    }

    public void propagateExceptionToStateWaiters(final Exception error)
    {
        for (StateWaiter waiter : _waiters)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Notifying waiter " + waiter + " for error:" + error.getMessage());
            }
            waiter.error(error);
        }
    }

    /**
     * This provides a single place that the maximum time for state change to occur can be accessed.
     * It is currently set via System property amqj.MaximumStateWait
     *
     * @return long Milliseconds value for a timeout
     */
    public long getWaitTimeout()
    {
        return MAXIMUM_STATE_WAIT_TIME;
    }

    /**
     * Create and add a new waiter to the notification list.
     *
     * @param states The waiter will attempt to wait for one of these desired set states to be achived.
     *
     * @return the created StateWaiter.
     */
    public StateWaiter createWaiter(Set<AMQState> states)
    {
        final StateWaiter waiter;
        synchronized (_stateLock)
        {
            waiter = new StateWaiter(this, _currentState, states);

            _waiters.add(waiter);
        }

        return waiter;
    }

    /**
     * Remove the waiter from the notification list.
     *
     * @param waiter The waiter to remove.
     */
    public void removeWaiter(StateWaiter waiter)
    {
        synchronized (_stateLock)
        {
            _waiters.remove(waiter);
        }
    }

    public Exception getLastException()
    {
        return _lastException;
    }

    public void clearLastException()
    {
        _lastException = null;
    }
}
