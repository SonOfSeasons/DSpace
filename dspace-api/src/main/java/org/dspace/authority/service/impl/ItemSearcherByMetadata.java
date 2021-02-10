/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authority.service.impl;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authority.service.ItemReferenceResolver;
import org.dspace.authority.service.ItemSearcher;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.authority.service.ChoiceAuthorityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.DiscoverResultIterator;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.discovery.indexobject.IndexableWorkflowItem;
import org.dspace.discovery.indexobject.IndexableWorkspaceItem;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link ItemSearcher} and {@link ItemReferenceResolver} to
 * search the item by the configured metadata.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class ItemSearcherByMetadata implements ItemSearcher, ItemReferenceResolver {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ChoiceAuthorityService choiceAuthorityService;

    private final String metadata;

    private final String authorityPrefix;

    public ItemSearcherByMetadata(String metadata, String authorityPrefix) {
        this.metadata = metadata;
        this.authorityPrefix = authorityPrefix;
    }

    @Override
    public Item searchBy(Context context, String searchParam) {
        try {
            return performSearchByMetadata(context, searchParam);
        } catch (SearchServiceException e) {
            throw new RuntimeException("An error occurs searching the item by metadata", e);
        }
    }

    @Override
    public void resolveReferences(Context context, Item item) {

        List<MetadataValue> metadataValues = itemService.getMetadataByMetadataString(item, metadata);
        if (CollectionUtils.isEmpty(metadataValues)) {
            return;
        }

        try {
            context.turnOffAuthorisationSystem();
            resolveReferences(context, metadataValues, item);
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException("An error occurs resolving references", e);
        } finally {
            context.restoreAuthSystemState();
        }

    }

    @SuppressWarnings("rawtypes")
    private Item performSearchByMetadata(Context context, String searchParam) throws SearchServiceException {
        String query = metadata + ":" + searchParam;
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.addFilterQueries(query);

        DiscoverResult discoverResult = searchService.search(context, discoverQuery);
        List<IndexableObject> indexableObjects = discoverResult.getIndexableObjects();

        if (CollectionUtils.isEmpty(indexableObjects)) {
            return null;
        }

        if (indexableObjects.size() > 1) {
            throw new SearchServiceException("Multiple item found for search param " + query);
        }

        return (Item) indexableObjects.get(0).getIndexedObject();
    }

    private void resolveReferences(Context context, List<MetadataValue> metadataValues, Item item)
        throws SQLException, AuthorizeException {

        String relationshipType = itemService.getMetadataFirstValue(item, "relationship", "type", null, Item.ANY);

        List<String> authorities = metadataValues.stream()
            .map(MetadataValue::getValue)
            .map(value -> AuthorityValueService.REFERENCE + authorityPrefix + "::" + value)
            .collect(Collectors.toList());

        Iterator<Item> itemIterator = findItemsToResolve(context, authorities, relationshipType);

        while (itemIterator.hasNext()) {
            Item itemWithReference = itemIterator.next();

            itemWithReference.getMetadata().stream()
                .filter(metadataValue -> authorities.contains(metadataValue.getAuthority()))
                .forEach(metadataValue -> metadataValue.setAuthority(item.getID().toString()));

            itemService.update(context, itemWithReference);
        }
    }

    private Iterator<Item> findItemsToResolve(Context context, List<String> authorities, String relationshipType) {

        String query = choiceAuthorityService.getAuthorityControlledFieldsByRelationshipType(relationshipType).stream()
            .map(field -> getFieldFilter(field, authorities))
            .collect(Collectors.joining(" OR "));

        if (StringUtils.isEmpty(query)) {
            return Collections.emptyIterator();
        }

        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.addDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.addDSpaceObjectFilter(IndexableWorkspaceItem.TYPE);
        discoverQuery.addDSpaceObjectFilter(IndexableWorkflowItem.TYPE);
        discoverQuery.addFilterQueries(query);

        return new DiscoverResultIterator<Item, UUID>(context, discoverQuery, false);

    }

    private String getFieldFilter(String field, List<String> authorities) {
        return authorities.stream()
            .map(authority -> field.replaceAll("_", ".") + "_authority: \"" + authority + "\"")
            .collect(Collectors.joining(" OR "));
    }

}
