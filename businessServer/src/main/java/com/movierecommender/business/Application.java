package com.movierecommender.business;

import com.movierecommender.business.utils.Constant;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.ApplicationContext;

/**
 * @创建人 陈灯顺
 * @创建日期 2020/12/5
 * @描述
 */
public class Application {


    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        ApplicationContext context = new ClassPathXmlApplicationContext("classpath:application.xml");

        Settings settings = Settings.builder().put("cluster.name","esCluster").build();
        TransportClient esClient = new PreBuiltTransportClient(settings);
        esClient.addTransportAddress(new TransportAddress(InetAddress.getByName("192.168.1.101"), 9300));
        GetResponse getResponse = esClient.prepareGet(Constant.ES_INDEX,Constant.ES_MOVIE_TYPE,"3062").get();

//        Map<String,GetField> filed = getResponse.getFields();

        Object value = getResponse.getSourceAsMap().get("tags");

        if(value == null){
            UpdateRequest updateRequest = new UpdateRequest(Constant.ES_INDEX,Constant.ES_MOVIE_TYPE,"3062");
            updateRequest.doc(XContentFactory.jsonBuilder().startObject()
                    .field("tags","abc")
                    .endObject());
            esClient.update(updateRequest).get();
        }else{
            UpdateRequest updateRequest = new UpdateRequest(Constant.ES_INDEX,Constant.ES_MOVIE_TYPE,"2542");
            updateRequest.doc(XContentFactory.jsonBuilder().startObject()
                    .field("tags",value+"|abc")
                    .endObject());
            esClient.update(updateRequest).get();
        }

        System.out.println(Math.round(4.466D));

    }

}
