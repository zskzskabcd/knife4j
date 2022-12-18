/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.github.xiaoymin.knife4j.datasource;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.xiaoymin.knife4j.common.lang.ConfigMode;
import com.github.xiaoymin.knife4j.common.lang.DesktopConstants;
import com.github.xiaoymin.knife4j.common.utils.PropertyUtils;
import com.github.xiaoymin.knife4j.datasource.config.ConfigDataProvider;
import com.github.xiaoymin.knife4j.datasource.model.ConfigMeta;
import com.github.xiaoymin.knife4j.datasource.model.ServiceDocument;
import com.github.xiaoymin.knife4j.datasource.model.config.common.ConfigEnv;
import com.github.xiaoymin.knife4j.datasource.model.config.common.ConfigInfo;
import com.github.xiaoymin.knife4j.datasource.service.ServiceDataProvider;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author <a href="xiaoymin@foxmail.com">xiaoymin@foxmail.com</a>
 * 2022/12/15 21:34
 * @since:knife4j-desktop
 */
@Slf4j
public class ConfigDataProviderHolder implements BeanFactoryAware, EnvironmentAware, InitializingBean, DisposableBean {
    
    private final DocumentSessionHolder sessionHolder;
    
    private Environment environment;
    private BeanFactory beanFactory;
    private ConfigDataProvider configDataProvider;
    private Thread thread;
    private volatile boolean stop = false;
    
    public ConfigDataProviderHolder(DocumentSessionHolder sessionHolder) {
        this.sessionHolder = sessionHolder;
    }
    
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
    
    @Override
    public void afterPropertiesSet() {
        try {
            ApplicationArguments applicationArguments = this.beanFactory.getBean(ApplicationArguments.class);
            Set<String> optionNames = applicationArguments.getOptionNames();
            Map<String, String> params = new HashMap<>();
            for (String key : optionNames) {
                String value=this.environment.getProperty(key);
                log.info("Args -> {}:{}",key,value);
                params.put(key, value);
            }
            String source = this.environment.getProperty(DesktopConstants.DESKTOP_SOURCE_KEY);
            ConfigMode configMode = ConfigMode.config(source);
            log.info("Config mode:{}", configMode);
            // 回调配置
            Optional<ConfigEnv> configEnvOptional = PropertyUtils.resolveSingle(params, ConfigEnv.class);
            ConfigInfo configInfo = configEnvOptional.isPresent() ? configEnvOptional.get().getKnife4j() : ConfigInfo.defaultConfig();

            //bean 注入
            Class<? extends ConfigDataProvider> clazz=configMode.getConfigClazz();
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
            builder.setRole(BeanDefinition.ROLE_SUPPORT);
            builder.setPrimary(true);
            Constructor constructor=clazz.getConstructor(ConfigInfo.class);
            if (constructor!=null){
                builder.addConstructorArgValue(configInfo);
            }
            DefaultListableBeanFactory beanRegistry = (DefaultListableBeanFactory) beanFactory;
            String beanName = configMode.getValue() + DesktopConstants.CONFIG_SERVICE_NAME;
            beanRegistry.registerBeanDefinition(beanName, builder.getBeanDefinition());
            // callback
            ConfigDataProvider configDataProvider = beanRegistry.getBean(beanName, ConfigDataProvider.class);
            this.configDataProvider = configDataProvider;
            this.start();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            // throw new RuntimeException(e);
        }
    }
    
    public void start() {
        log.info("Start Config monitor thread.");
        thread = new Thread(() -> {
            while (!stop) {
                try {
                    List<? extends ConfigMeta> configRoutes = this.configDataProvider.getConfig();
                    List<String> documentIds = new ArrayList<>();
                    if (CollectionUtil.isNotEmpty(configRoutes)) {
                        for (ConfigMeta configMeta : configRoutes) {
                            Optional<ServiceDataProvider> providerOptional = this.sessionHolder.getServiceProvider(configMeta.serviceDataProvider());
                            ServiceDataProvider serviceDataProvider = null;
                            if (providerOptional.isPresent()) {
                                serviceDataProvider = providerOptional.get();
                            } else {
                                serviceDataProvider = (ServiceDataProvider) ReflectUtil.newInstance(configMeta.serviceDataProvider());
                                this.sessionHolder.addServiceProvider(configMeta.serviceDataProvider(), serviceDataProvider);
                            }
                            ServiceDocument serviceDocument = serviceDataProvider.getDocument(configMeta);
                            if (serviceDocument != null) {
                                documentIds.add(serviceDocument.getContextPath());
                                Optional<ServiceDocument> documentOptional = this.sessionHolder.getContext(serviceDocument.getContextPath());
                                if (documentOptional.isPresent()) {
                                    ServiceDocument cacheDocument = documentOptional.get();
                                    // 对比,无变化
                                    if (!StrUtil.equalsIgnoreCase(serviceDocument.contextId(), cacheDocument.contextId())) {
                                        log.info("document has changed，context-path:{}", serviceDocument.getContextPath());
                                        this.sessionHolder.addContext(serviceDocument);
                                    }
                                } else {
                                    this.sessionHolder.addContext(serviceDocument);
                                }
                            }
                            // log.info("config:{}", DesktopConstants.GSON.toJson(serviceDocument));
                        }
                    }
                    // 清理
                    this.sessionHolder.clearContext(documentIds);
                } catch (Exception e) {
                    log.debug(e.getMessage(), e);
                }
                ThreadUtil.sleep(10000L);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }


    @SneakyThrows
    @Override
    public void destroy() {
        log.info("stop Config Provider Holder thread.");
        this.stop = true;
        if (thread != null) {
            ThreadUtil.interrupt(thread, true);
        }
    }
}