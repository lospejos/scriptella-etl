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

package scriptella;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;

import org.slf4j.LoggerFactory;

/**
 * Logging configurer which keeps messages sent to a specified logger.
 *
 * @author Fyodor Kupolov
 */
public class TestLoggingConfigurer {

    public static final int MAX_LOG_MESSAGEBUFFER_SIZE = 100000; //100 KB
    public final StringBuilder buf = new StringBuilder();

    private final String loggerName;
    private final Appender<ILoggingEvent> handler = new AppenderBase<>() {

        @Override
        protected void append(ILoggingEvent iLoggingEvent) {
            buf.append(iLoggingEvent.getLoggerName()).append('|')
                    .append(iLoggingEvent.getLevel()).append('|')
                    .append(iLoggingEvent.getMessage()).append('\n');
            if (buf.length() > MAX_LOG_MESSAGEBUFFER_SIZE) {
                buf.delete(0, buf.length() / 2);
            }
        }
    };

    public TestLoggingConfigurer(String loggerName) {
        this.loggerName = loggerName;
    }

    public void setUp() {
        final Logger logger = (Logger) LoggerFactory.getLogger(loggerName);

        handler.setContext(logger.getLoggerContext());
        handler.setName(loggerName);
        handler.start();

        logger.addAppender(handler);
    }

    public void tearDown() {
        final Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        logger.detachAppender(loggerName);
    }

    public int getMessageCount(String msg) {
        int count = 0;
        for (int nextIndex = 0; nextIndex >= 0; ) {
            nextIndex = buf.indexOf(msg, nextIndex);
            if (nextIndex >= 0) {
                nextIndex += msg.length();
                count++;
            }
        }
        return count;
    }

}
