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
package scriptella.interactive;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import org.slf4j.LoggerFactory;

/**
 * Scriptella runtime configurer for java.util.Logging.
 *
 * @author Fyodor Kupolov
 * @version 1.0
 */
public class LoggingConfigurer {

    private static final Level original = getScriptellaLogger().getLevel();

    private LoggingConfigurer() {
    }

    /**
     * Configures logging messages to use specified handler
     * @param handler to use.
     */
    public static void configure(Logger handler) {
        final Logger logger = getScriptellaLogger();
        logger.setLevel(handler.getLevel());
    }

    public static void reset() {
        getScriptellaLogger().setLevel(original);
    }

    public static Logger getScriptellaLogger() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }


}
