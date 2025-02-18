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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
class AssistantController {
    private final Logger logger = LoggerFactory.getLogger(AssistantController.class);
    private final ContentConfig config;
    private final StringRedisTemplate redis;

    AssistantController(ContentConfig config, StringRedisTemplate redis) {
        this.config = config;
        this.redis = redis;
    }

    @ModelAttribute("model")
    String getModelName() {
        return config.model();
    }

    @GetMapping("/assistant")
    String newPage(Model model, @ModelAttribute AssistantForm form) {
        return "assistant";
    }

    @GetMapping("/assistant/{id}")
    String getPage(Model model, @ModelAttribute AssistantForm form, @PathVariable("id") String contentId) {
        // Load prompt.
        final var prompt = redis.opsForValue().get("content::" + contentId + "::prompt");
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Content not found");
        }
        // Initialize the form with existing values.
        form.setPreviousContentId(contentId);
        form.setPrompt(prompt);

        // Put the content id in the model to trigger content loading - which may be
        // asynchronously generated as the content is loaded in the iframe.
        model.addAttribute("contentId", contentId);

        return "assistant";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/assistant")
    String submitPrompt(@ModelAttribute AssistantForm form) {
        logger.atDebug().log("Received assistant form: {}", form);

        final var previousContentId = form.getPreviousContentId();
        final var newContentId = UUID.randomUUID().toString();
        final var prompt = form.getPrompt();
        logger.atInfo().log("Submitting prompt for content {}: {}", newContentId, prompt);

        redis.opsForValue().set("content::" + newContentId + "::prompt", prompt);
        if (StringUtils.hasText(previousContentId)) {
            logger.atInfo().log("Linking new content {} to previous content {}", newContentId, previousContentId);
            redis.opsForValue().set("content::" + newContentId + "::previous", previousContentId);
        }

        // Let's redirect the user to a brand new page to avoid "Refresh" issues with POST requests.
        return "redirect:/assistant/" + newContentId;
    }
}
