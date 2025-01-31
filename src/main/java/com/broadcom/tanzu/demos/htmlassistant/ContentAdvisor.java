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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;

class ContentAdvisor implements CallAroundAdvisor {
    private final Logger logger = LoggerFactory.getLogger(ContentAdvisor.class);
    private final String contentId;
    private final boolean reuseContent;
    private final StringRedisTemplate redis;

    ContentAdvisor(String contentId, boolean reuseContent, StringRedisTemplate redis) {
        this.contentId = contentId;
        this.reuseContent = reuseContent;
        this.redis = redis;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        final var currentPrompt = redis.opsForValue().get("content::" + contentId + "::prompt");
        if (!StringUtils.hasText(currentPrompt)) {
            throw new IllegalStateException("No prompt found");
        }

        final var previousPrompts = new ArrayList<String>(4);
        String lastContent = null;
        // Collect previous prompts.
        var i = contentId;
        do {
            // Let's see if there's an older content linked to this one.
            final var previousId = redis.opsForValue().get("content::" + i + "::previous");
            if (!StringUtils.hasText(previousId)) {
                // Found no content.
                break;
            }

            // Get previous prompt.
            final var previousPrompt = redis.opsForValue().get("content::" + previousId + "::prompt");
            if (previousPrompt == null) {
                continue;
            }

            // Get last generated content, if any.
            if (lastContent == null && reuseContent) {
                lastContent = redis.opsForValue().get("content::" + previousId + "::source");
            }

            previousPrompts.add(previousPrompt);

            // Prepare for next round.
            i = previousId;
        } while (true);

        final var newPrompt = new StringBuilder(4096);
        if (!previousPrompts.isEmpty()) {
            newPrompt.append("The user previously generated a website using instructions.\n")
                    .append("Please consider these instructions when processing the new website:\n");
            for (final String p : previousPrompts.reversed()) {
                newPrompt.append("instruction: ").append(p).append("\n");
            }
            newPrompt.append("\n");
        }

        newPrompt.append("Process this instruction to generate the new website:\n")
                .append("instruction: ")
                .append(currentPrompt).append("\n")
                .append("\nPlease note that the current year is ").append(LocalDate.now().getYear()).append(" in case you need to generate copyright statements.\n");

        if (lastContent != null) {
            newPrompt.append("\nUse the following HTML page as a starting point to generate the new website:\n\n")
                    .append(lastContent).append("\n");
        }

        final var newPromptStr = newPrompt.toString();
        logger.atDebug().log("New augmented prompt:\n{}", newPromptStr);

        return chain.nextAroundCall(AdvisedRequest.from(advisedRequest)
                .userText(newPromptStr).build());
    }

    @Override
    public String getName() {
        return "ContentAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
