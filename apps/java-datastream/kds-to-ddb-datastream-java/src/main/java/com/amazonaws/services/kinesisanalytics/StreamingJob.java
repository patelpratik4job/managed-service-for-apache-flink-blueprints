/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Apache-2.0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

 package com.amazonaws.services.kinesisanalytics;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.kinesisanalytics.stock.Stock;
import com.amazonaws.services.kinesisanalytics.stock.StockDeserializationSchema;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.DeserializationSchema;

import org.apache.flink.connector.base.sink.writer.ElementConverter;
import org.apache.flink.connector.dynamodb.sink.DynamoDbSink;
import org.apache.flink.connector.dynamodb.sink.DynamoDbWriteRequest;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.*;

import static java.util.Collections.singletonList;

public class StreamingJob {

	private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

	private static final String KINESIS_STREAM_NAME = "StreamName";
	private static final String AWS_REGION = "AWSRegion";
	private static final String STREAM_INITIAL_POSITION = "StreamInitialPosition";
	private static final String SINK_PARALLELISM_KEY = "SinkParallelism";
	private static final String FLINK_APPLICATION_PROPERTIES = "BlueprintMetadata";

	private static final String DDB_TABLE_NAME = "DDBTableName";

	private static Properties getAppProperties() throws IOException {
		// note: this won't work when running locally
		Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
		Properties flinkProperties = applicationProperties.get(FLINK_APPLICATION_PROPERTIES);

		if(flinkProperties == null) {
			LOG.error("Unable to retrieve " + FLINK_APPLICATION_PROPERTIES + "; please ensure that you've " +
					"supplied them via application properties.");
			return null;
		}

		if(!flinkProperties.containsKey(KINESIS_STREAM_NAME)) {
			LOG.error("Unable to retrieve property: " + KINESIS_STREAM_NAME);
			return null;
		}

		if(!flinkProperties.containsKey(AWS_REGION)) {
			LOG.error("Unable to retrieve property: " + AWS_REGION);
			return null;
		}

		if(!flinkProperties.containsKey(STREAM_INITIAL_POSITION)) {
			LOG.error("Unable to retrieve property: " + STREAM_INITIAL_POSITION);
			return null;
		}

		if(!flinkProperties.containsKey(DDB_TABLE_NAME)) {
			LOG.error("Unable to retrieve property: " + DDB_TABLE_NAME);
			return null;
		}

		return flinkProperties;
	}

	private static boolean isLocal(StreamExecutionEnvironment env) {
		return env instanceof LocalStreamEnvironment;
	}

	private static FlinkKinesisConsumer<Stock> getKinesisSource(StreamExecutionEnvironment env,
																Properties appProperties) {

		String streamName = "myKinesisStream";
		String regionStr = "us-east-1";
		String streamInitPos = "LATEST";

		if(!isLocal(env)) {
			streamName = appProperties.get(KINESIS_STREAM_NAME).toString();
			regionStr = appProperties.get(AWS_REGION).toString();
			streamInitPos = appProperties.get(STREAM_INITIAL_POSITION).toString();
		}

		Properties consumerConfig = new Properties();
		consumerConfig.put(AWSConfigConstants.AWS_REGION, regionStr);
		consumerConfig.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, streamInitPos);
		// Default is POLLING, but we're specifying it explicitly here.
		consumerConfig.put(ConsumerConfigConstants.RECORD_PUBLISHER_TYPE,
						   ConsumerConfigConstants.RecordPublisherType.POLLING.name());

		DeserializationSchema<Stock> deserializationSchema = new StockDeserializationSchema();

		FlinkKinesisConsumer<Stock> kinesisStockSource = new FlinkKinesisConsumer<>(streamName,
																					deserializationSchema,
																					consumerConfig);
		return kinesisStockSource;
	}

	private static void runAppWithKinesisSource(StreamExecutionEnvironment env,
												Properties appProperties) {
		// Source
		FlinkKinesisConsumer<Stock> stockSource = getKinesisSource(env, appProperties);
		DataStream<Stock> stockStream = env.addSource(stockSource, "Kinesis source");

		// Do your processing here.
		// We've included a simple transform, but you can replace this with your own
		// custom processing. Please see the official Apache Flink docs:
		// https://nightlies.apache.org/flink/flink-docs-stable/
		// for more ideas.
		stockStream.flatMap((FlatMapFunction<Stock, Stock>) (stock, out) -> {
			// Filter out stocks with price higher than 1
			if(stock.getPrice() >= 1) {
				out.collect(stock);
			}
		}).returns(Stock.class);

		Properties sinkProperties = new Properties();
		sinkProperties.put(AWSConfigConstants.AWS_REGION, appProperties.get(AWS_REGION).toString());

		ElementConverter<Stock, DynamoDbWriteRequest> elementConverter = new CustomElementConverter();

		DynamoDbSink<Stock> dynamoDbSink =
				DynamoDbSink.<Stock>builder()
						.setDynamoDbProperties(sinkProperties)              				// Required
						.setTableName(appProperties.get(DDB_TABLE_NAME).toString())         // Required
						.setElementConverter(elementConverter)              				// Required
						.setOverwriteByPartitionKeys(singletonList("ticker"))  				// Optional
						.setFailOnError(false)                              				// Optional
						.setMaxBatchSize(25)                                				// Optional
						.setMaxInFlightRequests(50)                         				// Optional
						.setMaxBufferedRequests(10_000)                     				// Optional
						.setMaxTimeInBufferMS(5000)                         				// Optional
						.build();

		DataStreamSink<Stock> sink = stockStream.sinkTo(dynamoDbSink);

		if(!isLocal(env) && appProperties.containsKey(SINK_PARALLELISM_KEY)) {
			int sinkParallelism = Integer.parseInt(appProperties.get(SINK_PARALLELISM_KEY).toString());

			sink.setParallelism(sinkParallelism);
		}
	}

	public static void main(String[] args) throws Exception {
		// Set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setRuntimeMode(RuntimeExecutionMode.STREAMING);

		// Only for local
		// Configure via MSF when running in cloud
		if(isLocal(env)) {
			env.enableCheckpointing(2000);

			env.setParallelism(2);
		}

		Properties appProperties = null;
		if(!isLocal(env)) {
			appProperties = getAppProperties();
			if(appProperties == null) {
				LOG.error("Incorrectly specified application properties. Exiting...");
				return;
			}
		}

		runAppWithKinesisSource(env, appProperties);

		// execute program
		env.execute("Kinesis Data Streams to DDB Flink Streaming App");
	} // main
} // class