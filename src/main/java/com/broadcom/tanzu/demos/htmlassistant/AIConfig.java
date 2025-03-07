/*
 * Copyright (c) 2025 Broadcom, Inc. or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.broadcom.tanzu.demos.htmlassistant;

import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
// FIXME workaround for missing metadata in native-image starting with Spring 1.0.0-M6
@RegisterReflectionForBinding(OpenAiChatOptions.class)
class AIConfig {
    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                          @Value("${app.content.prompt}") String systemPrompt) {
        // Set up a default chat client instance for the app.
        return chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();
    }

    @Bean
    CommandLineRunner onStart(ChatModel chatModel) {
        return args -> {
            final var logger = LoggerFactory.getLogger(AIConfig.class);
            logger.atInfo().log("Using chat model: {}", chatModel);
        };
    }
}
