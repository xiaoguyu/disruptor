package com.lmax.disruptor.test;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.lmax.disruptor.util.Util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @description:
 * @title: MainTest
 * @date 2023/3/28 16:24
 */
public class MainTest {

    public static void main(String[] args) throws Exception {

        Logger.getGlobal().setLevel(Level.ALL);

        // 队列中的元素
        class LongEvent {
            private long value;

            public void set(long value) {
                this.value = value;
            }

            @Override
            public String toString() {
                return "LongEvent{" + "value=" + value + '}';
            }
        }

        // 指定RingBuffer的大小
        int bufferSize = 8;

        // RingBuffer生产工厂,初始化RingBuffer的时候使用
        EventFactory<LongEvent> factory = LongEvent::new;
        Disruptor<LongEvent> disruptor = new Disruptor<>(factory, bufferSize, DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, new BlockingWaitStrategy());

        // 设置EventHandler
        EventHandler<LongEvent> firstHandler = (event, sequence, endOfBatch) -> {
            System.out.println("Event: " + event);
            Thread.sleep(1000);
        };
        EventHandler<LongEvent> cleanHandler = (event, sequence, endOfBatch) -> event.set(0);
        disruptor.handleEventsWith(firstHandler);
        disruptor.after(firstHandler)
                // clean handler
                .then(cleanHandler);
        disruptor.start();


        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        /*EventTranslatorOneArg<LongEvent, ByteBuffer> translator = new EventTranslatorOneArg<LongEvent, ByteBuffer>() {
            @Override
            public void translateTo(LongEvent event, long sequence, ByteBuffer buffer) {
                event.set(buffer.getLong(0));
            }
        };

        ByteBuffer bb = ByteBuffer.allocate(8);
        for (long l = 0; true; l++) {
            bb.putLong(0, l);
            // 发布事件
            ringBuffer.publishEvent(translator, bb);
            Thread.sleep(1000);
        }*/

        for (long l = 0; l < 100; l++) {
            // 获取下一个可用位置的下标
            long sequence = ringBuffer.next();
            try {
                // 返回可用位置的元素
                LongEvent event = ringBuffer.get(sequence);
                // 设置该位置元素的值
                event.set(l);
            } finally {
                ringBuffer.publish(sequence);
            }
//            Thread.sleep(100);
        }

        // 获取下一个可用位置的下标
        long sequence = ringBuffer.next(2);
        for (long i =  sequence-1; i <= sequence; i++) {
            try {
                // 返回可用位置的元素
                LongEvent event = ringBuffer.get(i);
                // 设置该位置元素的值
                event.set(1);
            } finally {
                ringBuffer.publish(i);
            }
        }

    }

    public static void main2(String[] args) {
        System.out.println(Util.log2(8));
    }
}
