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

package org.jboss.as.console.client.core.settings;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.CustomProvider;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.NoGatekeeper;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.client.proxy.RevealRootPopupContentEvent;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.core.RequiredResourcesProvider;

/**
 * @author Heiko Braun
 * @date 5/3/11
 */
public class SettingsPresenter extends Presenter<SettingsPresenter.MyView, SettingsPresenter.MyProxy> {

    @NoGatekeeper
    @ProxyCodeSplit
    @CustomProvider(RequiredResourcesProvider.class)
    @NameToken(NameTokens.SettingsPresenter)
    public interface MyProxy extends ProxyPlace<SettingsPresenter> {}

    public interface MyView extends View {}

    
    private SettingsPresenterWidget settingsWidget;

    @Inject
    public SettingsPresenter(EventBus eventBus, MyView view, MyProxy proxy,
                             SettingsPresenterWidget settingsWidget) {
        super(eventBus, view, proxy);
        this.settingsWidget = settingsWidget;
    }

    @Override
    protected void revealInParent() {
        RevealRootPopupContentEvent.fire(this, settingsWidget);
        //addToPopupSlot(settingsWidget, true);
    }
}
