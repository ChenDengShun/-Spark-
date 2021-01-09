package com.movierecommender.kafkastreaming;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.apache.kafka.streams.processor.TopologyBuilder;

import java.util.Properties;

/**
 * @创建人 陈灯顺
 * @创建日期 2020/12/4
 * @描述
 */
public class Application {
    public static void main(String[] args) {
        String brokers="192.168.1.101:9092";

        //定义输入和输出的topic
        String from="log";
        String to="recommender";

        //定义kafka streaming的配置
        Properties settings=new Properties();
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG,"logFilter");
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,brokers);

        StreamsConfig config=new StreamsConfig(settings);
        //拓扑建构器
        TopologyBuilder builder=new TopologyBuilder();
        //定义流处理的拓扑结构
        builder.addSource("SOURCE",from)
                .addProcessor("PROCESS",new ProcessorSupplier<byte[],byte[]>(){

                    @Override
                    public Processor<byte[], byte[]> get() {
                        return new LogProcessor();
                    }
                },"SOURCE")
                .addSink("SINK",to,"PROCESS");
        KafkaStreams streams=new KafkaStreams(builder,config);
        streams.start();
    }
}

