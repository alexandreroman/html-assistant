# HTML Assistant

A [Spring Boot](https://spring.io/projects/spring-boot) application that provides
an AI-powered HTML content generation assistant.
This project demonstrates how to integrate AI capabilities with a web interface
to generate HTML content based on user-provided prompts, leveraging
[Spring AI](https://spring.io/projects/spring-ai).

![app.png](app.png)

## Overview

The HTML Assistant is a web-based tool that allows users to generate HTML content through an AI-powered interface.
The application uses Spring Boot and Spring AI as its backend framework and integrates with Redis for state management.
The AI capabilities are provided through a chat client that can be configured to work with different AI providers.

## Features

- AI-powered HTML content generation
- User-friendly web interface
- Redis-based state management
- Configurable AI integration
- Responsive design

## Requirements

- Java 21 or higher
- Docker daemon to run Redis on your workstation
- AI API key (e.g., Mistral AI)

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/html-assistant.git
   ```

2. Build the project using Maven:
   ```bash
   cd html-assistant
   ./mvnw clean package
   ```

3. Configure AI API keys (see Configuration section below).

4. Run the application:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=<AI profile, see below>
   ```

## Configuration

The application has been tested with Mistral AI, DeepSeek, Gemma, and Llama.
DeepSeek, Gemma and Llama are provided through [Groq Cloud](https://groq.com/)
and its OpenAI compatible endpoint.

Mistral AI is used by default: just set environment variable `MISTRALAI_API_KEY`.

For other AI models, you need a Groq API key, defined by the environment variable `GROQ_API_KEY`.

Depending on the AI model you want to run, you need to enable the according Spring profile:

* DeepSeek: `deepseek`
* Gemma: `gemma`
* Llama: `llama`

For instance, run this app with the DeepSeek AI model:

```shell
export GROQ_API_KEY=xxx
./mvnw spring-boot:run -Dspring-boot.run.profiles=deepseek
```

## Usage

1. Start the application and navigate to `http://localhost:8080/assistant` in your web browser.

2. Enter a prompt describing the HTML content you want to generate.

3. Hit "Enter" to create the HTML content.

4. Review the generated content live on your browser.

## Contributing

Contributions are welcome!
If you'd like to contribute to this project, please fork the repository and submit a pull request.

## License

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
