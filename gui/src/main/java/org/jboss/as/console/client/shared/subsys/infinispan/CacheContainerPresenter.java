/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.console.client.shared.subsys.infinispan;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.HasPresenter;
import org.jboss.as.console.client.core.ManualRevealPresenter;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.shared.model.ResponseWrapper;
import org.jboss.as.console.client.shared.subsys.RevealStrategy;
import org.jboss.as.console.client.shared.subsys.infinispan.model.CacheContainerStore;
import org.jboss.as.console.client.shared.viewframework.FrameworkView;
import org.jboss.as.console.spi.RequiredResources;
import org.jboss.as.console.spi.SearchIndex;


/**
 * The Presenter for Cache Containers
 *
 * @author Stan Silvert
 */
public class CacheContainerPresenter extends ManualRevealPresenter<CacheContainerPresenter.MyView, CacheContainerPresenter.MyProxy> {

    @ProxyCodeSplit
    @NameToken(NameTokens.CacheContainerPresenter)
    @RequiredResources(resources = {"{selected.profile}/subsystem=infinispan"})
    @SearchIndex(keywords = {"cache", "ejb", "hibernate", "web", "transport"})
    public interface MyProxy extends ProxyPlace<CacheContainerPresenter> {}

    public interface MyView extends FrameworkView, View, HasPresenter<CacheContainerPresenter> {}


    private RevealStrategy revealStrategy;
    private CacheContainerStore cacheContainerStore;

    @Inject
    public CacheContainerPresenter(
            EventBus eventBus, MyView view, MyProxy proxy,
            CacheContainerStore cacheContainerStore,
            RevealStrategy revealStrategy) {
        super(eventBus, view, proxy);

        this.revealStrategy = revealStrategy;
        this.cacheContainerStore = cacheContainerStore;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    protected void onReset() {
        super.onReset();
        getView().initialLoad();
    }

    @Override
    protected void revealInParent() {
        revealStrategy.revealInParent(this);
    }

    public void clearCaches(final String cacheContainerName) {
        cacheContainerStore.clearCaches(cacheContainerName, new SimpleCallback<ResponseWrapper<Boolean>>() {
            @Override
            public void onSuccess(ResponseWrapper<Boolean> response) {
                if (response.getUnderlying())
                    Console.info(Console.MESSAGES.successful("Clear caches successful for container: " + cacheContainerName));
                else
                    Console.error(Console.MESSAGES.failed("Failed to clear caches for container: " + cacheContainerName), response.getResponse().toString());
            }
        });
    }
}
