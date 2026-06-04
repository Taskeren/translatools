package cn.elytra.translatools.api;

import cn.elytra.translatools.api.handler.Handler;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

@NullMarked
public interface HandlerProvider {

    /**
     * Provide named handlers.
     * The keys are the name of the handler.
     */
    Map<String, Handler<?>> provide();

    /**
     * The loading priority.
     * Higher priority loads earlier.
     */
    default int priority() {
        return 0;
    }

}
