package org.jboss.ddoyle.drools.serialization.demo.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Interface for all our <code>Drools Fusion</code> Events.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public interface Event extends Serializable {
	
	public abstract String getId();
	
	public abstract Date getTimestamp();

}
