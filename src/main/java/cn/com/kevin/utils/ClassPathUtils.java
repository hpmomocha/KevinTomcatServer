package cn.com.kevin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class ClassPathUtils {
    static final Logger logger = LoggerFactory.getLogger(ClassPathUtils.class);

    public static byte[] readBytes(String path) {
        // 读入 resources 目录下的文件
        // path 不以’/’开头时默认是从此类所在的包下取资源，以’/’开头则是从ClassPath根下获取。
        try (InputStream input = ClassPathUtils.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readString(String path) {
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }
}
