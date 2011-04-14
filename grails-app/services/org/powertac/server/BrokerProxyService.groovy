/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.powertac.server

import grails.plugin.jms.Queue
import org.powertac.common.Broker
import com.thoughtworks.xstream.*
import javax.jms.JMSException
import org.powertac.common.*
import org.powertac.common.msg.*
import org.powertac.common.interfaces.BrokerProxy
import org.powertac.common.interfaces.BrokerMessageListener

/**
 * BrokerProxyService is responsible for handling in- and outgoing communication with brokers
 * @author David Dauer
 */
class BrokerProxyService 
    implements BrokerProxy,
               org.springframework.beans.factory.InitializingBean
{

  static transactional = true
  static expose = ['jms']

  def jmsService
  
  XStream xstream

  def tariffRegistrations = []
  def marketRegistrations = []
  Set tariffMessageTypes = 
    [TariffSpecification.class, Rate.class, HourlyCharge.class, TariffUpdate.class,
     TariffExpire.class, TariffRevoke.class, VariableRateUpdate.class] as Set
  
  void afterPropertiesSet ()
  {
    xstream = new XStream()
    xstream.processAnnotations(Competition.class)
    xstream.processAnnotations(SimStart.class)
    xstream.processAnnotations(CustomerInfo.class)
    xstream.processAnnotations(CashPosition.class)
    xstream.processAnnotations(Timeslot.class)
    xstream.processAnnotations(ClearedTrade.class)
    xstream.processAnnotations(MarketPosition.class)
    xstream.processAnnotations(MarketTransaction.class)
    xstream.processAnnotations(Shout.class)
    xstream.processAnnotations(TariffStatus.class)
    xstream.processAnnotations(TariffTransaction.class)
    xstream.processAnnotations(TariffSpecification.class)
    xstream.processAnnotations(Rate.class)
    xstream.processAnnotations(HourlyCharge.class)
    xstream.processAnnotations(TariffUpdate.class)
    xstream.processAnnotations(TariffExpire.class)
    xstream.processAnnotations(TariffRevoke.class)
    xstream.processAnnotations(VariableRateUpdate.class)
  }

/**
 * Send a message to a specific broker
 */
  void sendMessage(Broker broker, Object messageObject) {
    if (broker.local) {
      broker.receiveMessage(messageObject)
      return
    }
    def queueName = broker.toQueueName()
    String xml = xstream.toXML(messageObject)
    try {
      jmsService.send(queueName, xml)
    } catch (Exception e) {
      throw new JMSException("Failed to send message to queue '$queueName' ($xml)")
    }
  }

/**
 * Send a list of messages to a specific broker
 */
  void sendMessages(Broker broker, List<?> messageObjects) {
    sendMessage(broker, messageObjects)
  }

/**
 * Send a message to all brokers
 */
  void broadcastMessage(Object messageObject) {
    String xml = xstream.toXML(messageObject)
    broadcastMessage(xml)

    // Include local brokers
    def localBrokers = Broker.findAll { (it.local) }
    localBrokers*.receiveMessage(messageObject)
  }

  void broadcastMessage(String text) {
    def brokerQueueNames = Broker.findAll { !(it.local) }?.collect { it.toQueueName() }
    def queueName = brokerQueueNames.join(",")
    log.info("Broadcast queue name is ${queueName}")
    try {
      jmsService.send(queueName, text)
    } catch (Exception e) {
      throw new JMSException("Failed to send message to queue '$queueName' ($xml)")
    }
  }

/**
 * Sends a list of messages to all brokers
 */
  void broadcastMessages(List<?> messageObjects) {
    broadcastMessage(messageObjects)
  }

/**
 * Receives and routes all incoming messages
 */
  @Queue(name = "server.inputQueue")
  def receiveMessage(String xmlMessage) {
    def thing = xstream.fromXML(xmlMessage)
    log.debug "received ${xmlMessage}"
    if (tariffMessageTypes.contains(thing.class)) {
      tariffRegistrations.each { listener ->
        listener.receiveMessage(thing)
      }
    }
    else {
      marketRegistrations.each { listener ->
        listener.receiveMessage(thing)
      }
    }
  }

/**
 * Should be called if tariff-related incoming broker messages should be sent to listener
 */
  void registerBrokerTariffListener(BrokerMessageListener listener) {
    tariffRegistrations.add(listener)
  }

/**
 * Should be called if market-related incoming broker messages should be sent to listener
 */
  void registerBrokerMarketListener(BrokerMessageListener listener) {
    marketRegistrations.add(listener)
  }
}
