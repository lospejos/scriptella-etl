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
package scriptella.tools.launcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import scriptella.configuration.ConfigurationEl;
import scriptella.configuration.ConfigurationFactory;
import scriptella.execution.EtlExecutor;
import scriptella.execution.EtlExecutorException;
import scriptella.execution.ExecutionStatistics;
import scriptella.execution.JmxEtlManager;
import scriptella.interactive.ConsoleProgressIndicator;
import scriptella.interactive.LoggingConfigurer;
import scriptella.interactive.ProgressIndicator;
import scriptella.spi.AbstractScriptellaDriver;
import scriptella.tools.template.TemplateManager;
import scriptella.util.CollectionUtils;
import scriptella.util.IOUtils;
import scriptella.util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Command line launcher.
 *
 * @author Fyodor Kupolov
 * @version 1.0
 */
public class EtlLauncher {

    private static final Logger logger = LoggingConfigurer.getScriptellaLogger();

    private static final PrintStream out = System.out;
    private static final PrintStream err = System.err;

    /**
     * Error codes returned by the launcher.
     */
    public enum ErrorCode {
        OK(0), FAILED(1), FILE_NOT_FOUND(2), UNRECOGNIZED_OPTION(3);

        ErrorCode(int code) {
            errorCode = code;
        }

        private final int errorCode;

        public int getErrorCode() {
            return errorCode;
        }
    }

    public static final ConsoleAppender<LoggingEvent> STD_FORMATTER = new ConsoleAppender<>() {
        private final MessageFormat f = new MessageFormat("{0,date} {0,time} <{1}> {2}");
        private final Object[] args = new Object[3]; //arguments for formatter
        private final StringBuffer sb = new StringBuffer();
        private final Date d = new Date();

        @Override
        protected void append(LoggingEvent record) {
            d.setTime(record.getTimeStamp());
            args[0] = d;
            args[1] = record.getLevel().levelStr;
            args[2] = record.getMessage();

            f.format(args, sb, null);
            final IThrowableProxy err = record.getThrowableProxy();
            sb.append('\n');
            if (err != null) {
                sb.append(err.getMessage());
                sb.append('\n');
            }
            final String s = sb.toString();
            sb.setLength(0);

            super.append(record);
        }

    };

    private EtlExecutor exec = new EtlExecutor();
    private ConfigurationFactory factory = new ConfigurationFactory();
    private ProgressIndicator indicator;
    private Map<String, ?> properties;
    public static final String DEFAULT_FILE_NAME = "etl.xml";

    public EtlLauncher() {
        exec.setJmxEnabled(false); //JMX monitoring is always disable when launcher is used
    }

    /**
     * @param suppressStatistics true if statistics must be suppressed.
     * @see scriptella.execution.EtlExecutor#setSuppressStatistics(boolean)
     */
    public void setNoStat(boolean suppressStatistics) {
        exec.setSuppressStatistics(suppressStatistics);
    }

    /**
     * Suppress JMX MBean registration for monitoring.
     *
     * @param enable true if JMX should be suppressed.
     */
    public void enabledJmx(boolean enable) {
        exec.setJmxEnabled(enable);
    }

    /**
     * Launches ETL script using command line arguments.
     *
     * @param args command line arguments.
     * @return exit error code.
     * @see System#exit(int)
     */
    ErrorCode launch(String[] args) {
        boolean failed = false;
        List<File> files = new ArrayList<>();
        ConsoleProgressIndicator indicator = new ConsoleProgressIndicator("Execution Progress");

        try {
            List<String> arguments = new ArrayList<>(Arrays.asList(args));
            while (!arguments.isEmpty()) {
                String arg = arguments.get(0);
                arguments.remove(0);
                if (arg.startsWith("-h")) {
                    printUsage();
                    return ErrorCode.OK;
                }
                if (arg.startsWith("-d")) {
                    logger.setLevel(Level.DEBUG);
                    continue;
                }
                if (arg.startsWith("-q")) {
                    logger.setLevel(Level.WARN);
                    continue;
                }
                if (arg.startsWith("-v")) {
                    printVersion();
                    return ErrorCode.OK;
                }
                if (arg.startsWith("-t")) {
                    return template(arguments);
                }
                if (arg.startsWith("-nostat")) {
                    setNoStat(true);
                    continue;
                }
                if (arg.startsWith("-nojmx")) {
                    enabledJmx(false);
                    continue;
                }
                if (arg.startsWith("-")) {
                    err.println("Unrecognized option " + arg);
                    return ErrorCode.UNRECOGNIZED_OPTION;
                } else {
                    files.add(resolveFile(null, arg));
                }
            }

            if (files.isEmpty()) { //adding default name if no files specified
                files.add(resolveFile(null, null));
            }
        } catch (FileNotFoundException e) {
            err.println(e.getMessage());
            return ErrorCode.FILE_NOT_FOUND;
        }

        setProgressIndicator(indicator);

        if (properties == null) {
            setProperties(CollectionUtils.asMap(System.getProperties()));
        }
        for (File file : files) {
            try {
                execute(file);
            } catch (Exception e) {
                failed = true;
                logger.error("Script " + file + " execution failed.", e);
                if (BugReport.isPossibleBug(e)) {
                    logger.error(new BugReport(e).toString());
                } else if (logger.getLevel().toInt() < Level.INFO_INT) {
                    //Print stack trace of exception in debug mode
                    err.println("---------------Debug Stack Trace-----------------");
                    Throwable t = e.getCause() == null ? e : e.getCause();
                    t.printStackTrace();
                }
            }
        }
        return failed ? ErrorCode.FAILED : ErrorCode.OK;
    }

    protected void printVersion() {
        String v = AbstractScriptellaDriver.getScriptellaVersion();
        String p = AbstractScriptellaDriver.getScriptellaTitle();
        if (p != null && v != null) {
            out.println(p + " Version " + v);
        } else {
            out.println("Scriptella version information unavailable");
        }
    }

    protected void printUsage() {
        out.println("scriptella [-options] [<file 1> ... <file N>]");
        out.println("Options:");
        out.println("  -help,     -h        display help ");
        out.println("  -debug,    -d        print debugging information");
        out.println("  -quiet,    -q        be extra quiet");
        out.println("  -version,  -v        print version");
        out.println("  -nostat              Suppress statistics collecting. Improves performance");
        out.println("  -nojmx               Suppress JMX MBean registration");
        out.println("  -template, -t        creates an etl.xml template file in the current directory");
    }

    protected ErrorCode template(List<String> args) {
        try {
            String name = args.isEmpty() ? null : args.get(0);
            String props = null;
            if (name != null) { //if not an option - shift the argument
                args.remove(0);
                props = args.isEmpty() ? null : args.get(0);
            }
            TemplateManager.create(name, props);
        } catch (Exception e) {
            logger.error("Template generation failed", e);
            return ErrorCode.FAILED;
        }
        return ErrorCode.OK;
    }

    /**
     * Sets additional properties available for ETL.
     * <p>By default {@link System#getProperties()} is used.
     *
     * @param props properties map.
     */
    public void setProperties(final Map<String, ?> props) {
        properties = props;
    }

    public void setProgressIndicator(final ProgressIndicator indicator) {
        this.indicator = indicator;
    }

    public void execute(final File file) throws EtlExecutorException {
        try {
            factory.setResourceURL(IOUtils.toUrl(file));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Wrong file path " + file.getPath(), e);
        }

        factory.setExternalParameters(properties);
        final ConfigurationEl c = factory.createConfiguration();

        exec.setConfiguration(c);
        ExecutionStatistics st = exec.execute(indicator);
        if (logger.isInfoEnabled()) {
            if (!exec.isSuppressStatistics()) {
                logger.info("Execution statistics:\n" + st.toString());
            }
            logger.info("Successfully executed ETL file " + file);
        }
    }

    /**
     * Resolves ETL file using the following rule:
     * if specified file exists - it is returned, otherwise if file has no extension
     * <code>name</code>+&quot;.etl.xml&quot; file is checked for presence and returned.
     *
     * @param dir  parent directory, may be null.
     * @param name file name, may be null in this case default name is used.
     * @return resolved ETL file.
     * @throws FileNotFoundException if ETL file cannot be found.
     */
    public File resolveFile(File dir, String name) throws FileNotFoundException {
        File f;
        if (StringUtils.isEmpty(name)) {
            f = new File(dir, DEFAULT_FILE_NAME);
        } else {
            f = new File(dir, name);
            if (!isFile(f) && name.indexOf('.') < 0) { //not a file and no extension
                f = new File(dir, name + '.' + DEFAULT_FILE_NAME);
            }
        }
        if (!isFile(f)) {
            throw new FileNotFoundException("ETL file " + f + " was not found.");
        }

        return f.getAbsoluteFile();
    }

    /**
     * Overridable for testing.
     */
    protected boolean isFile(File file) {
        return file.isFile();
    }

    public static void main(final String[] args) {
        EtlLauncher launcher = new EtlLauncher();
        System.exit(launcher.launch(args).getErrorCode());
    }

    /**
     * Shutdown hook for ETL cancellation.
     */
    private static class EtlShutdownHook extends Thread {

        private static final EtlShutdownHook INSTANCE = new EtlShutdownHook();

        private EtlShutdownHook() {
            setName("ETL Cancellation Thread");
        }

        public void run() {
            //if any mbean present - inform user about cancellation
            if (!JmxEtlManager.findEtlMBeans().isEmpty()) {
                System.out.println("Cancelling ETL tasks and rolling back changes...");
            }
            //Cancel all ETL task, the findEtlMBeans result may be stale
            int i = JmxEtlManager.cancelAll();
            if (i > 1) {
                System.out.println(i + " ETL tasks cancelled");
            } else if (i == 1) {
                System.out.println("ETL cancelled");
            }
        }

    }

    static {
        //Register a system shutdown hook which cancels all
        //in-progress ETL tasks on VM exit.
        try {
            Runtime.getRuntime().addShutdownHook(EtlShutdownHook.INSTANCE);
        } catch (Exception e) {
            logger.warn("Unable to add shutdown hook. ETL will not be rolled back on abnormal termination.", e);
        }
    }

}
