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
package scriptella.core;

import scriptella.configuration.ConfigurationException;
import scriptella.configuration.ConnectionEl;
import scriptella.execution.EtlContext;
import scriptella.jdbc.JdbcConnection;
import scriptella.spi.Connection;
import scriptella.spi.ConnectionParameters;
import scriptella.spi.ScriptellaDriver;
import scriptella.util.UrlPathTokenizer;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: Add documentation
 *
 * @author Fyodor Kupolov
 * @version 1.0
 */
public class ConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionManager.class.getName());
    Connection connection;
    List<Connection> newConnections;
    private ScriptellaDriver driver;
    ConnectionParameters connectionParameters;

    public ConnectionManager(EtlContext ctx, ConnectionEl c) {
        //Obtains a classloader
        ClassLoader cl = getClass().getClassLoader();
        connectionParameters = new ConnectionParameters(c, ctx);
        try {
            driver = DriverFactory.getDriver(c.getDriver(), cl);
        } catch (ClassNotFoundException e) {
            if (c.getClasspath() != null) { //if classpath specified
                //Parse it and create a new classloader
                UrlPathTokenizer tok = new UrlPathTokenizer(ctx.getScriptFileURL());
                try {
                    final URL[] urls = tok.split(c.getClasspath());
                    if (urls.length > 0) {
                        cl = new DriverClassLoader(urls);
                    }
                    driver = DriverFactory.getDriver(c.getDriver(), cl);
                } catch (Exception ex) {
                    throw new ConfigurationException("Unable to parse classpath parameter for " + c, ex);
                }
            } else {
                throw new ConfigurationException("Driver class " + c.getDriver() + " not found for " + connectionParameters +
                        ".Please check if the class name is correct and required libraries available on classpath", e);
            }
        } catch (Exception e) {
            throw new ConfigurationException("Unable to initialize driver for " + connectionParameters + ":" + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        if (connection == null) {
            connection = driver.connect(connectionParameters);
            if (connection == null) {
                throw new ConfigurationException("Driver returned null connection for " + connectionParameters);
            }
        }

        return connection;
    }

    public Connection newConnection() {
        final Connection c = driver.connect(connectionParameters);
        if (c == null) {
            throw new ConfigurationException("Driver returned null connection for " + connectionParameters);
        }

        if (newConnections == null) {
            newConnections = new ArrayList<>();
        }

        newConnections.add(c);

        return c;
    }

    public void rollback() {
        for (Connection c : getAllConnections()) {
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Rolling back " + c);
                }
                if (c instanceof JdbcConnection) {
                    c.rollback();
                }
            } catch (UnsupportedOperationException e) {
                String msg = e.getMessage();
                LOG.warn("Unable to rollback transaction for connection " + c + (msg == null ? "" : ": " + msg));

            } catch (Exception e) {
                LOG.warn("Unable to rollback transaction for connection " + c, e);
            }
        }
    }

    public void commit() {
        for (Connection c : getAllConnections()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Commiting connection " + c);
            }
            c.commit();
        }
    }

    public void close() {
        for (Connection c : getAllConnections()) {
            if (c != null) {
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Closing " + c);
                    }
                    c.close();
                } catch (Exception e) {
                    LOG.error("Problem occured while trying to close connection " + c, e);
                }
            }
        }

        connection = null;
        newConnections = null;
        connectionParameters = null;
        driver = null;
    }

    /**
     * Returns number of executed statements by managed connections.
     */
    public long getExecutedStatementsCount() {
        long s = 0;
        if (connection != null) {
            s += connection.getExecutedStatementsCount();

        }
        if (newConnections != null) {
            for (Connection c : newConnections) {
                s += c.getExecutedStatementsCount();
            }
        }
        return s;
    }

    /**
     * @return connection and newtx connections
     */
    private List<Connection> getAllConnections() {
        List<Connection> cl = new ArrayList<>();

        if (newConnections != null) {
            cl.addAll(newConnections);
        }

        if (connection != null) {
            cl.add(connection);
        }
        return cl;
    }

}
