/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * https://github.com/CILEA/dspace-cris/wiki/License
 */
package org.dspace.app.cris.discovery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.discovery.SolrServiceIndexPlugin;
import org.dspace.discovery.configuration.DiscoverySearchFilter;
import org.dspace.utils.DSpace;
import org.dspace.versioning.Version;
import org.dspace.versioning.VersionHistory;
import org.dspace.versioning.VersioningService;

public class BitstreamSolrIndexer implements SolrServiceIndexPlugin
{
    private static final Logger log = Logger
            .getLogger(BitstreamSolrIndexer.class);
    
    private static final String MVUNT_SUFFIX = "_mvuntokenized";
    
    private static final String ORIGINAL_BUNDLE = "ORIGINAL";
    
    private static final String REAL_ORIGINAL_BUNDLE = "REALORIGINAL";
    
    private static final String DISPLAY_BUNDLE = "DISPLAY";

    @Override
    public void additionalIndex(Context context, DSpaceObject dso,
            SolrInputDocument document, Map<String, List<DiscoverySearchFilter>> searchFilters)
    {
        if (!(dso instanceof Item))
            return;
        Item item = (Item) dso;
        try
        {
            Bundle[] bb = item.getBundles();
            List<String> unitedOriginalAndDisplay = new ArrayList<String>();
            for (Bundle b : bb)
            {

            	String bundleName = b.getName();
                List<String> bitstreams = new ArrayList<String>();
                for (Bitstream bitstream : b.getBitstreams())
                {
                    bitstreams.add(bitstream.getType() + "-"
                            + bitstream.getID());
                }
                
                if (StringUtils.equals(bundleName, ORIGINAL_BUNDLE) || StringUtils.equals(bundleName, DISPLAY_BUNDLE)) {
                	unitedOriginalAndDisplay.addAll(bitstreams);
                	continue;
                }
                
                document.addField(bundleName + MVUNT_SUFFIX, bitstreams);

            }

            //added versioned visit on old ORIGINAL/DISPLAY bitstream
            List<String> versionedUnitedOriginalAndDisplay = new ArrayList<String>();
            boolean existVersions = false;
            VersioningService versioningService = new DSpace().getSingletonService(VersioningService.class);
            VersionHistory versionHistory = versioningService.findVersionHistory(context, item.getID());
            if(versionHistory!=null) {
            	for(Version version : versionHistory.getVersions()) {
            		Item itemOld = version.getItem();
            		Bundle[] bundle = itemOld.getBundles(ORIGINAL_BUNDLE);
                    for (Bundle b : bundle)
                    {
	                    for (Bitstream bitstream : b.getBitstreams())
	                    {
	                    	versionedUnitedOriginalAndDisplay.add(bitstream.getType() + "-"
	                                + bitstream.getID());
	                    }
                    }
            		bundle = itemOld.getBundles(DISPLAY_BUNDLE);
                    for (Bundle b : bundle)
                    {
	                    for (Bitstream bitstream : b.getBitstreams())
	                    {
	                    	versionedUnitedOriginalAndDisplay.add(bitstream.getType() + "-"
	                                + bitstream.getID());
	                    }
                    }
            	}
            	existVersions = true;
            	if (!versionedUnitedOriginalAndDisplay.isEmpty()) {
            		document.addField(ORIGINAL_BUNDLE + MVUNT_SUFFIX, versionedUnitedOriginalAndDisplay);
            	}
            }

            if (!unitedOriginalAndDisplay.isEmpty()) {
            	if(!existVersions) {
            		document.addField(ORIGINAL_BUNDLE + MVUNT_SUFFIX, unitedOriginalAndDisplay);
            	}
            	else {
            		document.addField(REAL_ORIGINAL_BUNDLE + MVUNT_SUFFIX, unitedOriginalAndDisplay);
            	}
            }            
        }      
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

}
