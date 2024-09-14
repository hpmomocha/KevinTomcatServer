package cn.com.kevin;

import cn.com.kevin.classloader.Resource;
import cn.com.kevin.classloader.WebAppClassLoader;
import cn.com.kevin.connector.HttpConnector;
import cn.com.kevin.utils.ClassPathUtils;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.annotation.WebServlet;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public class Start {
    static Logger logger = LoggerFactory.getLogger(Start.class);

    public static void main(String[] args) throws Exception {
        String warFile = null;
        String customConfigPath = null;
        // Options 是 Commons CLI 中的类
        // Commons CLI 是一个用于 表示、处理、验证 命令行参数的 API。
        Options options = new Options();
        options.addOption(Option.builder("w").longOpt("war").argName("file").hasArg().desc("specify war file.").required().build());
        options.addOption(Option.builder("c").longOpt("config").argName("file").hasArg().desc("specify external configuration file.").build());

        try {
            var parser = new DefaultParser();
            // 解析 main 方法参数
            CommandLine cmd = parser.parse(options, args);
            warFile = cmd.getOptionValue("war");
            customConfigPath = cmd.getOptionValue("config");
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            var help = new HelpFormatter();
            var jarName = Path.of(Start.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString();
            help.printHelp("java -jar " + jarName + " [options]", options);
            System.exit(1);
            return;
        }

        // 获取war文件路径和自定义配置文件路径后启动
        new Start().start(warFile, customConfigPath);
    }

    public void start(String warFile, String customConfigPath) throws IOException {
        Path warPath = parseWarFile(warFile);

        // extract war if necessary:
        Path[] ps = extractWarIfNecessary(warPath);

        String webRoot = ps[0].getParent().getParent().toString();
        logger.info("set web root: {}", webRoot);

        // 默认配置文件路径
        String defaultConfigYaml = ClassPathUtils.readString("/server.yml");
        String customConfigYaml = null;

        if (customConfigPath != null) {
            logger.info("load external config {}...", customConfigPath);
            try {
                customConfigYaml = Files.readString(Paths.get(customConfigPath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.error("Could not read config: " + customConfigPath, e);
                System.exit(1);
                return;
            }
        }

        Config config;
        Config customConfig;
        try {
            // 读取默认配置文件的配置内容
            config = loadConfig(defaultConfigYaml);
        } catch (JacksonException e) {
            logger.error("Parse default config failed.", e);
            throw new RuntimeException(e);
        }

        if (customConfigYaml != null) {
            try {
                customConfig = loadConfig(customConfigYaml);
            } catch (JacksonException e) {
                logger.error("Parse custom config failed: " + customConfigPath, e);
                throw new RuntimeException(e);
            }
            // copy custom-config to default-config:
            try {
                merge(config, customConfig);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        // set classloader:
        var classLoader = new WebAppClassLoader(ps[0], ps[1]);

        // scan class:
        Set<Class<?>> classSet = new HashSet<>();
        Consumer<Resource> handler = (r) -> {
            if (r.name().endsWith(".class")) {
                String className = r.name().substring(0, r.name().length() - 6).replace('/', '.');
                if (className.endsWith("module-info") || className.endsWith("package-info")) {
                    return;
                }
                Class<?> clazz;
                try {
                    // JVM 加载类文件
                    clazz = classLoader.loadClass(className);
                } catch (ClassNotFoundException e) {
                    logger.warn("load class '{}' failed: {}: {}", className, e.getClass().getSimpleName(), e.getMessage());
                    return;
                } catch (NoClassDefFoundError err) {
                    logger.error("load class '{}' failed: {}: {}", className, err.getClass().getSimpleName(), err.getMessage());
                    return;
                }
                // 如果是 Servlet 类
                if (clazz.isAnnotationPresent(WebServlet.class)) {
                    logger.info("Found @WebServlet: {}", clazz.getName());
                    classSet.add(clazz);
                }
                // 如果是 Filter 类
                if (clazz.isAnnotationPresent(WebFilter.class)) {
                    logger.info("Found @WebFilter: {}", clazz.getName());
                    classSet.add(clazz);
                }
                // 如果是 Listener 类
                if (clazz.isAnnotationPresent(WebListener.class)) {
                    logger.info("Found @WebListener: {}", clazz.getName());
                    classSet.add(clazz);
                }
            }
        };
        classLoader.scanClassPath(handler);
        classLoader.scanJar(handler);
        List<Class<?>> autoScannedClasses = new ArrayList<>(classSet);

        // executor:
        if (config.server.enableVirtualThread) {
            logger.info("Virtual thread is enabled.");
        }
//        ExecutorService executor = config.server.enableVirtualThread ? Executors.newVirtualThreadPerTaskExecutor()
//                : new ThreadPoolExecutor(0, config.server.threadPoolSize, 0L, TimeUnit.MILLISECONDS,
//                new LinkedBlockingQueue<>());

        // 创建线程池
        ExecutorService executor = new ThreadPoolExecutor(
                0,
                config.server.threadPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        try (HttpConnector connector = new HttpConnector(
                config, webRoot, executor, classLoader, autoScannedClasses)) {
            for (;;) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        logger.info("jerrymouse http server was shutdown.");
    }

    // 读取 yaml 格式配置文件
    Config loadConfig(String config) throws JacksonException {
        var objectMapper = new ObjectMapper(new YAMLFactory()).setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(config, Config.class);
    }

    // return classes and lib path:
    Path[] extractWarIfNecessary(Path warPath) throws IOException {
        if (Files.isDirectory(warPath)) {
            logger.info("war is directy: {}", warPath);
            // resolve方法的主要用途在于连接两个路径
            Path classesPath = warPath.resolve("WEB-INF/classes");
            Path libPath = warPath.resolve("WEB-INF/lib");
            // 创建 classes 目录
            Files.createDirectories(classesPath);
            // 创建 lib 目录
            Files.createDirectories(libPath);
            return new Path[] { classesPath, libPath };
        }
        // 如果需要解压缩
        Path extractPath = createExtractTo();
        logger.info("extract '{}' to '{}'", warPath, extractPath);
        // The JarFile class is used to read the contents of a JAR file from any file that can be opened with java.io.RandomAccessFile.
        JarFile war = new JarFile(warPath.toFile());
        war.stream().sorted((e1, e2) -> e1.getName().compareTo(e2.getName())).forEach(entry -> {
            if (!entry.isDirectory()) {
                Path file = extractPath.resolve(entry.getName());
                Path dir = file.getParent();
                if (!Files.isDirectory(dir)) {
                    try {
                        Files.createDirectories(dir);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                try (InputStream in = war.getInputStream(entry)) {
                    Files.copy(in, file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        // check WEB-INF/classes and WEB-INF/lib:
        Path classesPath = extractPath.resolve("WEB-INF/classes");
        Path libPath = extractPath.resolve("WEB-INF/lib");
        Files.createDirectories(classesPath);
        Files.createDirectories(libPath);
        return new Path[] { classesPath, libPath };
    }

    Path parseWarFile(String warFile) {
        Path warPath = Path.of(warFile).toAbsolutePath().normalize();
        if (!Files.isRegularFile(warPath) && !Files.isDirectory(warPath)) {
            System.err.printf("war file '%s' was not found.\n", warFile);
            System.exit(1);
        }
        return warPath;
    }

    /**
     * 创建解压缩目录
     * @return
     * @throws IOException
     */
    Path createExtractTo() throws IOException {
        // 创建临时目录
        Path tmp = Files.createTempDirectory("_jm_");
        // 使用 Runtime.addShutdownHook(Thread hook) 方法，可以注册一个JVM关闭的钩子，
        // 这个钩子可以在以下几种场景被调用：
        // 1. 程序正常退出
        // 2. 使用System.exit()
        // 3. 终端使用Ctrl+C触发的中断
        // 4. 系统关闭
        // 5. 使用Kill pid命令干掉进程（kill -9 不会触发）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Delete temp dir: {} before web server is down.", tmp.toString());
                deleteDir(tmp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        return tmp;
    }

    void deleteDir(Path p) throws IOException {
        Files.list(p).forEach(c -> {
            try {
                if (Files.isDirectory(c)) {
                    deleteDir(c);
                } else {
                    Files.delete(c);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        Files.delete(p);
    }

    static void merge(Object source, Object override) throws ReflectiveOperationException {
        for (Field field : source.getClass().getFields()) {
            Object overrideFieldValue = field.get(override);
            if (overrideFieldValue != null) {
                Class<?> type = field.getType();
                if (type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)) {
                    // source.xyz = override.xyz:
                    field.set(source, overrideFieldValue);
                } else if (Map.class.isAssignableFrom(type)) {
                    // source.map.putAll(override.map):
                    @SuppressWarnings("unchecked")
                    Map<String, String> sourceMap = (Map<String, String>) field.get(source);
                    @SuppressWarnings("unchecked")
                    Map<String, String> overrideMap = (Map<String, String>) overrideFieldValue;
                    sourceMap.putAll(overrideMap);
                } else {
                    // merge(source.xyz, override.xyz):
                    merge(field.get(source), overrideFieldValue);
                }
            }
        }
    }
}
