package com.jfeng.pan.core.utils;

import cn.hutool.core.date.DateUtil;
import com.jfeng.pan.core.constants.RPanConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;

/**
 * 文件相关工具类
 */
public class FileUtil {

    /**
     * 获取文件后缀工具类
     * @param filename
     * @return
     */
    public static String getFileSuffix(String filename){
        if (StringUtils.isBlank(filename) || !filename.contains(RPanConstants.POINT_STR)) {
            return StringUtils.EMPTY;
        }
        return filename.substring(filename.lastIndexOf(RPanConstants.POINT_STR));
    }

    /**
     * 通过文件大小转化展示名称
     * @param totalSize
     * @return
     */
    public static String byteCountToDisplaySize(Long totalSize) {
        if(Objects.isNull(totalSize)){
            return RPanConstants.EMPTY_STR;
        }

        // 定义文件大小单位
        final long ONE_KB = 1024L;
        final long ONE_MB = ONE_KB * 1024L;
        final long ONE_GB = ONE_MB * 1024L;
        final long ONE_TB = ONE_GB * 1024L;
        final long ONE_PB = ONE_TB * 1024L;

        DecimalFormat df = new DecimalFormat("#.##");

        if (totalSize < ONE_KB) {
            return totalSize + " B";
        } else if (totalSize < ONE_MB) {
            double kbSize = (double) totalSize / ONE_KB;
            return df.format(kbSize) + " KB";
        } else if (totalSize < ONE_GB) {
            double mbSize = (double) totalSize / ONE_MB;
            return df.format(mbSize) + " MB";
        } else if (totalSize < ONE_TB) {
            double gbSize = (double) totalSize / ONE_GB;
            return df.format(gbSize) + " GB";
        } else if (totalSize < ONE_PB) {
            double tbSize = (double) totalSize / ONE_TB;
            return df.format(tbSize) + " TB";
        } else {
            double pbSize = (double) totalSize / ONE_PB;
            return df.format(pbSize) + " PB";
        }

    }

    /**
     * 本地批量删除物理文件
     * @param realPathList
     */
    public static void deleteFiles(List<String> realPathList) {
        if(CollectionUtils.isEmpty(realPathList))
            return;
        for (String path : realPathList){
            cn.hutool.core.io.FileUtil.del(new File(path));
        }
    }

    /**
     * 生成文件的存储路径
     * 生成规则：基础路径 + 年 + 月 + 日 + 随机的名称规则
     * @param basePath
     * @param filename
     * @return
     */
    public static String generateStoreFileRealPath(String basePath, String filename) {
        return basePath +
                File.separator +
                DateUtil.thisYear() +
                File.separator +
                DateUtil.thisMonth() +
                File.separator +
                DateUtil.thisDayOfMonth() +
                File.separator +
                UUIDUtil.getUUID() +
                getFileSuffix(filename);
    }


    /**
     * 将文件的输入流写入到文件中
     * 使用Java底层 NIO实现
     * 调用sendfile零拷贝技术提高传输效率
     *
     * @param inputStream
     * @param targetFile
     * @param totalSize
     */
    public static void writeStream2File(InputStream inputStream, File targetFile, Long totalSize) throws IOException {
        createFile(targetFile);
        RandomAccessFile randomAccessFile = new RandomAccessFile(targetFile, "rw");
        FileChannel outputChannel = randomAccessFile.getChannel();
        ReadableByteChannel inputChanner = Channels.newChannel(inputStream);
        outputChannel.transferFrom(inputChanner, 0L, totalSize);
        inputChanner.close();
        outputChannel.close();
        randomAccessFile.close();

    }

    /**
     * 创建文件夹
     * 包含父文件一起视情况去创建
     *
     * @param targetFile
     */
    public static void createFile(File targetFile) throws IOException {
        if(!targetFile.getParentFile().exists()){
            targetFile.getParentFile().mkdirs();
        }
        targetFile.createNewFile();
    }

    /**
     * 生成默认的文件存储路径
     * 生成规则：当前登录用户的文件目录 + rpan
     *
     * @return
     */
    public static String generateDefaultStoreFileRealPath() {
       return System.getProperty("user.home") +
               File.separator +
               "RPan";

    }

    /**
     * 生成默认的文件分片的存储路径抢嘴
     * @return
     */
    public static String generateDefaultStoreFileChunkRealPath() {
        return System.getProperty("user.home") +
                File.separator +
                "RPan" +
                File.separator +
                "chunks";


    }

    /**
     * <p>
     *     <li>生成文件分片的存储路径</li>
     *     <li>生成规则：基础路径+年+月+日+唯一标识+随机的文件名称+__,__+文件分片下标</li>
     * </p>
     *
     * @param basePath
     * @param filename
     * @param identifier
     * @param chunkNumber
     * @return
     */
    public static String generateStoreFileChunkRealPath(String basePath, String filename, String identifier, Integer chunkNumber) {
        return basePath +
                File.separator +
                DateUtil.thisYear() +
                File.separator +
                DateUtil.thisMonth() +
                File.separator +
                DateUtil.thisDayOfMonth() +
                File.separator +
                identifier +
                File.separator +
                UUIDUtil.getUUID() +
                File.separator +
                RPanConstants.COMMON_SEPARATOR +
                File.separator +
                chunkNumber;
    }

    /**
     * 追加写文件
     *
     * @param target
     * @param source
     */
    public static void appendWrite(Path target, Path source) throws IOException {
        Files.write(target, Files.readAllBytes(source), StandardOpenOption.APPEND);
    }

    /**
     * <p>利用零拷贝技术读取文件内容并写入到文件的输出流中</p>
     *
     * @param fileInputStream
     * @param outputStream
     * @param length
     */
    public static void writeFile2OutputStream(FileInputStream fileInputStream, OutputStream outputStream, long length) throws IOException {
        FileChannel fileChannel = fileInputStream.getChannel();
        WritableByteChannel writableByteChannel = Channels.newChannel(outputStream);
        try {
            fileChannel.transferTo(RPanConstants.ZERO_LONG, length, writableByteChannel);
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            writableByteChannel.close();
            fileChannel.close();
            outputStream.close();
        }
    }

    /**
     * 普通的流对流的数据传输
     *
     * @param inputStream
     * @param outputStream
     */
    public static void writeStream2StreamNormal(InputStream inputStream, OutputStream outputStream) throws IOException {
       byte[] buffer = new byte[1024];
       int len;
       while ((len=inputStream.read(buffer)) != RPanConstants.MINUS_ONE_INT ){
           outputStream.write(buffer, RPanConstants.ZERO_INT, len);
       }
       outputStream.flush();
       inputStream.close();
       outputStream.close();
    }
}
