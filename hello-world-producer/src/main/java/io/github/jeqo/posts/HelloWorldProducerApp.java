package io.github.jeqo.posts;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import io.github.jeqo.posts.infrastructure.KafkaHelloWorldProducer;
import io.github.jeqo.posts.resource.HelloWorldResource;
import io.opentracing.Tracer;
import io.opentracing.contrib.dropwizard.DropWizardTracer;
import io.opentracing.contrib.dropwizard.ServerTracingFeature;
import io.opentracing.contrib.kafka.TracingKafkaProducer;
import io.opentracing.util.GlobalTracer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;


/**
 * Dropwizard Application.
 */
public final class HelloWorldProducerApp extends Application<HelloWorldProducerConfig> {

  public static void main(String[] args) throws Exception {
    new HelloWorldProducerApp().run(args);
  }

  @Override
  public String getName() {
    return "hello-world-producer";
  }

  /**
   * Execute dropwizard application.
   *
   * @param config      Dropwizard configuration.
   * @param environment Dropwizard environment.
   * @throws Exception exception at runtime.
   */
  public void run(HelloWorldProducerConfig config, Environment environment)
      throws Exception {
    //Instantiate and register Tracer
    final Tracer tracer =
        new com.uber.jaeger.Configuration(
            getName(),
            new com.uber.jaeger.Configuration.SamplerConfiguration("const", 1),
            new com.uber.jaeger.Configuration.ReporterConfiguration(
                true,  // logSpans
                "localhost",
                6831,
                1000,   // flush interval in milliseconds
                10000)  /*max buffered Spans*/)
            .getTracer();
    GlobalTracer.register(tracer);
    final DropWizardTracer dropWizardTracer = new DropWizardTracer(tracer);
    environment.jersey()
        .register(
            new ServerTracingFeature.Builder(dropWizardTracer)
                .withTraceAnnotations()
                .build());


    //Define Kafka Producer Properties
    final Properties producerProperties = new Properties();
    producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    //Instantiate Kafka Producer
    final KafkaProducer<String, String> kafkaProducer =
        new KafkaProducer<>(producerProperties, new StringSerializer(), new StringSerializer());
    final TracingKafkaProducer<String, String> tracingKafkaProducer =
        new TracingKafkaProducer<>(kafkaProducer, tracer);

    //Inject dependencies and register endpoint
    final KafkaHelloWorldProducer kafkaHelloWorldProducer =
        new KafkaHelloWorldProducer(tracingKafkaProducer);
    final HelloWorldResource helloWorldResource =
        new HelloWorldResource(dropWizardTracer, kafkaHelloWorldProducer);
    environment.jersey().register(helloWorldResource);
  }
}
