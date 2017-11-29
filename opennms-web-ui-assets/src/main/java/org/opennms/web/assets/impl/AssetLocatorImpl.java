/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.web.assets.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opennms.web.assets.api.AssetLocator;
import org.opennms.web.assets.api.AssetResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

public class AssetLocatorImpl implements AssetLocator, InitializingBean {
    private static Logger LOG = LoggerFactory.getLogger(AssetLocatorImpl.class);
    private static final boolean s_useMinified = Boolean.parseBoolean(System.getProperty("org.opennms.web.assets.minified", "true"));
    private static AssetLocator s_instance;

    private final ScheduledExecutorService m_executor = Executors.newSingleThreadScheduledExecutor();
    private Map<String,List<AssetResource>> m_assets = new HashMap<>();

    public AssetLocatorImpl() {
    }

    public static AssetLocator getInstance() {
        return s_instance;
    }

    public Collection<String> getAssets() {
        return m_assets.keySet();
    }

    public Collection<AssetResource> getResources(final String assetName) {
        return m_assets.get(assetName);
    }

    public AssetResource getResource(final String assetName, final String type) {
        return m_assets.get(assetName).parallelStream().filter(resource -> {
            return type.equals(resource.getType());
        }).findFirst().orElse(null);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        m_executor.scheduleAtFixedRate(() -> {
            reload();
        }, 0, 5, TimeUnit.MINUTES);
        // make it easier to reach from JSP pages
        s_instance = this;
    }

    @Override
    public void reload() {
        try {
            final Map<String,List<AssetResource>> newAssets = new HashMap<>();
            byte[] bdata = null;

            final String filesystemPath = System.getProperty("org.opennms.web.assets.path");
            if (filesystemPath != null) {
                final Path p = Paths.get(filesystemPath).resolve(s_useMinified? "assets.min.json" : "assets.json");
                if (p.toFile().exists()) {
                    LOG.info("Loading asset data from {}", p);
                    bdata = FileCopyUtils.copyToByteArray(p.toFile());
                }
            }
            if (bdata == null) {
                LOG.info("Loading asset data from the classpath.");
                final ClassPathResource cpr = new ClassPathResource(s_useMinified? "/assets/assets.min.json" : "/assets/assets.json");
                bdata = FileCopyUtils.copyToByteArray(cpr.getInputStream());
            }

            final String json = new String(bdata, StandardCharsets.UTF_8);
            final JSONObject assetsObj = new JSONObject(json);
            final JSONArray names = assetsObj.names();
            for (int i=0; i < names.length(); i++) {
                final String assetName = names.getString(i);
                final JSONObject assetObj = assetsObj.getJSONObject(assetName);
                final List<AssetResource> assets = new ArrayList<>(assetObj.length());
                final JSONArray keys = assetObj.names();
                for (int j=0; j < keys.length(); j++) {
                    final String type = keys.getString(j);
                    final String path = assetObj.getString(type);
                    assets.add(new AssetResource(assetName, type, path));
                }
                if (assetObj.length() > 0) {
                    newAssets.put(assetName, assets);
                }
            }
            m_assets = newAssets;
        } catch (final IOException e) {
            LOG.warn("Failed to load asset manifest.", e);
        }
    }
}
