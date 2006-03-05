/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
  * by the @authors tag. See the copyright.txt in the distribution for a
  * full listing of individual contributors.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
  */
package org.jboss.messaging.core.plugin;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.messaging.core.Message;
import org.jboss.messaging.core.MessageReference;
import org.jboss.messaging.core.plugin.contract.MessageStore;
import org.jboss.messaging.util.Util;
import org.jboss.system.ServiceMBeanSupport;

/**
 * A MessageStore implementation that stores messages in an in-memory cache.
 * 
 * It stores one instance of the actual message in memory and returns new WeakMessageReference
 * instances to those messages via one of the reference() methods. Calling one of the reference()
 * methods causes the reference count for the message to be increased.
 *   
 * TODO - do spillover onto disc at low memory by reusing jboss mq message cache.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class PagingMessageStore extends ServiceMBeanSupport implements MessageStore
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(PagingMessageStore.class);

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------
   
   private boolean trace = log.isTraceEnabled();

   private Serializable storeID;
   
   private boolean acceptReliableMessages;

   // <messageID - MessageHolder>
   private Map messages;

   // Constructors --------------------------------------------------

   /**
    * @param storeID - if more than one message store is to be used in a distributed messaging
    *        configuration, each store must have an unique store ID.
    */
   public PagingMessageStore(String storeID)
   {
      this(storeID, true);
   }

   /**
    * @param storeID - if more than one message store is to be used in a distributed messaging
    *        configuration, each store must have an unique store ID.
    */
   public PagingMessageStore(String storeID, boolean acceptReliableMessages)
   {
      this.storeID = storeID;
      
      this.acceptReliableMessages = acceptReliableMessages;
      
      messages = new HashMap();

      log.debug(this + " initialized");
   }

   // ServiceMBeanSupport overrides ---------------------------------

   protected void startService() throws Exception
   {
      log.debug(this + " started");
   }

   protected void stopService() throws Exception
   {
      log.debug(this + " stopped");
   }

   // MessageStore implementation ---------------------------

   public Object getInstance()
   {
      return this;
   }

   public Serializable getStoreID()
   {
      return storeID;
   }

   public boolean isRecoverable()
   {
      return false;
   }

   public boolean acceptReliableMessages()
   {
      return acceptReliableMessages;
   }

   public synchronized MessageReference reference(Message m)
   {
      if (m.isReliable() && !acceptReliableMessages)
      {
         throw new IllegalStateException(this + " does not accept reliable messages (" + m + ")");
      }
      
      if (trace) { log.trace(this + " referencing " + m); }
      
      MessageHolder holder = (MessageHolder)messages.get(m.getMessageID());
      
      if (holder == null)
      {      
         holder = addMessage(m);
      }

      MessageReference ref = new SimpleMessageReference(holder, this);

      return ref;
   }

   public MessageReference reference(String messageId)
   {
      MessageHolder holder = (MessageHolder)messages.get(messageId);
      
      if (holder == null)
      {
         return null;
      }
       
      MessageReference ref = new SimpleMessageReference(holder, this);
      
      return ref;      
   }
   
   public boolean containsMessage(String messageId)
   {
      return messages.get(messageId) != null;
   }
   
   public boolean forgetMessage(String messageID)
   {
      return messages.remove(messageID) != null;
   }
   
   public int size()
   {
      return messages.size();
   }
   
   public List messageIds()
   {
      List msgs = new ArrayList(messages.keySet());
      return msgs;
   }

   // Public --------------------------------------------------------
   
   public void setStoreID(String storeID)
   {
      this.storeID = storeID;
   }

   public String toString()
   {
      return "MemoryStore[" + Util.guidToString(storeID) + "]";
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------
   
   protected MessageHolder addMessage(Message m)
   {
      MessageHolder holder = new MessageHolder(m, this);
      
      messages.put(m.getMessageID(), holder);
      
      return holder;
   }
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------   
      
}
