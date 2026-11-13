package com.scheduler.engine.action;

import com.scheduler.domain.schedule.ActionType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ActionHandlerConfig {

    @Bean
    public Map<ActionType, ActionHandler> actionHandlersByType(
            ApiCallActionHandler apiCallActionHandler,
            DisplayOnlyActionHandler displayOnlyActionHandler
    ) {
        return Map.of(
                ActionType.API_CALL, apiCallActionHandler,
                ActionType.DISPLAY_ONLY, displayOnlyActionHandler
        );
    }
}
