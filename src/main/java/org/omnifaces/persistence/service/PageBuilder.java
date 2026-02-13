/*
 * Copyright 2021 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.service;

import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService.MappedQueryBuilder;

/**
 * Helper class of {@link BaseEntityService}.
 */
class PageBuilder<T> {

    private final Page page;
    private final boolean cacheable;
    private final Class<T> resultType;
    private final MappedQueryBuilder<T> queryBuilder;

    private boolean shouldBuildCountSubquery;
    private boolean canBuildValueBasedPagingPredicate;

    public PageBuilder(Page page, boolean cacheable, Class<T> resultType, MappedQueryBuilder<T> queryBuilder) {
        this.page = page;
        this.cacheable = cacheable;
        this.resultType = resultType;
        this.queryBuilder = queryBuilder;
        this.canBuildValueBasedPagingPredicate = page.getLast() != null && page.getOffset() > 0;
    }

    public void shouldBuildCountSubquery(boolean yes) {
        shouldBuildCountSubquery |= yes;
    }

    public boolean shouldBuildCountSubquery() {
        return shouldBuildCountSubquery;
    }

    public void canBuildValueBasedPagingPredicate(boolean yes) {
        canBuildValueBasedPagingPredicate &= yes;
    }

    public boolean canBuildValueBasedPagingPredicate() {
        return canBuildValueBasedPagingPredicate;
    }

    public Page getPage() {
        return page;
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public Class<T> getResultType() {
        return resultType;
    }

    public MappedQueryBuilder<T> getQueryBuilder() {
        return queryBuilder;
    }

}
