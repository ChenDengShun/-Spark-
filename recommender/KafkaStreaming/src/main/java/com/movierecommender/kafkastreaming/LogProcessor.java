package com.movierecommender.kafkastreaming;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 * @创建人 陈灯顺
 * @创建日期 2020/12/4
 * @描述
 */
public class LogProcessor implements Processor<byte[],byte[]> {
    private ProcessorContext context;
    @Override
    public void init(ProcessorContext context) {
        this.context=context;
    }

    @Override
    public void process(byte[] key, byte[] value) {
        String input=new String(value);
        //根据前缀过滤日志信息，提取后面的内容
        if(input.contains("MOVIE_RATING_PREFIX:")){
           // System.out.println("movie rating coming!!!!"+input);
            input=input.split("MOVIE_RATING_PREFIX:")[1].trim();
            context.forward("logProcessor".getBytes(),input.getBytes());
        }
    }

    @Override
    public void punctuate(long l) {

    }

    @Override
    public void close() {

    }
}
