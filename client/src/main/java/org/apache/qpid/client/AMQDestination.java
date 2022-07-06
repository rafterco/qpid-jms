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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URISyntaxException;
import java.util.Map;

import javax.jms.Destination;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.messaging.address.AddressHelper;
import org.apache.qpid.client.messaging.address.Link;
import org.apache.qpid.client.messaging.address.Node;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jndi.ObjectFactory;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;
import org.apache.qpid.url.URLHelper;


public abstract class AMQDestination implements Destination, Referenceable, Externalizable
{
    public static final String JNDI_ADDRESS_DESTINATION_ADDRESS = "address";

    private static final Logger _logger = LoggerFactory.getLogger(AMQDestination.class);
    private static final long serialVersionUID = -3152767926421156400L;

    static
    {
        ClientProperties.ensureIsLoaded();
    }

    private String _exchangeName;

    private String _exchangeClass;

    private boolean _exchangeAutoDelete;

    private boolean _exchangeDurable;

    private boolean _exchangeInternal;

    private boolean _isDurable;

    private boolean _isExclusive;

    private boolean _isAutoDelete;

    private boolean _browseOnly;

    private String _queueName;

    private String _routingKey;

    private String[] _bindingKeys;

    private String _url;
    private AMQShortString _urlAsShortString;

    private boolean _checkedForQueueBinding;

    private RejectBehaviour _rejectBehaviour;

    private Map<String,Object> _consumerArguments;

    public static final int QUEUE_TYPE = 1;
    public static final int TOPIC_TYPE = 2;
    public static final int UNKNOWN_TYPE = 3;
    private boolean _sendEncrypted;
    private String _encryptedRecipients;
    private long _deliveryDelay;
    private String _localAddress;

    protected void setExclusive(boolean exclusive)
    {
        _isExclusive = exclusive;
    }

    protected String getName()
    {
        return _name;
    }

    protected void setName(String name)
    {
        _name = name;
    }

    public boolean neverDeclare()
    {
        return false;
    }

    public boolean sendEncrypted()
    {
        return _sendEncrypted;
    }

    public String getEncryptedRecipients()
    {
        return _encryptedRecipients;
    }

    public long getDeliveryDelay()
    {
        return _deliveryDelay;
    }

    public String getLocalAddress()
    {
        return _localAddress;
    }

    // ----- Fields required to support new address syntax -------

    public enum DestSyntax {
      BURL,ADDR;

      public static DestSyntax getSyntaxType(String s)
      {
          if (("BURL").equals(s))
          {
              return BURL;
          }
          else if (("ADDR").equals(s))
          {
              return ADDR;
          }
          else
          {
              throw new IllegalArgumentException("Invalid Destination Syntax Type" +
                                                 " should be one of {BURL|ADDR}");
          }
      }

      public String asPrefix()
      {
          return name() + ":";
      }
    }

    public enum AddressOption {
      ALWAYS, NEVER, SENDER, RECEIVER;

      public static AddressOption getOption(String str)
      {
          if ("always".equals(str))
          {
              return ALWAYS;
          }
          else if ("never".equals(str))
          {
              return NEVER;
          }
          else if ("sender".equals(str))
          {
              return SENDER;
          }
          else if ("receiver".equals(str))
          {
              return RECEIVER;
          }
          else
          {
              throw new IllegalArgumentException(str + " is not an allowed value");
          }
      }
    }

    private final static DestSyntax defaultDestSyntax;

    private DestSyntax _destSyntax = DestSyntax.ADDR;

    private AddressHelper _addrHelper;
    private Address _address;
    private int _addressType = AMQDestination.UNKNOWN_TYPE;
    private String _name;
    private String _subject;
    private AddressOption _create = AddressOption.NEVER;
    private AddressOption _assert = AddressOption.NEVER;
    private AddressOption _delete = AddressOption.NEVER;

    private Node _node;
    private Link _link;


    // ----- / Fields required to support new address syntax -------

    static
    {
        defaultDestSyntax = DestSyntax.getSyntaxType(
                     System.getProperty(ClientProperties.DEST_SYNTAX,
                                        DestSyntax.ADDR.toString()));


    }

    public static DestSyntax getDefaultDestSyntax()
    {
        return defaultDestSyntax;
    }

    protected AMQDestination()
    {
    }

    protected AMQDestination(Address address)
    {
        this._address = address;
        getInfoFromAddress();
        _destSyntax = DestSyntax.ADDR;
        _logger.debug("Based on " + address + " the selected destination syntax is " + _destSyntax);
    }

    public  static DestSyntax getDestType(String str)
    {
        if (str.startsWith("BURL:") ||
                (!str.startsWith("ADDR:") && defaultDestSyntax == DestSyntax.BURL))
        {
            return DestSyntax.BURL;
        }
        else
        {
            return DestSyntax.ADDR;
        }
    }

    public static String stripSyntaxPrefix(String str)
    {
        if (str.startsWith("BURL:") || str.startsWith("ADDR:"))
        {
            return str.substring(5,str.length());
        }
        else
        {
            return str;
        }
    }

    protected AMQDestination(String str) throws URISyntaxException
    {
        parseDestinationString(str);
    }

    protected void parseDestinationString(String str) throws URISyntaxException
    {
        _destSyntax = getDestType(str);
        str = stripSyntaxPrefix(str);
        if (_destSyntax == DestSyntax.BURL)
        {
            getInfoFromBindingURL(new AMQBindingURL(str));
        }
        else
        {
            this._address = createAddressFromString(str);
            try
            {
                getInfoFromAddress();
            }
            catch(Exception e)
            {
                URISyntaxException ex = new URISyntaxException(str,"Error parsing address");
                ex.initCause(e);
                throw ex;
            }
        }
        _logger.debug("Based on " + str + " the selected destination syntax is " + _destSyntax);
    }

    //retained for legacy support
    protected AMQDestination(BindingURL binding)
    {
        getInfoFromBindingURL(binding);
        _destSyntax = DestSyntax.BURL;
        _logger.debug("Based on " + binding + " the selected destination syntax is " + _destSyntax);
    }

    protected void getInfoFromBindingURL(BindingURL binding)
    {
        _exchangeName = binding.getExchangeName();
        _exchangeClass = binding.getExchangeClass();
        _exchangeDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCHANGE_DURABLE));
        _exchangeAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCHANGE_AUTODELETE));
        _exchangeInternal = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCHANGE_INTERNAL));

        _isExclusive = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCLUSIVE));
        _isAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_AUTODELETE));
        _isDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_DURABLE));
        _browseOnly = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_BROWSE));
        _queueName = binding.getQueueName() == null ? null : binding.getQueueName();
        _routingKey = binding.getRoutingKey() == null ? null : binding.getRoutingKey();
        _bindingKeys = binding.getBindingKeys() == null || binding.getBindingKeys().length == 0 ? new String[0] : binding.getBindingKeys();
        final String rejectBehaviourValue = binding.getOption(BindingURL.OPTION_REJECT_BEHAVIOUR);
        _rejectBehaviour = rejectBehaviourValue == null ? null : RejectBehaviour.valueOf(rejectBehaviourValue.toUpperCase());
        _consumerArguments = binding.getConsumerOptions();
        _sendEncrypted = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_SEND_ENCRYPTED));
        _encryptedRecipients = binding.getOption(BindingURL.OPTION_ENCRYPTED_RECIPIENTS);
        String deliveryDelayVal = binding.getOption(BindingURL.OPTION_DELIVERY_DELAY);
        _deliveryDelay = deliveryDelayVal == null ? 0L : Long.parseLong(deliveryDelayVal);
        _localAddress = binding.getOption(BindingURL.OPTION_LOCAL_ADDRESS);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String routingKey, String queueName)
    {
        this(exchangeName, exchangeClass, routingKey, false, false, queueName, null);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String routingKey, String queueName, String[] bindingKeys)
    {
        this(exchangeName, exchangeClass, routingKey, false, false, queueName,bindingKeys);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName)
    {
        this(exchangeName, exchangeClass, destinationName, false, false, null,null);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String routingKey, boolean isExclusive,
            boolean isAutoDelete, String queueName)
    {
        this(exchangeName, exchangeClass, routingKey, isExclusive, isAutoDelete, queueName, false,null);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String routingKey, boolean isExclusive,
                             boolean isAutoDelete, String queueName, String[] bindingKeys)
    {
        this(exchangeName, exchangeClass, routingKey, isExclusive, isAutoDelete, queueName, false,bindingKeys);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String routingKey, boolean isExclusive,
            boolean isAutoDelete, String queueName, boolean isDurable){
        this (exchangeName, exchangeClass, routingKey, isExclusive,isAutoDelete,queueName,isDurable,null);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String routingKey, boolean isExclusive,
                             boolean isAutoDelete, String queueName, boolean isDurable, String[] bindingKeys)
    {
        this (exchangeName, exchangeClass, routingKey, isExclusive,isAutoDelete,queueName,isDurable, bindingKeys, false);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String routingKey, boolean isExclusive,
                             boolean isAutoDelete, String queueName, boolean isDurable, String[] bindingKeys, boolean browseOnly)
    {
        if (exchangeName == null)
        {
            throw new IllegalArgumentException("Exchange name must not be null");
        }
        if (exchangeClass == null)
        {
            throw new IllegalArgumentException("Exchange class must not be null");
        }
        _exchangeName = exchangeName;
        _exchangeClass = exchangeClass;
        _routingKey = routingKey;
        _isExclusive = isExclusive;
        _isAutoDelete = isAutoDelete;
        _queueName = queueName;
        _isDurable = isDurable;
        _bindingKeys = bindingKeys == null || bindingKeys.length == 0 ? new String[0] : bindingKeys;
        _destSyntax = DestSyntax.BURL;
        _browseOnly = browseOnly;
        _rejectBehaviour = null;
        _exchangeAutoDelete = false;
        _exchangeDurable = false;
        _exchangeInternal = false;

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Based on " + toString() + " the selected destination syntax is " + _destSyntax);
        }
    }

    public void setDestinationString(String str) throws Exception
    {
        parseDestinationString(str);
    }

    public String getDestinationString()
    {
        return toString();
    }

    public DestSyntax getDestSyntax()
    {
        return _destSyntax;
    }

    protected void setDestSyntax(DestSyntax syntax)
    {
        _destSyntax = syntax;
    }

    public AMQShortString getEncodedName()
    {
        if(getDestSyntax() == DestSyntax.BURL)
        {
            if (_urlAsShortString == null)
            {
                if (_url == null)
                {
                    toURL();
                }
                _urlAsShortString = new AMQShortString(_url);
            }
            return _urlAsShortString;
        }
        else
        {
            return AMQShortString.valueOf(getName());
        }
    }

    public boolean isDurable()
    {
        return _isDurable;
    }

    public String getExchangeName()
    {
        return _exchangeName;
    }

    public boolean isDefaultExchange()
    {
        return _exchangeName == null || "".equals(_exchangeName);
    }

    public String getExchangeClass()
    {
        return _exchangeClass;
    }

    public boolean isExchangeDurable()
    {
        return _exchangeDurable;
    }

    public boolean isExchangeAutoDelete()
    {
        return _exchangeAutoDelete;
    }

    public boolean isExchangeInternal()
    {
        return _exchangeInternal;
    }

    public boolean isTopic()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public boolean isQueue()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public String getQueueName()
    {
        return _queueName;
    }

    public String getAMQQueueName()
    {
        return _queueName;
    }

    public void setQueueName(String queueName)
    {

        _queueName = queueName;
        // calculated URL now out of date
        _url = null;
        _urlAsShortString = null;
    }

    public String getRoutingKey()
    {
        return _routingKey;
    }

    public String[] getBindingKeys()
    {
        if (_bindingKeys != null && _bindingKeys.length > 0)
        {
            return _bindingKeys;
        }
        else if (_routingKey != null)
        {
            // catering to the common use case where the
            //routingKey is the same as the bindingKey.
            return new String[]{_routingKey};
        }
        else
        {
            return new String[0];
        }
    }

    public boolean isExclusive()
    {
        return _isExclusive;
    }

    public boolean isAutoDelete()
    {
        return _isAutoDelete;
    }

    public abstract boolean isNameRequired();

    public String toString()
    {
        if (_destSyntax == DestSyntax.BURL)
        {
            return toURL();
        }
        else
        {
            return _address.toString();
        }

    }

    public boolean isCheckedForQueueBinding()
    {
        return _checkedForQueueBinding;
    }

    public void setCheckedForQueueBinding(boolean checkedForQueueBinding)
    {
        _checkedForQueueBinding = checkedForQueueBinding;
    }

    public String toURL()
    {
        String url = _url;
        if(url == null)
        {


            StringBuffer sb = new StringBuffer();

            sb.append(_exchangeClass);
            sb.append("://");
            sb.append(_exchangeName);

            sb.append("/");
            if (_routingKey != null)
            {
                sb.append(_routingKey);
            }
            sb.append("/");

            if (_queueName != null)
            {
                sb.append(_queueName);
            }

            sb.append('?');

            if (_routingKey != null)
            {
                sb.append(BindingURL.OPTION_ROUTING_KEY);
                sb.append("='");
                sb.append(_routingKey).append("'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            // We can't allow both routingKey and bindingKey
            if (_routingKey == null && _bindingKeys != null && _bindingKeys.length>0)
            {

                for (String bindingKey:_bindingKeys)
                {
                    sb.append(BindingURL.OPTION_BINDING_KEY);
                    sb.append("='");
                    sb.append(bindingKey);
                    sb.append("'");
                    sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);

                }
            }

            if (_isDurable)
            {
                sb.append(BindingURL.OPTION_DURABLE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_isExclusive)
            {
                sb.append(BindingURL.OPTION_EXCLUSIVE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_isAutoDelete)
            {
                sb.append(BindingURL.OPTION_AUTODELETE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_rejectBehaviour != null)
            {
                sb.append(BindingURL.OPTION_REJECT_BEHAVIOUR);
                sb.append("='" + _rejectBehaviour + "'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_exchangeDurable)
            {
                sb.append(BindingURL.OPTION_EXCHANGE_DURABLE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_exchangeAutoDelete)
            {
                sb.append(BindingURL.OPTION_EXCHANGE_AUTODELETE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_exchangeInternal)
            {
                sb.append(BindingURL.OPTION_EXCHANGE_INTERNAL);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            //removeKey the last char '?' if there is no options , ',' if there are.
            sb.deleteCharAt(sb.length() - 1);
            url = sb.toString();
            _url = url;
        }
        return url;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof AMQDestination))
        {
            return false;
        }

        final AMQDestination that = (AMQDestination) o;

        if (_destSyntax != that.getDestSyntax())
        {
            return false;
        }

        if (_destSyntax == DestSyntax.ADDR)
        {
            if (_addressType != that.getAddressType())
            {
                return false;
            }
            if (!_name.equals(that.getAddressName()))
            {
                return false;
            }
            if (_subject == null)
            {
                if (that.getSubject() != null)
                {
                    return false;
                }
            }
            else if (!_subject.equals(that.getSubject()))
            {
                return false;
            }
        }
        else
        {
            if (!_exchangeClass.equals(that._exchangeClass))
            {
                return false;
            }
            if (!_exchangeName.equals(that._exchangeName))
            {
                return false;
            }
            if ((_queueName == null && that._queueName != null) ||
                (_queueName != null && !_queueName.equals(that._queueName)))
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        int result;
        if (_destSyntax == DestSyntax.ADDR)
        {
            result = 29 * _addressType + _name.hashCode();
            if (_subject != null)
            {
                result = 29 * result + _subject.hashCode();
            }
        }
        else
        {
            result = _exchangeName == null ? "".hashCode() : _exchangeName.hashCode();
            result = 29 * result + (_exchangeClass == null ? "".hashCode() :_exchangeClass.hashCode());
            if (_queueName != null)
            {
                result = 29 * result + _queueName.hashCode();
            }
        }
        return result;
    }

    public Map<String, Object> getConsumerArguments()
    {
        return _consumerArguments;
    }

    public Reference getReference() throws NamingException
    {
        return new Reference(
                this.getClass().getName(),
                new StringRefAddr(JNDI_ADDRESS_DESTINATION_ADDRESS,
                        _destSyntax == DestSyntax.ADDR ? getAddress().toString() : DestSyntax.BURL.asPrefix() + toURL()),
                ObjectFactory.class.getName(),
                null);          // factory location
    }

    public static Destination createDestination(BindingURL binding)
    {
        String type = binding.getExchangeClass();

        if (ExchangeDefaults.DIRECT_EXCHANGE_CLASS.equals(type))
        {
            return new AMQQueue(binding);
        }
        else if (ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(type))
        {
            return new AMQTopic(binding);
        }
        else if (ExchangeDefaults.HEADERS_EXCHANGE_CLASS.equals(type))
        {
            return new AMQHeadersExchange(binding);
        }
        else
        {
            return new AMQAnyDestination(binding);
        }
    }

    public static Destination createDestination(String str, final boolean useNodeTypeForDestinationType)
            throws URISyntaxException
    {
         DestSyntax syntax = getDestType(str);
         str = stripSyntaxPrefix(str);
         if (syntax == DestSyntax.BURL)
         {
             return createDestination(new AMQBindingURL(str));
         }
         else
         {
             Address address = createAddressFromString(str);
             if(useNodeTypeForDestinationType)
             {
                 AddressHelper helper = new AddressHelper(address);
                 switch(helper.getNodeType())
                 {
                     case AMQDestination.QUEUE_TYPE:
                         return new AMQQueue(address);
                     case AMQDestination.TOPIC_TYPE:
                         return new AMQTopic(address);
                     default:
                         return new AMQAnyDestination(address);
                 }

             }
             else
             {
                 return new AMQAnyDestination(address);
             }
         }
    }

    // ----- new address syntax -----------

    public static class Binding
    {
        private String exchange;
        private String bindingKey;
        private String queue;
        private Map<String,Object> args;

        public Binding(String exchange,
                       String queue,
                       String bindingKey,
                       Map<String,Object> args)
        {
            this.exchange = exchange;
            this.queue = queue;
            this.bindingKey = bindingKey;
            this.args = args;
        }

        public String getExchange()
        {
            return exchange;
        }

        public String getQueue()
        {
            return queue;
        }

        public String getBindingKey()
        {
            return bindingKey;
        }

        public Map<String, Object> getArgs()
        {
            return args;
        }
    }

    public Address getAddress() {
        return _address;
    }

    protected void setAddress(Address addr) {
        _address = addr;
    }

    public int getAddressType()
    {
        return _addressType;
    }

    public void setAddressType(int addressType){
        _addressType = addressType;
    }

    public String getAddressName() {
        return _name;
    }

    public void setAddressName(String name){
        _name = name;
    }

    public String getSubject()
    {
        return _subject;
    }

    public void setSubject(String subject) {
        _subject = subject;
    }

    public AddressOption getCreate() {
        return _create;
    }

    public void setCreate(AddressOption option) {
        _create = option;
    }

    public AddressOption getAssert() {
        return _assert;
    }

    public void setAssert(AddressOption option) {
        _assert = option;
    }

    public AddressOption getDelete() {
        return _delete;
    }

    public void setDelete(AddressOption option) {
        _delete = option;
    }

    public Node getNode()
    {
        return _node;
    }

    public void setNode(Node node)
    {
        _node = node;
    }

    public Link getLink()
    {
        return _link;
    }

    public void setLink(Link link)
    {
        _link = link;
    }

    public void setExchangeName(String name)
    {
        this._exchangeName = name;
    }

    public void setExchangeClass(String type)
    {
        this._exchangeClass = type;
    }

    public void setRoutingKey(String rk)
    {
        this._routingKey = rk;
    }

    private static Address createAddressFromString(String str)
    {
        return Address.parse(str);
    }

    private void getInfoFromAddress()
    {
        _name = _address.getName();
        _subject = _address.getSubject();

        _addrHelper = new AddressHelper(_address);

        _create = _addrHelper.getCreate() != null ?
                 AddressOption.getOption(_addrHelper.getCreate()):AddressOption.NEVER;

        _assert = _addrHelper.getAssert() != null ?
                 AddressOption.getOption(_addrHelper.getAssert()):AddressOption.NEVER;

        _delete = _addrHelper.getDelete() != null ?
                 AddressOption.getOption(_addrHelper.getDelete()):AddressOption.NEVER;

        _browseOnly = _addrHelper.isBrowseOnly();

        _addressType = _addrHelper.getNodeType();
        _node =  _addrHelper.getNode();
        _link = _addrHelper.getLink();
        _deliveryDelay = _link.getDelay();
        _localAddress = _link.getLocalAddress();
        _sendEncrypted = _addrHelper.getSendEncrypted();
        _encryptedRecipients = _addrHelper.getEncryptedRecipients();
    }

    // ----- / new address syntax -----------

    public boolean isBrowseOnly()
    {
        return _browseOnly;
    }

    private void setBrowseOnly(boolean b)
    {
        _browseOnly = b;
    }

    public AMQDestination copyDestination()
    {
        AMQDestination dest =
            new AMQAnyDestination(_exchangeName,
                                  _exchangeClass,
                                  _routingKey,
                                  _isExclusive,
                                  _isAutoDelete,
                                  _queueName,
                                  _isDurable,
                                  _bindingKeys
                                  );

        dest.setDestSyntax(_destSyntax);
        dest.setAddress(_address);
        dest.setAddressName(_name);
        dest.setSubject(_subject);
        dest.setCreate(_create);
        dest.setAssert(_assert);
        dest.setDelete(_delete);
        dest.setBrowseOnly(_browseOnly);
        dest.setAddressType(_addressType);
        dest.setNode(_node);
        dest.setLink(_link);
        return dest;
    }

    protected void setAutoDelete(boolean b)
    {
        _isAutoDelete = b;
    }

    protected void setDurable(boolean b)
    {
        _isDurable = b;
    }

    /**
     * This option is only applicable for 0-8/0-9/0-9-1 protocols connection
     * <p>
     * It tells the client to delegate the requeue/DLQ decision to the
     * server .If this option is not specified, the messages won't be moved to
     * the DLQ (or dropped) when delivery count exceeds the maximum.
     *
     * @return destination reject behaviour
     */
    public RejectBehaviour getRejectBehaviour()
    {
        return _rejectBehaviour;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException
    {
        out.writeObject(_destSyntax);
        if (_destSyntax == DestSyntax.BURL)
        {
            out.writeObject(toURL());
        }
        else
        {
            out.writeObject(_address);
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException
    {
        _destSyntax = (DestSyntax) in.readObject();
        if (_destSyntax == DestSyntax.BURL)
        {
            String burl = (String) in.readObject();
            final AMQBindingURL binding;
            try
            {
                binding = new AMQBindingURL(burl);
            }
            catch (URISyntaxException e)
            {
                throw new IllegalStateException("Cannot convert url " + burl + " into a BindingURL", e);
            }
            getInfoFromBindingURL(binding);
        }
        else
        {
            _address = (Address) in.readObject();
            try
            {
                getInfoFromAddress();
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Cannot convert get info from  " + _address, e);
            }
        }
    }
}
