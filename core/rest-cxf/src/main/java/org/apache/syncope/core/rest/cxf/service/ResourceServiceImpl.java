/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.rest.cxf.service;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.BulkAction;
import org.apache.syncope.common.lib.to.BulkActionResult;
import org.apache.syncope.common.lib.to.ConnObjectTO;
import org.apache.syncope.common.lib.to.ResourceTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.ResourceDeassociationActionType;
import org.apache.syncope.common.lib.wrap.AnyKey;
import org.apache.syncope.common.rest.api.RESTHeaders;
import org.apache.syncope.common.rest.api.service.ResourceService;
import org.apache.syncope.core.logic.AbstractResourceAssociator;
import org.apache.syncope.core.logic.AnyObjectLogic;
import org.apache.syncope.core.logic.ResourceLogic;
import org.apache.syncope.core.logic.GroupLogic;
import org.apache.syncope.core.logic.UserLogic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ResourceServiceImpl extends AbstractServiceImpl implements ResourceService {

    @Autowired
    private ResourceLogic logic;

    @Autowired
    private AnyObjectLogic anyObjectLogic;

    @Autowired
    private UserLogic userLogic;

    @Autowired
    private GroupLogic groupLogic;

    @Override
    public Response create(final ResourceTO resourceTO) {
        ResourceTO created = logic.create(resourceTO);
        URI location = uriInfo.getAbsolutePathBuilder().path(created.getKey()).build();
        return Response.created(location).
                header(RESTHeaders.RESOURCE_ID, created.getKey()).
                build();
    }

    @Override
    public void update(final String resourceKey, final ResourceTO resourceTO) {
        resourceTO.setKey(resourceKey);
        logic.update(resourceTO);
    }

    @Override
    public void delete(final String resourceKey) {
        logic.delete(resourceKey);
    }

    @Override
    public ResourceTO read(final String resourceKey) {
        return logic.read(resourceKey);
    }

    @Override
    public List<ResourceTO> list() {
        return logic.list();
    }

    @Override
    public ConnObjectTO readConnObject(final String resourceKey, final String anyTypeKey, final Long key) {
        return logic.readConnObject(resourceKey, anyTypeKey, key);
    }

    @Override
    public boolean check(final ResourceTO resourceTO) {
        return logic.check(resourceTO);
    }

    @Override
    public BulkActionResult bulkDeassociation(
            final String resourceKey, final String anyTypeKey, final ResourceDeassociationActionType type,
            final List<AnyKey> keys) {

        AbstractResourceAssociator<? extends AnyTO> associator = anyTypeKey.equalsIgnoreCase(AnyTypeKind.USER.name())
                ? userLogic
                : anyTypeKey.equalsIgnoreCase(AnyTypeKind.GROUP.name())
                        ? groupLogic
                        : anyObjectLogic;

        BulkActionResult result = new BulkActionResult();

        for (AnyKey key : keys) {
            Set<String> resources = Collections.singleton(resourceKey);
            try {
                switch (type) {
                    case DEPROVISION:
                        associator.deprovision(key.getElement(), resources);
                        break;

                    case UNASSIGN:
                        associator.unassign(key.getElement(), resources);
                        break;

                    case UNLINK:
                        associator.unlink(key.getElement(), resources);
                        break;

                    default:
                }

                result.getResults().put(String.valueOf(key.getElement()), BulkActionResult.Status.SUCCESS);
            } catch (Exception e) {
                LOG.warn("While executing {} on {} {}", type, anyTypeKey, key.getElement(), e);
                result.getResults().put(String.valueOf(key.getElement()), BulkActionResult.Status.FAILURE);
            }
        }

        return result;
    }

    @Override
    public BulkActionResult bulk(final BulkAction bulkAction) {
        BulkActionResult result = new BulkActionResult();

        if (bulkAction.getOperation() == BulkAction.Type.DELETE) {
            for (String name : bulkAction.getTargets()) {
                try {
                    result.getResults().put(logic.delete(name).getKey(), BulkActionResult.Status.SUCCESS);
                } catch (Exception e) {
                    LOG.error("Error performing delete for resource {}", name, e);
                    result.getResults().put(name, BulkActionResult.Status.FAILURE);
                }
            }
        }

        return result;
    }
}
