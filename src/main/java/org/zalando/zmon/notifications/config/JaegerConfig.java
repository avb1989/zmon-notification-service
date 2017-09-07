package org.zalando.zmon.notifications.config;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.samplers.*;
import io.opentracing.Tracer;

import java.util.Arrays;
import java.util.List;

public class JaegerConfig implements OpenTracerConfig {

    private String jaegerHost = "localhost";
    private int jaegerPort = 5775;
    private boolean logSpans = true;
    private int flushIntervalMs = 5000;
    private int maxQueueSize = 100;
    private String serviceName;
    private String samplerType=ProbabilisticSampler.TYPE;
    private int samplingRate=1;

    public Tracer generateTracer(){
        Tracer tracer = new Configuration(serviceName,
                new Configuration.SamplerConfiguration(samplerType, samplingRate),
                new Configuration.ReporterConfiguration(logSpans, jaegerHost, jaegerPort, flushIntervalMs, maxQueueSize)).getTracer();
        return tracer;
    }

    public JaegerConfig(OpenTracingConfigProperties config){
        this.jaegerHost = config.getJaegerHost();
        this.jaegerPort = config.getJaegerPort();
        this.logSpans = config.isJaegerLogSpans();
        this.flushIntervalMs = config.getJaegerFlushIntervalMs();
        this.maxQueueSize = config.getJaegerMaxQueueSize();
        this.serviceName = config.getOpenTracingServiceName();
        switch (config.getJaegerSamplerType().toLowerCase()){
            case ConstSampler.TYPE:
                this.samplerType = ConstSampler.TYPE;
            case RateLimitingSampler.TYPE:
                this.samplerType = RateLimitingSampler.TYPE;
            case RemoteControlledSampler.TYPE:
                this.samplerType = RemoteControlledSampler.TYPE;
            default:
                this.samplerType = ProbabilisticSampler.TYPE;
        }
        this.samplingRate=config.getJaegerSamplingRate();
    }
}
