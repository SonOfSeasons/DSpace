package org.dspace.app.cris.service;

import java.util.Queue;

import com.google.common.collect.EvictingQueue;

public class VolatileObjects {
    public static class CandidateObjectValue {
    	private int id;
    	private String type;
    	private String value;
    	private String tag;
    	private Integer persistedObjectID;
    	
    	public static final String SEP = "  ";

    	public CandidateObjectValue(int id, String type, String value, String tag) {
    		this.id = id;
    		this.type = type;
    		this.value = value;
    		this.tag = tag;
    	}
		
		public int getId() {
			return id;
		}
		
		public String getType() {
			return type;
		}
		
		public String getFullValue() {
			return value + SEP + tag;
		}
		
		public String getValue() {
			return value;
		}
		
		public Integer getPersistedObjectID() {
			return persistedObjectID;
		}
		
		public void setPersistedObjectID(Integer persistedObjectID) {
			this.persistedObjectID = persistedObjectID;
		}
		
		/***
		 * A value can be updated changing only its case
		 * 
		 * @param value new value (case sensitive)
		 */
		public void updateValue(String value, String tag) {
			if (this.value.equalsIgnoreCase(value) && this.value.equalsIgnoreCase(tag)) {
				this.value = value;
				this.tag = tag;
			}
		}
		
		/***
		 * Compare by value
		 * 
		 * @param type the type of object
		 * @param value The name
		 * @param tag The tag
		 * @return True is the object contains the value/tag.
		 */
		public boolean equalsTo(String type, String value, String tag) {
			return this.type.equalsIgnoreCase(type) && this.value.equalsIgnoreCase(value)
					&& this.tag.equalsIgnoreCase(tag);
		}
    }
	
    /***
     * Maximum number of simultaneous calls is maxOperations/2
     */
    public int maxOperations = 500;
    public int maxSequenceValue = maxOperations * 2;
    
    /***
     * 	The candidate data (id & value) are cached inside the queue, whereas
     * 	the id value is a negative integer generated by and internal "sequence".
     *  
	 *	The queue can hold only maxOperations.
     */
    private Queue<CandidateObjectValue> queue = EvictingQueue.create(maxOperations);
    private int sequence = 0;
    
    /***
     * Add a candidate value to the queue.
     * 
     * @param value The value
     * @param tag The tag
     */
    public synchronized Integer addCandidateValue(String type, String value, String tag) {
    	if (value == null || value.length() <= 0)
    		return null;
    	
    	/* check for inserted value */
    	for (CandidateObjectValue cval : queue) {
    		if (cval.equalsTo(type, value, tag)) {
    			cval.updateValue(value, tag);
    			return cval.getId();
    		}
    	}
    	if (sequence <= -maxSequenceValue) {
    		sequence = 0;
    	}
    	sequence = sequence - 1;
    	queue.add(new CandidateObjectValue(sequence, type, value, tag));
    	
    	return sequence;
    }
    
    /***
     * Get candidate value by id.
     * 
     * @param idThread The current thread id
     * @param from The starting date used for operation description retrieval
     * @param id The key of the candidate Value
     * @return result status of http operations performed
     */
    public synchronized CandidateObjectValue getCandidate(int id) {
    	for (CandidateObjectValue cval : queue) {
    		if (cval.getId() == id) {
    			return cval;
    		}
    	}
    	return null;
    }
}
