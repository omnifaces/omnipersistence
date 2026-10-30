/*
 * Copyright OmniFaces
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
package org.omnifaces.persistence.test.service;

import java.util.List;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Text;

/**
 * A service overriding the {@link BaseEntityService} methods which other {@link BaseEntityService} methods invoke, recording each invocation in
 * {@link Invocations}. This pins that those other methods invoke the overridden ones. The protected {@link #updateAndFlush(Text)}, {@link #saveAndFlush(Text)}
 * and {@link #exists(Text)} are made public so that they can be invoked as well.
 */
public abstract class TrackingTextService extends BaseEntityService<Long, Text> {

    public static final String ENTITY_MANAGER = "getEntityManager";

    @Inject
    private Invocations invocations;

    public List<String> getInvocations() {
        return invocations.get();
    }

    public void clearInvocations() {
        invocations.clear();
    }

    @Override
    protected EntityManager getEntityManager() {
        invocations.add(ENTITY_MANAGER);
        return super.getEntityManager();
    }

    @Override
    protected Text getById(Long id, boolean includeSoftDeleted) {
        invocations.add("getById");
        return super.getById(id, includeSoftDeleted);
    }

    @Override
    protected List<Text> list(boolean includeSoftDeleted) {
        invocations.add("list");
        return super.list(includeSoftDeleted);
    }

    @Override
    public Long persist(Text entity) {
        invocations.add("persist");
        return super.persist(entity);
    }

    @Override
    public Text update(Text entity) {
        invocations.add("update");
        return super.update(entity);
    }

    @Override
    public boolean exists(Text entity) {
        return super.exists(entity);
    }

    @Override
    public Text updateAndFlush(Text entity) {
        return super.updateAndFlush(entity);
    }

    @Override
    public Text save(Text entity) {
        invocations.add("save");
        return super.save(entity);
    }

    @Override
    public Text saveAndFlush(Text entity) {
        return super.saveAndFlush(entity);
    }

    @Override
    public void delete(Text entity) {
        invocations.add("delete");
        super.delete(entity);
    }

    @Override
    public void softDelete(Text entity) {
        invocations.add("softDelete");
        super.softDelete(entity);
    }

    @Override
    public void softUndelete(Text entity) {
        invocations.add("softUndelete");
        super.softUndelete(entity);
    }

    @Override
    protected Consumer<EntityManager> beforePage() {
        return entityManager -> invocations.add("beforePage");
    }

    @Override
    protected <T extends Text> Consumer<TypedQuery<?>> onPage(Class<T> resultType, boolean cacheable) {
        var onPage = super.onPage(resultType, cacheable);
        return typedQuery -> {
            invocations.add("onPage");
            onPage.accept(typedQuery);
        };
    }

    @Override
    protected Consumer<EntityManager> afterPage() {
        return entityManager -> invocations.add("afterPage");
    }

}
