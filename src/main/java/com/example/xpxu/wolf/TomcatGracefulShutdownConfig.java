package com.example.xpxu.wolf;

import com.netflix.discovery.EurekaClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

/**
 *
 * An ApplicationListener to ensure all requests no matter GET or POST
 * will gracefully finish with no need of any retry strategy with caller.
 * Notice that this ApplicationListener works with tomcat server and eureka
 * client together.
 *
 * @author xupeng.
 *
 */
@Configuration
public class TomcatGracefulShutdownConfig {

    /**
     * create a EmbeddedServletContainerCustomizer bean
     */
    @Bean
    public EmbeddedServletContainerCustomizer getTomcatCustomizer(
            final TomcatGracefulShutdownListener tomcatShutdownListener) {

        return new EmbeddedServletContainerCustomizer() {
            @Override
            public void customize(ConfigurableEmbeddedServletContainer container) {
                if (container instanceof TomcatEmbeddedServletContainerFactory) {
                    ((TomcatEmbeddedServletContainerFactory) container)
                            .addConnectorCustomizers(tomcatShutdownListener);
                }
            }
        };
    }

    @Configuration
    class TomcatGracefulShutdownListener implements TomcatConnectorCustomizer,
            ApplicationListener<ContextClosedEvent> {

        private final Logger log = LoggerFactory.getLogger(TomcatGracefulShutdownListener.class);

        private final AtomicBoolean isSleepedAfterEurekaShutdown = new AtomicBoolean(false);

        private volatile Connector connector;

        private static final int PROCESS_TIMEOUT = 30;

        @Value("#{new Integer('${eureka.client.sleep-time-after-shutdown}')}")
        private int sleepTimeAfterEurekaClientShutdown = 10;

        @Autowired
        EurekaClient eurekaClient;

        @Override
        public void customize(Connector connector) {
            this.connector = connector;
        }

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            eurekaClient.shutdown();
            sleepAfterEurekaShutdown(sleepTimeAfterEurekaClientShutdown);
            processRemainedRequests();
        }

        /**
         * sleep a while to wait for all upstream eureka client update local cache for service instance
         * and after which all requests have sent to this service node to make use all upstream caller
         * will never send request to this service node when this method return.
         * @param seconds seconds to sleep
         */
        private void sleepAfterEurekaShutdown(int seconds) {
            if (isSleepedAfterEurekaShutdown.compareAndSet(false, true)) {
                try {
                    log.info("start to sleep a while after eureka shutdown");
                    Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));
                    log.info(String.format("finished sleep %s after eureka shutdown", sleepTimeAfterEurekaClientShutdown));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void processRemainedRequests() {
            this.connector.pause();
            Executor executor = this.connector.getProtocolHandler().getExecutor();
            if (executor instanceof ThreadPoolExecutor) {
                try {
                    ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                    threadPoolExecutor.shutdown();
                    if (!threadPoolExecutor.awaitTermination(PROCESS_TIMEOUT, TimeUnit.SECONDS)) {
                        log.warn("Tomcat thread pool did not shut down gracefully within "
                                + PROCESS_TIMEOUT + " seconds. Proceeding with forceful shutdown");

                        threadPoolExecutor.shutdownNow();

                        if (!threadPoolExecutor.awaitTermination(PROCESS_TIMEOUT, TimeUnit.SECONDS)) {
                            log.error("Tomcat thread pool did not terminate");
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
