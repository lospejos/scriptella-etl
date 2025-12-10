/*
 * Copyright 2006-2012 The Scriptella Project Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scriptella.driver.spring;

import scriptella.configuration.ConfigurationFactory;
import scriptella.execution.EtlExecutor;
import scriptella.execution.EtlExecutorException;
import scriptella.execution.ExecutionStatistics;
import scriptella.interactive.ProgressIndicator;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;

/**
 * Implementation of {@link EtlExecutor} for Spring IoC container.
 * <p>This class exposes a set of configurable properties and provides
 * a {@link Callable} invocation interface to avoid dependency on Scriptella
 * in application code.
 *
 * @author Fyodor Kupolov
 * @version 1.0
 */
public class EtlExecutorBean extends EtlExecutor implements InitializingBean, BeanFactoryAware {

    private static Logger logger = LoggerFactory.getLogger(EtlExecutorBean.class);

    private static BeanFactory beanFactory;
    private ProgressIndicator progressIndicator;
    protected boolean autostart;
    private Map<String, ?> properties;
    private URL configLocation;


    /**
     * Creates scripts executor.
     */
    public EtlExecutorBean() {
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        logger.info("Received beanfactory: {} from context", beanFactory);
        EtlExecutorBean.beanFactory = beanFactory;
    }

    /**
     * Sets autostart property.
     *
     * @param autostart true if executor must be automatically runned after initialization.
     *                  Default value is <code>false</code>.
     */
    public void setAutostart(boolean autostart) {
        this.autostart = autostart;
    }

    /**
     * Sets progress indicator to use.
     * <p>By default no progress shown.
     *
     * @param progressIndicator progress indicator to use.
     */
    public void setProgressIndicator(ProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;
    }

    /**
     * Sets configuration location.
     *
     * @param resource configuration resource.
     */
    public void setConfigLocation(Resource resource) throws IOException {
        configLocation = resource.getURL();
    }

    public Map<String, ?> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, ?> properties) {
        this.properties = properties;
    }

    public void afterPropertiesSet() throws Exception {

        if (getConfiguration() == null) {
            if (autostart && configLocation == null) {
                throw new IllegalStateException("configLocation must be specified");
            }
            if (configLocation != null) { //Initialize configuration
                ConfigurationFactory cf = new ConfigurationFactory();
                cf.setResourceURL(configLocation);
                cf.setExternalParameters(properties);
                setConfiguration(cf.createConfiguration());
            }
        }
        if (autostart) {
            execute();
        }
    }

    /**
     * Simply calls {@link #execute()}.
     */
    public ExecutionStatistics call() throws EtlExecutorException {
        return execute();
    }


    @Override
    public ExecutionStatistics execute() throws EtlExecutorException {
        if (progressIndicator != null) {
            return execute(progressIndicator);
        } else {
            return super.execute();
        }
    }

    /**
     * Return the bean factory associated with the current thread.
     * @return bean factory associated with the current thread.
     */
    static synchronized BeanFactory getContextBeanFactory() {
        return beanFactory;
    }
}
