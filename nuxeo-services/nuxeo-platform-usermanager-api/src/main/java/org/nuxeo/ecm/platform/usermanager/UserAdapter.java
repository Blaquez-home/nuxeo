/*
 * (C) Copyright 2006-2012 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     tmartins
 */
package org.nuxeo.ecm.platform.usermanager;

import java.util.List;


/**
 * Interface to expose user model fields
 *
 * @since 5.7
 * @author <a href="mailto:tm@nuxeo.com">Thierry Martins</a>
 */
public interface UserAdapter {

    String getName();

    String getFirstName();

    String getLastName();

    String getEmail();

    String getCompany();

    List<String> getGroups();

    /**
     * @since 8.1
     */
    String getTenantId();

    String getSchemaName();
}