/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;


/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 */
final class ProcessingSequenceBarrier implements SequenceBarrier
{
    private final WaitStrategy waitStrategy; // 等待策略
    private final Sequence dependentSequence; // 依赖sequence
    private volatile boolean alerted = false;
    private final Sequence cursorSequence; // 消费定位sequence
    private final Sequencer sequencer; // 生产者sequencer

    ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences)
    {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        // cursorSequence其实持有的是AbstractSequencer的成员变量cursor实例的引用
        this.cursorSequence = cursorSequence;
        // 如果消费者前面依赖的其他消费者链长度为0，其实就是只有一个消费者的情况下
        // 那么dependentsequence其实就是生产者的cursor游标
        if (0 == dependentSequences.length)
        {
            dependentSequence = cursorSequence;
        }
        // 如果消费者前面还有被依赖的消费者，那么dependentsequence就是前面消费者的sequence
        // 如果是指定执行顺序链的会执行到这里，比如 disruptor.after(eventHandler1).handleEventswith(eventHandler2):
        else
        {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException
    {
        checkAlert();
        // 获取可用的序列，这里返回的是Sequencer#next方法设置成功的可用下标，不是Sequencer#publish
        // cursorSequence：生产者的最大可用序列
        // dependentSequence：依赖的消费者的最大可用序列
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        if (availableSequence < sequence)
        {
            return availableSequence;
        }
        // 获取最大的已发布成功的序号（对于发布是否成功的校验在此方法中）
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}