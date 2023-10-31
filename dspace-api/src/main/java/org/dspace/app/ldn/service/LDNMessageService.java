/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.ldn.service;

import java.sql.SQLException;
import java.util.List;

import org.dspace.app.ldn.LDNMessageEntity;
import org.dspace.app.ldn.model.Notification;
import org.dspace.core.Context;

/**
 * Service interface class for the {@link LDNMessageEntity} object.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science dot it)
 */
public interface LDNMessageService {

    /**
     * find the ldn message by id
     *
     * @param context the context
     * @param id the uri
     * @return the ldn message by id
     * @throws SQLException If something goes wrong in the database
     */
    public LDNMessageEntity find(Context context, String id) throws SQLException;

    /**
     * Creates a new LDNMessage
     *
     * @param context The DSpace context
     * @param id the uri
     * @return the created LDN Message
     * @throws SQLException If something goes wrong in the database
     */
    public LDNMessageEntity create(Context context, String id) throws SQLException;

    /**
     * Creates a new LDNMessage
     *
     * @param context The DSpace context
     * @param notification the requested notification
     * @return the created LDN Message
     * @throws SQLException If something goes wrong in the database
     */
    public LDNMessageEntity create(Context context, Notification notification) throws SQLException;

    /**
     * Update the provided LDNMessage
     *
     * @param context The DSpace context
     * @param ldnMessage the LDNMessage
     * @throws SQLException If something goes wrong in the database
     */
    public void update(Context context, LDNMessageEntity ldnMessage) throws SQLException;

    /**
     * Find the oldest queued LDNMessages that still can be elaborated
     *
     * @return list of LDN messages
     * @param context The DSpace context
     * @throws SQLException If something goes wrong in the database
     */
    public List<LDNMessageEntity> findOldestMessagesToProcess(Context context) throws SQLException;

    /**
     * Find all messages in the queue with the Processing status but timed-out
     * 
     * @return all the LDN Messages to be fixed on their queue_ attributes
     * @param context The DSpace context
     * @throws SQLException If something goes wrong in the database
     */
    public List<LDNMessageEntity> findProcessingTimedoutMessages(Context context) throws SQLException;

    /**
     * Find all messages in the queue with the Processing status but timed-out and modify their queue_status
     * considering the queue_attempts
     * 
     * @return number of messages fixed
     * @param context The DSpace context
     */
    public int checkQueueMessageTimeout(Context context);

    /**
     * Elaborates the oldest enqueued message
     * 
     * @return number of messages fixed
     * @param context The DSpace context
     */
    public int extractAndProcessMessageFromQueue(Context context) throws SQLException;
}