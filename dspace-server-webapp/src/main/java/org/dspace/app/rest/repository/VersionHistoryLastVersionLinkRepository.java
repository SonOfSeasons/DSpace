/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;
import java.sql.SQLException;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.model.VersionHistoryRest;
import org.dspace.app.rest.model.VersionRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.core.Context;
import org.dspace.versioning.Version;
import org.dspace.versioning.VersionHistory;
import org.dspace.versioning.service.VersionHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the Repository that retrieve the most recent version in the history
 * that could live eventually in the workspace or workflow.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
@Component(VersionHistoryRest.CATEGORY + "." + VersionHistoryRest.NAME + "." + VersionHistoryRest.LAST_VERSION)
public class VersionHistoryLastVersionLinkRepository extends AbstractDSpaceRestRepository
        implements LinkRestRepository {

    @Autowired
    private VersionHistoryService versionHistoryService;

    @PreAuthorize("hasAuthority('ADMIN')")
    public VersionRest getLastVersion(@Nullable HttpServletRequest request,
                                                Integer versionHistoryId,
                                      @Nullable Pageable optionalPageable,
                                                Projection projection) throws SQLException {
        Context context = obtainContext();
        if (Objects.isNull(versionHistoryId) || versionHistoryId < 0) {
            throw new DSpaceBadRequestException("Provied id is not correct!");
        }
        VersionHistory versionHistory = versionHistoryService.find(context, versionHistoryId);
        if (Objects.isNull(versionHistory)) {
            throw new ResourceNotFoundException("No such versio found");
        }
        Version oldestVersion = versionHistoryService.getLatestVersion(context, versionHistory);
        if (Objects.isNull(oldestVersion)) {
            throw new ResourceNotFoundException("No such version for versionhistory with id:" + versionHistoryId);
        }
        return converter.toRest(oldestVersion, projection);
    }

}