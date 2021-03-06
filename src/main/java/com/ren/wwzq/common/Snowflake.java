package com.ren.wwzq.common;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Random;

/**
 * @program: dd
 * @description: 雪花算法
 * @author: Yu
 * @create: 2018-08-01 17:43
 **/
@Slf4j
public class Snowflake {
    private static final long TWEPOCH = 1288834974657L;

    // 机器标识位数
    private static final long WORKER_ID_BITS = 5L;

    // 数据中心标识位数
    private static final long DATA_CENTER_ID_BITS = 5L;

    // 机器ID最大值 31
    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);

    // 数据中心ID最大值 31
    private static final long MAX_DATA_CENTER_ID = -1L ^ (-1L << DATA_CENTER_ID_BITS);

    // 毫秒内自增位
    private static final long SEQUENCE_BITS = 12L;

    // 机器ID偏左移12位
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    // 时间毫秒左移22位
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    private static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);

    private long lastTimestamp = -1L;

    private long sequence = 0L;
    private final long workerId;
    private final long dataCenterId;
    private static Snowflake snowflake = null;
    private static Object lock = new Object();

    private static Random random() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            log.error("随机数异常:{}", e);
        }
        return new Random();
    }

    public Snowflake(long workerId, long dataCenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            workerId = getRandom();
        }

        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {

            throw new IllegalArgumentException(String.format("%s 数据中心ID最大值 必须是 %d 到 %d 之间", dataCenterId, 0, MAX_DATA_CENTER_ID));
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    /**
     * 获取单列
     *
     * @return
     */
    public static Snowflake getInstanceSnowflake() {
        if (snowflake == null) {
            synchronized (lock) {
                long workerId;
                //= getRandom();
                long dataCenterId = getRandom();
                try {
                    //第一次使用获取mac地址的
                    workerId = getWorkerId();
                } catch (Exception e) {
                    workerId = getRandom();
                }
                snowflake = new Snowflake(workerId, dataCenterId);
            }
        }
        return snowflake;
    }

    /**
     * 生成1-31之间的随机数
     *
     * @return
     */
    private static long getRandom() {
        int max = (int) (MAX_WORKER_ID);
        int min = 1;
        Random random = new Random();
        long result = random.nextInt(max - min) + min + 0L;
        return result;
    }

    public synchronized long nextId() {
        long timestamp = time();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟向后移动，拒绝生成id  " + (lastTimestamp - timestamp) + " milliseconds");
        }

        if (lastTimestamp == timestamp) {
            // 当前毫秒内，则+1
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒内计数满了，则等待下一秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;

        // ID偏移组合生成最终的ID，并返回ID
        long nextId = ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;

        return nextId;
    }

    private long tilNextMillis(final long lastTimestamp) {
        long timestamp = this.time();
        while (timestamp <= lastTimestamp) {
            timestamp = this.time();
        }
        return timestamp;
    }

    private long time() {
        return System.currentTimeMillis();
    }

    @SuppressWarnings("Duplicates")
    private static long getWorkerId() throws SocketException, UnknownHostException, NullPointerException {
        @SuppressWarnings("unused")
        InetAddress ip = InetAddress.getLocalHost();

        NetworkInterface network = null;
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface nint = en.nextElement();
            if (!nint.isLoopback() && nint.getHardwareAddress() != null) {
                network = nint;
                break;
            }
        }

        if (null == network) {
            throw new RuntimeException("生成id异常");
        }

        @SuppressWarnings("ConstantConditions")
        byte[] mac = network.getHardwareAddress();

        Random rnd = new Random();
        byte rndByte = (byte) (rnd.nextInt() & 0x000000FF);

        // take the last byte of the MAC address and a random byte as worker ID
        return ((0x000000FF & (long) mac[mac.length - 1]) | (0x0000FF00 & (((long) rndByte) << 8))) >> 6;
    }
}