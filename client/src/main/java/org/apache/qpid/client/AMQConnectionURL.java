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
package org.apache.qpid.client;

import static org.apache.qpid.client.BrokerDetails.PASSWORD_YIELDING_OPTIONS;

import org.apache.qpid.client.url.URLParser;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.url.URLHelper;
import org.apache.qpid.url.URLSyntaxException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AMQConnectionURL implements ConnectionURL, Serializable
{
    private static final long serialVersionUID = -5102704772070465832L;

    private String _url;
    private String _failoverMethod;
    private Map<String, String> _failoverOptions;
    private Map<String, String> _options;
    private List<BrokerDetails> _brokers;
    private String _clientName;
    private String _username;
    private String _password;
    private String _virtualHost;
    private String _defaultQueueExchangeName;
    private String _defaultTopicExchangeName;
    private String _temporaryTopicExchangeName;
    private String _temporaryQueueExchangeName;

    public AMQConnectionURL(String fullURL) throws URLSyntaxException
    {
        if (fullURL == null)
        {
            throw new IllegalArgumentException("URL cannot be null");
        }
        _url = fullURL;
        _options = new HashMap<String, String>();
        _brokers = new LinkedList<BrokerDetails>();
        _failoverOptions = new HashMap<String, String>();
        new URLParser(this);
    }

    public String getURL()
    {
        return _url;
    }

    public Map<String,String> getOptions()
    {
        return _options;
    }

    public String getFailoverMethod()
    {
        return _failoverMethod;
    }

    public void setFailoverMethod(String failoverMethod)
    {
        _failoverMethod = failoverMethod;
    }

    public Map<String,String> getFailoverOptions()
    {
        return _failoverOptions;
    }

    public String getFailoverOption(String key)
    {
        return _failoverOptions.get(key);
    }

    public void setFailoverOption(String key, String value)
    {
        _failoverOptions.put(key, value);
    }

    public int getBrokerCount()
    {
        return _brokers.size();
    }

    public BrokerDetails getBrokerDetails(int index)
    {
        if (index < _brokers.size())
        {
            return _brokers.get(index);
        }
        else
        {
            return null;
        }
    }

    public void addBrokerDetails(BrokerDetails broker)
    {
        if (!(_brokers.contains(broker)))
        {
            _brokers.add(broker);
        }
    }

    public void setBrokerDetails(List<BrokerDetails> brokers)
    {
        _brokers = brokers;
    }

    public List<BrokerDetails> getAllBrokerDetails()
    {
        return _brokers;
    }

    public String getClientName()
    {
        return _clientName;
    }

    public void setClientName(String clientName)
    {
        _clientName = clientName;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }

    public String getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(String virtuaHost)
    {
        _virtualHost = virtuaHost;
    }

    public String getOption(String key)
    {
        return _options.get(key);
    }

    public void setOption(String key, String value)
    {
        _options.put(key, value);
    }

    public String getDefaultQueueExchangeName()
    {
        return _defaultQueueExchangeName;
    }

    public void setDefaultQueueExchangeName(String defaultQueueExchangeName)
    {
        _defaultQueueExchangeName = defaultQueueExchangeName;
    }

    public String getDefaultTopicExchangeName()
    {
        return _defaultTopicExchangeName;
    }

    public void setDefaultTopicExchangeName(String defaultTopicExchangeName)
    {
        _defaultTopicExchangeName = defaultTopicExchangeName;
    }

    public String getTemporaryQueueExchangeName()
    {
        return _temporaryQueueExchangeName;
    }

    public void setTemporaryQueueExchangeName(String temporaryQueueExchangeName)
    {
        _temporaryQueueExchangeName = temporaryQueueExchangeName;
    }

    public String getTemporaryTopicExchangeName()
    {
        return _temporaryTopicExchangeName;
    }

    public void setTemporaryTopicExchangeName(String temporaryTopicExchangeName)
    {
        _temporaryTopicExchangeName = temporaryTopicExchangeName;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AMQConnectionURL that = (AMQConnectionURL) o;

        if (_brokers != null ? !_brokers.equals(that._brokers) : that._brokers != null)
        {
            return false;
        }
        if (_clientName != null ? !_clientName.equals(that._clientName) : that._clientName != null)
        {
            return false;
        }
        if (_defaultQueueExchangeName != null
                ? !_defaultQueueExchangeName.equals(that._defaultQueueExchangeName)
                : that._defaultQueueExchangeName != null)
        {
            return false;
        }
        if (_defaultTopicExchangeName != null
                ? !_defaultTopicExchangeName.equals(that._defaultTopicExchangeName)
                : that._defaultTopicExchangeName != null)
        {
            return false;
        }
        if (_failoverMethod != null ? !_failoverMethod.equals(that._failoverMethod) : that._failoverMethod != null)
        {
            return false;
        }
        if (_failoverOptions != null ? !_failoverOptions.equals(that._failoverOptions) : that._failoverOptions != null)
        {
            return false;
        }
        if (_options != null ? !_options.equals(that._options) : that._options != null)
        {
            return false;
        }
        if (_password != null ? !_password.equals(that._password) : that._password != null)
        {
            return false;
        }
        if (_temporaryQueueExchangeName != null
                ? !_temporaryQueueExchangeName.equals(that._temporaryQueueExchangeName)
                : that._temporaryQueueExchangeName != null)
        {
            return false;
        }
        if (_temporaryTopicExchangeName != null
                ? !_temporaryTopicExchangeName.equals(that._temporaryTopicExchangeName)
                : that._temporaryTopicExchangeName != null)
        {
            return false;
        }
        if (_url != null ? !_url.equals(that._url) : that._url != null)
        {
            return false;
        }
        if (_username != null ? !_username.equals(that._username) : that._username != null)
        {
            return false;
        }
        if (_virtualHost != null ? !_virtualHost.equals(that._virtualHost) : that._virtualHost != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _url != null ? _url.hashCode() : 0;
        result = 31 * result + (_failoverMethod != null ? _failoverMethod.hashCode() : 0);
        result = 31 * result + (_failoverOptions != null ? _failoverOptions.hashCode() : 0);
        result = 31 * result + (_options != null ? _options.hashCode() : 0);
        result = 31 * result + (_brokers != null ? _brokers.hashCode() : 0);
        result = 31 * result + (_clientName != null ? _clientName.hashCode() : 0);
        result = 31 * result + (_username != null ? _username.hashCode() : 0);
        result = 31 * result + (_password != null ? _password.hashCode() : 0);
        result = 31 * result + (_virtualHost != null ? _virtualHost.hashCode() : 0);
        result = 31 * result + (_defaultQueueExchangeName != null ? _defaultQueueExchangeName.hashCode() : 0);
        result = 31 * result + (_defaultTopicExchangeName != null ? _defaultTopicExchangeName.hashCode() : 0);
        result = 31 * result + (_temporaryTopicExchangeName != null ? _temporaryTopicExchangeName.hashCode() : 0);
        result = 31 * result + (_temporaryQueueExchangeName != null ? _temporaryQueueExchangeName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append(AMQ_PROTOCOL);
        sb.append("://");

        if (_username != null)
        {
            sb.append(_username);

            if (_password != null)
            {
                sb.append(':');
                sb.append("********");
            }

            sb.append('@');
        }

        sb.append(_clientName);

        sb.append(_virtualHost);

        sb.append(optionsToString());

        return sb.toString();
    }

    private String optionsToString()
    {
        StringBuilder sb = new StringBuilder();
        
        if (!_options.isEmpty())
        {
            sb.append(URLHelper.printOptions(_options, PASSWORD_YIELDING_OPTIONS));
            sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
        }
        else
        {
            sb.append("?");
        }

        sb.append(OPTIONS_BROKERLIST).append("='");
        for (BrokerDetails service : _brokers)
        {
            sb.append(service.toString());
            sb.append(URLHelper.BROKER_SEPARATOR);
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append("'");

        if (_failoverMethod != null)
        {
            sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            sb.append(OPTIONS_FAILOVER);
            sb.append("='");
            sb.append(_failoverMethod);
            sb.append(URLHelper.printOptions(_failoverOptions));
            sb.append("'");
        }

        return sb.toString();
    }
}
