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

package org.apache.qpid.client.message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotWriteableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.QpidException;
import org.apache.qpid.AMQPInvalidClassException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQDestination.DestSyntax;
import org.apache.qpid.client.CustomJMSXProperty;
import org.apache.qpid.client.util.JMSExceptionHelper;
import org.apache.qpid.jms.Message;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.ExchangeQueryResult;
import org.apache.qpid.transport.Future;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;
import org.apache.qpid.transport.TransportException;

/**
 * This extends AbstractAMQMessageDelegate which contains common code between
 * both the 0_8 and 0_10 Message types.
 *
 */
public class AMQMessageDelegate_0_10 extends AbstractAMQMessageDelegate
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQMessageDelegate_0_10.class);

    private static final float DESTINATION_CACHE_LOAD_FACTOR = 0.75f;
    private static final int DESTINATION_CACHE_SIZE = 500;
    private static final int DESTINATION_CACHE_CAPACITY = (int) (DESTINATION_CACHE_SIZE / DESTINATION_CACHE_LOAD_FACTOR);

    private static final Map<ReplyTo, Destination> _destinationCache =
            Collections.synchronizedMap(new LinkedHashMap<ReplyTo,Destination>(DESTINATION_CACHE_CAPACITY,
                                                                              DESTINATION_CACHE_LOAD_FACTOR,
                                                                              true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ReplyTo, Destination> eldest)
        {
            return size() >= DESTINATION_CACHE_SIZE;
        }
    });

    public static final String JMS_TYPE = "x-jms-type";


    private boolean _readableProperties = false;

    private Destination _destination;

    private MessageProperties _messageProps;
    private DeliveryProperties _deliveryProps;
    private String _messageID;
    private boolean isStrictJMS = Boolean.getBoolean("strict-jms");

    protected AMQMessageDelegate_0_10()
    {
        this(new MessageProperties(), new DeliveryProperties(), -1);
        _readableProperties = false;
    }

    protected AMQMessageDelegate_0_10(MessageProperties messageProps, DeliveryProperties deliveryProps, long deliveryTag)
    {
        this(AMQDestination.getDefaultDestSyntax(),messageProps,deliveryProps,deliveryTag);
    }

    protected AMQMessageDelegate_0_10(DestSyntax destSyntax,MessageProperties messageProps, DeliveryProperties deliveryProps, long deliveryTag)
    {
        super(deliveryTag);
        _messageProps = messageProps;
        _deliveryProps = deliveryProps;
        _readableProperties = (_messageProps != null);

        AMQDestination dest;

        if (destSyntax == AMQDestination.DestSyntax.BURL)
        {
            dest = generateDestination(_deliveryProps.getExchange(),
                                       _deliveryProps.getRoutingKey());
        }
        else
        {
            String subject = null;
            if (isStrictJMS && messageProps != null && messageProps.getApplicationHeaders() != null)
            {
                subject = (String)messageProps.getApplicationHeaders().get(QpidMessageProperties.QPID_SUBJECT);
                if (subject != null)
                {
                    messageProps.getApplicationHeaders().remove(QpidMessageProperties.QPID_SUBJECT);
                    messageProps.getApplicationHeaders().put(QpidMessageProperties.QPID_SUBJECT_JMS_PROPERTY,subject);
                }
            }
            dest = (AMQDestination) convertToAddressBasedDestination(_deliveryProps.getExchange(),
                    _deliveryProps.getRoutingKey(), subject, false, AMQDestination.UNKNOWN_TYPE);
        }

        if(messageProps != null && messageProps.getApplicationHeaders() != null)
        {
            messageProps.getApplicationHeaders().remove(QpidMessageProperties.QPID_NOT_VALID_BEFORE);
        }

        setJMSDestination(dest);        
    }

    /**
     * Use the 0-10 ExchangeQuery call to validate the exchange type.
     *
     * This is used primarily to provide the correct JMSDestination value.
     *
     * The query is performed synchronously iff the map exchange is not already
     * present in the exchange Map.
     *
     * @param header The message headers, from which the exchange name can be extracted
     * @param session The 0-10 session to use to call ExchangeQuery
     */
    public static void updateExchangeTypeMapping(Header header, org.apache.qpid.transport.Session session)
    {
        DeliveryProperties deliveryProps = header.getDeliveryProperties();
        if (deliveryProps != null)
        {
            String exchange = deliveryProps.getExchange();
            checkAndUpdateExchange(exchange,session);
                    
        }
        
        MessageProperties msgProps = header.getMessageProperties();
        if (msgProps != null && msgProps.getReplyTo() != null)
        {
            String exchange = msgProps.getReplyTo().getExchange();
            checkAndUpdateExchange(exchange,session);
                    
        }
    }
    
    private static void checkAndUpdateExchange(String exchange, org.apache.qpid.transport.Session session)
    {
        if (exchange != null && !exchangeMapContains(exchange))
        {
            Future<ExchangeQueryResult> future =
                    session.exchangeQuery(exchange);
            ExchangeQueryResult res = future.get();

            updateExchangeType(exchange, res.getType());
        }
    }


    public String getJMSMessageID() throws JMSException
    {
        if (_messageID == null && _messageProps.getMessageId() != null)
        {
            UUID id = _messageProps.getMessageId();
            _messageID = "ID:" + id;
        }
        return _messageID;
    }

    public void setJMSMessageID(String messageId) throws JMSException
    {
        if(messageId == null)
        {
            _messageProps.clearMessageId();
            _messageID = null;
        }
        else
        {
            if(messageId.startsWith("ID:"))
            {
                _messageID = messageId;
            }
            else
            {
                throw new JMSException("MessageId '"+messageId+"' is not of the correct format, it must be prefixed with 'ID:'");
            }
        }
    }

    /* Used by the internal implementation */
    public void setJMSMessageID(UUID messageId) throws JMSException
    {
        if(messageId == null)
        {
            _messageProps.clearMessageId();
        }
        else
        {
            _messageProps.setMessageId(messageId);
        }
    }

    public long getJMSTimestamp() throws JMSException
    {
        return _deliveryProps.getTimestamp();
    }

    public void setJMSTimestamp(long timestamp) throws JMSException
    {
        _deliveryProps.setTimestamp(timestamp);
    }

    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        return _messageProps.getCorrelationId();
    }

    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException
    {
        _messageProps.setCorrelationId(bytes);
    }

    public void setJMSCorrelationID(String correlationId) throws JMSException
    {
        setJMSCorrelationIDAsBytes(correlationId == null ? null : correlationId.getBytes(StandardCharsets.UTF_8));
    }

    public String getJMSCorrelationID() throws JMSException
    {
        byte[] correlationIDAsBytes = getJMSCorrelationIDAsBytes();
        return correlationIDAsBytes == null ? null : new String(correlationIDAsBytes, StandardCharsets.UTF_8);
    }

    public Destination getJMSReplyTo()
    {
        ReplyTo replyTo = _messageProps.getReplyTo();

        if ((replyTo == null) || ((replyTo.getExchange() == null) && (replyTo.getRoutingKey() == null)))
        {
            return null;
        }
        else
        {
            Destination dest = _destinationCache.get(replyTo);

            if (dest == null)
            {
                String exchange = replyTo.getExchange();
                String routingKey = replyTo.getRoutingKey();

                if (AMQDestination.getDefaultDestSyntax() == AMQDestination.DestSyntax.BURL)
                {
                    dest = generateDestination(exchange, routingKey);
                }
                else
                {
                    dest = convertToAddressBasedDestination(exchange,routingKey,null, false,
                                                            AMQDestination.UNKNOWN_TYPE);
                }
                _destinationCache.put(replyTo, dest);
            }

            return dest;
        }
    }

    public void setJMSReplyTo(Destination destination) throws JMSException
    {
        if (destination == null)
        {
            _messageProps.clearReplyTo();
            return;
        }

        if (!(destination instanceof AMQDestination))
        {
            throw new IllegalArgumentException(
                "ReplyTo destination may only be an AMQDestination - passed argument was type " + destination.getClass());
        }

        final AMQDestination amqd = (AMQDestination) destination;

        if (amqd.getDestSyntax() == AMQDestination.DestSyntax.ADDR)
        {
           try
           {
               int type = getAMQSession().resolveAddressType(amqd);
               if (type == AMQDestination.QUEUE_TYPE)
               {
                   getAMQSession().setLegacyFieldsForQueueType(amqd);
               }
               else
               {
                   getAMQSession().setLegacyFieldsForTopicType(amqd);
               }
           }
           catch(QpidException ex)
           {
               throw JMSExceptionHelper.chainJMSException(new JMSException(
                       "Error occured while figuring out the node type"), ex);
           }
           catch (TransportException e)
           {
               throw JMSExceptionHelper.chainJMSException(new JMSException(
                       "Exception occured while figuring out the node type:" + e.getMessage()), e);
           }
        }

        final ReplyTo replyTo = new ReplyTo(amqd.getExchangeName(), amqd.getRoutingKey());
        _destinationCache.put(replyTo, destination);
        _messageProps.setReplyTo(replyTo);
    }

    public Destination getJMSDestination()
    {
        return _destination;
    }

    public void setJMSDestination(Destination destination)
    {
        _destination = destination;
    }

    public void setContentType(String contentType)
    {
        _messageProps.setContentType(contentType);
    }

    public String getContentType()
    {
        return _messageProps.getContentType();
    }

    public void setEncoding(String encoding)
    {
        if(encoding == null || encoding.length() == 0)
        {
            _messageProps.clearContentEncoding();
        }
        else
        {
            _messageProps.setContentEncoding(encoding);
        }
    }

    public String getEncoding()
    {
        return _messageProps.getContentEncoding();
    }

    public String getReplyToString()
    {
        Destination replyTo = getJMSReplyTo();
        if(replyTo != null)
        {
            return ((AMQDestination)replyTo).toString();
        }
        else
        {
            return null;
        }

    }

    public int getJMSDeliveryMode() throws JMSException
    {

        MessageDeliveryMode deliveryMode = _deliveryProps.getDeliveryMode();
        if(deliveryMode != null)
        {
            switch(deliveryMode)
            {
                case PERSISTENT :
                    return DeliveryMode.PERSISTENT;
                case NON_PERSISTENT:
                    return DeliveryMode.NON_PERSISTENT;
                default:
                    throw new JMSException("Unknown Message Delivery Mode: " + _deliveryProps.getDeliveryMode());
            }
        }
        else
        {
            return Message.DEFAULT_DELIVERY_MODE;
        }

    }

    public void setJMSDeliveryMode(int deliveryMode) throws JMSException
    {
        switch(deliveryMode)
        {
            case DeliveryMode.PERSISTENT:
                _deliveryProps.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                break;
            case DeliveryMode.NON_PERSISTENT:
                _deliveryProps.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
                break;
            default:
                throw new JMSException("Unknown JMS Delivery Mode: " + deliveryMode);
        }

    }


    public String getJMSType() throws JMSException
    {
        if(getApplicationHeaders().containsKey(JMS_TYPE))
        {
            return getStringProperty(JMS_TYPE);
        }
        else
        {
            return null;
        }
    }

    private Map<String, Object> getApplicationHeaders()
    {
        Map<String, Object> map = _messageProps.getApplicationHeaders();
        return map == null ? Collections.EMPTY_MAP : map;
    }

    public void setJMSType(String type) throws JMSException
    {
        Map<String, Object> headers = _messageProps.getApplicationHeaders();
        if(type == null)
        {
            if(headers != null)
            {
                headers.remove(JMS_TYPE);
            }
        }
        else
        {
            if(headers == null)
            {
                headers = new HashMap<String,Object>();
                _messageProps.setApplicationHeaders(headers);

            }
            headers.put(JMS_TYPE, type);
        }
    }

    public long getJMSExpiration() throws JMSException
    {
        return _deliveryProps.getExpiration();
    }

    public void setJMSExpiration(long l) throws JMSException
    {
        _deliveryProps.setExpiration(l);
    }



    public boolean propertyExists(String propertyName) throws JMSException
    {
        return getApplicationHeaders().containsKey(propertyName);
    }

    public boolean getBooleanProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);

        Object o = getApplicationHeaders().get(propertyName);

        if(o instanceof Boolean)
        {
            return ((Boolean)o).booleanValue();
        }
        else if(o instanceof String)
        {
            return Boolean.valueOf((String) o).booleanValue();
        }
        else if(getApplicationHeaders().containsKey(propertyName))
        {
            throw new MessageFormatException("getBooleanProperty(\""+propertyName+"\") failed as value is not boolean: " + o);
        }
        else
        {
            return Boolean.valueOf(null);
        }
    }

    public byte getByteProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);

        Map<String, Object> propertyMap = getApplicationHeaders();

        Object o = propertyMap.get(propertyName);

        if(o instanceof Byte)
        {
            return ((Byte)o).byteValue();
        }
        else if(o instanceof String)
        {
            return Byte.valueOf((String) o).byteValue();
        }
        else if(getApplicationHeaders().containsKey(propertyName))
        {
            throw new MessageFormatException("getByteProperty(\""+propertyName+"\") failed as value is not a byte: " + o);
        }
        else
        {
            return Byte.valueOf(null);
        }
    }

    public short getShortProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);

        Map<String, Object> propertyMap = getApplicationHeaders();

        Object o = propertyMap.get(propertyName);

        if(o instanceof Short)
        {
            return ((Short)o).shortValue();
        }
        else if(o instanceof String)
        {
            return Short.valueOf((String) o);
        }
        else
        {
            try
            {
                return Short.valueOf(getByteProperty(propertyName));
            }
            catch(MessageFormatException e)
            {
                throw new MessageFormatException("getShortProperty(\""+propertyName+"\") failed as value is not a short: " + o);
            }
        }


    }

    public int getIntProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);

        Map<String, Object> propertyMap = getApplicationHeaders();

        Object o = propertyMap.get(propertyName);

        if(o instanceof Integer)
        {
            return ((Integer)o).intValue();
        }
        else if(o instanceof String)
        {
            return Integer.valueOf((String) o);
        }
        else
        {
            try
            {
                return Integer.valueOf(getShortProperty(propertyName));
            }
            catch(MessageFormatException e)
            {
                throw new MessageFormatException("getIntProperty(\""+propertyName+"\") failed as value is not an int: " + o);
            }

        }
    }

    public long getLongProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);

        Map<String, Object> propertyMap = getApplicationHeaders();

        Object o = propertyMap.get(propertyName);

        if(o instanceof Long)
        {
            return ((Long)o).longValue();
        }
        else if(o instanceof String)
        {
            return Long.valueOf((String) o);
        }
        else
        {
            try
            {
                return Long.valueOf(getIntProperty(propertyName));
            }
            catch(MessageFormatException e)
            {
                throw new MessageFormatException("getLongProperty(\""+propertyName+"\") failed as value is not a long: " + o);
            }

        }
    }

    public float getFloatProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Map<String, Object> propertyMap = getApplicationHeaders();

        Object o = propertyMap.get(propertyName);

        if(o instanceof Float)
        {
            return ((Float)o).floatValue();
        }
        else if(o instanceof String)
        {
            return Float.valueOf((String) o).floatValue();
        }
        else if(getApplicationHeaders().containsKey(propertyName))
        {
            throw new MessageFormatException("getFloatProperty(\""+propertyName+"\") failed as value is not a float: " + o);
        }
        else
        {
            throw new NullPointerException("No such property: " + propertyName);
        }

    }

    public double getDoubleProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);

        Map<String, Object> propertyMap = getApplicationHeaders();

        Object o = propertyMap.get(propertyName);

        if(o instanceof Double)
        {
            return ((Double)o).doubleValue();
        }
        else if (o instanceof String)
        {
            return Double.valueOf((String)o);
        }
        else
        {
            try
            {
                return Double.valueOf(getFloatProperty(propertyName));
            }
            catch(MessageFormatException e)
            {
                throw new MessageFormatException("getDoubleProperty(\""+propertyName+"\") failed as value is not a double: " + o);
            }

        }
    }

    public String getStringProperty(String propertyName) throws JMSException
    {
        if (propertyName.equals(CustomJMSXProperty.JMSXUserID.toString()))
        {
            final byte[] userIdBytes = _messageProps.getUserId();
            return userIdBytes == null ? null : new String(userIdBytes);
        }
        else if (QpidMessageProperties.AMQP_0_10_APP_ID.equals(propertyName) &&
                _messageProps.getAppId() != null)
        {
            return new String(_messageProps.getAppId());
        }
        else if (QpidMessageProperties.AMQP_0_10_ROUTING_KEY.equals(propertyName) &&
                _deliveryProps.getRoutingKey() != null)
        {
            return _deliveryProps.getRoutingKey();
        }
        else if (isStrictJMS && QpidMessageProperties.QPID_SUBJECT.equals(propertyName))
        {
            return (String)getApplicationHeaders().get(QpidMessageProperties.QPID_SUBJECT_JMS_PROPERTY);
        }
        else
        {
            checkPropertyName(propertyName);
            Map<String, Object> propertyMap = getApplicationHeaders();

            Object o = propertyMap.get(propertyName);

            if(o instanceof String)
            {
                return (String) o;
            }
            else if(o == null)
            {
                return null;
            }
            else if(o.getClass().isArray())
            {
                throw new MessageFormatException("getString(\""+propertyName+"\") failed as value of type " + o.getClass()+ " is an array.");
            }
            else
            {
                return String.valueOf(o);
            }

        }
    }

    public Object getObjectProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Map<String, Object> propertyMap = getApplicationHeaders();

        return propertyMap.get(propertyName);

    }

    public Enumeration getPropertyNames() throws JMSException
    {
        List<String> props = new ArrayList<String>();
        Map<String, Object> propertyMap = getApplicationHeaders();
        for (String prop: getApplicationHeaders().keySet())
        {
            Object value = propertyMap.get(prop);
            if (value instanceof Boolean || value instanceof Number 
                || value instanceof String)
            {
                props.add(prop);
            }
        }
        
        return java.util.Collections.enumeration(props);        
    }

    public void setBooleanProperty(String propertyName, boolean b) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        setApplicationHeader(propertyName, b);
    }

    public void setByteProperty(String propertyName, byte b) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        setApplicationHeader(propertyName, b);
    }

    public void setShortProperty(String propertyName, short i) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        setApplicationHeader(propertyName, i);
    }

    public void setIntProperty(String propertyName, int i) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        setApplicationHeader(propertyName, i);
    }

    public void setLongProperty(String propertyName, long l) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        setApplicationHeader(propertyName, l);
    }

    public void setFloatProperty(String propertyName, float f) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        setApplicationHeader(propertyName, f);
    }

    public void setDoubleProperty(String propertyName, double v) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        setApplicationHeader(propertyName, v);
    }

    public void setStringProperty(String propertyName, String value) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        if (QpidMessageProperties.AMQP_0_10_APP_ID.equals(propertyName))
        {
            _messageProps.setAppId(value.getBytes());
        }
        else
        {
            setApplicationHeader(propertyName, value);
        }
    }

    private static final Set<Class> ALLOWED = new HashSet();
    static
    {
        ALLOWED.add(Boolean.class);
        ALLOWED.add(Byte.class);
        ALLOWED.add(Short.class);
        ALLOWED.add(Integer.class);
        ALLOWED.add(Long.class);
        ALLOWED.add(Float.class);
        ALLOWED.add(Double.class);
        ALLOWED.add(Character.class);
        ALLOWED.add(String.class);
        ALLOWED.add(byte[].class);
    }
    
    public void setObjectProperty(String propertyName, Object object) throws JMSException
    {
        checkPropertyName(propertyName);
        checkWritableProperties();
        if (object == null)
        {
            throw new MessageFormatException(AMQPInvalidClassException.INVALID_OBJECT_MSG + "null");
        }
        else if (!ALLOWED.contains(object.getClass()))
        {
            throw new MessageFormatException(AMQPInvalidClassException.INVALID_OBJECT_MSG + object.getClass());
        }
        setApplicationHeader(propertyName, object);
    }

    private void setApplicationHeader(String propertyName, Object object)
    {
        Map<String, Object> headers = _messageProps.getApplicationHeaders();
        if(headers == null)
        {
            headers = new HashMap<String,Object>();
            _messageProps.setApplicationHeaders(headers);
        }
        headers.put(propertyName, object);
    }

    public void removeProperty(String propertyName) throws JMSException
    {
        Map<String, Object> headers = _messageProps.getApplicationHeaders();
        if(headers != null)
        {
            headers.remove(propertyName);
        }
    }



    protected void checkWritableProperties() throws MessageNotWriteableException
    {
        if (_readableProperties)
        {
            throw new MessageNotWriteableException("You need to call clearProperties() to make the message writable");
        }
    }


    public int getJMSPriority() throws JMSException
    {
        MessageDeliveryPriority messageDeliveryPriority = _deliveryProps.getPriority();
        return messageDeliveryPriority == null ? Message.DEFAULT_PRIORITY : messageDeliveryPriority.getValue();
    }

    public void setJMSPriority(int i) throws JMSException
    {
        _deliveryProps.setPriority(MessageDeliveryPriority.get((short)i));
    }

    public void clearProperties() throws JMSException
    {
        if(!getApplicationHeaders().isEmpty())
        {
            getApplicationHeaders().clear();
        }

        _readableProperties = false;
    }

    protected void checkPropertyName(CharSequence propertyName)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if (propertyName.length() == 0)
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        checkIdentiferFormat(propertyName);
    }

    protected void checkIdentiferFormat(CharSequence propertyName)
    {
//        JMS requirements 3.5.1 Property Names
//        Identifiers:
//        - An identifier is an unlimited-length character sequence that must begin
//          with a Java identifier start character; all following characters must be Java
//          identifier part characters. An identifier start character is any character for
//          which the method Character.isJavaIdentifierStart returns true. This includes
//          '_' and '$'. An identifier part character is any character for which the
//          method Character.isJavaIdentifierPart returns true.
//        - Identifiers cannot be the names NULL, TRUE, or FALSE.
//          Identifiers cannot be NOT, AND, OR, BETWEEN, LIKE, IN, IS, or
//          ESCAPE.
//          Identifiers are either header field references or property references. The
//          type of a property value in a message selector corresponds to the type
//          used to set the property. If a property that does not exist in a message is
//          referenced, its value is NULL. The semantics of evaluating NULL values
//          in a selector are described in Section 3.8.1.2, Null Values.
//          The conversions that apply to the get methods for properties do not
//          apply when a property is used in a message selector expression. For
//          example, suppose you set a property as a string value, as in the
//          following:
//              myMessage.setStringProperty("NumberOfOrders", "2")
//          The following expression in a message selector would evaluate to false,
//          because a string cannot be used in an arithmetic expression:
//          "NumberOfOrders > 1"
//          Identifiers are case sensitive.
//          Message header field references are restricted to JMSDeliveryMode,
//          JMSPriority, JMSMessageID, JMSTimestamp, JMSCorrelationID, and
//          JMSType. JMSMessageID, JMSCorrelationID, and JMSType values may be
//          null and if so are treated as a NULL value.

        if (isStrictJMS)
        {
            // JMS start character
            if (!(Character.isJavaIdentifierStart(propertyName.charAt(0))))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' does not start with a valid JMS identifier start character");
            }

            // JMS part character
            int length = propertyName.length();
            for (int c = 1; c < length; c++)
            {
                if (!(Character.isJavaIdentifierPart(propertyName.charAt(c))))
                {
                    throw new IllegalArgumentException("Identifier '" + propertyName + "' contains an invalid JMS identifier character");
                }
            }

            // JMS invalid names
            if ((propertyName.equals("NULL")
                 || propertyName.equals("TRUE")
                 || propertyName.equals("FALSE")
                 || propertyName.equals("NOT")
                 || propertyName.equals("AND")
                 || propertyName.equals("OR")
                 || propertyName.equals("BETWEEN")
                 || propertyName.equals("LIKE")
                 || propertyName.equals("IN")
                 || propertyName.equals("IS")
                 || propertyName.equals("ESCAPE")))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' is not allowed in JMS");
            }
        }

    }


    public MessageProperties getMessageProperties()
    {
        return _messageProps;
    }


    public DeliveryProperties getDeliveryProperties()
    {
        return _deliveryProps;
    }

    @Override
    Object getProperty(final String name)
    {
        return _messageProps.getApplicationHeaders().get(name);
    }

    @Override
    boolean hasProperty(final String name)
    {
        return _messageProps.getApplicationHeaders().containsKey(name);
    }
}
