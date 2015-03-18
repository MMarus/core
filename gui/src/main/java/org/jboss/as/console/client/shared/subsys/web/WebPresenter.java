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

package org.jboss.as.console.client.shared.subsys.web;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.Proxy;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.HasPresenter;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.model.ModelAdapter;
import org.jboss.as.console.client.shared.subsys.Baseadress;
import org.jboss.as.console.client.shared.subsys.RevealStrategy;
import org.jboss.as.console.client.shared.subsys.web.model.HttpConnector;
import org.jboss.as.console.client.shared.subsys.web.model.JSPContainerConfiguration;
import org.jboss.as.console.client.shared.subsys.web.model.VirtualServer;
import org.jboss.as.console.client.widgets.forms.AddressBinding;
import org.jboss.as.console.client.widgets.forms.ApplicationMetaData;
import org.jboss.as.console.client.widgets.forms.EntityAdapter;
import org.jboss.as.console.client.widgets.forms.PropertyBinding;
import org.jboss.as.console.mbui.behaviour.CoreGUIContext;
import org.jboss.as.console.mbui.behaviour.CrudOperationDelegate;
import org.jboss.as.console.mbui.dmr.ResourceAddress;
import org.jboss.as.console.spi.AccessControl;
import org.jboss.as.console.spi.SearchIndex;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.Property;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * @author Harald Pehl
 * @author Heiko Braun
 * @author Pavel Slegr
 * @date 5/11/11
 */
public class WebPresenter extends Presenter<WebPresenter.MyView, WebPresenter.MyProxy> {

    @ProxyCodeSplit
    @NameToken(NameTokens.WebPresenter)
    @AccessControl(resources = "/{selected.profile}/subsystem=web", recursive = false)
    @SearchIndex(keywords = {"http", "ssl", "servlet", "jsp", "virtual-host", "filter"})
    public interface MyProxy extends Proxy<WebPresenter>, Place {}


    public interface MyView extends View, HasPresenter<WebPresenter> {
        void updateGlobals(ModelNode globals);
        void updateJsp(ModelNode jsp);
        void setConnectors(List<HttpConnector> connectors);
        void enableEditConnector(boolean b);
        void setVirtualServers(List<VirtualServer> servers);
        void enableEditVirtualServer(boolean b);
        void setSocketBindings(List<String> socketBindings);
    }


    private final BeanFactory factory;
    private final DispatchAsync dispatcher;
    private final ApplicationMetaData metaData;
    private final RevealStrategy revealStrategy;
    private final Scheduler scheduler;
    private final EntityAdapter<JSPContainerConfiguration> containerAdapter;
    private final LoadConnectorCmd loadConnectorCmd;
    private final LoadSocketBindingsCmd socketBinding;
    private final CrudOperationDelegate operationDelegate;
    private final CrudOperationDelegate.Callback operationCallback;

    private List<HttpConnector> connectors;
    private List<VirtualServer> virtualServers;
    private List<String> socketsBindingList;

    private DefaultWindow window;

    @Inject
    public WebPresenter(EventBus eventBus, MyView view, MyProxy proxy, BeanFactory factory, DispatchAsync dispatcher,
            ApplicationMetaData metaData, RevealStrategy revealStrategy, CoreGUIContext statementContext, Scheduler scheduler) {
        super(eventBus, view, proxy);

        this.factory = factory;
        this.dispatcher = dispatcher;
        this.metaData = metaData;
        this.revealStrategy = revealStrategy;
        this.scheduler = scheduler;

        this.containerAdapter = new EntityAdapter<JSPContainerConfiguration>(JSPContainerConfiguration.class, metaData);
        this.loadConnectorCmd = new LoadConnectorCmd(dispatcher, factory, false);
        this.socketBinding = new LoadSocketBindingsCmd(dispatcher);
        this.operationDelegate = new CrudOperationDelegate(statementContext, dispatcher);
        this.operationCallback = new CrudOperationDelegate.Callback() {
            @Override
            public void onSuccess(ResourceAddress address, String name) {
                loadGlobalAttributes();
                loadJSPConfig();
            }

            @Override
            public void onFailure(ResourceAddress address, String name, Throwable t) {
                // noop
            }
        };
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    protected void revealInParent() {
        revealStrategy.revealInParent(this);
    }

    @Override
    protected void onReset() {
        super.onReset();
        loadGlobalAttributes();
        loadJSPConfig();
        loadConnectors();
        loadVirtualServer();
        loadSocketBindings();
    }

    private void loadGlobalAttributes() {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "web");

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse response) {
                ModelNode result = response.get();
                if (result.isFailure()) {
                    Console.error(Console.MESSAGES.failed("Global attributes"), result.getFailureDescription());
                } else {
                    final ModelNode globals = result.get(RESULT);
                    getView().updateGlobals(globals);
                }
            }
        });
    }

    private void loadJSPConfig() {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "web");
        operation.get(ADDRESS).add("configuration", "jsp-configuration");

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse response) {
                ModelNode result = response.get();
                if (result.isFailure()) {
                    Console.error(Console.MESSAGES.failed("JSP attributes"), result.getFailureDescription());
                } else {
                    final ModelNode jsp = result.get(RESULT);
                    getView().updateJsp(jsp);
                }
            }
        });
    }

    private void loadVirtualServer() {
        // /profile=default/subsystem=web:read-children-resources(child-type=virtual-server, recursive=true)
        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "web");
        operation.get(CHILD_TYPE).set("virtual-server");
        operation.get(RECURSIVE).set(Boolean.TRUE);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                List<Property> propList = response.get(RESULT).asPropertyList();
                List<VirtualServer> servers = new ArrayList<VirtualServer>(propList.size());

                for (Property prop : propList) {
                    String name = prop.getName();
                    ModelNode propValue = prop.getValue();

                    VirtualServer server = factory.virtualServer().as();
                    server.setName(name);

                    List<String> aliases = new ArrayList<String>();
                    if (propValue.hasDefined("alias")) {
                        List<ModelNode> aliasList = propValue.get("alias").asList();
                        for (ModelNode alias : aliasList)
                            aliases.add(alias.asString());
                    }

                    server.setAlias(aliases);

                    if (propValue.hasDefined("default-web-module"))
                        server.setDefaultWebModule(propValue.get("default-web-module").asString());

                    servers.add(server);
                }

                virtualServers = servers;
                getView().setVirtualServers(servers);
            }
        });

    }

    private void loadConnectors() {
        loadConnectorCmd.execute(new SimpleCallback<List<HttpConnector>>() {
            @Override
            public void onSuccess(List<HttpConnector> result) {
                setConnectors(result);
                getView().setConnectors(result);
            }
        });
    }

    private void loadSocketBindings() {
        socketBinding.loadSocketBindingGroupForSelectedProfile(new SimpleCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> result) {
                setSocketBindings(result);
                getView().setSocketBindings(result);
            }
        });
    }

    private void setConnectors(List<HttpConnector> connectors) {
        this.connectors = connectors;
    }

    private void setSocketBindings(List<String> bindings) {
        this.socketsBindingList = bindings;
    }

    public void onEditConnector() {
        getView().enableEditConnector(true);
    }

    public void onSaveConnector(final String name, Map<String, Object> changedValues) {
        getView().enableEditConnector(false);

        if (changedValues.isEmpty()) return;

        if (changedValues.containsKey("socketBinding")) {
            boolean inUse = false;
            for (HttpConnector existing : connectors) {
                if (existing.getSocketBinding().equals(changedValues.get("socketBinding"))) {
                    inUse = true;
                    break;
                }
            }

            if (inUse) {
                Console.error(Console.CONSTANTS.subsys_web_socketInUse());
                loadConnectors();
                return;
            }
        }

        ModelNode proto = new ModelNode();
        proto.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        proto.get(ADDRESS).set(Baseadress.get());
        proto.get(ADDRESS).add("subsystem", "web");
        proto.get(ADDRESS).add("connector", name);

        List<PropertyBinding> bindings = metaData.getBindingsForType(HttpConnector.class);
        ModelNode operation = ModelAdapter.detypedFromChangeset(proto, changedValues, bindings);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if (successful)
                    Console.info(Console.MESSAGES.modified("Connector " + name));
                else
                    Console.error(Console.MESSAGES.modificationFailed("Connector " + name), response.getFailureDescription());

                loadConnectors();
            }
        });
    }

    public void onDeleteConnector(final String name) {
        ModelNode connector = new ModelNode();
        connector.get(OP).set(REMOVE);
        connector.get(ADDRESS).set(Baseadress.get());
        connector.get(ADDRESS).add("subsystem", "web");
        connector.get(ADDRESS).add("connector", name);

        dispatcher.execute(new DMRAction(connector), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if (successful)
                    Console.info(Console.MESSAGES.deleted("Connector " + name));
                else
                    Console.error(Console.MESSAGES.deletionFailed("Connector " + name), response.getFailureDescription());

                scheduler.scheduleDeferred(WebPresenter.this::loadConnectors);
            }
        });
    }

    public void launchConnectorDialogue() {
        window = new DefaultWindow(Console.MESSAGES.createTitle("Connector"));
        window.setWidth(480);
        window.setHeight(400);
        window.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {

            }
        });

        window.trapWidget(
                new NewConnectorWizard(this, connectors, socketsBindingList).asWidget()
        );

        window.setGlassEnabled(true);
        window.center();
    }

    public void onCreateConnector(final HttpConnector entity) {
        closeDialogue();

        ModelNode connector = new ModelNode();
        connector.get(OP).set(ADD);
        connector.get(ADDRESS).set(Baseadress.get());
        connector.get(ADDRESS).add("subsystem", "web");
        connector.get(ADDRESS).add("connector", entity.getName());

        connector.get("protocol").set(entity.getProtocol());
        connector.get("scheme").set(entity.getScheme());
        connector.get("socket-binding").set(entity.getSocketBinding());
        connector.get("enabled").set(entity.isEnabled());

        dispatcher.execute(new DMRAction(connector), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if (successful)
                    Console.info(Console.MESSAGES.added("Connector " + entity.getName()));
                else
                    Console.error(Console.MESSAGES.addingFailed("Connector " + entity.getName()), response.getFailureDescription());

                scheduler.scheduleDeferred(WebPresenter.this::loadConnectors);
            }
        });
    }

    public void onEditVirtualServer() {
        getView().enableEditVirtualServer(true);
    }

    public void onCreateVirtualServer(final VirtualServer server) {
        closeDialogue();

        ModelNode operation = new ModelNode();
        operation.get(OP).set(ADD);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "web");
        operation.get(ADDRESS).add("virtual-server", server.getName());

        if (server.getAlias() != null && server.getAlias().size() > 0) {
            for (String alias : server.getAlias())
                operation.get("alias").add(alias);

        }

        if (server.getDefaultWebModule() != null && !server.getDefaultWebModule().isEmpty())
            operation.get("default-web-module").set(server.getDefaultWebModule());

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if (successful)
                    Console.info(Console.MESSAGES.added("Virtual Server " + server.getName()));
                else
                    Console.error(Console.MESSAGES.added("Virtual Server " + server.getName()), response.getFailureDescription());

                scheduler.scheduleDeferred(WebPresenter.this::loadVirtualServer);
            }
        });
    }

    public void onSaveVirtualServer(final String name, Map<String, Object> changedValues) {
        getView().enableEditVirtualServer(false);

        if (changedValues.isEmpty()) return;

        AddressBinding addressBinding = metaData.getBeanMetaData(VirtualServer.class).getAddress();
        ModelNode address = addressBinding.asResource(Baseadress.get(), name);

        EntityAdapter<VirtualServer> adapter = new EntityAdapter<VirtualServer>(VirtualServer.class, metaData);
        ModelNode operation = adapter.fromChangeset(changedValues, address);

        if (changedValues.containsKey("alias")) {
            ModelNode protoType = new ModelNode();
            protoType.get(ADDRESS).set(address.get(ADDRESS));
            protoType.get(OP).set(WRITE_ATTRIBUTE_OPERATION);

            List<String> values = (List<String>) changedValues.get("alias");
            ModelNode list = new ModelNode();
            for (String alias : values)
                list.add(alias);

            protoType.get(NAME).set("alias");
            protoType.get(VALUE).set(list);

            operation.get(STEPS).add(protoType);
        }

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if (successful)
                    Console.info(Console.MESSAGES.modified("Virtual Server " + name));
                else
                    Console.error(Console.MESSAGES.modificationFailed("Virtual Server " + name));

                scheduler.scheduleDeferred(WebPresenter.this::loadVirtualServer);
            }
        });
    }

    public void onDeleteVirtualServer(final String name) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "web");
        operation.get(ADDRESS).add("virtual-server", name);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if (successful)
                    Console.info(Console.MESSAGES.deleted("Virtual Server " + name));
                else
                    Console.error(Console.MESSAGES.deletionFailed("Virtual Server " + name));

                scheduler.scheduleDeferred(WebPresenter.this::loadVirtualServer);
            }
        });
    }

    public void launchVirtualServerDialogue() {

        window = new DefaultWindow(Console.MESSAGES.createTitle("Virtual Server"));
        window.setWidth(480);
        window.setHeight(360);
        window.trapWidget(
                new NewVirtualServerWizard(this, virtualServers).asWidget()
        );

        window.setGlassEnabled(true);
        window.center();
    }

    public void onSaveJSPConfig(Map<String, Object> changeset) {

        AddressBinding addressBinding = metaData.getBeanMetaData(JSPContainerConfiguration.class).getAddress();
        ModelNode address = addressBinding.asResource(Baseadress.get());
        ModelNode extra = null;

        if (changeset.containsKey("instanceId")) {
            Object instanceId = changeset.get("instanceId");
            changeset.remove("instanceId");

            extra = new ModelNode();
            extra.get(ADDRESS).set(Baseadress.get());
            extra.get(ADDRESS).add("subsystem", "web");
            extra.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
            extra.get(NAME).set("instance-id");

            if (instanceId instanceof String) {
                extra.get(VALUE).set((String) instanceId);
            }
        }

        ModelNode operation = null;
        if (extra != null)
            operation = containerAdapter.fromChangeset(changeset, address, extra);
        else
            operation = containerAdapter.fromChangeset(changeset, address);


        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();
                boolean successful = !response.isFailure();
                if (successful)
                    Console.info(Console.MESSAGES.successful("JSP Configuration"));
                else
                    Console.error(Console.MESSAGES.failed("JSP Configuration"), response.getFailureDescription());

                loadJSPConfig();

            }
        });
    }

    public void closeDialogue() {
        window.hide();
    }

    public void onSaveResource(String resourceAddress, String name, Map<String,Object> changedValues) {
        // used for both the global attributes and the JSP related attributes
        operationDelegate.onSaveResource(resourceAddress, name, changedValues, operationCallback);
    }
}
