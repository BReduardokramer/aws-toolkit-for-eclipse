/*
 * Copyright 2011 Amazon Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://aws.amazon.com/apache2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amazonaws.eclipse.explorer.actions;

import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.services.IDisposable;

import com.amazonaws.eclipse.core.AwsToolkitCore;
import com.amazonaws.eclipse.core.preferences.PreferenceConstants;
import com.amazonaws.eclipse.core.regions.DefaultRegionChangeRefreshListener;
import com.amazonaws.eclipse.core.regions.Region;
import com.amazonaws.eclipse.core.regions.RegionUtils;
import com.amazonaws.eclipse.core.ui.IRefreshable;
import com.amazonaws.eclipse.explorer.ContentProviderRegistry;
import com.amazonaws.eclipse.explorer.ExplorerNode;

/**
 * Action provider for explorer toolbar and view menu.
 */
public class ConfigurationActionProvider extends CommonActionProvider {

    private final RegionSelectionMenuAction regionSelectionAction;
    private final RefreshAction refreshAction;


    @Override
    public void fillContextMenu(IMenuManager menu) {
        StructuredSelection selection = (StructuredSelection)getActionSite().getStructuredViewer().getSelection();

        if (selection.getFirstElement() instanceof ExplorerNode && selection.size() == 1) {
            ExplorerNode node = (ExplorerNode)selection.getFirstElement();

            if (node.getOpenAction() != null) {
                menu.add(node.getOpenAction());
                menu.add(new Separator());
            }
        }
    }

    private final class RefreshAction extends Action {
        public RefreshAction() {
            this.setText("Refresh");
            this.setToolTipText("Refresh AWS Explorer");
            this.setImageDescriptor(AwsToolkitCore.getDefault().getImageRegistry().getDescriptor(AwsToolkitCore.IMAGE_REFRESH));
        }

        @Override
        public void run() {
            ContentProviderRegistry.refreshAllContentProviders();
        }
    }

    private final class RegionSelectionMenuAction extends Action implements IRefreshable, IDisposable {
        private Menu menu;
        private DefaultRegionChangeRefreshListener regionChangeRefreshListener;

        private RegionSelectionMenuAction() {
            super(null, Action.AS_DROP_DOWN_MENU);
            updateRegionFlag();

            setId("aws-region-selection");

            regionChangeRefreshListener = new DefaultRegionChangeRefreshListener(this);
        }

        @Override
        public IMenuCreator getMenuCreator() {
            return new IMenuCreator() {
                public Menu getMenu(Menu parent) {
                    return null;
                }

                public Menu getMenu(Control parent) {
                    if (menu == null) menu = createMenu(parent);
                    return menu;
                }

                public void dispose() {
                    if (menu != null) menu.dispose();
                }
            };
        }

        private Menu createMenu(final Control parent) {
            Menu menu = new Menu(parent);

            Region currentRegion = RegionUtils.getCurrentRegion();
            for (final Region region : RegionUtils.getRegions()) {
                MenuItem menuItem = new MenuItem(menu, SWT.CHECK);
                menuItem.setText(region.getName());
                menuItem.setData(region);
                menuItem.setSelection(region.equals(currentRegion));
                menuItem.addSelectionListener(new SelectionListener() {
                    public void widgetSelected(SelectionEvent e) {
                        IPreferenceStore preferenceStore = AwsToolkitCore.getDefault().getPreferenceStore();
                        preferenceStore.setValue(PreferenceConstants.P_DEFAULT_REGION, region.getId());
                    }

                    public void widgetDefaultSelected(SelectionEvent e) {}
                });

                String imageId = lookupRegionImageId(region.getId());
                menuItem.setImage(AwsToolkitCore.getDefault().getImageRegistry().get(imageId));
            }

            return menu;
        }

        private void updateRegionFlag() {
            String imageId = lookupRegionImageId();
            setImageDescriptor(AwsToolkitCore.getDefault().getImageRegistry().getDescriptor(imageId));

            Region currentRegion = RegionUtils.getCurrentRegion();
            if (menu != null) {
                for (MenuItem menuItem : menu.getItems()) {
                    Region region = (Region)menuItem.getData();
                    menuItem.setSelection(region.equals(currentRegion));
                }
            }
        }

        private String lookupRegionImageId(String regionId) {
            if (regionId.startsWith("us-")) {
                return AwsToolkitCore.IMAGE_FLAG_US;
            } else if (regionId.startsWith("eu-")) {
                return AwsToolkitCore.IMAGE_FLAG_EU;
            } else if (regionId.startsWith("ap-southeast")) {
                return AwsToolkitCore.IMAGE_FLAG_SINGAPORE;
            } else if (regionId.startsWith("ap-northeast")) {
                return AwsToolkitCore.IMAGE_FLAG_JAPAN;
            }

            return AwsToolkitCore.IMAGE_AWS_ICON;
        }

        private String lookupRegionImageId() {
            Region region = RegionUtils.getCurrentRegion();
            return lookupRegionImageId(region.getId());
        }

        public void refreshData() {
            updateRegionFlag();
        }

        public void dispose() {
            if (regionChangeRefreshListener != null) regionChangeRefreshListener.stopListening();
        }
    }

    private final class AccountPreferencesAction extends Action {
        public AccountPreferencesAction() {
            setText("Configure AWS Accounts");
            setImageDescriptor(AwsToolkitCore.getDefault().getImageRegistry().getDescriptor(AwsToolkitCore.IMAGE_GEAR));
        }

        @Override
        public void run() {
            String resource = AwsToolkitCore.ACCOUNT_PREFERENCE_PAGE_ID;
            PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
                null, resource, new String[] { resource }, null);
            dialog.open();
        }
    }


    public ConfigurationActionProvider() {
        regionSelectionAction = new RegionSelectionMenuAction();
        refreshAction = new RefreshAction();
    }

    /**
     * This method is invoked whenever the selection in the viewer changes, so
     * we need to make sure not to add our action more than once.
     */
    @Override
    public void fillActionBars(IActionBars actionBars) {
        for ( IContributionItem item : actionBars.getToolBarManager().getItems() ) {
            if ( item.getId() == regionSelectionAction.getId() )
                return;
        }

        actionBars.getToolBarManager().add(new Separator());
        actionBars.getToolBarManager().add(refreshAction);

        actionBars.getToolBarManager().add(new Separator());
        actionBars.getToolBarManager().add(regionSelectionAction);

        MenuManager menuMgr = new MenuManager("AWS Account", AwsToolkitCore.getDefault().getImageRegistry()
                .getDescriptor(AwsToolkitCore.IMAGE_AWS_ICON), "");
        menuMgr.setRemoveAllWhenShown(true);

        menuMgr.addMenuListener(new IMenuListener() {

            public void menuAboutToShow(IMenuManager manager) {
                String currentAccountId = AwsToolkitCore.getDefault().getCurrentAccountId();
                Map<String, String> accounts = AwsToolkitCore.getDefault().getAccounts();
                for ( Entry<String, String> entry : accounts.entrySet() ) {
                    manager.add(new SwitchAccountAction(entry.getKey(), entry.getValue(), currentAccountId.equals(entry.getKey())));
                }
                manager.add(new Separator());
                manager.add(new AccountPreferencesAction());
            }
        });
        actionBars.getMenuManager().add(menuMgr);
        actionBars.getToolBarManager().update(true);
    }

    private final class SwitchAccountAction extends Action {

        private final String accountId;

        public SwitchAccountAction(String accountId, String accountName, boolean isCurrentAccount) {
            super(accountName, IAction.AS_CHECK_BOX);
            this.accountId = accountId;
            setChecked(isCurrentAccount);
        }

        @Override
        public void run() {
            AwsToolkitCore.getDefault().setCurrentAccountId(accountId);
        }

    };

}
