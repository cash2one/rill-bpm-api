/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.ws.tx.at.tube;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.transaction.InvalidTransactionException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.Xid;

import com.sun.istack.logging.Logger;
import com.sun.xml.ws.api.message.Header;
import com.sun.xml.ws.api.message.Headers;
import com.sun.xml.ws.tx.at.WSATConstants;
import com.sun.xml.ws.tx.at.WSATHelper;
import com.sun.xml.ws.tx.at.common.TransactionImportManager;
import com.sun.xml.ws.tx.at.common.TransactionManagerImpl;
import com.sun.xml.ws.tx.at.internal.WSATGatewayRM;
import com.sun.xml.ws.tx.at.internal.XidImpl;
import com.sun.xml.ws.tx.at.localization.LocalizationMessages;
import com.sun.xml.ws.tx.at.runtime.TransactionIdHelper;
import com.sun.xml.ws.tx.coord.common.WSATCoordinationContextBuilder;
import com.sun.xml.ws.tx.coord.common.WSCBuilderFactory;
import com.sun.xml.ws.tx.coord.common.types.CoordinationContextIF;


public class WSATClientHelper implements WSATClient {
	// Modified by MENGRAN at 2011-12-22 for volatile -> AtomicInteger and change to static variables
    private static AtomicInteger counter = new AtomicInteger(0);
    
    private static final Logger LOGGER = Logger.getLogger(WSATClientHelper.class);

    /**
     * For outbound case, if transaction exists, suspend and store it and attach CoordinationContext to SOAP Header
     * For return of outbound case, if suspend transaction exists, resume it
     *
     * @return a list of Header that should be added the request message
     */
    public List<Header> doHandleRequest(TransactionalAttribute transactionalAttribute, Map<String, Object> map) {
        try {
        	// Add by MENGRAN at 2011-11-09 for disable WSAT when transaction attribute is not enabled.
            if (TransactionManagerImpl.getInstance().getTransactionManager().getTransaction()==null || 
            		!transactionalAttribute.isEnabled()) 
                return new ArrayList();
        } catch (SystemException e) {
            e.printStackTrace();
            return new ArrayList();
        }
        List<Header> addedHeaders = processTransactionalRequest(transactionalAttribute, map);
        return addedHeaders;
    }

    public boolean doHandleResponse(Map<String, Object> map) {
       return resumeAndClearXidTxMap(map);
    }

    public void doHandleException(Map<String, Object> map) {
          LOGGER.info(LocalizationMessages.WSAT_4569_INBOUND_APPLICATION_MESSAGE());
       resumeAndClearXidTxMap(map);
    }

    private boolean resumeAndClearXidTxMap(Map<String, Object> map) {
        if (WSATHelper.isDebugEnabled()) 
            LOGGER.info(LocalizationMessages.WSAT_4569_INBOUND_APPLICATION_MESSAGE());
        Xid xid = getWSATXidFromMap(map);
        if (xid != null) {
            WSATHelper.getInstance().removeFromXidToTransactionMap(xid);
        }
        Transaction transaction = getWSATTransactionFromMap(map);
        return !(transaction != null && !resume(transaction));
    }

   private Transaction getWSATTransactionFromMap(Map map) {
      Transaction transaction = (Transaction)map.get(WSATConstants.WSAT_TRANSACTION);
      return transaction;
   }

   private Xid getWSATXidFromMap(Map map) {
      Xid xid = (Xid)map.get(WSATConstants.WSAT_TRANSACTION_XID);
      return xid;
   }

   /**
     * Resume the transaction previous suspended by this handler.
     *
     * @return boolean if resume was successful
     */
   private boolean resume(Transaction transaction) {
       if (WSATHelper.isDebugEnabled())
           LOGGER.info(LocalizationMessages.WSAT_4570_WILL_RESUME_IN_CLIENT_SIDE_HANDLER(transaction, Thread.currentThread()));
       try {
           TransactionManagerImpl.getInstance().getTransactionManager().resume(transaction);
           if (WSATHelper.isDebugEnabled())
               LOGGER.info(LocalizationMessages.WSAT_4571_RESUMED_IN_CLIENT_SIDE_HANDLER(transaction, Thread.currentThread()));
           return true;
       } catch (InvalidTransactionException e) {
           if (WSATHelper.isDebugEnabled())
               LOGGER.severe(LocalizationMessages.WSAT_4572_INVALID_TRANSACTION_EXCEPTION_IN_CLIENT_SIDE_HANDLER(
                       transaction, Thread.currentThread()), e);
           try {
               transaction.setRollbackOnly();
           } catch (IllegalStateException ex) {
               Logger.getLogger(WSATClientHelper.class).log(Level.SEVERE, null, ex);
               return false;
           } catch (SystemException ex) {
               Logger.getLogger(WSATClientHelper.class).log(Level.SEVERE, null, ex);
               return false;
           }
           return false;
       } catch (SystemException e) {
           if (WSATHelper.isDebugEnabled())
               LOGGER.severe(LocalizationMessages.WSAT_4573_SYSTEM_EXCEPTION_IN_CLIENT_SIDE_HANDLER(
                       transaction, Thread.currentThread()), e);
           try {
               transaction.setRollbackOnly();
               return false;
           } catch (IllegalStateException ex) {
               Logger.getLogger(WSATClientHelper.class).log(Level.SEVERE, null, ex);
               return false;
           } catch (SystemException ex) {
               Logger.getLogger(WSATClientHelper.class).log(Level.SEVERE, null, ex);
               return false;
           }

       }
   }
   
	static long ethernetAddress = 0L;
	static {
		try {
			Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces();
			while (en.hasMoreElements()) {
				NetworkInterface nint = en.nextElement();
				if (!nint.isLoopback()) {
					byte[] data = nint.getHardwareAddress();
					if (data != null && data.length == 6) {
						if (data.length != 6) {
				            throw new NumberFormatException("Ethernet address has to consist of 6 bytes");
				        }
				        long l = data[0] & 0xFF;
				        for (int i = 1; i < 6; ++i) {
				            l = (l << 8) | (data[i] & 0xFF); 
				        }
				        ethernetAddress = l;
						break;
					}
				}
			}
		} catch (java.net.SocketException e) {
			// fine, let's take is as signal of not having any interfaces
		}

		LOGGER.info("Retrieve ethernet address asLong: " + ethernetAddress);
	}
   
   private byte[] globalTransactionId() {
	   
	   // Add by MENGRAN for cluster-wide
	   byte[] originalBytes = new String(ethernetAddress + "-" + System.currentTimeMillis() + "-" + counter.incrementAndGet()).getBytes();
	   return originalBytes;
	   
   }

    /**
     * Process ClientRequestInfo received for this outgoing request.
     * Determine if this transaction interceptor is interested in the operation.
     * Get the current transaction.  One should exist as we checked for it's presence before calling this method
     * If no transaction exists do nothing.
     * If one does exist create and add the transaction CoordinationContext.
     *
     * @param transactionalAttribute
     * @return false if there are any issues with suspend or message header creation (namely SOAPException),
     *         true otherwise
     */
    private List<Header> processTransactionalRequest(
            TransactionalAttribute transactionalAttribute, Map<String, Object> map) {
        while (!WSATGatewayRM.isReadyForRuntime) {
            LOGGER.info("WS-AT recovery is enabled but WS-AT is not ready for runtime.  Processing WS-AT recovery log files...");
            WSATGatewayRM.getInstance().recover();
        }
        List<Header> headers = new ArrayList<Header>();
        String txId;
        byte[] activityId = WSATHelper.assignUUID().getBytes();
        LOGGER.info("WS-AT activityId:" + activityId);
        // Modified by MENGRAN at 2011-12-22 for change counter++ to AtomicInteger.incrementAndGet()
        Xid xid = new XidImpl(1234, globalTransactionId(), new byte[]{});
        txId = TransactionIdHelper.getInstance().xid2wsatid(xid);
        long ttl = 0;
        try {
            ttl = TransactionImportManager.getInstance().getTransactionRemainingTimeout();
            if (WSATHelper.isDebugEnabled())
                LOGGER.info(LocalizationMessages.WSAT_4575_WSAT_INFO_IN_CLIENT_SIDE_HANDLER(
                        txId, ttl, "suspendedTransaction", Thread.currentThread()));
        } catch (SystemException ex) {
            Logger.getLogger(WSATClientHelper.class).log(Level.SEVERE, null, ex);
        }
        if (WSATHelper.isDebugEnabled())
            LOGGER.info(LocalizationMessages.WSAT_4575_WSAT_INFO_IN_CLIENT_SIDE_HANDLER(
                    txId, ttl, "suspendedTransaction", Thread.currentThread()));
        WSCBuilderFactory builderFactory =
                WSCBuilderFactory.newInstance(transactionalAttribute.getVersion());
        WSATCoordinationContextBuilder builder  =
                builderFactory.newWSATCoordinationContextBuilder();
        CoordinationContextIF cc =
                builder.txId(txId).expires(ttl).soapVersion(transactionalAttribute.getSoapVersion()).mustUnderstand(true)
                        .build();
        Header coordinationHeader =
                Headers.create(cc.getJAXBRIContext(),cc.getDelegate());
        headers.add(coordinationHeader);
       if (WSATHelper.isDebugEnabled())
           LOGGER.info(LocalizationMessages.WSAT_4568_OUTBOUND_APPLICATION_MESSAGE_TRANSACTION_AFTER_ADDING_CONTEXT(
                                                                                                    "suspendedTransaction"));
        Transaction suspendedTransaction = suspend(map); //note suspension moved after context creation
        map.put(WSATConstants.WSAT_TRANSACTION_XID, xid);
        WSATHelper.getInstance().putToXidToTransactionMap(xid, suspendedTransaction);
        return headers;
    }

    /**
     * Suspend the transaction on the current thread which will be resumed upon return
     *
     * @return Transaction not null if suspend is successful
     */
    private Transaction suspend(Map<String, Object> map) {
        Transaction suspendedTransaction = null;
       try {
           suspendedTransaction = TransactionManagerImpl.getInstance().getTransactionManager().suspend();
           if (WSATHelper.isDebugEnabled())
               LOGGER.info(LocalizationMessages.WSAT_4577_ABOUT_TO_SUSPEND_IN_CLIENT_SIDE_HANDLER(
                   suspendedTransaction, Thread.currentThread()));
           map.put(WSATConstants.WSAT_TRANSACTION, suspendedTransaction);
           if (WSATHelper.isDebugEnabled())
               LOGGER.info(LocalizationMessages.WSAT_4578_SUSPENDED_IN_CLIENT_SIDE_HANDLER(
                   suspendedTransaction, Thread.currentThread()));
         } catch (SystemException e) {
            //tx should always be null here as suspend would either work or not
            return null;
         }
         return suspendedTransaction;
    }

}
