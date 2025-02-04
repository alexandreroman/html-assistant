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

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Controller
class ContentController {
    private final Logger logger = LoggerFactory.getLogger(ContentController.class);
    private final boolean reuseContent;
    private final StringRedisTemplate redis;
    private final ChatClient chatClient;

    ContentController(@Value("${app.content.reuse-content}") boolean reuseContent,
                      ChatClient chatClient, StringRedisTemplate redis) {
        this.reuseContent = reuseContent;
        this.chatClient = chatClient;
        this.redis = redis;
    }

    @GetMapping("/content/site-{id}")
    @ResponseBody
    String generateContent(@PathVariable("id") String contentId,
                           WebRequest req, HttpServletResponse resp) {
        // Enable HTTP cache on client side.
        resp.setHeader(HttpHeaders.CACHE_CONTROL,
                CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable().getHeaderValue());

        final var timestampStr = redis.opsForValue().get("content::" + contentId + "::timestamp");
        long timestamp = 0;
        if (timestampStr != null) {
            try {
                timestamp = ZonedDateTime.parse(timestampStr).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                logger.atWarn().log("Failed to parse timestamp from content {}: {}", contentId, timestampStr, e);
            }
        }

        if (timestamp != 0 && req.checkNotModified(contentId, timestamp)) {
            // The client already has a "cached" content (ETag header is set): let Spring MVC returns a 304.
            logger.atDebug().log("Using cached content: {}", contentId);
            return null;
        }
        final var existingContent = redis.opsForValue().get("content::" + contentId + "::source");
        if (existingContent != null) {
            // We already have the content but the ETag header is not set: let's just return the content as it is.
            logger.atDebug().log("Reusing existing content: {}", contentId);
            return existingContent;
        }

        logger.atInfo().log("Generating content with AI: {}", contentId);
        try {
            final var prompt = redis.opsForValue().get("content::" + contentId + "::prompt");
            if (prompt == null) {
                throw new IllegalArgumentException("Content not found");
            }

            // Call AI model.
            // Note that we do get the output as a plain String, without using a Java entity
            // as some AI models fail to render the output as a JSON construct.
            final var content = sanitizeContent(
                    chatClient.prompt()
                            .user(prompt)
                            .advisors(new ContentAdvisor(contentId, reuseContent, redis))
                            .call().content()
            );

            logger.atInfo().log("Content generated with AI: {}", contentId);
            logger.atTrace().log("About to store content {}:\n{}", contentId, content);
            redis.opsForValue().set("content::" + contentId + "::source", content);

            final var now = ZonedDateTime.now();
            redis.opsForValue().set("content::" + contentId + "::timestamp", now.toString());
            resp.setHeader(HttpHeaders.LAST_MODIFIED, now.format(DateTimeFormatter.RFC_1123_DATE_TIME));

            return content;
        } catch (Exception e) {
            throw new ContentGenerationFailedException(contentId, e);
        }
    }

    private String sanitizeContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("No content generated");
        }
        // Some AI models (such as DeepSeek) don't fully comply with the "output" instructions
        // (such as "just return plain HTML content"): in this case we have to manually clean up
        // the output.
        return content.replaceAll("[\\s\\S]*?</think>", "")
                .replaceAll("^```html", "")
                .replaceAll("```$", "")
                .trim();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.notFound().build();
    }
}
