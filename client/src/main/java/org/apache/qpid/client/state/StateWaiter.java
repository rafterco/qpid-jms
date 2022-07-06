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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.QpidException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.util.BlockingWaiter;

import java.util.Set;

/**
 * This is an implementation of the {@link BlockingWaiter} to provide error handing and a waiting mechanism for state
 * changes.
 *
 * On construction the current state and a set of States to await for is provided.
 *
 * When await() is called the state at construction is compared against the awaitStates. If the state at construction is
 * a desired state then await() returns immediately.
 *
 * Otherwise it will block for the set timeout for a desired state to be achieved.
 *
 * The state changes are notified via the {@link #process} method.
 *
 * Any notified error is handled by the BlockingWaiter and thrown from the {@link #block} method.
 *
 */
public class StateWaiter extends BlockingWaiter<AMQState>
{
    private static final Logger _logger = LoggerFactory.getLogger(StateWaiter.class);

    private final Set<AMQState> _awaitStates;
    private final AMQState _startState;
    private final AMQStateManager _stateManager;

    /**
     *
     * @param stateManager The StateManager
     * @param currentState
     * @param awaitStates
     */
    public StateWaiter(AMQStateManager stateManager, AMQState currentState, Set<AMQState> awaitStates)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("New StateWaiter :" + currentState + ":" + awaitStates);
        }
        _stateManager = stateManager;
        _awaitStates = awaitStates;
        _startState = currentState;
    }

    /**
     * When the state is changed this StateWaiter is notified to process the change.
     *
     * @param state The new state that has been achieved.
     * @return
     */
    public boolean process(AMQState state)
    {
        return _awaitStates.contains(state);
    }

    @Override
    public String getConnectionDetails()
    {
        return null;
    }

    /**
     * Await for the required State to be achieved within the default timeout.
     * @return The achieved state that was requested.
     * @throws QpidException The exception that prevented the required state from being achieved.
     */
    public AMQState await() throws QpidException
    {
        return await(_stateManager.getWaitTimeout());
    }

    /**
     * Await for the required State to be achieved.
     * <p>
     * It is the responsibility of this class to remove the waiter from the StateManager
     *
     * @param timeout The time in milliseconds to wait for any of the states to be achieved.
     * @return The achieved state that was requested.
     * @throws QpidException The exception that prevented the required state from being achieved.
     */
    public AMQState await(long timeout) throws QpidException
    {
        try
        {
            if (process(_startState))
            {
                return _startState;
            }

            try
            {
                return (AMQState) block(timeout);
            }
            catch (AMQTimeoutException e)
            {
                throw new AMQTimeoutException("Waiting for " + timeout + "ms to attain one of the states " + _awaitStates + "; current state is " + _stateManager.getCurrentState(), e);
            }
            catch (FailoverException e)
            {
                _logger.error("Failover occurred whilst waiting for states:" + _awaitStates);

                return null;
            }
        }
        finally
        {
            //Prevent any more errors being notified to this waiter.
            close();

            //Remove the waiter from the notification list in the state manager
            _stateManager.removeWaiter(this);
        }
    }
}
