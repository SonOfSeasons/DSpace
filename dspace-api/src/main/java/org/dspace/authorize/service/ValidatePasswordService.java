/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authorize.service;

import org.dspace.core.Context;

/**
 * Services to use during Validating of password.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
 */
public interface ValidatePasswordService {

    /**
     * This method checks whether the password is valid based on the configured
     * rules/strategies.
     * 
     * @param context  the DSpace context
     * @param password password to validate
     */
    public boolean isPasswordValid(Context context, String password);

}