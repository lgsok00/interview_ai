package com.interviewai.interview.generation;

import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.search.RagSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InterviewChatQuestionGeneratorTest {
    private final ChatModel model = mock(ChatModel.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private InterviewChatQuestionGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new InterviewChatQuestionGenerator(model, mapper);
    }

    @Test
    void usesSingleCallAndPersistsExactlyTheSubmittedContext() {
        String json = mapper.writeValueAsString(
                Map.of("questions", InterviewGenerationPolicy.fallback("TEST").questions())
        );
        when(model.call(any(Prompt.class))).thenReturn(response(json));

        var batch = generator.generate(input(), "test-model");
        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());

        Prompt prompt = captor.getValue();
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo(batch.contextSnapshot());

        var options = prompt.getOptions();
        assertThat(options).isInstanceOf(OpenAiChatOptions.class);

        if (!(options instanceof OpenAiChatOptions chatOptions)) {
            throw new AssertionError("OpenAiChatOptions가 필요합니다.");
        }

        assertThat(chatOptions.getModel()).isEqualTo("test-model");
        assertThat(chatOptions.getMaxRetries()).isZero();
        assertThat(chatOptions.getTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(batch.questions()).hasSize(5);

        var context = mapper.readTree(batch.contextSnapshot());
        assertThat(context.get("ragContext").size()).isEqualTo(5);
        assertThat(context.get("sessionSnapshot").get("resumeContent").asString()).isEmpty();
        assertThat(batch.contextSnapshot()).doesNotContain("text".repeat(20000));
        verifyNoMoreInteractions(model);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not json", "{}", "[]", "{\"questions\":[]}",
            "```json\n{}\n```", "{\"questions\":null}"})
    void rejectsMalformedOutputWithoutHiddenRetry(String text) {
        when(model.call(any(Prompt.class))).thenReturn(response(text));
        assertThatThrownBy(() -> generator.generate(input(), "test-model"))
                .isInstanceOf(InterviewGenerationPolicy.GenerationException.class)
                .hasMessage("AI_INVALID_OUTPUT");
        verify(model).call(any(Prompt.class));
    }

    @Test
    void rejectsDuplicateQuestionsAndUnexpectedFields() {
        var questions = new java.util.ArrayList<>(InterviewGenerationPolicy.fallback("TEST").questions());
        questions.set(1, questions.getFirst());
        when(model.call(any(Prompt.class))).thenReturn(response(
                mapper.writeValueAsString(Map.of("questions", questions))));
        assertThatThrownBy(() -> generator.generate(input(), "test-model"))
                .hasMessage("AI_INVALID_OUTPUT");
        when(model.call(any(Prompt.class))).thenReturn(response(mapper.writeValueAsString(
                Map.of("questions", InterviewGenerationPolicy.fallback("TEST").questions(), "extra", true))));
        assertThatThrownBy(() -> generator.generate(input(), "test-model"))
                .hasMessage("AI_INVALID_OUTPUT");
    }

    @Test
    void classifiesEmptyResultsAsInvalidOutput() {
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of()));

        assertThatThrownBy(() -> generator.generate(input(), "test-model"))
                .isInstanceOf(InterviewGenerationPolicy.GenerationException.class)
                .hasMessage("AI_INVALID_OUTPUT");
    }

    @Test
    void propagatesProviderFailureForWorkerClassification() {
        RuntimeException failure = new RuntimeException("provider unavailable");
        when(model.call(any(Prompt.class))).thenThrow(failure);
        assertThatThrownBy(() -> generator.generate(input(), "test-model")).isSameAs(failure);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text == null ? "" : text))));
    }

    private InterviewGenerationInputService.Input input() {
        return new InterviewGenerationInputService.Input("회사", "공고", "Backend",
                "text".repeat(20000), null, null,
                IntStream.range(0, 6).mapToObj(i -> new RagSearchResult(
                        RagSourceType.COMPANY, 1L, "제목", "검색 자료", 0.8, UUID.randomUUID(), i)).toList());
    }
}
