package app.ai.chat.feature;

import reactor.core.publisher.Flux;

public interface ChatFeatureService {

    Flux<String> stream(String userMessage);

    //default

}
