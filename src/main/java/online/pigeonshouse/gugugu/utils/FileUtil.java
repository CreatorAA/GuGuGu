package online.pigeonshouse.gugugu.utils;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION;

    private static final ThreadLocal<MessageDigest> SHA1_CTX = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unsupported", e);
        }
    });

    /**
     * 验证文件名是否有效
     *
     * @param name 文件名
     * @return 如果文件名有效返回true，否则返回false
     */
    public static boolean isValidFileName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        if (name.length() > 255) {
            return false;
        }

        if (name.matches(".*[<>:\"/\\\\|?*].*")) {
            return false;
        }

        if (name.endsWith(" ") || name.endsWith(".")) {
            return false;
        }

        String upperName = name.toUpperCase();
        String[] reserved = {"CON", "PRN", "AUX", "NUL",
                "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        for (String res : reserved) {
            if (upperName.equals(res) || upperName.startsWith(res + ".")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 生成安全的文件名，将不安全字符替换为$$
     *
     * @param name 原始文件名
     * @return 处理后的安全文件名
     */
    public static String safeFileName(@NonNull String name) {
        return UNSAFE.matcher(name).replaceAll("\\$\\$");
    }

    /**
     * 创建目录，如果目录不存在则创建
     *
     * @param path 要创建的目录路径
     */
    public static void createDirectory(@NonNull Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 创建文件，如果文件不存在则创建（包括父目录）
     *
     * @param path 要创建的文件路径
     */
    public static void createFile(@NonNull Path path) {
        try {
            if (Files.notExists(path)) {
                createDirectory(path.getParent());
                Files.createFile(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 计算文件的SHA-1哈希值
     *
     * @param filePath 文件路径
     * @return 文件的SHA-1哈希值（十六进制字符串）
     */
    public static String hashFile(@NonNull Path filePath) {
        MessageDigest md = SHA1_CTX.get();
        md.reset();

        try (InputStream is = Files.newInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {

            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = bis.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            return bytesToHex(md.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * 原子性地复制文件，确保操作的完整性
     *
     * @param src 源文件路径
     * @param dst 目标文件路径
     */
    @SneakyThrows
    public static void copyAtomic(Path src, Path dst) {
        Path tmp = dst.resolveSibling(dst.getFileName() + ".tmp");
        Files.createDirectories(dst.getParent());
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 原子性地复制目录，包括所有子文件和子目录
     *
     * @param source 源目录路径
     * @param target 目标目录路径
     */
    public static void copyDirectoryAtomic(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                copyAtomic(file, target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 并行压缩目录
     *
     * @param sourceDirPath   源目录路径
     * @param zipFilePath     目标ZIP文件路径
     * @param filter          文件过滤器
     * @param parallelism     并行度
     * @param maxByteCapacity 最大字节数限制
     */
    public static void compressDirectoryParallel(
            @NonNull Path sourceDirPath,
            @NonNull Path zipFilePath,
            List<String> filter,
            int parallelism,
            long maxByteCapacity
    ) throws IOException, InterruptedException {
        if (filter == null) filter = List.of();
        createFile(zipFilePath);
        try (ZipOutputStream zos = createZipStream(zipFilePath)) {
            ByteCapacityController controller = new ByteCapacityController(maxByteCapacity);
            BlockingQueue<ZipTask> taskQueue = new LinkedBlockingQueue<>();
            List<Path> largeFiles = Collections.synchronizedList(new ArrayList<>());

            ExecutorService consumers = startConsumers(zos, taskQueue, parallelism, controller);
            Thread producer = startProducer(sourceDirPath, filter, maxByteCapacity, controller, taskQueue, largeFiles, parallelism);
            producer.join();
            shutdownAndAwait(consumers);
            writeLargeFiles(sourceDirPath, largeFiles, zos);
        }
    }

    /**
     * 创建ZIP输出流
     *
     * @param zipFilePath ZIP文件路径
     * @return ZipOutputStream实例
     */
    private static ZipOutputStream createZipStream(Path zipFilePath) throws IOException {
        createDirectory(zipFilePath.getParent());
        OutputStream os = Files.newOutputStream(zipFilePath);
        BufferedOutputStream bos = new BufferedOutputStream(os);
        ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8);
        zos.setLevel(COMPRESSION_LEVEL);
        return zos;
    }

    /**
     * 启动消费者线程池
     *
     * @param zos         ZIP输出流
     * @param queue       任务队列
     * @param parallelism 并行度
     * @param controller  字节容量控制器
     * @return ExecutorService实例
     */
    private static ExecutorService startConsumers(
            ZipOutputStream zos,
            BlockingQueue<ZipTask> queue,
            int parallelism,
            ByteCapacityController controller
    ) {
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        for (int i = 0; i < parallelism; i++) {
            pool.submit(() -> consumeQueue(zos, queue, controller));
        }
        return pool;
    }

    /**
     * 消费任务队列
     *
     * @param zos        ZIP输出流
     * @param queue      任务队列
     * @param controller 字节容量控制器
     */
    private static void consumeQueue(
            ZipOutputStream zos,
            BlockingQueue<ZipTask> queue,
            ByteCapacityController controller
    ) {
        try {
            while (true) {
                ZipTask task = queue.take();
                if (task == ZipTask.POISON_TASK) {
                    queue.put(task);
                    break;
                }
                writeTask(zos, task, controller);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 写入ZIP任务
     *
     * @param zos        ZIP输出流
     * @param task       ZIP任务
     * @param controller 字节容量控制器
     */
    private static void writeTask(
            ZipOutputStream zos,
            ZipTask task,
            ByteCapacityController controller
    ) throws IOException, InterruptedException {
        synchronized (zos) {
            zos.putNextEntry(new ZipEntry(task.getEntryName()));
            ChunkData chunk;
            while ((chunk = task.chunks.take()) != ChunkData.POISON_CHUNK) {
                zos.write(chunk.data);
                controller.release(chunk.data.length);
            }
            zos.closeEntry();
        }
    }

    /**
     * 启动生产者线程
     *
     * @param sourceDir   源目录
     * @param filter      文件过滤器
     * @param maxBytes    最大字节数
     * @param controller  字节容量控制器
     * @param queue       任务队列
     * @param largeFiles  大文件列表
     * @param parallelism 并行度
     * @return 生产者线程
     */
    private static Thread startProducer(
            Path sourceDir,
            List<String> filter,
            long maxBytes,
            ByteCapacityController controller,
            BlockingQueue<ZipTask> queue,
            List<Path> largeFiles,
            int parallelism
    ) {
        Thread producer = new Thread(() -> {
            try {
                Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryName = normalizeEntryName(sourceDir, file);
                        if (shouldFilter(entryName, filter)) {
                            return FileVisitResult.CONTINUE;
                        }
                        long size = attrs.size();
                        if (size > maxBytes / 2) {
                            largeFiles.add(file);
                        } else {
                            try {
                                enqueueFile(queue, controller, entryName, file);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                for (int i = 0; i < parallelism; i++) {
                    try {
                        queue.put(ZipTask.POISON_TASK);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        producer.start();
        return producer;
    }

    /**
     * 规范化ZIP条目名称
     *
     * @param base 基础路径
     * @param file 文件路径
     * @return 规范化的条目名称
     */
    private static String normalizeEntryName(Path base, Path file) {
        return base.relativize(file).toString().replace(File.separatorChar, '/');
    }

    /**
     * 检查文件是否应该被过滤
     *
     * @param entryName 条目名称
     * @param filter    过滤器列表
     * @return 是否应该被过滤
     */
    private static boolean shouldFilter(String entryName, List<String> filter) {
        return filter.stream().anyMatch(entryName::contains);
    }

    /**
     * 将文件加入压缩队列
     *
     * @param queue      任务队列
     * @param controller 字节容量控制器
     * @param entryName  条目名称
     * @param file       文件路径
     */
    private static void enqueueFile(
            BlockingQueue<ZipTask> queue,
            ByteCapacityController controller,
            String entryName,
            Path file
    ) throws InterruptedException, IOException {
        ZipTask task = new ZipTask(entryName, controller);
        queue.put(task);
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = bis.read(buf)) != -1) {
                task.write(Arrays.copyOf(buf, len));
            }
            task.complete();
        }
    }

    /**
     * 关闭并等待线程池终止
     *
     * @param pool 线程池
     */
    private static void shutdownAndAwait(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            pool.shutdownNow();
        }
    }

    /**
     * 写入大文件到ZIP
     *
     * @param sourceDir  源目录
     * @param largeFiles 大文件列表
     * @param zos        ZIP输出流
     */
    private static void writeLargeFiles(
            Path sourceDir,
            List<Path> largeFiles,
            ZipOutputStream zos
    ) throws IOException {
        for (Path file : largeFiles) {
            String entryName = normalizeEntryName(sourceDir, file);
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream is = Files.newInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int len;
                while ((len = bis.read(buf)) != -1) {
                    zos.write(buf, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    /**
     * 并行解压ZIP文件到目录
     *
     * @param zipFilePath ZIP文件路径
     * @param destPath    目标目录路径
     * @return 解压后的目录路径
     */
    public static Path unzipToDirectoryParallel(Path zipFilePath, Path destPath) throws IOException {
        int workers = Runtime.getRuntime().availableProcessors();
        try {
            return unzipToDirectoryParallel(zipFilePath, destPath, workers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Parallel unzip interrupted", e);
        }
    }

    /**
     * 并行解压ZIP文件到目录（指定并行度）
     *
     * @param zipFilePath ZIP文件路径
     * @param destPath    目标目录路径
     * @param parallelism 并行度
     * @return 解压后的目录路径
     */
    public static Path unzipToDirectoryParallel(Path zipFilePath, Path destPath, int parallelism) throws IOException, InterruptedException {
        createDirectory(destPath);
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            List<Future<?>> tasks = new ArrayList<>();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                tasks.add(pool.submit(() -> {
                    try {
                        Path entryPath = destPath.resolve(entry.getName()).normalize();
                        if (!entryPath.startsWith(destPath)) throw new IOException(entry.getName());
                        if (entry.isDirectory()) {
                            createDirectory(entryPath);
                        } else {
                            createDirectory(entryPath.getParent());
                            try (InputStream is = zipFile.getInputStream(entry);
                                 BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
                                 OutputStream os = Files.newOutputStream(entryPath);
                                 BufferedOutputStream bos = new BufferedOutputStream(os, BUFFER_SIZE)) {
                                byte[] buffer = new byte[BUFFER_SIZE];
                                int read;
                                while ((read = bis.read(buffer)) != -1) bos.write(buffer, 0, read);
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
            }

            for (Future<?> f : tasks) f.get();
        } catch (ExecutionException e) {
            throw new IOException("Failed during parallel unzip", e.getCause());
        } finally {
            pool.shutdown();
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }
        return destPath;
    }

    /**
     * 删除目录及其所有内容
     *
     * @param dir 要删除的目录路径
     */
    public static void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 字节容量控制器，用于控制内存使用
     */
    private static class ByteCapacityController {
        private final long maxBytes;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private long currentBytes = 0;

        public ByteCapacityController(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        /**
         * 获取指定字节的容量
         *
         * @param bytes 要获取的字节数
         */
        public void acquire(long bytes) throws InterruptedException {
            lock.lock();
            try {
                while (currentBytes + bytes > maxBytes) {
                    notFull.await();
                }
                currentBytes += bytes;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 释放指定字节的容量
         *
         * @param bytes 要释放的字节数
         */
        public void release(long bytes) {
            lock.lock();
            try {
                currentBytes -= bytes;
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * ZIP任务类，用于压缩单个文件
     */
    private static class ZipTask {
        static final ZipTask POISON_TASK = new ZipTask(null, null);
        @Getter
        private final String entryName;
        private final ByteCapacityController controller;
        private final BlockingQueue<ChunkData> chunks = new LinkedBlockingQueue<>();

        public ZipTask(String entryName, ByteCapacityController controller) {
            this.entryName = entryName;
            this.controller = controller;
        }

        /**
         * 写入数据块
         *
         * @param data 数据块
         */
        public void write(byte[] data) throws InterruptedException {
            controller.acquire(data.length);
            chunks.put(new ChunkData(data));
        }

        /**
         * 完成任务
         */
        public void complete() throws InterruptedException {
            chunks.put(ChunkData.POISON_CHUNK);
        }
    }

    /**
     * 数据块记录类
     */
    private record ChunkData(byte[] data) {
        static final ChunkData POISON_CHUNK = new ChunkData(null);
    }
}
